package com.twitter.finagle.postgres.generic

import com.twitter.finagle.postgres.Param
import shapeless.ops.hlist.ToList
import shapeless.ops.record.Keys
import shapeless.{HList, LabelledGeneric}

case class Columns private (names: List[String]) extends QueryParam {
  @inline final def params: Seq[Param[_]] = Seq.empty
  @inline final def placeholders(start: Int): Seq[String] = names

  def as(name: String) =
    copy(names = names.map(n => s"""$name.$n AS "$name.$n""""))
  def join(that: Columns) = Columns(names ++ that.names)
}

object Columns {
  def apply[T <: Product](implicit columnsOf: ColumnsOf[T]) = new Columns(
    columnsOf.columns
  )
}

case class ColumnsOf[T <: Product] private (columns: List[String])

object ColumnsOf {
  implicit def derive[T <: Product, L <: HList, K <: HList](implicit
      gen: LabelledGeneric.Aux[T, L],
      keys: Keys.Aux[L, K],
      toList: ToList[K, Symbol],
      columnNamer: ColumnNamer
  ): ColumnsOf[T] = ColumnsOf[T](toList(keys()).map(s => columnNamer(s.name)))
}
