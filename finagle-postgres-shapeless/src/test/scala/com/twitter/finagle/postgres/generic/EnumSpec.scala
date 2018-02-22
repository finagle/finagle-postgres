package com.twitter.finagle.postgres.generic

import java.nio.charset.StandardCharsets

import com.twitter.finagle.postgres.generic.enumeration.InvalidValue
import com.twitter.finagle.postgres.values.{ValueDecoder, ValueEncoder}
import com.twitter.util.{Return, Throw}
import io.netty.buffer.{Unpooled, UnpooledByteBufAllocator}

import org.scalatest.{FlatSpec, Matchers}



class EnumSpec extends FlatSpec with Matchers {

  sealed trait TestEnum
  case object CaseOne extends TestEnum
  case object CaseTwo extends TestEnum

  sealed trait AnotherBranch extends TestEnum
  case object CaseThree extends AnotherBranch

  val UTF8 = StandardCharsets.UTF_8

  private val allocator = UnpooledByteBufAllocator.DEFAULT

  "Enum decoding" should "decode enumeration ADTs from strings" in  {

    val decoder = ValueDecoder[TestEnum]

    decoder.decodeText("enum_recv", "CaseOne") shouldEqual Return(CaseOne)
    decoder.decodeText("enum_recv", "CaseTwo") shouldEqual Return(CaseTwo)
    decoder.decodeText("enum_recv", "CaseThree") shouldEqual Return(CaseThree)

    decoder.decodeBinary(
      "enum_recv",
      Unpooled.copiedBuffer("CaseOne", UTF8),
      UTF8
    ) shouldEqual Return(CaseOne)

    decoder.decodeBinary(
      "enum_recv",
      Unpooled.copiedBuffer("CaseTwo", UTF8),
      UTF8
    ) shouldEqual Return(CaseTwo)

    decoder.decodeBinary(
      "enum_recv",
      Unpooled.copiedBuffer("CaseThree", UTF8),
      UTF8
    ) shouldEqual Return(CaseThree)

  }

  it should "fail for an invalid value" in {
    val decoder = ValueDecoder[TestEnum]

    decoder.decodeText("enum_recv", "CasePurple") shouldEqual Throw(InvalidValue("CasePurple"))
    decoder.decodeBinary(
      "enum_recv",
      Unpooled.copiedBuffer("CasePurple", UTF8),
      UTF8
    ) shouldEqual Throw(InvalidValue("CasePurple"))

  }

  "Enum encoding" should "encode enumeration ADTs to Strings" in {
    val encoder = ValueEncoder[TestEnum]
    encoder.encodeText(CaseOne) shouldEqual Some("CaseOne")
    encoder.encodeText(CaseTwo) shouldEqual Some("CaseTwo")
    encoder.encodeText(CaseThree) shouldEqual Some("CaseThree")
    encoder.encodeBinary(CaseOne, UTF8, allocator).get.toString(UTF8) shouldEqual "CaseOne"
    encoder.encodeBinary(CaseTwo, UTF8, allocator).get.toString(UTF8) shouldEqual "CaseTwo"
    encoder.encodeBinary(CaseThree, UTF8, allocator).get.toString(UTF8) shouldEqual "CaseThree"
  }

}
