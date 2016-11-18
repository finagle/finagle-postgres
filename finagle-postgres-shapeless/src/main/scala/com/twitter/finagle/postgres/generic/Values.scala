package com.twitter.finagle.postgres.generic

import com.twitter.finagle.postgres.Param
import shapeless.ops.hlist.{Length, Mapper, ToTraversable}
import shapeless.ops.nat.ToInt
import shapeless.{HList, LabelledGeneric, Nat}

case class Values private (params: Seq[Param[_]], length: Int) extends QueryParam {
  def placeholders(start: Int): Seq[String] = (start until (start + length)).map(i => s"$$$i")
}

object Values {
  def apply[T <: Product, L <: HList, V <: HList, P <: HList, N <: Nat](obj: T)(implicit
    gen: LabelledGeneric.Aux[T, L],
    values: shapeless.ops.record.Values.Aux[L, V],
    mapper: Mapper.Aux[toParam.type, V, P],
    length: Length.Aux[L, N],
    toInt: ToInt[N],
    toTraversable: ToTraversable.Aux[P, Seq, Param[_]]
  ): Values = Values(toTraversable(mapper(values(gen.to(obj)))), toInt())
}