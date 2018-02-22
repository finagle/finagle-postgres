package com.twitter.finagle.postgres.messages

import com.twitter.finagle.postgres.Spec
import io.netty.buffer.UnpooledByteBufAllocator

class PacketSpec extends Spec {
  val allocator = UnpooledByteBufAllocator.DEFAULT
  "Packet" should {
    "encode an empty packet" in {
      val pack = PacketBuilder(allocator).toPacket.encode(allocator)

      pack.readInt must equal(4)
    }

    "encode a one byte packet" in {
      val pack = PacketBuilder(allocator).writeByte(30).toPacket.encode(allocator)

      pack.readInt must equal(5)
      pack.readByte must equal(30)
    }

    "encode a one char packet" in {
      val pack = PacketBuilder(allocator).writeChar('c').toPacket.encode(allocator)

      pack.readInt must equal(5)
      pack.readByte.asInstanceOf[Char] must equal('c')
    }

    "encode a one short packet" in {
      val pack = PacketBuilder(allocator).writeShort(30).toPacket.encode(allocator)

      pack.readInt must equal(6)
      pack.readShort must equal(30)
    }

    "encode a one int packet" in {
      val pack = PacketBuilder(allocator).writeInt(30).toPacket.encode(allocator)

      pack.readInt must equal(8)
      pack.readInt must equal(30)
    }

    "encode a one string packet" in {
      val pack = PacketBuilder(allocator).writeCString("two").toPacket.encode(allocator)

      pack.readInt must equal(8)
    }

    "encode a packet with chars" in {
      val pack = PacketBuilder('c', allocator).writeInt(30).toPacket.encode(allocator)

      pack.readByte.asInstanceOf[Char] must equal('c')
      pack.readInt must equal(8)
      pack.readInt must equal(30)
    }
  }
}