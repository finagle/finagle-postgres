package com.twitter.finagle.postgres.generic

import com.twitter.finagle.postgres.Param
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import patchless.Patch
import shapeless.record.Record

class DSLSpec extends AnyFreeSpec with Matchers {

  "Columns" - {

    "should snake case by default" in {
      case class Camels(camelOne: String, camelTwoWords: Int, camelWith2NumbersAnd5Words: Boolean)
      Columns[Camels].names shouldEqual List("camel_one", "camel_two_words", "camel_with_2_numbers_and_5_words")
    }

  }

  "Updates" - {

    "should recover updates from record of options" in {
      val record = Record(foo = Some(22):Option[Int], bar = None:Option[String])
      Updates(record) shouldEqual Updates(List("foo" -> Param(22)))
    }

    "should recover updates from a case class" in {
      case class Foo(foo: String, bar: Int)
      val foo = Foo("hello", 22)
      Updates(foo) shouldEqual Updates(List("foo" -> Param("hello"), "bar" -> Param(22)))
    }

    "should recover updates from a patchless Patch" in {
      case class Foo(foo: String, bar: Int)
      val a = Foo("hello", 22)
      val b = Foo("updated", 22)
      val patch = Patch.diff(a, b)
      Updates(patch) shouldEqual Updates(List("foo" -> Param("updated")))
    }

    "uses snake case namer by default" in {
      val record = Record(fooCamel = Some(22):Option[Int], barCamel = None:Option[String])
      Updates(record) shouldEqual Updates(List("foo_camel" -> Param(22)))

      case class Foo(fooCamel: String, barCamel: Int)
      val foo = Foo("hello", 22)
      Updates(foo) shouldEqual Updates(List("foo_camel" -> Param("hello"), "bar_camel" -> Param(22)))
    }

  }

}
