package weaver

import cats.effect.{IO, Resource}
import com.twitter.util.{Future, Return, Throw}

abstract class PureTwitterFutureSuite extends PureIOSuite {}

abstract class MutableTwitterFutureSuite extends MutableIOSuite {

  class PartiallyAppliedTestFuture(name: TestName) {
    def apply(run: => Future[Expectations]): Unit =
      registerTest(name)(_ => Test(name.name, TwitterCompat.translate(run)))

    def apply(run: Res => Future[Expectations]): Unit =
      registerTest(name)(res =>
        Test(name.name, TwitterCompat.translate(run(res)))
      )

    def -(run: => Future[Expectations]): Unit = apply(run)
    def -(run: Res => Future[Expectations]): Unit = apply(run)

    // this alias helps using pattern matching on `Res`
    def usingRes(run: Res => Future[Expectations]): Unit = apply(run)
  }

  def future(name: TestName): PartiallyAppliedTestFuture =
    new PartiallyAppliedTestFuture(name)
}

abstract class SimpleMutableTwitterFutureSuite
    extends MutableTwitterFutureSuite {
  type Res = Unit
  def sharedResource: Resource[IO, Unit] = Resource.pure[IO, Unit](())
}

trait FunSuiteTwitterFuture extends FunSuiteIO

object TwitterCompat {
  def translate[A](future: => Future[A]): IO[A] = {
    IO.async { cb =>
      future.respond {
        case Throw(e)  => cb(Left(e))
        case Return(r) => cb(Right(r))
      }
      IO.delay {
        Some {
          IO.delay {
            future.raise(new InterruptedException())
          }
        }
      }
    }
  }
}
