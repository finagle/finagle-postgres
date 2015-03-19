package com.twitter.finagle.postgres.messages

import com.twitter.finagle.postgres.Spec
import com.twitter.finagle.postgres.values.{Charsets, Type, Value}
import org.jboss.netty.buffer.ChannelBuffers

class PgResponsesSpec extends Spec {
  "Field.processFields" should {
    "Extract names from fields" in {
      val fields = IndexedSeq(Field("foo", 0, Type.BOOL), Field("bar", 0, 9999))
      val customTypes = Map("9999" -> "hstore")
      val (fieldNames, _) = Field.processFields(fields, customTypes)

      fieldNames must equal(Seq("foo", "bar"))
    }
  }

  "SelectResult.toRowList" should {
    "Return a `Row` with correct data" in {
      val fields = IndexedSeq(Field("email", 0, Type.VAR_CHAR))
      val row1 = DataRow(IndexedSeq(ChannelBuffers.copiedBuffer("donald@duck.com".getBytes(Charsets.Utf8))))
      val row2 = DataRow(IndexedSeq(ChannelBuffers.copiedBuffer("daisy@duck.com".getBytes(Charsets.Utf8))))
      val rowList = SelectResult(fields, List(row1, row2)).toRowList()

      rowList.size must equal(2)
      rowList(0).fields must equal(Seq("email"))
      rowList(0).vals must equal(Seq((Value("donald@duck.com"))))
      rowList(1).fields must equal(Seq("email"))
      rowList(1).vals must equal(Seq(Value("daisy@duck.com")))
    }
  }
}
