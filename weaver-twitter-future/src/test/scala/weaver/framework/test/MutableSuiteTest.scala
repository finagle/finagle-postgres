package weaver
package framework
package test

import com.twitter.conversions.DurationOps.richDurationFromInt
import com.twitter.util.{Duration, Future, JavaTimer, Time}

abstract class MutableSuiteTest extends SimpleTwitterFutureSuite {

  pureTest("23 is odd") {
    expect(23 % 2 == 1)
  }

  future("sleeping") {
    for {
      before <- Future(Time.nowNanoPrecision)
      _ <- Future.sleep(1.seconds)(new JavaTimer())
      after <- Future(Time.nowNanoPrecision)
    } yield expect(after - before >= Duration.fromMilliseconds(1000))
  }

  pureTest("23 is odd") {
    expect(23 % 2 == 1)
  }

  loggedTest("logged") { log =>
    log.info("hello").as(success)
  }
}

object MutableSuiteTest extends MutableSuiteTest
