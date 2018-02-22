package com.twitter.finagle.postgres.values

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.sql.Timestamp
import java.time._
import java.time.temporal.JulianFields
import java.util.UUID

import io.netty.buffer.{ByteBuf, ByteBufAllocator, Unpooled}

import scala.language.existentials

/**
  * Typeclass responsible for encoding a parameter of type T for sending to postgres
  * @tparam T The type which it encodes
  */
trait ValueEncoder[T] {
  def encodeText(t: T): Option[String]
  def encodeBinary(t: T, charset: Charset, allocator: ByteBufAllocator): Option[ByteBuf]
  def typeName: String
  def elemTypeName: Option[String]

  def contraMap[U](fn: U => T, newTypeName: String = this.typeName, newElemTypeName: Option[String] = elemTypeName): ValueEncoder[U] = {
    val prev = this
    new ValueEncoder[U] {
      def encodeText(u: U): Option[String] = prev.encodeText(fn(u))
      def encodeBinary(u: U, charset: Charset, allocator: ByteBufAllocator): Option[ByteBuf] = prev.encodeBinary(fn(u), charset, allocator)
      val typeName = newTypeName
      val elemTypeName = newElemTypeName
    }
  }
}

object ValueEncoder extends LowPriorityEncoder {

  def apply[T](implicit encoder: ValueEncoder[T]) = encoder

  case class Exported[T](encoder: ValueEncoder[T])

  private val nullParam = {
    val buf = Unpooled.buffer(4)
    buf.writeInt(-1)
    buf
  }

  def instance[T](
    instanceTypeName: String,
    text: T => String,
    binary: (T, Charset, ByteBufAllocator) => Option[ByteBuf]
  ): ValueEncoder[T] = new ValueEncoder[T] {
    def encodeText(t: T): Option[String] = Option(t).map(text)
    def encodeBinary(t: T, c: Charset, allocator: ByteBufAllocator): Option[ByteBuf] = binary(t, c, allocator)
    val typeName = instanceTypeName
    val elemTypeName = None
  }

  def encodeText[T](t: T, encoder: ValueEncoder[T], charset: Charset = StandardCharsets.UTF_8, allocator: ByteBufAllocator) =
    Option(t).flatMap((t: T) => encoder.encodeText(t)) match {
      case None => nullParam
      case Some(str) =>
        val bytes = str.getBytes(charset)
        val buf = allocator.buffer(bytes.length + 4)
        buf.writeInt(bytes.length)
        buf.writeBytes(bytes)
        buf
    }

  def encodeBinary[T](t: T, encoder: ValueEncoder[T], charset: Charset = StandardCharsets.UTF_8, allocator: ByteBufAllocator) =
    Option(t).flatMap((t: T) => encoder.encodeBinary(t, charset, allocator)) match {
      case None => nullParam
      case Some(inBuf) =>
        inBuf.resetReaderIndex()
        val outBuf = allocator.buffer(inBuf.readableBytes() + 4)
        outBuf.writeInt(inBuf.readableBytes())
        outBuf.writeBytes(inBuf)
        outBuf
    }


  implicit val string: ValueEncoder[String] = instance(
    "text",
    identity,
    (s, c, a) => Option(s).map { s =>
      val bytes = s.getBytes(c)
      a.buffer(bytes.length).writeBytes(bytes)
    }
  )

  implicit val boolean: ValueEncoder[Boolean] = instance(
    "bool",
    b => if(b) "t" else "f",
    (b, c, a) => Some(a.buffer(1).writeByte(if(b) 1.toByte else 0.toByte))
  )

  implicit val bytea: ValueEncoder[Array[Byte]] = instance(
    "bytea",
    bytes => "\\x" + bytes.map("%02x".format(_)).mkString,
    (b, c, a) => Some(a.buffer(b.length).writeBytes(b))
  )
  implicit val int2: ValueEncoder[Short] = instance("int2", _.toString, (i, c, a) => Some(a.buffer(2).writeShort(i)))
  implicit val int4: ValueEncoder[Int] = instance("int4", _.toString, (i, c, a) => Some(a.buffer(4).writeInt(i)))
  implicit val int8: ValueEncoder[Long] = instance("int8", _.toString, (i, c, a) => Some(a.buffer(8).writeLong(i)))
  implicit val float4: ValueEncoder[Float] = instance("float4", _.toString, (i, c, a) => Some(a.buffer(4).writeFloat(i)))
  implicit val float8: ValueEncoder[Double] = instance("float8", _.toString, (i, c, a) => Some(a.buffer(8).writeDouble(i)))
  implicit val date: ValueEncoder[LocalDate] = instance("date", _.toString, (i, c, a) =>
    Option(i).map(i => a.buffer(4).writeInt((i.getLong(JulianFields.JULIAN_DAY) - 2451545).toInt))
  )
  implicit val timestamp: ValueEncoder[LocalDateTime] = instance(
    "timestamp",
    t => java.sql.Timestamp.valueOf(t).toString,
    (ts, c, a) => Option(ts).map(ts => DateTimeUtils.writeTimestamp(ts, a))
  )
  implicit val timestampTz: ValueEncoder[ZonedDateTime] = instance(
    "timestamptz", { t =>
      val offs = t.toOffsetDateTime
      val hours = (offs.getOffset.getTotalSeconds / 3600).formatted("%+03d")
      Timestamp.from(t.toInstant).toString + hours
    },
    (ts, c, a) => Option(ts).map(ts => DateTimeUtils.writeTimestampTz(ts, a))
  )
  implicit val instant: ValueEncoder[Instant] = instance(
    "timestamptz",
    i => Timestamp.from(i).toString + "+00",
    (ts, c, a) => Option(ts).map(ts => DateTimeUtils.writeInstant(ts, a))
  )
  implicit val time: ValueEncoder[LocalTime] = instance(
    "time",
    t => t.toString,
    (t, c, a) => Option(t).map(t => a.buffer(8).writeLong(t.toNanoOfDay / 1000))
  )
  implicit val timeTz: ValueEncoder[OffsetTime] = instance(
    "timetz",
    t => t.toString,
    (t, c, a) => Option(t).map(DateTimeUtils.writeTimeTz(_, a))
  )
  implicit val interval: ValueEncoder[Interval] = instance(
    "interval",
    i => i.toString,
    (i, c, a) => Option(i).map(DateTimeUtils.writeInterval(_, a))
  )
  implicit val numeric: ValueEncoder[BigDecimal] = instance(
    "numeric",
    d => d.bigDecimal.toPlainString,
    (d, c, a) => Option(d).map(d => Numerics.writeNumeric(d, a))
  )
  implicit val numericJava: ValueEncoder[java.math.BigDecimal] = instance(
    "numeric",
    d => d.toPlainString,
    (d, c, a) => Option(d).map(d => Numerics.writeNumeric(BigDecimal(d), a))
  )
  implicit val numericBigInt: ValueEncoder[BigInt] = instance(
    "numeric",
    i => i.toString,
    (i, c, a) => Option(i).map(i => Numerics.writeNumeric(BigDecimal(i), a))
  )
  implicit val numericJavaBigInt: ValueEncoder[java.math.BigInteger] = instance(
    "numeric",
    i => i.toString,
    (i, c, a) => Option(i).map(i => Numerics.writeNumeric(BigDecimal(i), a))
  )
  implicit val uuid: ValueEncoder[UUID] = instance(
    "uuid",
    u => u.toString,
    (u, c, a) => Option(u).map(u => a.buffer(16)
      .writeLong(u.getMostSignificantBits)
      .writeLong(u.getLeastSignificantBits))
  )
  implicit val hstore: ValueEncoder[Map[String, Option[String]]] = instance[Map[String, Option[String]]](
    "hstore",
    m => HStores.formatHStoreString(m),
    (m, c, a) => Option(m).map(HStores.encodeHStoreBinary(_, c, a))
  )
  implicit val hstoreNoNulls: ValueEncoder[Map[String, String]] = hstore.contraMap {
    m: Map[String, String] => m.mapValues(Option(_))
  }

  implicit val jsonb: ValueEncoder[JSONB] = instance[JSONB](
    "jsonb",
    j => JSONB.stringify(j),
    (j, c, a) => {
      val cb = a.buffer(1 + j.bytes.length)
      cb.writeByte(1)
      cb.writeBytes(j.bytes)
      Some(cb)
    }
  )

  @inline final implicit def option[T](implicit encodeT: ValueEncoder[T]): ValueEncoder[Option[T]] =
    new ValueEncoder[Option[T]] {
      val typeName = encodeT.typeName
      val elemTypeName = encodeT.elemTypeName
      def encodeText(optT: Option[T]): Option[String] = optT.flatMap((t: T) => encodeT.encodeText(t))
      def encodeBinary(tOpt: Option[T], c: Charset, allocator: ByteBufAllocator): Option[ByteBuf] = tOpt.flatMap(t => encodeT.encodeBinary(t, c, allocator))
    }

  @inline final implicit def some[T](implicit encodeT: ValueEncoder[T]): ValueEncoder[Some[T]] =
    encodeT.contraMap((s: Some[T]) => s.get)

  implicit object none extends ValueEncoder[None.type] {
    val typeName = "null"
    val elemTypeName = None
    def encodeText(none: None.type): Option[String] = None
    def encodeBinary(none: None.type, c: Charset, allocator: ByteBufAllocator): Option[ByteBuf] = None
  }
}

trait LowPriorityEncoder {
  implicit def fromExport[T](implicit export: ValueEncoder.Exported[T]): ValueEncoder[T] = export.encoder
}
