package weaver.framework.test

import com.twitter.util.Future
import weaver.SimpleTwitterFutureSuite

object SimpleSS extends SimpleTwitterFutureSuite {
  test("Test") {
    Future(expect(true))
  }
}

object aa extends App {
  SimpleSS.runUnsafe(List.empty)(println(_))
}
