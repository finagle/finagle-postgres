package object weaver {

  /** Extend this when each test in the suite returns an `Resource[IO, Res]` for
    * some shared resource `Res`
    */
  type TwitterFutureSuite = MutableTwitterFutureSuite

  /** Extend this when each test in the suite returns an `IO[_]`
    */
  type SimpleTwitterFutureSuite = SimpleMutableTwitterFutureSuite

  /** Extend this when each test in the suite is pure and does not return
    * `IO[_]`
    */
  type FunSuite = FunSuiteTwitterFuture
}
