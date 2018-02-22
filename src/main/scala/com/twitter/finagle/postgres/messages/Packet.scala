package com.twitter.finagle.postgres.messages

import com.twitter.finagle.postgres.values.Charsets
import io.netty.buffer.{ByteBuf, ByteBufAllocator, UnpooledByteBufAllocator}

object Packet {
  val INT_SIZE = 4
}

/*
 * Representation of a "packet" sent to Postgres.
 *
 * Converts content into byte format expected by Postgres.
 */
case class Packet(code: Option[Char], length: Int, content: ByteBuf, inSslNegotation: Boolean = false) {
  def encode(allocator: ByteBufAllocator): ByteBuf = {
    val result = allocator.buffer(length + 5)

    code.foreach { c =>
      result.writeByte(c)
    }

    result.writeInt(length + Packet.INT_SIZE)
    result.writeBytes(content)

    result
  }
}

/*
 * Helper class for creating packets from scala types.
 */
class PacketBuilder(val code: Option[Char], allocator: ByteBufAllocator) {
  private val underlying = allocator.buffer()

  def writeByte(byte: Byte) = {
    underlying.writeByte(byte)
    this
  }

  def writeBytes(bytes: Array[Byte]) = {
	  underlying.writeBytes(bytes)
	  this
  }

  def writeBuf(bytes: ByteBuf) = {
	  underlying.writeBytes(bytes)
	  this
  }

  def writeChar(char: Char) = {
    underlying.writeByte(char)
    this
  }
  def writeInt(int: Int) = {
    underlying.writeInt(int)
    this
  }

  def writeShort(short: Short) = {
    underlying.writeShort(short)
    this
  }

  def writeCString(str: String) = {
    underlying.writeBytes(str.getBytes(Charsets.Utf8))
    underlying.writeByte(0)
    this
  }

  def toPacket = new Packet(code, underlying.writerIndex(), underlying)
}

object PacketBuilder {
  def apply(allocator: ByteBufAllocator): PacketBuilder = new PacketBuilder(None, allocator)

  def apply(code: Char, allocator: ByteBufAllocator): PacketBuilder = new PacketBuilder(Some(code), allocator)
}