package com.twitter.finagle.postgres.codec

import java.net.InetSocketAddress
import java.util

import com.twitter.finagle._
import com.twitter.finagle.postgres.connection.{AuthenticationRequired, Connection, RequestingSsl, WrongStateForEvent}
import com.twitter.finagle.postgres.messages._
import com.twitter.finagle.postgres.values.Md5Encryptor
import com.twitter.finagle.ssl.client.{HostnameVerifier, SslClientConfiguration, SslClientEngineFactory, SslClientSessionVerifier}
import com.twitter.logging.Logger
import com.twitter.util.Future
import javax.net.ssl.SSLSession

import io.netty.buffer.{ByteBuf, ByteBufAllocator}
import io.netty.channel.{ChannelHandlerContext, ChannelPromise}
import io.netty.handler.codec.{ByteToMessageDecoder, MessageToMessageCodec, MessageToMessageDecoder}
import com.twitter.finagle.ssl.Ssl
import io.netty.handler.ssl.SslHandler

/*
 * Filter that converts exceptions into ServerErrors.
 */
class HandleErrorsProxy(
    delegate: ServiceFactory[PgRequest, PgResponse]) extends ServiceFactoryProxy(delegate) {

  override def apply(conn: ClientConnection): Future[Service[PgRequest, PgResponse]] = {
    for {
      service <- delegate.apply(conn)
    } yield HandleErrors.andThen(service)
  }

  object HandleErrors extends SimpleFilter[PgRequest, PgResponse] {

    def apply(request: PgRequest, service: Service[PgRequest, PgResponse]) = {
      service.apply(request).flatMap {
        case Error(msg, severity, sqlState, detail, hint, position) =>
          Future.exception(Errors.server(msg.getOrElse("unknown failure"), Some(request), severity, sqlState, detail, hint, position))
        case Terminated =>
          Future.exception(new ChannelClosedException())
        case r => Future.value(r)
      }
    }
  }
}

/*
 * Filter that does password authentication before issuing requests.
 */
class AuthenticationProxy(
    delegate: ServiceFactory[PgRequest, PgResponse],
    user: String, password: Option[String],
    database: String,
    useSsl: Boolean) extends ServiceFactoryProxy(delegate) {
  private val logger = Logger(getClass.getName)

  override def apply(conn: ClientConnection): Future[Service[PgRequest, PgResponse]] = {
    for {
      service <- delegate.apply(conn)
      optionalSslResponse <- sendSslRequest(service)
      _ <- handleSslResponse(optionalSslResponse)
      startupResponse <- service(PgRequest(StartupMessage(user, database)))
      passwordResponse <- sendPassword(startupResponse, service)
      _ <- verifyResponse(passwordResponse)
    } yield service
  }

  private[this] def sendSslRequest(service: Service[PgRequest, PgResponse]): Future[Option[PgResponse]] = {
    if (useSsl) {
      service(PgRequest(new SslRequestMessage)).map { response => Some(response) }
    } else {
      Future.value(None)
    }
  }

  private[this] def handleSslResponse(optionalSslResponse: Option[PgResponse]): Future[Unit] = {
    logger.ifDebug("SSL response: %s".format(optionalSslResponse))

    if (useSsl && (optionalSslResponse contains SslNotSupportedResponse)) {
      throw Errors.server("SSL requested by server doesn't support it")
    } else {
      Future(Unit)
    }
  }

  private[this] def sendPassword(
      startupResponse: PgResponse, service: Service[PgRequest, PgResponse]): Future[PgResponse] = {
    startupResponse match {
      case PasswordRequired(encoding) => password match {
        case Some(pass) =>
          val msg = encoding match {
            case ClearText => PasswordMessage(pass)
            case Md5(salt) => PasswordMessage(new String(Md5Encryptor.encrypt(user.getBytes, pass.getBytes, salt)))
          }
          service(PgRequest(msg))

        case None => Future.exception(Errors.client("Password has to be specified for authenticated connection"))
      }

      case r => Future.value(r)
    }
  }

  private[this] def verifyResponse(response: PgResponse): Future[Unit] = {
    response match {
      case AuthenticatedResponse(statuses, processId, secretKey) =>
        logger.ifDebug("Authenticated: %d %d\n%s".format(processId, secretKey, statuses))
        Future(Unit)
    }
  }
}


/*
 * Decodes a Packet into a BackendMessage.
 */
class BackendMessageDecoder(val parser: BackendMessageParser) extends MessageToMessageDecoder[Packet] {
  private val logger = Logger(getClass.getName)

  override def decode(ctx: ChannelHandlerContext, msg: Packet, out: util.List[AnyRef]): Unit = parser.parse(msg) match {
    case Some(backendMessage) =>
      out.add(backendMessage)
    case None => logger.warning("Cannot parse the packet. Disconnecting...")
  }
}

/*
 * Decodes a byte stream into a Packet.
 */
class PacketDecoder(@volatile var inSslNegotation: Boolean) extends ByteToMessageDecoder {
  private val logger = Logger(getClass.getName)

  // TODO: SSL negoatiation should be moved upstream; then this could be a LengthFieldBasedFrameDecoder.
  override def decode(ctx: ChannelHandlerContext, buffer: ByteBuf, out: util.List[AnyRef]): Unit = if (inSslNegotation && buffer.readableBytes() >= 1) {

    val SslCode: Char = buffer.readByte().asInstanceOf[Char]
    logger.ifDebug("Got ssl negotiation char packet: %s".format(SslCode))
    inSslNegotation = false

    out.add(new Packet(Some(SslCode), 1, null, true))

  } else if (buffer.readableBytes() >= 5) {
    buffer.markReaderIndex()
    val code: Char = buffer.readByte().asInstanceOf[Char]

    val totalLength = buffer.readInt()
    val length = totalLength - 4

    if (buffer.readableBytes() < length) {
      buffer.resetReaderIndex()
    } else {
      out.add(new Packet(Some(code), totalLength, buffer.readBytes(length)))
      // TODO (jeremyrsmith): can we slice here? I tried using readSlice (illegal ref counts everywhere) and readRetainedSlice (severe leak warning).
    }
  }

}

/*
 * Map PgRequest to PgResponse.
 */
class PgClientChannelHandler(
  sslEngineFactory: SslClientEngineFactory,
  sslConfig: Option[SslClientConfiguration],
  val useSsl: Boolean,
  allocator: ByteBufAllocator
) extends MessageToMessageCodec[BackendMessage, Object] {

  private[this] val logger = Logger(getClass.getName)
  private[this] val connection = {
    if (useSsl) {
      new Connection(startState = RequestingSsl)
    } else {
      new Connection(startState = AuthenticationRequired)
    }
  }

  override def decode(ctx: ChannelHandlerContext, message: BackendMessage, out: util.List[AnyRef]): Unit = message match {
    case SwitchToSsl =>
      logger.ifDebug("Got switchToSSL message; adding ssl handler into pipeline")

      val pipeline = ctx.pipeline()

      val addr = ctx.channel().remoteAddress()
      val inetAddr = addr match {
        case i: InetSocketAddress => Some(i)
        case _ => None
      }

      val engine = inetAddr.map {
        inet =>
          sslConfig.map(sslEngineFactory(Address(inet), _)).getOrElse(Ssl.client(inet.getHostString, inet.getPort))
      }.getOrElse(Ssl.client()).self

      engine.setUseClientMode(true)

      val sslHandler = new SslHandler(engine)
      pipeline.addFirst("ssl", sslHandler)

      val verifier: SSLSession => Boolean = inetAddr match {
        case Some(inet) =>
          session => HostnameVerifier(Address(inet), SslClientConfiguration(hostname = Some(inet.getHostName)),session)
        case None =>
          _ => true
      }

      connection.receive(SwitchToSsl).foreach(out.add)

    case msg =>
      try {
        connection.receive(msg).foreach(out.add)
      } catch {
        case err @ WrongStateForEvent(evt, state) =>
          logger.error(s"Could not handle event $evt while in state $state; connection will be terminated", err)
          ctx.channel().writeAndFlush(Terminate.asPacket(allocator).encode(allocator))
          ctx.fireExceptionCaught(err)
      }
  }

  override def encode(ctx: ChannelHandlerContext, message: Object, out: util.List[AnyRef]): Unit = {
    val outMsg = message match {
      case PgRequest(msg, flush) =>
        val packet = msg.asPacket(allocator)
        out.add(packet.encode(allocator))

        if (flush) {
          out.add(Flush.asPacket(allocator).encode(allocator))
        }

        try {
          connection.send(msg)
        } catch {
          case err @ WrongStateForEvent(evt, state) =>
            logger.error(s"Could not handle event $evt while in state $state; connection will be terminated", err)
            ctx.fireExceptionCaught(err)
            Some(com.twitter.finagle.postgres.messages.Terminated)
        }

      case buffer: ByteBuf =>
        out.add(buffer)
        None

      case other =>
        logger.warning(s"Cannot convert message of type ${other.getClass.getName}... Skipping")
        out.add(other)
        None
    }

    outMsg collect {
      case term @ com.twitter.finagle.postgres.messages.Terminated =>
        ctx.channel().close()
        ctx.fireChannelRead(term)
    }
  }

  override def disconnect(ctx: ChannelHandlerContext, promise: ChannelPromise): Unit = {
    logger.ifDebug("Detected channel disconnected!")
    super.disconnect(ctx, promise)
  }

}






