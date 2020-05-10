package com.twitter.finagle.postgres.generic

import com.twitter.finagle.postgres.Row
import com.twitter.finagle.postgres.values.ValueDecoder
import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RowDecoderSpec extends AnyFlatSpec with Matchers with MockFactory {

  val row = mock[Row]

  "Row decoder" should "decode non-nullables" in {
    case class Foo(int: Int, string: String, numeric: BigDecimal)
    val decoder = RowDecoder[Foo]

    (row.get(_: String)(_: ValueDecoder[Int])) expects ("int", ValueDecoder.int4) returning 10
    (row.get(_: String)(_: ValueDecoder[String])) expects ("string", ValueDecoder.string) returning "ten"
    (row.get(_: String)(_: ValueDecoder[BigDecimal])) expects ("numeric", ValueDecoder.bigDecimal) returning BigDecimal(10.0)

    decoder(row) shouldEqual Foo(10, "ten", 10.0)
  }

  it should "decode nullables" in {
    case class FooWithNulls(int: Int, string: Option[String], numeric: BigDecimal)
    val decoder = RowDecoder[FooWithNulls]

    (row.get(_: String)(_: ValueDecoder[Int])) expects ("int", ValueDecoder.int4) returning 10
    (row.getOption(_: String)(_: ValueDecoder[String])) expects ("string", ValueDecoder.string) returning None
    (row.get(_: String)(_: ValueDecoder[BigDecimal])) expects ("numeric", ValueDecoder.bigDecimal) returning BigDecimal(10.0)

    decoder(row) shouldEqual FooWithNulls(10, None, 10.0)
  }

  it should "decode join results" in {
    case class A(int: Int, string: String)
    case class B(int: Int, bool: Boolean)
    val decoder = RowDecoder[(A, B)]

    (row.get(_: String)(_: ValueDecoder[Int])) expects ("_1.int", ValueDecoder.int4) returning 10
    (row.get(_: String)(_: ValueDecoder[String])) expects ("_1.string", ValueDecoder.string) returning "ten"
    (row.get(_: String)(_: ValueDecoder[Int])) expects ("_2.int", ValueDecoder.int4) returning 20
    (row.get(_: String)(_: ValueDecoder[Boolean])) expects ("_2.bool", ValueDecoder.boolean) returning true

    decoder(row) shouldEqual (A(10, "ten"), B(20, true))
  }
}
