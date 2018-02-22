package com.twitter.finagle.postgres.generic.enumeration

import java.nio.charset.Charset

import com.twitter.finagle.postgres.values.{ValueDecoder, ValueEncoder}
import com.twitter.util.{Return, Throw, Try}
import io.netty.buffer.{ByteBuf, ByteBufAllocator}
import org.jboss.netty.buffer.{ChannelBuffer, ChannelBuffers}
import shapeless.labelled._
import shapeless.{:+:, CNil, Coproduct, Inl, Inr, LabelledGeneric, Witness}

case class InvalidValue(repr: String) extends IllegalArgumentException(
  s"Reached CNil case, but CNil does not exist (value '$repr' is not a valid value for this enumeration)"
)


class EnumCConsDecoder[K <: Symbol, H, T <: Coproduct](
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


class EnumCoproductDecoder[T, C <: Coproduct](
  gen: LabelledGeneric.Aux[T, C],
  decodeC: ValueDecoder[C]
) extends ValueDecoder[T] {
  @inline final def decodeText(recv: String, text: String): Try[T] = decodeC.decodeText(recv, text).map(gen.from)
  @inline final def decodeBinary(recv: String, bytes: ChannelBuffer, charset: Charset): Try[T] =
    decodeC.decodeBinary(recv, bytes, charset).map(gen.from)
}

class EnumCConsEncoder[K <: Symbol, H, T <: Coproduct](
  name: Witness.Aux[K],
  leaf: Witness.Aux[H],
  encodeTail: ValueEncoder[T]
) extends ValueEncoder[FieldType[K, H] :+: T] {
  private val str = name.value.name

  @inline final def encodeText(t: :+:[FieldType[K, H], T], allocator: ByteBufAllocator): Option[String] = t match {
    case Inl(_)    => Some(str)
    case Inr(tail) => encodeTail.encodeText(tail, )
  }

  @inline final def encodeBinary(t: :+:[FieldType[K, H], T], charset: Charset, allocator: ByteBufAllocator): Option[ChannelBuffer] = t match {
    case Inl(_)    => Some(ChannelBuffers.copiedBuffer(str, charset))
    case Inr(tail) => encodeTail.encodeBinary(tail, charset, )
  }

  val typeName: String = "" // enums are sent over the wire as text
  val elemTypeName: Option[String] = None
}

class EnumCoproductEncoder[T, C <: Coproduct](
  gen: LabelledGeneric.Aux[T, C],
  encodeC: ValueEncoder[C]
) extends ValueEncoder[T] {
  @inline final def encodeText(t: T, allocator: ByteBufAllocator): Option[String] = encodeC.encodeText(gen.to(t), )
  @inline final def encodeBinary(t: T, charset: Charset, allocator: ByteBufAllocator): Option[ByteBuf] = encodeC.encodeBinary(gen.to(t), charset, )
  val typeName = ""
  val elemTypeName = None
}

trait Enums {

  implicit object EnumCNilDecoder extends ValueDecoder[CNil] {
    @inline final def decodeText(recv: String, text: String): Try[CNil] = Throw(InvalidValue(text))

    @inline final def decodeBinary(
      recv: String, bytes: ChannelBuffer,
      charset: Charset
    ): Try[CNil] = Throw(InvalidValue(bytes.toString(charset)))
  }

  implicit def enumCConsDecoder[K <: Symbol, H, T <: Coproduct](implicit
    name: Witness.Aux[K],
    leaf: Witness.Aux[H],
    decodeTail: ValueDecoder[T]
  ): ValueDecoder[FieldType[K, H] :+: T] = new EnumCConsDecoder[K, H, T](name, leaf, decodeTail)


  implicit def enumCoproductDecoder[T, C <: Coproduct](implicit
    gen: LabelledGeneric.Aux[T, C],
    decodeC: ValueDecoder[C]
  ): ValueDecoder[T] = new EnumCoproductDecoder[T, C](gen, decodeC)

  implicit object EnumCNilEncoder extends ValueEncoder[CNil] {
    @inline final def encodeText(c: CNil, allocator: ByteBufAllocator): Option[String] = None
    @inline final def encodeBinary(c: CNil, char: Charset, allocator: ByteBufAllocator): Option[ByteBuf] = None
    val typeName = ""
    val elemTypeName = None
  }

  implicit def enumCConsEncoder[K <: Symbol, H, T <: Coproduct](implicit
    name: Witness.Aux[K],
    leaf: Witness.Aux[H],
    encodeTail: ValueEncoder[T]
  ): ValueEncoder[FieldType[K, H] :+: T] = new EnumCConsEncoder[K, H, T](name, leaf, encodeTail)

  implicit def enumCoproductEncoder[T, C <: Coproduct](implicit
    gen: LabelledGeneric.Aux[T, C],
    encodeC: ValueEncoder[C]
  ): ValueEncoder[T] = new EnumCoproductEncoder[T, C](gen, encodeC)
}