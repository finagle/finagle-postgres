package io.getquill

import com.twitter.util.Future
import weaver.*

object Test extends SimpleMutableTwitterFutureSuite {
  future("sdsd") - Future(expect(true))
}
