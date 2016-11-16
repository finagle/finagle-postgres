package com.twitter.finagle.postgres

import scala.collection.immutable.Queue

import com.twitter.finagle.postgres.generic.enumeration.Enums
import shapeless.ops.hlist.{LeftFolder, LiftAll, ToTraversable, Zip}
import shapeless._

package object generic extends Enums {

  implicit class ToShapelessClientOps(val client: PostgresClientImpl) extends AnyVal {

    def queryAs[T <: Product](query: String, params: Param[_]*)(implicit
      rowDecoder: RowDecoder[T],
      columnNamer: ColumnNamer
    ) = {
      client.prepareAndQuery(query, params: _*)(row => rowDecoder(row)(columnNamer))
    }

  }

  implicit class ToQueryParam[T](v: T)(implicit qp: QueryParams[T]) {
    def params: Seq[Param[_]] = qp(v)
    def placeholders(start: Int): Seq[String] = qp.placeholders(v, start)
  }

  implicit class QueryContext(val str: StringContext) extends AnyVal {
    def sql(params: ToQueryParam[_]*) = {
      val placeholders = params.foldLeft((1, Queue.empty[Seq[String]])) {
        case ((start, accum), next) =>
          val nextParams = next.placeholders(start)
          (start + nextParams.length, accum enqueue nextParams)
      }._2
      val queryString = str.parts.zipAll(placeholders, "", Seq.empty).flatMap {
        case (part, ph) => Seq(part, ph.mkString(", "))
      }.mkString

      Query(
        queryString,
        params.flatMap(p => p.params),
        identity)
    }
  }

}
