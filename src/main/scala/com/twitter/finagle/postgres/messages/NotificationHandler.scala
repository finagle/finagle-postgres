package com.twitter.finagle.postgres.messages

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiFunction

import com.twitter.concurrent.AsyncStream
import com.twitter.logging.Logger
import com.twitter.util.Promise
import org.jboss.netty.channel.ChannelHandler.Sharable
import org.jboss.netty.channel.ChannelHandlerContext
import org.jboss.netty.channel.MessageEvent
import org.jboss.netty.channel.SimpleChannelUpstreamHandler

import scala.collection.parallel.mutable

@Sharable
class NotificationHandler extends SimpleChannelUpstreamHandler() {
  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent): Unit = {
    e.getMessage match {
      case SingleMessageResponse(n: NotificationResponse) => Listeners.notify(n)
      case _ => super.messageReceived(ctx, e)
    }
  }
}

object Listener {
  def unapply(l: Listener) = Some((l.stream, () => l.stop()))
}

trait Listener {
  def stream: AsyncStream[NotificationResponse]
  def stop(): Unit
}

case class NextPromise(id: UUID, value: Promise[NotificationResponse])

object Listeners {

  private[this] val logger = Logger(getClass)

  private val map: ConcurrentHashMap[String, List[NextPromise]] = new ConcurrentHashMap()
  private val listeners = mutable.ParSet[UUID]()

  def alreadyListening(channel: String): Boolean = map.containsKey(channel)

  def addConsumer(channel: String, id: UUID = UUID.randomUUID()): Listener = {
    val p = NextPromise(id, Promise[NotificationResponse])
    listeners += id
    map.compute(channel, addPromise(p))
    createListener(channel, id, createStream(channel, p))
  }

  private def createListener(channel: String, id: UUID, msgStream: AsyncStream[NotificationResponse]) = {
    new Listener {
      override def stream: AsyncStream[NotificationResponse] = msgStream
      override def stop(): Unit = {
        map.computeIfPresent(channel, removePromise(id))
        listeners - id
      }
    }
  }

  private def createStream(channel: String, p: NextPromise) = {
    AsyncStream.fromFuture(p.value) ++ {
      if (listeners.contains(p.id))
        addConsumer(channel, p.id).stream
      else
        AsyncStream.empty
    }
  }

  def removeAllConsumers(channel: String): Unit = {
    listeners.clear()
    map.remove(channel)
  }

  def notify(msg: NotificationResponse): Unit = {
      val clients = map.remove(msg.channel)
      map.putIfAbsent(msg.channel, List())
      clients.filterNot(_.value.isDefined).foreach(_.value.setValue(msg))
  }

  private def addPromise(p: NextPromise) = new BiFunction[String, List[NextPromise], List[NextPromise]] {
    override def apply(t: String, u: List[NextPromise]): List[NextPromise] = {
      p :: Option(u).getOrElse(List())
    }
  }

  private def removePromise(id: UUID) = new BiFunction[String, List[NextPromise], List[NextPromise]] {
    override def apply(t: String, u: List[NextPromise]): List[NextPromise] = {
      u.filterNot(_.id == id)
    }
  }
}