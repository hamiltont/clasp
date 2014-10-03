package clasp.core.remoting

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import scala.collection._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import WebSocketChannelManager._
import akka.actor.Actor
import akka.actor.ActorContext
import akka.actor.ActorIdentity
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorRefFactory
import akka.actor.Identify
import akka.actor.Props
import akka.actor.Terminated
import akka.actor.actorRef2Scala
import akka.io.Tcp
import akka.pattern.ask
import clasp.utils.ActorStack
import clasp.utils.Slf4jLoggingStack
import spray.can.Http
import spray.can.websocket.UpgradedToWebSocket
import spray.can.websocket.WebSocketServerWorker
import spray.can.websocket.frame.BinaryFrame
import spray.can.websocket.frame.TextFrame
import spray.can.websocket.frame.TextFrameStream
import spray.http.HttpRequest
import spray.http.HttpResponse
import spray.http.HttpResponse
import spray.http.HttpResponse
import spray.http.HttpResponse
import spray.http.StatusCodes
import spray.routing.HttpServiceActor
import akka.actor.ActorSelection.toScala
import akka.util.Timeout.durationToTimeout
import spray.httpx.marshalling.ToResponseMarshallable.isMarshallable
import spray.routing.Directive.pimpApply
import spray.routing.directives.OnCompleteFutureMagnet.apply
import spray.json.RootJsonFormat
import spray.json.RootJsonWriter
import spray.json.JsonPrinter
import spray.json.PrettyPrinter
import spray.json.CompactPrinter
import java.io.BufferedReader
import java.io.FileReader
import org.slf4j.LoggerFactory

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
  var chanManager = context.system.actorOf(Props(new WebSocketChannelManager(nodemanager, emulatormanager)), name = "channelManager")

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
 * actors and lists of WebSocket clients. Actors define the
 * available channels and WebSocket clients register to them.
 * A common pattern is for servers to use spray-json to convert their
 * response objects to JSON. This makes writing clients
 * simpler, as each message can be easily parsed back into a full JSON
 * object native to that client's language (e.g. a JSON object in
 * JavaScript). If servers use <code>import ClaspJson._</code> to do this,
 * the WebSocket interface will use the exact same JSON marshaling
 * as the REST interface. It's important to do this marshaling at
 * the sender to help distribute the compute workload across the nodes
 * instead of concentrating it within this Actor, which runs on the
 * master. If servers use the <code>trait ChannelServer</code> they
 * can rely on the method <code>createMessageString</code> to check
 * that a <code>RootJsonFormat</code> exists for their message object.
 *
 *
 * For each system run, this saves all messages going server->client
 * in a logfile underneath logs/. Each channel gets it's own file,
 * and each file is given the suffix .json in anticipation that the
 * outgoing messages will be JSON strings. When a new client connects
 * to a channel, this log file is replayed line by line to the new
 * client so they are up-to-date. This is a small hack to avoid having
 * clients need to poll our REST service and await a response before
 * they can subscribe to updates via the websocket channel. It works
 * for now because we don't usually have thousands of emulators
 * toggling offline/online status, so the stream of updates is normally
 * not much larger than the current status
 *
 * Messages from client to server are never buffered or logged - if the
 * server is not available then the messages are discarded
 */
object WebSocketChannelManager {
  val channelManagerId = "websocketchannelmanager"

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
class WebSocketChannelManager(val nodeManager: ActorRef, val emulatorManager: ActorRef) extends Actor
  with ActorLogging
  with ActorStack
  with Slf4jLoggingStack {

  var subscriptions = Map[Channel, Vector[Client]]()

  def channels = subscriptions.keys
  def clients = subscriptions.values.flatten
  def servers = realChannels.map(chan => chan.owner)

  def realChannels = channels.collect { case x: RealChannel => x }
  def deadChannels = channels.collect { case x: DeadChannel => x }
  def getChannel(name: String, from: Iterable[Channel] = channels) = from.find(c => c.cname == name)
  def getRealChannel(name: String) = realChannels.find(c => c.name == name)

  val x = new SimpleDateFormat("mm")

  var logFiles = Map[String, (BufferedWriter, File)]()
  val logFolder = s"logs/${new SimpleDateFormat("yyyy_MM_dd-HH_mm-z").format(Calendar.getInstance().getTime())}"
  def getChannelLogMap(name: String): (BufferedWriter, File) =
    logFiles.find(x => x._1 == name) match {
      case Some(writerMap) => writerMap._2
      case None => {
        val file = new File(s"$logFolder/${name.replaceAll("/", "_")}.json")
        file.getParentFile.mkdirs
        logFiles += (name -> (new BufferedWriter(new FileWriter(file, true)), file))
        getChannelLogMap(name)
      }
    }

  def getChannelLogWriter(name: String): BufferedWriter = getChannelLogMap(name)._1
  def getChannelLog(name: String): File = getChannelLogMap(name)._2
  def getChannelLogReader(name: String): BufferedReader = new BufferedReader(new FileReader(getChannelLog(name)))

  override def postStop = logFiles.foreach { map => map._2._1.close() }

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
  emulatorManager ! ActorIdentity(WebSocketChannelManager, Some(self))

  def wrappedReceive = {
    case RegisterChannel(name, owner) => {
      getChannel(name) match {
        case Some(deadChannel: DeadChannel) => {
          subscriptions += (RealChannel(name, Server(owner)) -> subscriptions(deadChannel))
          subscriptions -= deadChannel
          context.watch(owner)
          log.debug(s"Replaced $deadChannel with a real channel")
        }
        case Some(realChannel: RealChannel) =>
          log.error(s"Refusing to use RegisterChannel($name, $owner) due to $realChannel")
        case None => {
          subscriptions += (RealChannel(name, Server(owner)) -> Vector())
          context.watch(owner)
          log.debug(s"Registered a new channel $name")
        }
      }
    }
    case Terminated(dead) => {
      getRole(dead) match {
        case Some(client: Client) => // Remove subscriptions
          subscriptionsFor(client).foreach { subscription =>
            subscriptions += (subscription._1 -> subscription._2.filterNot(c => c == client))
          }
        case Some(server: Server) => // Remove any channels
          channelsBy(server).foreach { realChannel =>
            subscriptions += (DeadChannel(realChannel.name) -> subscriptions(realChannel))
            subscriptions -= realChannel
          }
        case None =>
          log.debug(s"Actor unrecognized as client or server")
      }
    }
    case RegisterClient(chanName, clientRef) => {
      context.watch(clientRef)
      // Add to channel
      getChannel(chanName) match {
        case Some(chan) => subscriptions += (chan -> (subscriptions(chan) :+ Client(clientRef)))
        case None => subscriptions += (DeadChannel(chanName) -> Vector(Client(clientRef)))
      }

      // Send all the pre-existing log files
      log.debug(s"Writing existing logs for $chanName to client")
      getChannelLogWriter(chanName).flush // Ensure it's all there
      scala.io.Source.fromFile(getChannelLog(chanName)).getLines.foreach { line =>
        {
          val frame = TextFrame(wsmultiplex.Message(chanName, line).write)
          clientRef ! frame
        }
      }
      log.debug(s"Done writing existing logs")
    }
    case UnRegisterClient(chanName, clientRef) => {
      context.unwatch(clientRef)
      getChannel(chanName) match {
        case Some(chan) => subscriptions += (chan -> (subscriptions(chan).filterNot(_.actor == clientRef)))
        case None => log.debug(s"No such channel $chanName")
      }
    }
    case Message(channel, data, sender) => {
      getRole(sender) match {
        case Some(client: Client) => { // Send to channel server
          getChannel(channel) match {
            case Some(real: RealChannel) => real.owner.actor ! Message(channel, data, self)
            case Some(dead: DeadChannel) => log.error(s"Discarded Message - dead channel")
            case None => log.error(s"Discarded Message - unregistered channel")
          }
        }
        case Some(server: Server) => { // Send to all clients
          // Access log file for this channel and store the message
          getChannelLogWriter(channel).write(data + "\n")

          getChannel(channel) match {
            case Some(real: RealChannel) => {
              val frame = TextFrame(wsmultiplex.Message(channel, data).write)
              subscriptions(real).foreach { client => client.actor ! frame }
            }
            case Some(dead: DeadChannel) => log.error(s"Discarded Message - sent to dead channel")
            case None => log.error(s"Discarded Message - unregistered channel")
          }
        }
        case None => log.error(s"Discarded Message - unknown sender")
      }
    }
  }

  override def postReceive = {
    case x: Message => {}
    case other => {
      log.info(s"Post Update State: $subscriptions")
    }
  }

}

trait ChannelServer {
  private[this] val log = LoggerFactory.getLogger(getClass())
  import log.{ error, debug, info, trace }

  import WebSocketChannelManager._

  val channelManagerId = WebSocketChannelManager.channelManagerId

  /**
   *  Tells the Channel master to identify itself to you
   *
   *  @param masterIP host for the master node e.g. 10.0.0.23
   */
  def identifyChannelMaster(masterIP: String, correlationId: Any = channelManagerId)(implicit context: ActorContext) = {
    context.actorSelection(s"akka.tcp://clasp@${masterIP}:2552/user/channelManager") ! Identify(correlationId)
  }

  /** Ensures that the passed value can be converted into a root JSON object */
  def createMessageString[T](value: T)(implicit format: RootJsonFormat[T], printer: JsonPrinter = CompactPrinter): String = {
    val jsvalue = format.write(value)
    jsonBuilder.setLength(0)
    printer.print(jsvalue, jsonBuilder)
    jsonBuilder.toString
  }
  val jsonBuilder = new java.lang.StringBuilder()

  /**
   *  Convenience method for sending a message to the channel manager inside an Actor
   *
   *  You'll need to declare <code>implicit var channelManager: Option[ActorRef] = None</code>
   *  and use the the <code>channelIdentifyMaster</code> to use
   */
  def channelSend[T](chanName: String, data: T)(implicit format: RootJsonFormat[T], self: ActorRef, channelManager: Option[ActorRef]) = {
    if (channelManager.isDefined)
      channelManager.get ! Message(chanName, createMessageString(data), self)
    else 
      log.error(s"Unable to send message to $chanName with data $data")
  }

  def channelRegister(chanName: String)(implicit self: ActorRef, channelManager: Option[ActorRef]) = {
    if (channelManager.isDefined)
    	channelManager.get ! RegisterChannel(chanName, self)
    else 
      log.error(s"Unable to register $chanName")
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
