package com.twitter.finagle.postgres.generic

import com.twitter.finagle.postgres.Param
import com.twitter.finagle.postgres.values.ValueEncoder
import patchless._
import shapeless.{HList, LabelledGeneric, Poly1, Witness}
import shapeless.labelled._
import shapeless.ops.hlist.{Mapper, _}

case class Updates (updates: List[(String, Param[_])]) extends QueryParam {
  def params: Seq[Param[_]] = updates.map(_._2)
  def placeholders(start: Int): Seq[String] = updates.zipWithIndex.map {
    case ((col, param), index) => s"$col = $$${index + start}"
  }
  def ++(that: Updates) = Updates(updates ++ that.updates)
  def without(columns: String*) = {
    val cols = columns.toSet
    copy(updates = updates.filter { case (k, _) => !columns.contains(k) })
  }
}

object Updates {
  def apply[O <: HList, MP <: HList](o: O)(implicit
    mapper: Mapper.Aux[updateWithOptions.type, O, MP],
    toList: ToList[MP, Option[(String, Param[_])]],
    columnNamer: ColumnNamer
  ): Updates = new Updates(toList(mapper(o)).flatten.map { case (k, v) => columnNamer(k) -> v})

  def apply[P <: Product, L <: HList, MP <: HList](p: P)(implicit
    gen: LabelledGeneric.Aux[P, L],
    mapper: Mapper.Aux[toLabelledParam.type, L, MP],
    toList: ToList[MP, (String, Param[_])],
    columnNamer: ColumnNamer
  ): Updates = new Updates(toList(mapper(gen.to(p))).map { case (k, v) => columnNamer(k) -> v})

  def apply[T, U <: HList, MP <: HList](patch: Patch[T])(implicit
    patchable: Patchable.Aux[T, U],
    mapper: Mapper.Aux[updateWithOptions.type, U, MP],
    toList: ToList[MP, Option[(String, Param[_])]],
    columnNamer: ColumnNamer
  ): Updates = apply[U, MP](patch.updates.asInstanceOf[U])

  object updateWithOptions extends Poly1 {
    implicit def cases[K <: Symbol, T](implicit
      encoder: ValueEncoder[T],
      name: Witness.Aux[K]
    ) = at[FieldType[K, Option[T]]] {
      opt => opt.map {
        v => name.value.name -> Param(v)
      }
    }
  }
}
