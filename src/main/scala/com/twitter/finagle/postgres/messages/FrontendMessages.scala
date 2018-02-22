package com.twitter.finagle.postgres.messages

import com.twitter.finagle.postgres.values.{Convert, Strings}
import io.netty.buffer.{ByteBuf, ByteBufAllocator}


/**
 * Messages sent to Postgres from the client.
 */
trait FrontendMessage extends Message {
  def asPacket(allocator: ByteBufAllocator): Packet
}

case class StartupMessage(user: String, database: String) extends FrontendMessage {
  def asPacket(allocator: ByteBufAllocator): Packet = PacketBuilder(allocator)
    .writeShort(3)
    .writeShort(0)
    .writeCString("user")
    .writeCString(user)
    .writeCString("database")
    .writeCString(database)
    .writeByte(0)
    .toPacket
}

case class SslRequestMessage() extends FrontendMessage {
  def asPacket(allocator: ByteBufAllocator): Packet = PacketBuilder(allocator)
    .writeShort(1234)
    .writeShort(5679)
    .toPacket
}

case class PasswordMessage(password: String) extends FrontendMessage {
  def asPacket(allocator: ByteBufAllocator): Packet = PacketBuilder('p', allocator)
    .writeCString(password)
    .toPacket
}

case class Query(str: String) extends FrontendMessage {
  def asPacket(allocator: ByteBufAllocator): Packet = PacketBuilder('Q', allocator)
    .writeCString(str)
    .toPacket
}

case class Parse(
      name: String = Strings.empty, query: String = "", paramTypes: Seq[Int] = Seq()) extends FrontendMessage {
  def asPacket(allocator: ByteBufAllocator): Packet = {
    val builder = PacketBuilder('P', allocator)
      .writeCString(name)
      .writeCString(query)
      .writeShort(Convert.asShort(paramTypes.length))

    for (param <- paramTypes) {
      builder.writeInt(param)
    }
    builder.toPacket
  }
}

case class Bind(portal: String = Strings.empty, name: String = Strings.empty, formats: Seq[Int] = Seq(),
                params: Seq[ByteBuf] = Seq(), resultFormats: Seq[Int] = Seq()) extends FrontendMessage {
  def asPacket(allocator: ByteBufAllocator): Packet = {
    val builder = PacketBuilder('B', allocator)
      .writeCString(portal)
      .writeCString(name)

    if (formats.isEmpty) {
      builder.writeShort(0)
    } else {
      builder.writeShort(formats.length.toShort)
      for (format <- formats) {
        builder.writeShort(format.toShort)
      }
    }

    builder.writeShort(Convert.asShort(params.length))

    for (param <- params) {
      param.resetReaderIndex()
      builder.writeBuf(param)
    }

    if (resultFormats.isEmpty) {
      builder.writeShort(0)
    } else {
      builder.writeShort(resultFormats.length.toShort)
      for (format <- resultFormats) {
        builder.writeShort(format.toShort)
      }
    }

    builder.toPacket
  }
}

case class Execute(name: String = Strings.empty, maxRows: Int = 0) extends FrontendMessage {
  def asPacket(allocator: ByteBufAllocator): Packet = {
    PacketBuilder('E', allocator)
      .writeCString(name)
      .writeInt(maxRows)
      .toPacket
  }
}

case class Describe(portal: Boolean = true, name: String = new String) extends FrontendMessage {
  def asPacket(allocator: ByteBufAllocator): Packet = {
    PacketBuilder('D', allocator)
      .writeChar(if (portal) 'P' else 'S')
      .writeCString(name)
      .toPacket
  }
}

object Flush extends FrontendMessage {
  def asPacket(allocator: ByteBufAllocator): Packet = {
    PacketBuilder('H', allocator)
      .toPacket
  }
}

object Sync extends FrontendMessage {
  def asPacket(allocator: ByteBufAllocator): Packet = {
    PacketBuilder('S', allocator)
      .toPacket
  }
}

object Terminate extends FrontendMessage {
  def asPacket(allocator: ByteBufAllocator): Packet = PacketBuilder('X', allocator).toPacket
}
