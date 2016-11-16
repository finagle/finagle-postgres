package com.twitter.finagle.postgres.generic

import com.twitter.finagle.postgres.{PostgresClient, Param, Row}
import com.twitter.util.Future

import scala.language.existentials

case class Query[T](sql: String, params: Seq[Param[A] forSome { type A }], cont: Row => T) {
  def run(client: PostgresClient): Future[Seq[T]] =
    client.prepareAndQuery[T](sql, params: _*)(cont)

  def exec(client: PostgresClient): Future[Int] = client.prepareAndExecute(sql, params: _*)

  def map[U](fn: T => U): Query[U] = copy(cont = cont andThen fn)

  def as[U](implicit rowDecoder: RowDecoder[U], columnNamer: ColumnNamer): Query[U] = {
    copy(cont = row => rowDecoder(row)(columnNamer))
  }
}
