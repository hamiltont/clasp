package clasp.core

import scala.concurrent.ExecutionContext.Implicits.global
import Node._
import NodeManager._
import EmulatorManager._
import akka.actor.ActorRef
import akka.pattern.ask
import spray.http.HttpMethods._
import spray.http.StatusCodes.InternalServerError
import spray.httpx.SprayJsonSupport
import spray.httpx.SprayJsonSupport._
import spray.httpx.marshalling._
import spray.http._
import spray.json._
import spray.json.DefaultJsonProtocol._
import spray.json.DefaultJsonProtocol
import spray.json.JsObject
import spray.json.JsString
import spray.json.JsValue
import spray.json.JsonFormat
import spray.routing._
import scala.concurrent.duration._
import scala.util.Success
import scala.util.Failure
import spray.http._
import spray.httpx.marshalling._
import spray.http.StatusCodes.NotFound
import org.slf4j.LoggerFactory
import clasp.core.sdktools.EmulatorOptions
import clasp.core.sdktools.ClaspOptions
import clasp.core.sdktools.DiskImageOptions
import clasp.core.sdktools.DebugOptions
import clasp.core.sdktools.MediaOptions
import clasp.core.sdktools.NetworkOptions
import clasp.core.sdktools.SystemOptions
import clasp.core.sdktools.UIoptions
import clasp.core.EmulatorActor.EmulatorDescription

object MyJsonProtocol extends DefaultJsonProtocol {

  // Teach Spray how we want to marshal an ActorRef
  implicit object actorFormat extends JsonFormat[ActorRef] {
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

  implicit object booleanFormat extends JsonFormat[Boolean] {
    def write(b: Boolean) = {
      JsObject(Map(
        "result" -> JsBoolean(b)))
    }

    def read(value: JsValue) =
      value.asJsObject.getFields("result") match {
        case Seq(JsBoolean(value)) =>
          value
        case _ => deserializationError("Boolean expected")
      }
  }

  implicit val nodeFormat = jsonFormat(NodeDescription, "ip", "name", "emulators", "asOf")

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

  def receive = runRoute {
    path("nodes") {
      get {
        onComplete(nodeManager.ask(NodeList())(3.seconds).mapTo[List[NodeDescription]]) {
          case Success(value) => {
            complete(value)
          }
          case Failure(ex) => {
            complete(InternalServerError, s"An error occurred: ${ex.getMessage}")
          }
        }
      }
    } ~
    pathPrefix("emulators") {
      path(JavaUUID) { uuid =>
        info(s"Matched a UUID, cool! $uuid")
          onComplete(emulatorManger.ask(GetEmulatorOptions(uuid.toString()))(10.seconds).mapTo[EmulatorOptions]) {
            case Success(value) => {
              complete(value)
            }
            case Failure(ex) => {
              complete(InternalServerError, s"An error occurred: ${ex.getMessage}")
            }
          }
        
      } ~
      pathEndOrSingleSlash {
          onComplete(emulatorManger.ask(ListEmulators())(3.seconds).mapTo[List[EmulatorDescription]]) {
            case Success(value) => {
              complete(value)
            }
            case Failure(ex) => {
              complete(InternalServerError, s"An error occurred: ${ex.getMessage}")
            }
          }
        }
    } ~
    pathPrefix("nodemanager") {
        // TODO send reply before shutting down. Current approach means no reply is sent
        path("shutdown") {
          onComplete(nodeManager.ask(NodeManager.Shutdown())(3.seconds).mapTo[Boolean]) {
            case Success(value) => {
              debug(s"Shutdown request received")
              complete(value.toString)
            }
            case Failure(ex) => {
              complete(InternalServerError, s"An error occurred: ${ex.getMessage}")
            }
          }
        }
      } ~
      pathPrefix("emulatormanager") {
        // TODO send reply before shutting down. Current approach means no reply is sent
        path("launch") {
          onComplete(emulatorManger.ask(EmulatorManager.LaunchEmulator())(3.seconds).mapTo[Boolean]) {
            case Success(value) => {
              complete(value.toString)
            }
            case Failure(ex) => {
              complete(InternalServerError, s"An error occurred: ${ex.getMessage}")
            }
          }
        }
      } ~
      path("test" / JavaUUID) { uuid =>
        info(s"Matched a UUID, cool! $uuid")
        val e = new EmulatorOptions
        complete("")
      } ~
      path("test2") { 
        val e = new EmulatorOptions
        complete(e)
      } ~
      pathEndOrSingleSlash {
        complete(NotFound)
      }

  }

}