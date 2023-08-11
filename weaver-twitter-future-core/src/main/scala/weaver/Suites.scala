package weaver

import cats.effect.{IO, Resource}
import com.twitter.util.Future

trait BaseTwitterSuite extends EffectSuite.Provider[Future]

abstract class PureTwitterFutureSuite
    extends RunnableSuite[Future]
    with BaseTwitterFutureSuite
    with Expectations.Helpers {

  def pureTest(name: String)(run: => Expectations): Future[TestOutcome] =
    Test[Future](name, Future.apply(run))
  def simpleTest(name: String)(run: Future[Expectations]): Future[TestOutcome] =
    Test[Future](name, run)
  def loggedTest(name: String)(
      run: Log[Future] => Future[Expectations]
  ): Future[TestOutcome] = Test[Future](name, run)

}

abstract class MutableTwitterFutureSuite
    extends MutableFSuite[Future]
    with BaseTwitterFutureSuite
    with Expectations.Helpers

abstract class SimpleMutableTwitterFutureSuite
    extends MutableTwitterFutureSuite {
  type Res = Unit
  def sharedResource: Resource[Future, Unit] = Resource.pure[Future, Unit](())
}

trait FunSuiteTwitterFuture
    extends BaseFunTwitterFutureSuite
    with Expectations.Helpers
