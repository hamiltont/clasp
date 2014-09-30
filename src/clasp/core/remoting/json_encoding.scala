package clasp.core.remoting

import spray.json.DefaultJsonProtocol
import spray.json.JsObject
import spray.json.RootJsonFormat
import spray.json.JsNull
import spray.json._
import spray.json.JsonFormat
import spray.json.JsString
import spray.json.JsValue
import org.slf4j.LoggerFactory
import clasp.core.Node
import scala.util.Failure
import scala.util.Try
import spray.json.DeserializationException
import scala.util.Success
import akka.actor.ActorRef
import clasp.core.sdktools._
import spray.json.JsBoolean
import clasp.core.Node.NodeDescription
import clasp.core.EmulatorActor.EmulatorDescription

// If we ever want to have simpler JSON objects than domain objects, 
// this is a neat trick
// case class User(id: Long, name: String)
// case class JUser(id: Option[Long], first_name: String)
// object UserImplicits {
//  implicit def domainToJUser(x: User): JUser = { User(x = Some(x.id), first_name = name)}
//  implicit def jUserToDomain(x:JUser): User  = { JUser(id = x.id.getOrElse(-1), name = x.first_name) }
// }

/**
 * Provides clasp-specific JSON marshalling and unmarshalling
 */
object ClaspJson extends DefaultJsonProtocol {

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

  implicit object RootBooleanFormat extends RootJsonFormat[Boolean] {
    def write(d: Boolean) = {
      JsObject(Map("value" -> JsBoolean(d)))
    }

    def read(value: JsValue) =
      value.asJsObject.getFields("value") match {
        case Seq(JsBoolean(value)) => value
        case _ => deserializationError("Boolean expected")
  implicit object sigarCpuFormat extends RootJsonFormat[CpuPerc] {
    def write(c: CpuPerc) =
      if (c == null)
        JsNull
      else
        JsObject("idle" -> JsNumber(c.getIdle),
          "irq" -> JsNumber(c.getIrq),
          "nice" -> JsNumber(c.getNice),
          "softirq" -> JsNumber(c.getSoftIrq),
          "stolen" -> JsNumber(c.getStolen),
          "sys" -> JsNumber(c.getSys),
          "user" -> JsNumber(c.getUser),
          "wait" -> JsNumber(c.getWait))

    def read(value: JsValue): CpuPerc =
      value.asJsObject.getFields("idle", "irq", "nice", "softirq", "stolen", "sys", "user", "wait") match {
        case Seq(JsNumber(idle), JsNumber(irq), JsNumber(nice), JsNumber(softirq), JsNumber(stolen), JsNumber(sys), JsNumber(user), JsNumber(wait)) =>
          // TODO....something? 
          null
        case _ => deserializationError("ActorRef expected")
      }

  }
  
  implicit object RootStringFormat extends RootJsonFormat[String] {
    def write(d: String) = {
      JsObject(Map("value" -> JsString(d)))
    }

    def read(value: JsValue) =
      value.asJsObject.getFields("value") match {
        case Seq(JsString(value)) => value
        case _ => deserializationError("String expected")
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