package finagle_postgres.skunk

import cats.effect.IO
import cats.effect.unsafe.{IORuntime, IORuntimeConfig, Scheduler}
import com.twitter.util.{
  Duration,
  Future,
  FuturePool,
  JavaTimer,
  Monitor,
  Promise
}

import java.time.Instant
import java.time.temporal.ChronoField
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

object Util {

  private val computeEC: ExecutionContext =
    new ExecutionContext {
      def execute(runnable: Runnable): Unit = Future(runnable.run())

      def reportFailure(cause: Throwable): Unit = Monitor.handle(cause)
    }

  private val blockingEC: ExecutionContext =
    new ExecutionContext {
      def execute(runnable: Runnable): Unit =
        FuturePool.unboundedPool(runnable.run())

      def reportFailure(cause: Throwable): Unit = Monitor.handle(cause)
    }

  // From cats
  private val scheduler: Scheduler = new Scheduler {
    override def sleep(delay: FiniteDuration, task: Runnable): Runnable =
      () =>
        Future.Unit.flatMap { _ =>
          implicit val timer: JavaTimer = new JavaTimer()
          Future.sleep(Duration.fromMilliseconds(delay.toMillis)).flatMap { _ =>
            Future { task.run() }
          }
        }

    override def nowMillis(): Long = System.currentTimeMillis()

    override def monotonicNanos(): Long = {
      val now = Instant.now()
      now.getEpochSecond * 1000000 + now.getLong(ChronoField.MICRO_OF_SECOND)
    }
  }

  private val config: IORuntimeConfig = IORuntimeConfig()
  private val runtime: IORuntime =
    IORuntime.apply(
      compute = computeEC,
      blocking = blockingEC,
      scheduler = scheduler,
      shutdown = () => (),
      config = config
    )

  /** Runs a Cats IO task in a Twitter Future context. */
  def runIO[A](io: IO[A]): Future[A] = {
    val promise: Promise[A] = Promise[A]()

    io.unsafeRunAsync {
      case Left(e)  => promise.setException(e)
      case Right(a) => promise.setValue(a)
    }(runtime)

    promise
  }
}
