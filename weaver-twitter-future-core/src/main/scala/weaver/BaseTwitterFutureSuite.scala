package weaver

import com.twitter.util.Future

trait BaseTwitterFutureSuite
    extends RunnableSuite[Future]
    with BaseTwitterSuite {
  implicit protected def effectCompat: UnsafeRun[Future] = TwitterUnsafeRun

  def getSuite: EffectSuite[Future] = this
}

trait BaseFunTwitterFutureSuite
    extends FunSuiteF[Future]
    with BaseTwitterSuite {
  implicit protected def effectCompat: UnsafeRun[Future] = TwitterUnsafeRun

  override def getSuite: EffectSuite[Future] = this
}
