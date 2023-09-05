package finagle_postgres.skunk

import java.nio.charset.{Charset, StandardCharsets}
import java.util.concurrent.atomic.AtomicBoolean

import cats.data.State
import cats.effect.{IO, Resource}
import com.twitter.concurrent.AsyncStream
import com.twitter.finagle.Status
import com.twitter.finagle.postgres.messages.SelectResult
import com.twitter.finagle.postgres.values.ValueDecoder
import com.twitter.finagle.postgres.{
  OK,
  Param,
  PostgresClient,
  QueryResponse,
  ResultSet,
  Row
}
import com.twitter.util.{Future, Return, Throw, Try}
import skunk.*
import skunk.data.{Completion, Type}
import skunk.util.Origin

/*
 * A Skunk client for communicating with Postgres.
 */

class PostgresClientImpl(sessionR: Resource[IO, Session[IO]])
    extends PostgresClient { self =>
  protected val isActive: AtomicBoolean = new AtomicBoolean(true)
  protected val isTransaction: Boolean = false

  private val alloc: Future[(Session[IO], IO[Unit])] =
    Util.runIO(sessionR.allocated)
  private val sessionFuture = alloc.map(_._1)
  // def as we dont want early execution
  private def finalizer(): Future[Unit] = alloc.flatMap { case (_, f) =>
    Util.runIO(f)
  }
  private val resourceUnreleased: Resource[IO, Session[IO]] =
    Resource.make[IO, Session[IO]](IO.async_ { cb =>
      sessionFuture.respond {
        case Throw(e)  => cb(Left(e))
        case Return(r) => cb(Right(r))
      }
    })(_ => IO.unit)

  override def charset: Charset = StandardCharsets.UTF_8

  override def inTransaction[T](fn: PostgresClient => Future[T]): Future[T] = {
    if (!isTransaction) {
      sessionFuture.flatMap { session =>
        Util
          .runIO(session.transaction.use { _ =>
            IO {
              fn(new PostgresClientImpl(resourceUnreleased) {
                override val isActive: AtomicBoolean = self.isActive
                override val isTransaction: Boolean = true
              })
            }
          })
          .flatten
      }
      // Allow nested transactions, probably a bad idea c:
      // Deal engine be like https://www.youtube.com/watch?v=5fbZTnZDvPA
    } else { fn(self) }
  }

  val decoder: Decoder[Row] = new Decoder[Row] {
    override def types: List[Type] = List.empty

    // INFO: offset is the current column, useful for returning an error with an specific
    // column. See Codec#simple
    // ss is a possible empty list (in that case we have 0 columns) with possible Null values
    // so None is NULL, and Some is a value without being decoded.
    // Converting to row should be trivial.
    // Note that I don't (currently) know how we handle NULL inside Row in the library, so either
    // need more investigation or we can try to just return as JVM's Null
    override def decode(
        offset: Int,
        ss: List[Option[String]]
    ): Either[Decoder.Error, Row] = Right(new Row {
      override def getOption[T](name: String)(implicit
          decoder: ValueDecoder[T]
      ): Option[T] =
        None

      override def getOption[T](index: Int)(implicit
          decoder: ValueDecoder[T]
      ): Option[T] =
        getTry(index).toOption

      override def get[T](name: String)(implicit decoder: ValueDecoder[T]): T =
        throw new UnsupportedOperationException()

      override def get[T](index: Int)(implicit decoder: ValueDecoder[T]): T = {
        decoder.decodeText("", ss(index).orNull).get()
      }

      override def getTry[T](name: String)(implicit
          decoder: ValueDecoder[T]
      ): Try[T] =
        Throw(new UnsupportedOperationException())

      override def getTry[T](index: Int)(implicit
          decoder: ValueDecoder[T]
      ): Try[T] = {
        decoder.decodeText("", ss(index).orNull)
      }

      override def getOrElse[T](name: String, default: => T)(implicit
          decoder: ValueDecoder[T]
      ): T =
        throw new UnsupportedOperationException()

      override def getOrElse[T](index: Int, default: => T)(implicit
          decoder: ValueDecoder[T]
      ): T =
        getTry(index).getOrElse(default)

      override def getAnyOption(name: String): Option[Any] =
        None

      override def getAnyOption(index: Int): Option[Any] =
        Try { ss(index) }.toOption.flatten
    })
  }

  override def query(sql: String): Future[QueryResponse] =
    sessionFuture.flatMap { session =>
      val query: Query[Void, Row] = Query(
        sql = sql,
        origin = Origin.unknown,
        encoder = skunk.Void.codec,
        decoder = decoder,
        isDynamic = true
      )
      Util.runIO(session.execute(query).map { xs =>
        ResultSet(AsyncStream.fromSeq(xs))
      })
    }

  override def fetch(sql: String): Future[SelectResult] = {
    val query: Query[Void, Row] = Query(
      sql = sql,
      origin = Origin.unknown,
      encoder = skunk.Void.codec,
      decoder = decoder,
      isDynamic = true
    )
    Future.exception(new NotImplementedError())
  }

  override def executeUpdate(sql: String): Future[OK] = execute(sql)

  override def execute(sql: String): Future[OK] = sessionFuture.flatMap {
    session =>
      val command: Command[Void] =
        Command(sql = sql, origin = Origin.unknown, encoder = skunk.Void.codec)
      Util.runIO(session.execute(command).map {
        case Completion.Insert(count) => OK(count)
        case Completion.Delete(count) => OK(count)
        case Completion.Select(count) => OK(count)
        case Completion.Update(count) => OK(count)
        case Completion.Copy(count)   => OK(count)
        case _                        => OK(0)
      })
  }

  override def selectToStream[T](sql: String)(f: Row => T): AsyncStream[T] = {
    val query: Query[Void, Row] = Query(
      sql = sql,
      origin = Origin.unknown,
      encoder = skunk.Void.codec,
      decoder = decoder,
      isDynamic = true
    )
    AsyncStream.fromFuture {
      sessionFuture.flatMap { session =>
        Util.runIO(
          fs2IO2Async(session.stream[Void, Row](query)(skunk.Void, 6).map(f))
        )
      }
    }.flatten
  }

  val encoder: Encoder[Seq[Param[_]]] = new Encoder[Seq[Param[_]]] {
    override val sql: State[Int, String] = State { (n: Int) =>
      val len = types.length
      (n + len, (n until n + len).map(i => s"$$$i").mkString(", "))
    }

    override def types: List[Type] = List.empty

    override def encode(a: Seq[Param[_]]): List[Option[String]] = a.map { b =>
      def f[A](ba: Param[A]): Option[String] = {
        ba.encoder.encodeText(ba.value)
      }
      f(b)
    }.toList
  }

  def fs2IO2Async[A](stream: fs2.Stream[IO, A]): IO[AsyncStream[A]] = {
    val head = stream.head
    val tail = stream.tail
    head.compile.last.map {
      case Some(value) =>
        AsyncStream.mk(
          value,
          AsyncStream.fromFuture(Util.runIO(fs2IO2Async(tail))).flatten
        )
      case None => AsyncStream.empty
    }
  }

  override def prepareAndQueryToStream[T](sql: String, params: Param[_]*)(
      f: Row => T
  ): AsyncStream[T] = {
    val query: Query[Seq[Param[_]], Row] = Query(
      sql = sql,
      origin = Origin.unknown,
      encoder = encoder,
      decoder = decoder,
      isDynamic = true
    )

    AsyncStream.fromFuture {
      sessionFuture.flatMap { session =>
        Util.runIO(session.prepare(query).flatMap { pc =>
          fs2IO2Async(pc.stream(params, 6).map(f))
        })
      }
    }.flatten
  }

  override def prepareAndExecute(sql: String, params: Param[_]*): Future[Int] =
    sessionFuture.flatMap { session =>
      val query: Command[Seq[Param[_]]] = Command(
        sql = sql,
        origin = Origin.unknown,
        encoder = encoder
      )

      Util.runIO(session.prepare(query).flatMap { pc =>
        pc.execute(params).map {
          case Completion.Insert(count) => count
          case Completion.Delete(count) => count
          case Completion.Select(count) => count
          case Completion.Update(count) => count
          case Completion.Copy(count)   => count
          case _                        => 0
        }
      })
    }

  /** Close the underlying connection pool and make this Client eternally down
    *
    * @return
    */
  override def close(): Future[Unit] = {
    isActive.set(false)
    finalizer()
  }

  /** The current availability [[Status]] of this client.
    */
  override def status: Status = if (isActive.get()) {
    Status.Open
  } else {
    Status.Closed
  }

  /** Determines whether this client is available (can accept requests with a
    * reasonable likelihood of success).
    */
  override def isAvailable: Boolean = isActive.get()
}
