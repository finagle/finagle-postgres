package com.twitter.finagle.postgres

import scala.collection.immutable.Queue

import com.twitter.finagle.postgres.generic.enumeration.Enums
import com.twitter.finagle.postgres.values.ValueEncoder
import shapeless.ops.hlist.{
  LeftFolder,
  LiftAll,
  Mapper,
  ToList,
  ToTraversable,
  Zip
}
import shapeless._
import shapeless.labelled.FieldType

package object generic extends Enums {

  implicit class ToShapelessClientOps(val client: PostgresClient)
      extends AnyVal {

    def queryAs[T <: Product](query: String, params: Param[_]*)(implicit
        rowDecoder: RowDecoder[T],
        columnNamer: ColumnNamer
    ) = {
      client.prepareAndQuery(query, params: _*)(row =>
        rowDecoder(row)(columnNamer)
      )
    }

  }

  trait QueryParam {
    def params: Seq[Param[_]]
    def placeholders(start: Int): Seq[String]
  }

  implicit class ToQueryParam[T](v: T)(implicit qp: QueryParams[T])
      extends QueryParam {
    @inline final def params: Seq[Param[_]] = qp(v)
    @inline final def placeholders(start: Int): Seq[String] =
      qp.placeholders(v, start)
  }

  object toParam extends Poly1 {
    implicit def cases[T](implicit encoder: ValueEncoder[T]) =
      at[T](t => Param(t))
  }

  object toLabelledParam extends Poly1 {
    implicit def cases[K <: Symbol, T](implicit
        name: Witness.Aux[K],
        encoder: ValueEncoder[T]
    ) = at[FieldType[K, T]] { t =>
      name.value.name -> Param(t: T)
    }
  }

  implicit class QueryContext(val str: StringContext) extends AnyVal {
    def sql(queryParams: QueryParam*) = {
      val parts =
        if (str.parts.last == "") str.parts.dropRight(1) else str.parts
      val diff = queryParams.length - parts.length
      val pad = if (diff > 0) Seq.fill(diff)("") else Seq.empty
      Query(parts ++ pad, queryParams, identity)
    }
  }

}
