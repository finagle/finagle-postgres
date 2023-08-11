package weaver

import cats.effect.kernel.{Fiber, Outcome}
import cats.{Applicative, Monad, Parallel, ~>}
import cats.effect.{Async, ParallelF}
import com.twitter.util.{Await, Future, Promise, Return, Throw}

import scala.concurrent.Future as ScalaF
import scala.concurrent.Promise as ScalaP

object TwitterUnsafeRun extends TwitterUnsafeRun

/** WARNING: Please dont trust this code u.u. But anyways, you may use in
  * production for bank things like transactions and such, but we (as this org)
  * are not reliable of any damage caused to you or any third party affected in
  * the process.
  *
  * Pls check it for bugs if you can.
  */
trait TwitterUnsafeRun extends UnsafeRun[Future] {
  override type CancelToken = Fiber[Future, Throwable, Unit]

  override def background(task: Future[Unit]): CancelToken = new Fiber[Future, Throwable, Unit] {
    override def cancel: Future[Unit] = Future {
      println("sffsfs")
      //scala.util.Try { task.raise(new Exception("raised!")) }
      println("ssksm")
      ()
    }.handle(_ => Future(()))

    override def join: Future[Outcome[Future, Throwable, Unit]] = {
      val promise = Promise[Outcome[Future, Throwable, Unit]]
      task.respond {
        case Throw(_: InterruptedException) => promise.setValue(cats.effect.Outcome.canceled)
        case Throw(e) => promise.setValue(cats.effect.Outcome.errored(e))
        case Return(r) => promise.setValue(cats.effect.Outcome.succeeded(Future(r)))
      }
      promise
    }
  }

  override def cancel(token: CancelToken): Unit = {
    token.cancel
    ()
  }

  override def unsafeRunSync(task: Future[Unit]): Unit = Await.result(task)

  override def unsafeRunAndForget(task: Future[Unit]): Unit = ()

  override def unsafeRunToFuture(
      task: Future[Unit]
  ): concurrent.Future[Unit] = {
    val promise = ScalaP[Unit]()
    task.respond {
      case Throw(e)  => promise.failure(e)
      case Return(r) => promise.success(r)
    }
    promise.future
  }

  val instance = new AsyncFutureT {}

  override implicit def parallel: Parallel[Future] = new Parallel[Future] {
    override def applicative: Applicative[F] = new Applicative[F] {
      override def pure[A](x: A): F[A] = ParallelF(Future.value(x))

      override def ap[A, B](ff: F[A => B])(fa: F[A]): F[B] =
        ParallelF((ParallelF.value(ff): Future[A => B]).flatMap { f =>
          ParallelF.value(fa).map(f)
        })
    }

    override def monad: Monad[Future] = instance

    override type F[A] = ParallelF[Future, A]

    override def sequential: ~>[F, Future] = new ~>[F, Future] {
      override def apply[A](fa: F[A]): Future[A] =
        ParallelF.value[Future, A](fa)
    }

    override def parallel: ~>[Future, F] = new ~>[Future, F] {
      override def apply[A](fa: Future[A]): F[A] =
        ParallelF.apply[Future, A](fa)
    }
  }

  override implicit def effect: Async[Future] = instance
}
