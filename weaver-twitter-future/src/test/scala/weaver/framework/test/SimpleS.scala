package weaver.framework.test

import com.twitter.util.Future
import weaver.SimpleTwitterFutureSuite

object SimpleS extends SimpleTwitterFutureSuite {
  test("Test") {
    Future(expect(true))
  }
}

object a {}
