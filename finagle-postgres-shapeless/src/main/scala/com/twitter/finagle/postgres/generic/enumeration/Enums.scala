package com.twitter.finagle.postgres.generic.enumeration

import java.nio.charset.Charset

import com.twitter.finagle.postgres.values.ValueDecoder
import com.twitter.util.{Return, Throw, Try}
import org.jboss.netty.buffer.ChannelBuffer
import shapeless.labelled._
import shapeless.{:+:, CNil, Coproduct, Inl, Inr, LabelledGeneric, Witness}

case class InvalidValue(repr: String) extends IllegalArgumentException(
  s"Reached CNil case, but CNil does not exist (value '$repr' is not a valid value for this enumeration)"
)


class EnumCCons[K <: Symbol, H, T <: Coproduct](
  name: Witness.Aux[K],
  leaf: Witness.Aux[H],
  decodeTail: ValueDecoder[T]
) extends ValueDecoder[FieldType[K, H] :+: T] {
  private val str = name.value.name

  @inline final def decodeText(recv: String, text: String): Try[FieldType[K, H] :+: T] =
    if(text == str)
      Return(Inl(field[K](leaf.value)))
    else
      decodeTail.decodeText(recv, text).map(Inr(_))

  @inline final def decodeBinary(
    recv: String, bytes: ChannelBuffer,
    charset: Charset
  ): Try[FieldType[K, H] :+: T] = decodeText(recv, bytes.toString(charset))
}


class EnumCoproduct[T, C <: Coproduct](
  gen: LabelledGeneric.Aux[T, C],
  decodeC: ValueDecoder[C]
) extends ValueDecoder[T] {
  @inline final def decodeText(recv: String, text: String): Try[T] = decodeC.decodeText(recv, text).map(gen.from)
  @inline final def decodeBinary(recv: String, bytes: ChannelBuffer, charset: Charset): Try[T] =
    decodeC.decodeBinary(recv, bytes, charset).map(gen.from)
}

trait Enums {

  implicit object EnumCNil extends ValueDecoder[CNil] {
    @inline final def decodeText(recv: String, text: String): Try[CNil] = Throw(InvalidValue(text))

    @inline final def decodeBinary(
      recv: String, bytes: ChannelBuffer,
      charset: Charset
    ): Try[CNil] = Throw(InvalidValue(bytes.toString(charset)))
  }

  implicit def enumCCons[K <: Symbol, H, T <: Coproduct](implicit
    name: Witness.Aux[K],
    leaf: Witness.Aux[H],
    decodeTail: ValueDecoder[T]
  ): ValueDecoder[FieldType[K, H] :+: T] = new EnumCCons[K, H, T](name, leaf, decodeTail)


  implicit def enumCoproduct[T, C <: Coproduct](implicit
    gen: LabelledGeneric.Aux[T, C],
    decodeC: ValueDecoder[C]
  ): ValueDecoder[T] = new EnumCoproduct[T, C](gen, decodeC)
}