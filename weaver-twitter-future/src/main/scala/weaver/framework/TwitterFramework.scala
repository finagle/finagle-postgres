package weaver
package framework

import cats.effect.IO

import java.io.PrintStream

class TwitterFuture(errorStream: PrintStream)
    extends WeaverFramework(
      "twitter-future",
      TwitterFingerprints,
      CatsUnsafeRun,
      errorStream
    ) {
  def this() = {
    this(System.err)
  }
}

object TwitterFingerprints
    extends WeaverFingerprints.Mixin[
      IO,
      BaseIOSuite,
      IOGlobalResource
    ]
