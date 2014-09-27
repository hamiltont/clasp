package clasp.core

import akka.actor.ActorRefFactory
import spray.can.websocket.frame.BinaryFrame
import spray.routing.HttpServiceActor
import spray.can.websocket.`package`.FrameCommandFailed
import spray.can.websocket.frame.TextFrame
import spray.http.HttpRequest
import akka.actor.ActorRef
import spray.can.websocket.WebSocketServerWorker
import akka.actor.actorRef2Scala
import spray.can.Http
import akka.actor.Props
import akka.actor.ActorLogging
import akka.actor.Actor
import spray.can.websocket.frame.TextFrameStream
import akka.io.Tcp
import java.io.ByteArrayInputStream
import spray.can.websocket.UpgradedToWebSocket
import spray.http.HttpResponse
import spray.http.HttpResponse
import akka.pattern.ask
import scala.concurrent.duration._
import spray.http.HttpResponse
import scala.util.Failure
import scala.util.Success
import akka.dispatch.OnComplete
import scala.concurrent.ExecutionContext.Implicits.global
import spray.can.websocket.frame.Frame
import spray.can.websocket.`package`.FrameCommand
import spray.http.HttpResponse
import spray.http.StatusCodes
import scala.collection.mutable.ListBuffer
import scala.collection._
import akka.actor.Terminated
import WebSocketChannelManager._
import akka.actor.ActorIdentity

/**
 * First line of defense in handling HTTP connections. Creates separate
 * Actor for each connection
 *
 * Responses are handled by {@link WebSocketWorker} first (for websocket connections),
 * then by {@link HttpApi} (for REST Api connections), and finally by the nodeJS
 * process that acts as a server for the web interface
 */
class WebSocketServer(val nodemanager: ActorRef, val emulatormanager: ActorRef) 
  extends Actor 
  with ActorLogging {
  var httpApi = context.system.actorOf(Props(new HttpApi(nodemanager, emulatormanager)), name = "httpApi")
  var chanManager = context.system.actorOf(Props(new WebSocketChannelManager(nodemanager)), name = "channelManager")

  /** Each incoming connection gets a dedicated Actor with a unique serverConnection pipeline */
  def receive = {
    case Http.Connected(remoteAddress, localAddress) =>
      {
        val serverConnection = sender()
        val conn = context.actorOf(Props(new WebSocketWorker(serverConnection, httpApi, chanManager)))
        serverConnection ! Http.Register(conn)
      }
  }
}

/**
 * Checks incoming HTTP connections for WebSocket Upgrade requests. Directly
 * handles WebSockets and passes all other HTTP off to {@link HttpApi}
 */
class WebSocketWorker(val serverConnection: ActorRef, val httpApi: ActorRef, val channelManager: ActorRef)
  extends HttpServiceActor
  with WebSocketServerWorker {

  /**
   * Handshaking with auto-call businessLogic if this is a websocket request
   */
  override def receive = handshaking orElse businessLogicNoUpgrade orElse closeLogic

  def businessLogicNoUpgrade: Receive = {
    implicit val refFactory: ActorRefFactory = context
    runRoute {
      requestInstance { request =>
        onComplete(httpApi.ask(request)(15.second).mapTo[HttpResponse]) {
          case Success(response) => {
            complete(response)
          }
          case Failure(reason) => {
            log.debug(s"Received failure of $reason")
            complete(StatusCodes.InternalServerError)
          }
        }
      }
    }
  }

  // var connection = new WebSocket('ws://localhost:8080');
  // connection.send('go:do:')
  override def businessLogic: Receive = {
    case x: BinaryFrame =>
      log.info("Server BinaryFrame Received:" + x)
      sender() ! x

    case TextFrame(payload) =>
      payload.utf8String match {
        case wsmultiplex.Unsubscribe(chan) =>
          channelManager ! UnRegisterClient(chan, sender())
        case wsmultiplex.Subscribe(chan) =>
          channelManager ! RegisterClient(chan, sender())
        case wsmultiplex.Message(chan, message) =>
          channelManager ! Message(chan, message, sender())
        case failure => {
          send(TextFrame(s"FAIL: ${payload.utf8String}"))
        }
      }

    case x: TextFrameStream =>
      log.info("Server Large TextFrame Received:" + x)
      sender() ! TextFrameStream(5, x.payload)

    case UpgradedToWebSocket =>
      log.info(s"Server Upgraded to WebSocket: ${sender()}")

    case x: HttpRequest => {
      log.info("Server HttpRequest Received")
      sender() ! HttpResponse()
    }

    case x: Tcp.ConnectionClosed =>
      log.info("Server Close")

    case x => log.error("Server Unknown " + x)
  }
}

/**
 * Creates broadcast channels to enable easy interaction between
 * actors and lists of websocket clients. Actors define the
 * available channels and websocket clients register to them.
 *
 * Messages are never queued - if the client/owner is not available then
 * they are discarded
 */
object WebSocketChannelManager {
  sealed abstract class Channel(val cname: String)

  // Internal Use classes

  case class RealChannel(name: String, owner: Server) extends Channel(name)

  sealed abstract class Role
  case class Client(actor: ActorRef) extends Role
  case class Server(actor: ActorRef) extends Role

  /**
   * A channel that has clients, but does not have any owner available. Used
   * as a placeholder until a channel is registered // TODO owner = deadLetters?
   */
  case class DeadChannel(name: String) extends Channel(name)

  // External use classes. Users should expect to get a Message
  // TODO eventually tell servers when they ha client
  case class RegisterChannel(name: String, owner: ActorRef)
  case class RegisterClient(channelName: String, client: ActorRef)
  case class UnRegisterClient(channelName: String, client: ActorRef)
  case class Message(chan: String, data: String, from: ActorRef)
}
class WebSocketChannelManager(val nodeManager: ActorRef) extends Actor with ActorLogging {

  var subscriptions = Map[Channel, Vector[Client]]()

  def channels = subscriptions.keys
  def clients = subscriptions.values.flatten
  def servers = realChannels.map(chan => chan.owner)

  def realChannels = channels.collect { case x: RealChannel => x }
  def deadChannels = channels.collect { case x: DeadChannel => x }
  def getChannel(name: String, from: Iterable[Channel] = channels) = from.find(c => c.cname == name)
  def getRealChannel(name: String) = realChannels.find(c => c.name == name)

  def subscriptionsFor(forClient: Client): Map[Channel, Vector[Client]] =
    subscriptions.filter { sub => sub._2.contains(forClient) }
  def channelsBy(server: Server) =
    realChannels.filter(chan => chan.owner == server)
  def getRole(actor: ActorRef): Option[Role] =
    clients.find(client => client.actor == actor) match {
      case Some(client) => Some(client)
      case None => servers.find(server => server.actor == actor) match {
        case Some(server) => Some(server)
        case None => None
      }
    }

  // Tell nodemanager we are ready
  nodeManager ! ActorIdentity(WebSocketChannelManager, Some(self))

  def receive = {
    case RegisterChannel(name, owner) => {
      getChannel(name) match {
        case Some(deadChannel: DeadChannel) => {
          subscriptions += (RealChannel(name, Server(owner)) -> subscriptions(deadChannel))
          subscriptions -= deadChannel
          context.watch(owner)
          log.debug(s"Replaced $deadChannel with a real channel $channels")
        }
        case Some(realChannel: RealChannel) =>
          log.error(s"Refusing to use RegisterChannel($name, $owner) due to $realChannel")
        case None => {
          subscriptions += (RealChannel(name, Server(owner)) -> Vector())
          context.watch(owner)
          log.debug(s"Registered a new channel $channels")
        }
      }
    }
    case Terminated(dead) => {
      getRole(dead) match {
        case Some(client: Client) => // Remove subscriptions
          log.debug(s"Received Client Terminated($dead) $client")
          subscriptionsFor(client).foreach { subscription =>
            subscriptions += (subscription._1 -> subscription._2.filterNot(c => c == client))
          }
        case Some(server: Server) => { // Remove any channels
          log.debug(s"Received Server Terminated($dead) $server")
          channelsBy(server).foreach { realChannel =>
            subscriptions += (DeadChannel(realChannel.name) -> subscriptions(realChannel))
            subscriptions -= realChannel
          }
        }
        case None =>
          log.debug(s"Received Terminated($dead) but no channel is registered")
      }
    }
    case RegisterClient(chanName, clientRef) => {
      context.watch(clientRef)
      getChannel(chanName) match {
        case Some(chan) => subscriptions += (chan -> (subscriptions(chan) :+ Client(clientRef)))
        case None => subscriptions += (DeadChannel(chanName) -> Vector(Client(clientRef)))
      }
    }
    case UnRegisterClient(chanName, clientRef) => {
      context.unwatch(clientRef)
      getChannel(chanName) match {
        case Some(chan) => subscriptions += (chan -> (subscriptions(chan).filterNot(_.actor == clientRef)))
        case None => log.debug(s"Ignoring UnRegisterClient($chanName, $clientRef) - no such channel")
      }
    }
    case Message(channel, data, sender) => {
      getRole(sender) match {
        case Some(client: Client) => { // Send to channel server
          getChannel(channel) match {
            case Some(real: RealChannel) => real.owner.actor ! Message(channel, data, self)
            case Some(dead: DeadChannel) => log.error(s"Discarded Message($channel, $data, $sender) - dead channel")
            case None => log.error(s"Discarded Message($channel, $data, $sender) - unregistered channel")
          }
        }
        case Some(server: Server) => { // Send to all clients
          getChannel(channel) match {
            case Some(real: RealChannel) => {
              val frame = TextFrame(wsmultiplex.Message(channel, data).write)
              subscriptions(real).foreach { client => client.actor ! frame }
            }
            case Some(dead: DeadChannel) => log.error(s"Discarded Message($channel, $data, $sender) - sent to dead channel")
            case None => log.error(s"Discarded Message($channel, $data, $sender) - unregistered channel")
          }
        }
        case None => log.error(s"Discarded Message($channel, $data, $sender) - unknown sender")
      }
    }
  }

}

/**
 * Implements the simple SockJS websocket-multiplex-0.1 protocol
 * so that our clients can use this library to easily have channels
 * in their websocket communications
 *
 * From the client JavaScript, you can do stuff like this:
 * <code>
 * var multiplexer = new WebSocketMultiplex(connection);
 * var ann  = multiplexer.channel('ann');
 * ann.send('hi')
 * ann.onopen    = function()  {print('[*] open', ws.protocol);};
 * ann.onmessage = function(e) {print('[.] message', e.data);};
 * ann.onclose   = function()  {print('[*] close');};
 * ann.close()
 * </code>
 *
 */
object wsmultiplex {

  sealed class SockJS

  class Subscribe(val chan: String) { def write = s"sub,$chan" }
  object Subscribe {
    def unapply(chan: String): Option[String] =
      if (chan.startsWith("sub,") && chan.split(',').length >= 2)
        Some(chan.split(',')(1))
      else None
    def apply(chan: String) = new Subscribe(chan)
  }

  class Unsubscribe(chan: String) { def write = s"uns,$chan" }
  object Unsubscribe {
    def unapply(x: String): Option[String] =
      if (x.startsWith("uns,") && x.split(',').length >= 2)
        Some(x.split(',')(1))
      else None
    def apply(chan: String) = new Unsubscribe(chan)
  }

  class Message(val chan: String, val data: String) { def write = s"msg,$chan,$data" }
  object Message {
    def unapply(x: String): Option[Pair[String, String]] =
      if (x.startsWith("msg,") && x.split(',').length >= 3)
        Some((x.split(',')(1), x.split(',')(2)))
      else None
    def apply(chan: String, data: String) = new Message(chan, data)
  }
}
