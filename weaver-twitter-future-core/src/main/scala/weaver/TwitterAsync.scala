package weaver

import cats.MonadError
import cats.data.State
import cats.effect.implicits.monadCancelOps_
import cats.effect.kernel.Sync
import cats.effect.kernel.Sync.Type
import cats.effect.{Async, Cont, Deferred, Fiber, Outcome, Poll, Ref}
import org.typelevel.catbird.util.twitterFutureInstance
import org.typelevel.catbird.util.FutureInstances
import org.typelevel.catbird.util.FutureMonadError
import cats.syntax.all.*
import cats.instances.all.*
import com.twitter.util.{Future, Return, Throw, Try}

import java.util.concurrent.AbstractExecutorService
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}
import scala.concurrent.duration.{FiniteDuration, MILLISECONDS, NANOSECONDS}

trait MonadErrorT extends MonadError[Future, Throwable] {
  override def raiseError[A](e: Throwable): Future[A] =
    twitterFutureInstance.raiseError(e)

  override def handleErrorWith[A](fa: Future[A])(
      f: Throwable => Future[A]
  ): Future[A] =
    twitterFutureInstance.handleErrorWith(fa)(f)

  override def pure[A](x: A): Future[A] = twitterFutureInstance.pure(x)

  override def flatMap[A, B](fa: Future[A])(f: A => Future[B]): Future[B] =
    twitterFutureInstance.flatMap(fa)(f)

  override def tailRecM[A, B](a: A)(f: A => Future[Either[A, B]]): Future[B] =
    twitterFutureInstance.tailRecM(a)(f)
}

/** WARNING: Please dont trust this code u.u. But anyways, you may use in
  * production for bank things like transactions and such, but we (as this org)
  * are not reliable of any damage caused to you or any third party affected in
  * the process.
  *
  * Pls check it for bugs if you can.
  */
class AsyncFutureT extends Async[Future] with MonadErrorT { self =>
  override def evalOn[A](fa: Future[A], ec: ExecutionContext): Future[A] =
    fa

  override def executionContext: Future[ExecutionContext] = Future {
    scala.concurrent.ExecutionContext.global
  }

  override def cont[K, R](body: Cont[Future, K, R]): Future[R] =
    Async.defaultCont(body)(this)

  override def async[A](
      k: (Either[Throwable, A] => Unit) => Future[Option[Future[Unit]]]
  ): Future[A] = {
    val p = com.twitter.util.Promise[Either[Throwable, A]]

    def get: Future[A] = p
      .map[Try[A]] { e =>
        Try.fromScala(e.toTry)
      }
      .lowerFromTry[A]

    Future {
      k({ e => p.setValue(e) }).flatMap {
        case Some(canceler) => onCancel(get, canceler)
        case None           => get
      }
    }
  }.flatten.interruptible()

  override protected def sleep(time: FiniteDuration): Future[Unit] = {
    import com.twitter.util.{Duration, JavaTimer}
    import scala.concurrent.duration.{FiniteDuration, Duration => SDuration}

    time match {
      case SDuration(l, u) =>
        implicit val timer: JavaTimer = new JavaTimer(true, None)
        Future.sleep(Duration(l, u))
    }
  }

  override def ref[A](a: A): Future[Ref[Future, A]] = delay {
    new Ref[Future, A] {
      private val var_ = com.twitter.util.Var[A](a)

      override def access: Future[(A, A => Future[Boolean])] = get.map { a =>
        val p = new Function[A, Future[Boolean]] {
          private val b_ = com.twitter.util.Var[Boolean](true)
          override def apply(v1: A): Future[Boolean] = if (b_.sample()) {
            b_.update(false)
            set(v1).map(_ => true)
          } else { pure(false) }
        }
        (a, p)
      }

      override def tryUpdate(f: A => A): Future[Boolean] =
        update(f).liftToTry.map {
          case Throw(e)  => false
          case Return(r) => true
        }

      override def tryModify[B](f: A => (A, B)): Future[Option[B]] =
        modify(f).liftToTry.map {
          case Throw(e)  => None
          case Return(r) => Some(r)
        }

      override def update(f: A => A): Future[Unit] = get.flatMap(set)

      override def modify[B](f: A => (A, B)): Future[B] =
        get.map(f).flatMap { case (a, b) =>
          set(a) >> pure(b)
        }

      override def tryModifyState[B](state: State[A, B]): Future[Option[B]] =
        tryModify(state.run(_).value)

      override def modifyState[B](state: State[A, B]): Future[B] =
        modify(state.run(_).value)

      override def get: Future[A] = delay { var_.sample() }

      override def set(a: A): Future[Unit] = delay { var_.update(a) }
    }
  }

  override def deferred[A]: Future[Deferred[Future, A]] = ???

  override def start[A](fa: Future[A]): Future[Fiber[Future, Throwable, A]] =
    delay {
      new Fiber[Future, Throwable, A] {

        private val service = scala.concurrent.ExecutionContext.global match {
          case null                                  => throw null
          case eces: ExecutionContextExecutorService => eces
          case other =>
            new AbstractExecutorService with ExecutionContextExecutorService {

              override def isShutdown = false

              override def isTerminated = false

              override def shutdown(): Unit = ()

              override def shutdownNow(): java.util.List[Runnable] =
                java.util.Collections.emptyList[Runnable]

              override def execute(runnable: Runnable): Unit =
                other execute runnable

              override def reportFailure(t: Throwable): Unit =
                other reportFailure t

              override def awaitTermination(
                  length: Long,
                  unit: java.util.concurrent.TimeUnit
              ): Boolean = false
            }
        }

        private val futureDetached =
          com.twitter.util.FuturePool.apply(service).apply(fa)

        override def cancel: Future[Unit] = Async[Future](self).canceled

        override def join: Future[Outcome[Future, Throwable, A]] =
          futureDetached.flatten.liftToTry.map {
            case Throw(FutureCatsInterrupt()) => Outcome.canceled
            case Throw(e)                     => Outcome.errored(e)
            case Return(r)                    => Outcome.succeeded(pure(r))
          }
      }
    }

  override def cede: Future[Unit] = Future.Unit

  override def suspend[A](hint: Sync.Type)(thunk: => A): Future[A] =
    Future.apply(thunk)

  override def forceR[A, B](fa: Future[A])(fb: Future[B]): Future[B] = {
    fa.flatMap(_ => fb).rescue(_ => fb)
  }

  override def uncancelable[A](body: Poll[Future] => Future[A]): Future[A] = {
    val o = new Poll[Future] {
      override def apply[A](fa: Future[A]): Future[A] = fa.interruptible()
    }
    body(o)
  }

  override def canceled: Future[Unit] = delay {
    unit.raise(FutureCatsInterrupt())
  }

  override def onCancel[A](fa: Future[A], fin: Future[Unit]): Future[A] =
    fa.onFailure {
      case FutureCatsInterrupt() => fin
      case _                     => unit
    }

  private case class FutureCatsInterrupt() extends InterruptedException

  override def monotonic: Future[FiniteDuration] = {
    import com.twitter.util.Time
    delay {
      FiniteDuration.apply(Time.nowNanoPrecision.inNanoseconds, NANOSECONDS)
    }
  }

  override def realTime: Future[FiniteDuration] = {
    import com.twitter.util.Time
    delay {
      FiniteDuration.apply(Time.nowNanoPrecision.inMillis, MILLISECONDS)
    }
  }
}
