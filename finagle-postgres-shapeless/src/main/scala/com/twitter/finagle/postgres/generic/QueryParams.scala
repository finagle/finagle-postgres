package com.twitter.finagle.postgres.generic

import scala.annotation.implicitNotFound
import scala.collection.GenTraversable

import com.twitter.finagle.postgres.Param
import com.twitter.finagle.postgres.values.ValueEncoder
import shapeless.ops.hlist.{
  Length,
  LiftAll,
  Mapper,
  ToList,
  ToTraversable,
  Tupler,
  Unifier,
  Zip
}
import shapeless.ops.nat.ToInt
import shapeless.ops.record.Keys
import shapeless.{
  Generic,
  HList,
  HNil,
  LUBConstraint,
  LabelledGeneric,
  Nat,
  Poly1
}

/** Typeclass allowing conversion of a type T into a sequence of postgres
  * parameters Used for quoting lists, tuples, etc in a query (i.e. "WHERE foo
  * IN ${("A", "B")})
  */
@implicitNotFound(
  """Could not represent the given value(s) of type ${T} as query parameters. The value must either be a scalar with a ValueEncoder instance, a Seq whose type parameter has a ValueEncoder instance, or a homogeneous tuple whose type has a ValueEncoder instance."""
)
trait QueryParams[T] {
  def apply(t: T): Seq[Param[_]]
  def placeholders(t: T, start: Int): Seq[String]
}

object QueryParams extends QueryParams0 {
  implicit def seq[F[A] <: Seq[A], T](implicit
      encoder: ValueEncoder[T]
  ): QueryParams[F[T]] = new QueryParams[F[T]] {
    @inline final def apply(ts: F[T]): Seq[Param[_]] = ts.map(t => Param(t))
    @inline final def placeholders(ts: F[T], start: Int) =
      (start until (start + ts.length)).map(i => s"$$$i")
  }
}

trait QueryParams0 extends QueryParams1 { self: QueryParams.type =>

  implicit def tuple[A <: Product, L <: HList, P <: HList, N <: Nat](implicit
      gen: Generic.Aux[A, L],
      length: Length.Aux[L, N],
      tupler: Tupler.Aux[L, A],
      toInt: ToInt[N],
      mapper: Mapper.Aux[toParam.type, L, P],
      toTraversable: ToTraversable.Aux[P, Seq, Param[_]]
  ): QueryParams[A] = new QueryParams[A] {
    @inline final def apply(a: A): Seq[Param[_]] = toTraversable(
      mapper(gen.to(a))
    )
    @inline final def placeholders(a: A, start: Int) =
      (start until (start + toInt())).map(i => s"$$$i")
  }

}

trait QueryParams1 { self: QueryParams.type =>

  implicit def single[T](implicit encoder: ValueEncoder[T]): QueryParams[T] =
    new QueryParams[T] {
      def apply(t: T): Seq[Param[T]] = Seq(Param(t))
      def placeholders(t: T, start: Int) = Seq(s"$$$start")
    }

}
