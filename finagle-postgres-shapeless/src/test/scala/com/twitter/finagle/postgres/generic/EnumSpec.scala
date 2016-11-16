package com.twitter.finagle.postgres.generic

import java.nio.charset.StandardCharsets

import com.twitter.finagle.postgres.generic.enumeration.InvalidValue
import com.twitter.finagle.postgres.values.ValueDecoder
import com.twitter.util.{Return, Throw}
import org.jboss.netty.buffer.ChannelBuffers
import org.scalatest.{FlatSpec, Matchers}



class EnumSpec extends FlatSpec with Matchers {

  sealed trait TestEnum
  case object CaseOne extends TestEnum
  case object CaseTwo extends TestEnum

  sealed trait AnotherBranch extends TestEnum
  case object CaseThree extends AnotherBranch

  val UTF8 = StandardCharsets.UTF_8


  "Enum decoding" should "decode enumeration ADTs from strings" in  {

    val decoder = ValueDecoder[TestEnum]

    decoder.decodeText("enum_recv", "CaseOne") shouldEqual Return(CaseOne)
    decoder.decodeText("enum_recv", "CaseTwo") shouldEqual Return(CaseTwo)
    decoder.decodeText("enum_recv", "CaseThree") shouldEqual Return(CaseThree)

    decoder.decodeBinary(
      "enum_recv",
      ChannelBuffers.copiedBuffer("CaseOne", UTF8),
      UTF8
    ) shouldEqual Return(CaseOne)

    decoder.decodeBinary(
      "enum_recv",
      ChannelBuffers.copiedBuffer("CaseTwo", UTF8),
      UTF8
    ) shouldEqual Return(CaseTwo)

    decoder.decodeBinary(
      "enum_recv",
      ChannelBuffers.copiedBuffer("CaseThree", UTF8),
      UTF8
    ) shouldEqual Return(CaseThree)

  }

  it should "fail for an invalid value" in {
    val decoder = ValueDecoder[TestEnum]

    decoder.decodeText("enum_recv", "CasePurple") shouldEqual Throw(InvalidValue("CasePurple"))
    decoder.decodeBinary(
      "enum_recv",
      ChannelBuffers.copiedBuffer("CasePurple", UTF8),
      UTF8
    ) shouldEqual Throw(InvalidValue("CasePurple"))

  }

}
