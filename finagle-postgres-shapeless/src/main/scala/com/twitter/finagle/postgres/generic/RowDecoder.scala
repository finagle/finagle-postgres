package com.twitter.finagle.postgres.generic

import com.twitter.finagle.postgres.Row
import com.twitter.finagle.postgres.values.ValueDecoder
import shapeless._
import shapeless.labelled.{FieldType, field}

trait RowDecoder[T] {
  @inline def apply(row: Row)(implicit columnNamer: ColumnNamer): T
}

object RowDecoder extends RowDecoder0 {

  def apply[T : RowDecoder] = implicitly[RowDecoder[T]]

  // Recursive base case for decoding rows
  implicit object hnil extends RowDecoder[HNil] {
    @inline final def apply(row: Row)(implicit columnNamer: ColumnNamer) = HNil
  }

  // Optional (nullable) values
  class HConsOption[K <: Symbol, H, T <: HList](implicit
    colName: Witness.Aux[K],
    decodeHead: ValueDecoder[H],
    decodeTail: RowDecoder[T]
  ) extends RowDecoder[FieldType[K, Option[H]] :: T] {
    @inline final def apply(row: Row)(implicit columnNamer: ColumnNamer): FieldType[K, Option[H]] :: T = {
      field[K](row.getOption[H](columnNamer(colName.value.name))) :: decodeTail(row)(columnNamer)
    }
  }

  implicit def hconsOption[K <: Symbol, H, T <: HList](implicit
    colName: Witness.Aux[K],
    decodeHead: ValueDecoder[H],
    decodeTail: RowDecoder[T]
  ): RowDecoder[FieldType[K, Option[H]] :: T] = new HConsOption

  // Generic decoder for any case class which has a LabelledGeneric
  class GenericProduct[T <: Product, L <: HList](implicit
    gen: LabelledGeneric.Aux[T, L],
    decodeL: RowDecoder[L]
  ) extends RowDecoder[T] {
    @inline final def apply(row: Row)(implicit columnNamer: ColumnNamer): T = gen.from(decodeL(row)(columnNamer))
  }

  implicit def genericProduct[T <: Product, L <: HList](implicit
    gen: LabelledGeneric.Aux[T, L],
    decodeL: RowDecoder[L]
  ): RowDecoder[T] = new GenericProduct[T, L]

}

trait RowDecoder0 {

  // Non-optional values (prioritized here underneath optional values)
  class HCons[K <: Symbol, H, T <: HList](implicit
    colName: Witness.Aux[K],
    decodeHead: ValueDecoder[H],
    decodeTail: RowDecoder[T]
  ) extends RowDecoder[FieldType[K, H] :: T] {
    @inline final def apply(row: Row)(implicit columnNamer: ColumnNamer): FieldType[K, H] :: T = {
      field[K](row.get[H](columnNamer(colName.value.name))) :: decodeTail(row)(columnNamer)
    }
  }

  implicit def hcons[K <: Symbol, H, T <: HList](implicit
    colName: Witness.Aux[K],
    decodeHead: ValueDecoder[H],
    decodeTail: RowDecoder[T]
  ): RowDecoder[FieldType[K, H] :: T] = new HCons

}

