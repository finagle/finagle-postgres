package com.twitter.finagle.postgres.messages

import java.util.concurrent.ConcurrentHashMap
import java.util.function
import java.util.function.BiFunction

import com.twitter.logging.Logger
import org.jboss.netty.channel.ChannelHandler.Sharable
import org.jboss.netty.channel.ChannelHandlerContext
import org.jboss.netty.channel.MessageEvent
import org.jboss.netty.channel.SimpleChannelUpstreamHandler

import scala.collection.parallel.mutable
import scala.collection.parallel.mutable.ParSet

@Sharable
class NotificationHandler extends SimpleChannelUpstreamHandler() {
  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent): Unit = {
    e.getMessage match {
      case SingleMessageResponse(n: NotificationResponse) => Listeners.notify(n)
      case _ => super.messageReceived(ctx, e)
    }
  }
}

object Listeners {

  private[this] val logger = Logger(getClass)

  private val map: ConcurrentHashMap[String, mutable.ParSet[NotificationResponse => Unit]] = new ConcurrentHashMap()

  def alreadyListening(channel: String): Boolean = map.containsKey(channel)

  def addConsumer(channel: String, block: Function[NotificationResponse, Unit]): Runnable = {
    map.computeIfAbsent(channel, firstConsumer)
    map.computeIfPresent(channel, addConsumer(block))
    new Runnable {
      override def run(): Unit = map.computeIfPresent(channel, removeConsumer(block))
    }
  }

  def removeAllConsumers(channel: String): Unit = map.remove(channel)

  def notify(msg: NotificationResponse): Unit = {
    logger.ifDebug(() => s"Notifying listeners with message $msg")
    Option(map.get(msg.channel)).foreach(_.foreach(_.apply(msg)))
  }

  private val firstConsumer = new function.Function[String, ParSet[NotificationResponse => Unit]] {
    override def apply(t: String): ParSet[NotificationResponse => Unit] = mutable.ParSet.empty
  }

  private def addConsumer(block: Function[NotificationResponse, Unit]) = new BiFunction[String, ParSet[NotificationResponse => Unit], ParSet[NotificationResponse => Unit]] {
    override def apply(t: String, u: ParSet[NotificationResponse => Unit]): ParSet[NotificationResponse => Unit] = {
      u + block
    }
  }

  private def removeConsumer(block: Function[NotificationResponse, Unit]) = new BiFunction[String, ParSet[NotificationResponse => Unit], ParSet[NotificationResponse => Unit]] {
    override def apply(t: String, u: ParSet[NotificationResponse => Unit]): ParSet[NotificationResponse => Unit] = {
      u - block
    }
  }
}