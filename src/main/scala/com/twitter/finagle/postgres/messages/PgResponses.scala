package com.twitter.finagle.postgres.messages

import com.twitter.finagle.postgres.Row
import com.twitter.finagle.postgres.values.{ValueParser, Value}

import org.jboss.netty.buffer.ChannelBuffer

/*
 * Response message types.
 */
trait PgResponse

case class SingleMessageResponse(msg: BackendMessage) extends PgResponse

case class Error(msg: Option[String], severity: Option[String] = None, sqlState: Option[String] = None, detail: Option[String] = None, hint: Option[String] = None, position: Option[String] = None) extends PgResponse

object Error {
  def apply(params: Map[Char,String]): Error =
    Error(params.get('M'), params.get('S'), params.get('C'), params.get('D'), params.get('H'), params.get('P'))
}

case object SslSupportedResponse extends PgResponse

case object SslNotSupportedResponse extends PgResponse

case object ParseCompletedResponse extends PgResponse

case object BindCompletedResponse extends PgResponse

case object ReadyForQueryResponse extends PgResponse

sealed trait PasswordEncoding

object ClearText extends PasswordEncoding

case class Md5(salt: Array[Byte]) extends PasswordEncoding

case class PasswordRequired(encoding: PasswordEncoding) extends PgResponse

case class AuthenticatedResponse(params: Map[String, String], processId: Int, secretKey: Int) extends PgResponse

case class Rows(rows: List[DataRow], completed: Boolean) extends PgResponse

object Field {
  /*
   * Extract an `IndexSeq[Field]` into a tuple containing
   * corresponding field-names and field-parsing functions.
   *
   * @param fields The `Field`s to be processed.
   * @param customTypes A `Map` containing name->type pairs representing custom
   * value types.
   */
  private[postgres] def processFields(
    fields: IndexedSeq[Field],
    customTypes: Map[String, String]
  ): (IndexedSeq[String], IndexedSeq[ChannelBuffer => Value[Any]]) = {
    val names = fields.map(f => f.name)
    val parsers = fields.map(f => ValueParser.parserOf(f.format, f.dataType, customTypes))

    (names, parsers)
  }
}

case class Field(name: String, format: Int, dataType: Int)

case class RowDescriptions(fields: IndexedSeq[Field]) extends PgResponse

case class Descriptions(params: IndexedSeq[Int], fields: IndexedSeq[Field]) extends PgResponse

case class ParamsResponse(types: IndexedSeq[Int]) extends PgResponse

case class SelectResult(fields: IndexedSeq[Field], rows: List[DataRow]) extends PgResponse {
  /*
   * Returns this `SelectResult` as a list of `Row`s.
   *
   * @param customTypes A `Map` containing name->type pairs representing custom
   * value types.
   */
  def toRowList(customTypes: Map[String, String] = Map.empty): List[Row] = {
    val (fieldNames, fieldParsers) = Field.processFields(fields, customTypes)

    rows.map(dataRow => new Row(fieldNames, dataRow.data.zip(fieldParsers).map {
      case (d, p) => if (d == null) null else p(d)
    }))
  }
}

case class CommandCompleteResponse(affectedRows: Int) extends PgResponse
