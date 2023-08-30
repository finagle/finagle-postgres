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

  /** Runs a Cats IO task in a Twitter Future context. */
  def runIO[A](io: IO[A]): Future[A] = {
    val computeEC: ExecutionContext =
      new ExecutionContext {
        def execute(runnable: Runnable): Unit = Future(runnable.run())

        def reportFailure(cause: Throwable): Unit = Monitor.handle(cause)
      }

    val blockingEC: ExecutionContext =
      new ExecutionContext {
        def execute(runnable: Runnable): Unit =
          FuturePool.unboundedPool(runnable.run())

        def reportFailure(cause: Throwable): Unit = Monitor.handle(cause)
      }

    val scheduler: Scheduler = new Scheduler {
      override def sleep(delay: FiniteDuration, task: Runnable): Runnable =
        new Runnable {
          override def run(): Unit = Future.Unit.flatMap { _ =>
            implicit val timer: JavaTimer = new JavaTimer()
            Future.sleep(Duration.fromMilliseconds(delay.toMillis))
          }
        }

      override def nowMillis(): Long = System.currentTimeMillis()

      override def monotonicNanos(): Long = {
        val now = Instant.now()
        now.getEpochSecond * 1000000 + now.getLong(ChronoField.MICRO_OF_SECOND)
      }
    }

    val config: IORuntimeConfig = IORuntimeConfig()
    val runtime: IORuntime =
      IORuntime.apply(
        compute = computeEC,
        blocking = blockingEC,
        scheduler = scheduler,
        shutdown = () => (),
        config = config
      )

    val promise: Promise[A] = Promise[A]()

    io.unsafeRunAsync {
      case Left(e)  => promise.setException(e)
      case Right(a) => promise.setValue(a)
    }(runtime)

    promise
  }
}
