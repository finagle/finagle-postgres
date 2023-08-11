package weaver
package framework

import java.io.PrintStream
import com.twitter.util.Future
import weaver.TwitterUnsafeRun.effect

class TwitterFuture(errorStream: PrintStream)
    extends WeaverFramework(
      "twitter-future",
      TwitterFingerprints,
      TwitterUnsafeRun,
      errorStream
    ) {
  def this() = {
    this(System.err)
  }
}

object TwitterFingerprints
    extends WeaverFingerprints.Mixin[
      Future,
      BaseTwitterSuite,
      TwitterFutureGlobalResource
    ]
