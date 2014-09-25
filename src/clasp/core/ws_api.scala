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

final case class Push(msg: String)

/**
 * First line of defense in handling HTTP connections. Creates separate
 * Actor for each connection
 * 
 * Responses are handled by {@link WebSocketWorker} first (for websocket connections), 
 * then by {@link HttpApi} (for REST Api connections), and finally by the nodeJS 
 * process that acts as a server for the web interface 
 */
class WebSocketServer(val httpApi: ActorRef) extends Actor with ActorLogging {
  def receive = {
    // when a new connection comes in we register a WebSocketConnection actor as the per connection handler
    case Http.Connected(remoteAddress, localAddress) =>
      {
        log.info(s"HTTP connected using $remoteAddress and $localAddress")
        log.info(s"Sender was ${sender()} (== $sender )?")
        val serverConnection = sender()
        log.info(s"Server is $serverConnection")
        val conn = context.actorOf(Props(new WebSocketWorker(serverConnection, httpApi)))
        log.info(s"Worker is $conn")
        serverConnection ! Http.Register(conn)
      }
    case rest =>
      log.debug(s"Received unknown object $rest")
  }
}

/**
 * Checks all HTTP connections for WebSocket Upgrade requests. Directly
 * handles these requests, and passes all other HTTP off to the {@link HttpApi}
 */
class WebSocketWorker(val serverConnection: ActorRef, val httpApi: ActorRef) extends HttpServiceActor with WebSocketServerWorker {
  log.info(s"I was created! $this ( $serverConnection ) HTTP is $httpApi")

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
            log.debug(s"Received response of $response")
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

  // This works if I don't handle HTTP requests 
  // var connection = new WebSocket('ws://localhost:8080');
  override def businessLogic: Receive = {
    case x: BinaryFrame =>
      log.info("Server BinaryFrame Received:" + x)
      sender() ! x

    case x: TextFrame =>
      if (x.payload.length <= 5) {
        log.info("Server TextFrame Received:" + x)
        sender() ! TextFrame("Got it!")
      } else {
        log.info("Server Large TextFrame Received:" + x)
        sender() ! TextFrameStream(5, new ByteArrayInputStream(x.payload.toArray))
      }

    case UpgradedToWebSocket => {
      log.info(s"Server Upgraded to WebSocket: ${sender()}")
      //      context.system.scheduler.schedule(10.second, 3.second) {
      //        send(TextFrame("scheduled message"))
      //      }
    }

    case x: HttpRequest => {
      log.info("Server HttpRequest Received")
      sender() ! HttpResponse()
    }

    case x: Tcp.ConnectionClosed =>
      log.info("Server Close")

    case x => log.error("Server Unknown " + x)
  }

}
