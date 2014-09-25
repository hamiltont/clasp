package clasp.core

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.sys.process.Process
import scala.sys.process.ProcessLogger
import scala.sys.process.stringToProcess
import org.slf4j.LoggerFactory
import EmulatorManager._
import Node._
import NodeManager._
import akka.actor.ActorRef
import akka.pattern.ask
import clasp.core.EmulatorActor.EmulatorDescription
import clasp.core.sdktools.ClaspOptions
import clasp.core.sdktools.DebugOptions
import clasp.core.sdktools.DiskImageOptions
import clasp.core.sdktools.EmulatorOptions
import clasp.core.sdktools.MediaOptions
import clasp.core.sdktools.NetworkOptions
import clasp.core.sdktools.SystemOptions
import clasp.core.sdktools.UIoptions
import clasp.utils.LinuxProcessUtils._
import spray.http._
import spray.http._
import spray.client.pipelining.Get
import spray.client
import spray.http.HttpMethods._
import spray.http.StatusCodes.InternalServerError
import spray.http.StatusCodes.NotFound
import spray.httpx.SprayJsonSupport
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
import akka.actor.Props
import akka.io.IO
import spray.can.Http
import clasp.core.sdktools._
import spray.http.HttpHeaders.Authorization

// If we ever want to have simpler JSON objects than domain objects, 
// this is a neat trick
// case class User(id: Long, name: String)
// case class JUser(id: Option[Long], first_name: String)
// object UserImplicits {
//  implicit def domainToJUser(x: User): JUser = { User(x = Some(x.id), first_name = name)}
//  implicit def jUserToDomain(x:JUser): User  = { JUser(id = x.id.getOrElse(-1), name = x.first_name) }
// }

object MyJsonProtocol extends DefaultJsonProtocol {

  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{ error, debug, info, trace }

  // Teach Spray how we want to marshal an ActorRef
  implicit object actorFormat extends RootJsonFormat[ActorRef] {
    def write(d: ActorRef) = {
      if (d == null)
        JsNull
      else
        JsString(d.toString)
    }

    // Not worried about recreating ActorRefs currently, but I may 
    // eventually use ActorSelection and then resolve to ActorRef in a future
    def read(value: JsValue) =
      value.asJsObject.getFields("path") match {
        case Seq(JsString(path)) =>
          null
        case _ => deserializationError("ActorRef expected")
      }
  }

  //  implicit object failFormat extends RootJsonFormat[scala.util.Failure[Nothing]] {
  //    def write(b: Failure[Nothing]) = {
  //      JsObject(Map(
  //        "status" -> JsString("failure"),
  //        "reason" -> JsString(b.exception.getMessage())))
  //    }
  //    def read(value: JsValue) =
  //      value.asJsObject.getFields("status", "reason") match {
  //        case Seq(JsBoolean(value), JsString(reason)) => Failure(new Exception(reason))
  //        case _ => deserializationError("Failure[Throwable] expected")
  //      }
  //  }

  implicit object booleanFormat extends RootJsonFormat[Boolean] {
    def write(d: Boolean) = {
      JsObject(Map("value" -> JsBoolean(d)))
    }

    def read(value: JsValue) =
      value.asJsObject.getFields("value") match {
        case Seq(JsBoolean(value)) => value
        case _ => deserializationError("Boolean expected")
      }
  }

  implicit object tryFormat extends RootJsonFormat[Try[Boolean]] {
    def write(b: Try[Boolean]) = {
      debug(s"Asked to write a try[boolean] of $b")
      b match {
        case Success(value) => {
          JsObject(Map(
            "status" -> (if (value) JsString("success") else JsString("failure"))))
        }
        case Failure(reason) => {
          JsObject(Map(
            "status" -> JsString("failure"),
            "reason" -> JsString(reason.getMessage())))
        }
      }
    }

    def read(value: JsValue) =
      value.asJsObject.getFields("status", "reason") match {
        case Seq(JsBoolean(value), JsNull) => Success(value)
        case Seq(JsBoolean(value), JsString(reason)) => Failure(new Exception(reason))
        case _ => deserializationError("Boolean expected")
      }
  }

  def jsonEnum[T <: Enumeration](enu: T) = new JsonFormat[T#Value] {
    def write(obj: T#Value) = JsString(obj.toString)

    def read(json: JsValue) = json match {
      case JsString(txt) => enu.withName(txt)
      case something => throw new DeserializationException(s"Expected a value from enum $enu instead of $something")
    }
  }

  implicit val nodeStatusFormat = jsonEnum(Node.Status)

  implicit val nodeFormat = jsonFormat(NodeDescription, "ip", "name", "status", "emulators", "asOf")

  implicit val emulatorDescriptionFormat = jsonFormat(EmulatorDescription, "publicip", "consolePort", "vncPort", "wsVncPort", "actorPath", "uuid")

  implicit val claspOptionsFormat = jsonFormat(ClaspOptions, "seedContacts", "seedCalendar", "display", "avdTarget", "abiName")
  implicit val diskOptionsFormat = jsonFormat(DiskImageOptions, "cache", "data", "initData", "nocache", "ramdisk", "sdcard", "wipeData")
  implicit val debugOptionsFormat = jsonFormat(DebugOptions, "debug", "logcat", "shell", "shellSerial", "showKernel", "trace", "verbose")
  implicit val mediaOptionsFormat = jsonFormat(MediaOptions, "audio", "audioIn", "audioOut", "noaudio", "radio", "useAudio")
  implicit val networkOptionsFormat = jsonFormat(NetworkOptions, "dns", "httpProxy", "netDelay", "netFast", "netSpeed", "consolePort", "adbPort", "reportConsole")
  implicit val systemOptionsForamt = jsonFormat(SystemOptions, "cpuDelay", "gpsDevice", "noJNI", "useGPU", "radio", "timezone", "memory", "qemu")
  implicit val uiOptionsFormat = jsonFormat(UIoptions, "dpiDevice", "noBootAnim", "noWindow", "scale", "rawKeys", "noSkin", "keySet", "onion", "onionAlpha", "onionRotation", "skin", "skinDir")
  implicit val emulatorOptionsFormat = jsonFormat(EmulatorOptions, "avd", "clasp", "disk", "debug", "media", "network", "ui", "system")
}

class HttpApi(val nodeManager: ActorRef,
  val emulatorManger: ActorRef) extends HttpServiceActor {

  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{ error, debug, info, trace }

  import MyJsonProtocol._

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

  /**
   * Handles common HTTP response case when a HTTP request generates an
   * ActorRef.ask
   */
  def askToComplete[T: ToResponseMarshaller](who: ActorRef, message: Any)(implicit tag: scala.reflect.ClassTag[T]) = {
    onComplete(who.ask(message)(15.seconds).mapTo[T]) {
      case Success(value) =>
        {
          complete(value)
        }
      case Failure(reason) =>
        {
          error("Failed to complete")
          complete(InternalServerError, s"Error occurred: ${reason.getMessage}")
        }
    }
  }

  // Handles 
  // ROOT/nodes
  // ROOT/nodes/all
  // ROOT/nodes/launch
  val nodes = pathPrefix("nodes") {
    path("all") {
      complete(nodeManager.ask(NodeList(false))(timeout).mapTo[List[NodeDescription]])
    } ~
      path("launch") {
        complete(nodeManager.ask(BootNode())(timeout).mapTo[Boolean])
      } ~
      pathEndOrSingleSlash {
        askToComplete[List[NodeDescription]](nodeManager, NodeList())
      }
  }

  // Handles 
  // ROOT/emulators
  // ROOT/emulators/<UUID>
  // ROOT/emulators/launch
  val emulators = pathPrefix("emulators") {
    path(JavaUUID) { uuid =>
      askToComplete[EmulatorOptions](emulatorManger, GetEmulatorOptions(uuid.toString))
    } ~
      path("launch") {
        askToComplete[Try[Boolean]](emulatorManger, EmulatorManager.LaunchEmulator())
      } ~
      pathEndOrSingleSlash {
        askToComplete[List[EmulatorDescription]](emulatorManger, ListEmulators())
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
        true
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
            val wsUri = uri.withPort(9090)
            implicit val system = context.system
            implicit val timeout: akka.util.Timeout = 5.second
            val foo = BasicHttpCredentials("brandon", "clasp")
            val outbound = HttpRequest(GET, wsUri, List(Authorization(foo)))

            val response = IO(Http).ask(outbound).mapTo[HttpResponse]
            debug("Requested dash")
            response.onComplete {
              case Success(value) => {
                debug("successful dash request")
              }
              case Failure(reason) => {
                debug("failed proxy request")
              }
            }

            complete(response)
          }
      } ~ complete(NotFound)

  def receive = runRoute {
    logRequestResponse("api", akka.event.Logging.InfoLevel) {
      nodes ~
        emulators ~
        system ~
        static
    }
  }

  override def postStop = {
    nodeProcess.getOrElse(Process("which echo").run).destroy
    super.postStop
  }

}