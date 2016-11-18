package com.twitter.finagle.postgres.generic

import com.twitter.finagle.postgres.Param
import com.twitter.finagle.postgres.values.ValueEncoder
import shapeless.{HList, LabelledGeneric, Poly1, Witness}
import shapeless.labelled._
import shapeless.ops.hlist.{Mapper, _}

case class Updates (updates: List[(String, Param[_])]) extends QueryParam {
  def params: Seq[Param[_]] = updates.map(_._2)
  def placeholders(start: Int): Seq[String] = updates.zipWithIndex.map {
    case ((col, param), index) => s"$col = $$${index + start}"
  }
}

object Updates {
  def apply[O <: HList, MP <: HList](o: O)(implicit
    mapper: Mapper.Aux[updateWithOptions.type, O, MP],
    toList: ToList[MP, Option[(String, Param[_])]]
  ): Updates = new Updates(toList(mapper(o)).flatten)

  def apply[P <: Product, L <: HList, MP <: HList](p: P)(implicit
    gen: LabelledGeneric.Aux[P, L],
    mapper: Mapper.Aux[toLabelledParam.type, L, MP],
    toList: ToList[MP, (String, Param[_])],
    columnNamer: ColumnNamer
  ): Updates = new Updates(toList(mapper(gen.to(p))).map { case (k, v) => columnNamer(k) -> v})

  object updateWithOptions extends Poly1 {
    implicit def cases[K <: Symbol, T](implicit
      encoder: ValueEncoder[T],
      name: Witness.Aux[K],
      columnNamer: ColumnNamer
    ) = at[FieldType[K, Option[T]]] {
      opt => opt.map {
        v => columnNamer(name.value.name) -> Param(v)
      }
    }
  }
}
