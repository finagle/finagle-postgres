package weaver
package framework
package test

import cats.implicits.toFunctorOps

import scala.concurrent.duration.*

abstract class MutableSuiteTest extends SimpleTwitterFutureSuite {

  pureTest("23 is odd") {
    expect(23 % 2 == 1)
  }

  test("sleeping") {
    for {
      before <- TwitterUnsafeRun.realTimeMillis
      _ <- TwitterUnsafeRun.sleep(1.seconds)
      after <- TwitterUnsafeRun.realTimeMillis
    } yield expect(after - before >= 1000)
  }

  pureTest("23 is odd") {
    expect(23 % 2 == 1)
  }

  loggedTest("logged") { log =>
    log.info("hello").as(success)
  }
}

object MutableSuiteTest extends MutableSuiteTest
