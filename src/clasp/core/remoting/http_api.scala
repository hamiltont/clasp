package clasp.core.remoting

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.sys.process.Process
import scala.sys.process.ProcessLogger
import org.slf4j.LoggerFactory
import akka.actor.ActorRef
import clasp.utils.LinuxProcessUtils._
import spray.http._
import spray.json._
import spray.http.HttpMethods._
import spray.http.StatusCodes.InternalServerError
import spray.http.StatusCodes.NotFound
import spray.httpx.SprayJsonSupport._
import spray.httpx.marshalling._
import spray.httpx.marshalling._
import spray.json._
import spray.json.DefaultJsonProtocol._
import spray.json.JsObject
import spray.json.JsString
import spray.json.JsValue
import spray.json.JsonFormat
import spray.routing._
import akka.io.IO
import akka.pattern.ask
import clasp.core.sdktools._
import spray.http.HttpHeaders.Authorization
import spray.can.server.UHttp
import akka.actor.actorRef2Scala
import akka.util.Timeout.durationToTimeout
import spray.httpx.marshalling.ToResponseMarshallable.isMarshallable
import spray.routing.Directive.pimpApply
import spray.routing.directives.OnCompleteFutureMagnet.apply
import clasp.core.Node.NodeDescription
import clasp.core.NodeManager
import clasp.core.Node
import clasp.core.EmulatorManager
import clasp.core.EmulatorActor.EmulatorDescription
import scala.util.Success
import clasp.core.remoting.ClaspJson._

/**
 * Defines HTTP REST API for Clasp. Called for HTTP messages that
 * {@link WebSocketWorker} decides are not for WebSocket handling.
 *
 * Any  HTTP requests not resolved by our REST API are forwarded to
 * the NodeJS server running the web interface, allowing us to
 * neatly hide the REST API from most clients.
 */
class HttpApi(val nodeManager: ActorRef,
  val emulatorManger: ActorRef) extends HttpServiceActor {

  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{ error, debug, info, trace }

  import clasp.core.remoting.ClaspJson._

  val timeout = 15.seconds

  val nodeProcess = {
    val env = "PATH" -> appendToEnv("PATH", "/usr/local/bin")
    val findNode = Process("which node", None, env)
    if (findNode.! == 0) {
      debug(s"Running NodeJS on localhost:9090")
      val nodeLogger = ProcessLogger(line => info(s"nodejs:out: $line"),
        line => error(s"nodejs:err: $line"))
      val node = Process(s"${findNode.!!} www/app.js", None, env).run(nodeLogger)
      Some(node)
    } else {
      debug("NodeJS not found, not launching dashboard")
      None
    }
  }

  val nodeCommand = s"node www/app.js"

  // Handles 
  // ROOT/nodes
  // ROOT/nodes/all
  // ROOT/nodes/launch
  val nodes = pathPrefix("nodes") {
    path("all") {
      complete(nodeManager.ask(NodeManager.NodeList(false))(timeout).mapTo[List[NodeDescription]])
    } ~
      path("launch") {
        complete(nodeManager.ask(NodeManager.BootNode())(timeout).mapTo[Ack])
      } ~
      pathEndOrSingleSlash {
        complete(nodeManager.ask(NodeManager.NodeList())(timeout).mapTo[List[NodeDescription]])
      }
  }

  // Handles 
  // ROOT/emulators
  // ROOT/emulators/<UUID>
  // ROOT/emulators/launch
  val emulators = pathPrefix("emulators") {
    path(JavaUUID) { uuid =>
      complete(emulatorManger.ask(EmulatorManager.GetEmulatorOptions(uuid.toString))(timeout).mapTo[EmulatorOptions])
    } ~
      path("launch") {
        complete(emulatorManger.ask(EmulatorManager.LaunchEmulator())(timeout).mapTo[Ack])
      } ~
      pathEndOrSingleSlash {
        complete(emulatorManger.ask(EmulatorManager.ListEmulators())(timeout).mapTo[List[EmulatorDescription]])
      }
  }

  // Handles
  // ROOT /system/shutdown
  val system = pathPrefix("system") {
    path("shutdown") {
      // Need a second to finish responding
      complete {
        debug(s"scheduling shutdown")
        context.system.scheduler.scheduleOnce(3.seconds)(nodeManager ! NodeManager.Shutdown())
        "success"
      }
    }
  }

  // Handles
  // ROOT -> proxy to localhost:9090 
  // Unresolved -> 404
  val static =
    path("testing") {
      complete("Youre testing")
    } ~
      noop {
        if (nodeProcess.isEmpty)
          complete("Root static page")
        else
          requestUri { uri =>
            implicit val system = context.system
            implicit val timeout: akka.util.Timeout = this.timeout
            val dashUri = uri.withPort(9090)
            val auth = BasicHttpCredentials("brandon", "clasp")
            val outbound = HttpRequest(GET, dashUri, List(Authorization(auth)))
            val response = IO(UHttp).ask(outbound).mapTo[HttpResponse]
            complete(response)
          }
      } ~ complete(NotFound)

  val corsHeaders = List(HttpHeaders.`Access-Control-Allow-Origin`(AllOrigins),
    HttpHeaders.`Access-Control-Allow-Methods`(GET, POST, OPTIONS, DELETE),
    HttpHeaders.`Access-Control-Allow-Headers`("Origin, X-Requested-With, Content-Type, Accept, Accept-Encoding, Accept-Language, Host, Referer, User-Agent"))

  def receive = runRoute {
    // logRequestResponse("api", akka.event.Logging.InfoLevel) {
    respondWithHeaders(corsHeaders) {
      nodes ~
        emulators ~
        system ~
        static
    }
    // }
  }

  override def postStop = {
    nodeProcess.getOrElse(Process("which echo").run).destroy
    super.postStop
  }

}