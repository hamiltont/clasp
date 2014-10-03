package clasp.core.remoting

import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone
import java.util.UUID
import org.hyperic.sigar.CpuPerc
import org.slf4j.LoggerFactory
import akka.actor.ActorRef
import clasp.core.EmulatorActor.EmulatorDescription
import clasp.core.Node
import clasp.core.Node.NodeDescription
import clasp.core.sdktools._
import spray.json._
import spray.json.DefaultJsonProtocol
import org.hyperic.sigar.Mem
import org.hyperic.sigar.Swap
import org.hyperic.sigar.NetStat

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
 *
 * <b>Warning:</b> Never create <code>RootJsonFormat</code> for the base
 * types like String, Int, Boolean, etc. This violates the mutually exclusive
 * property of Formatters required by spray-json, and your nice marshals like
 * <code>{ foo: "bar_string" }</code> are likely to become ugly things like
 * <code>{ foo: { value: "bar_string" } }</code>
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

  implicit object dateFormat extends RootJsonFormat[Date] {
    private val iso8601Format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    iso8601Format.setTimeZone(TimeZone.getTimeZone("UTC"))

    override def write(date: Date) = JsString(iso8601Format.format(date))

    override def read(json: JsValue): Date = json match {
      case JsString(date) => iso8601Format.parse(date)
      case _ => deserializationError("ISO8601 date expected")
    }
  }

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

  implicit object sigarMemFormat extends RootJsonFormat[Mem] {
    def write(m: Mem) =
      if (m == null)
        JsNull
      else
        JsObject("actualFree" -> JsNumber(m.getActualFree),
          "actualUsed" -> JsNumber(m.getActualUsed),
          "free" -> JsNumber(m.getFree),
          "freePercent" -> JsNumber(m.getFreePercent),
          "ram" -> JsNumber(m.getRam),
          "total" -> JsNumber(m.getTotal),
          "used" -> JsNumber(m.getUsed),
          "usedPercent" -> JsNumber(m.getUsedPercent))

    def read(value: JsValue): Mem = null
  }
  
  implicit object sigarSwapFormat extends RootJsonFormat[Swap] {
    def write(s: Swap) =
      if (s == null)
        JsNull
      else
        JsObject("free" -> JsNumber(s.getFree),
          "pageIn" -> JsNumber(s.getPageIn),
          "pageOut" -> JsNumber(s.getPageOut),
          "total" -> JsNumber(s.getTotal),
          "used" -> JsNumber(s.getUsed))

    def read(value: JsValue): Swap = null
  }
  
  implicit object uuidFormat extends RootJsonFormat[UUID] {
    def write(d: UUID) = {
      if (d == null)
        JsNull
      else
        JsString(d.toString)
    }
    def read(value: JsValue) = value match {
      case JsString(uuid) => UUID.fromString(uuid)
      case _ => deserializationError("UUID expected")
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

  implicit val nodeFormat = jsonFormat(NodeDescription, "ip", "name", "status", "emulators", "uuid", "asOf")

  implicit val emulatorDescriptionFormat = jsonFormat(EmulatorDescription, "publicip", "consolePort", "vncPort", "wsVncPort", "actorPath", "uuid")

  implicit val claspOptionsFormat = jsonFormat(ClaspOptions, "seedContacts", "seedCalendar", "display", "avdTarget", "abiName")
  implicit val diskOptionsFormat = jsonFormat(DiskImageOptions, "cache", "data", "initData", "nocache", "ramdisk", "sdcard", "wipeData")
  implicit val debugOptionsFormat = jsonFormat(DebugOptions, "debug", "logcat", "shell", "shellSerial", "showKernel", "trace", "verbose")
  implicit val mediaOptionsFormat = jsonFormat(MediaOptions, "audio", "audioIn", "audioOut", "noaudio", "radio", "useAudio")
  implicit val networkOptionsFormat = jsonFormat(NetworkOptions, "dns", "httpProxy", "netDelay", "netFast", "netSpeed", "consolePort", "adbPort", "reportConsole")
  implicit val systemOptionsForamt = jsonFormat(SystemOptions, "cpuDelay", "gpsDevice", "noJNI", "useGPU", "radio", "timezone", "memory", "qemu")
  implicit val uiOptionsFormat = jsonFormat(UIoptions, "dpiDevice", "noBootAnim", "noWindow", "scale", "rawKeys", "noSkin", "keySet", "onion", "onionAlpha", "onionRotation", "skin", "skinDir")
  implicit val emulatorOptionsFormat = jsonFormat(EmulatorOptions, "avd", "clasp", "disk", "debug", "media", "network", "ui", "system")

  /**
   * Helper class to allow a root-level JSON object without corrupting the
   * format of a Boolean. You should never create an Ack(false), just use
   * sender ! Try(new Exception("your failure reason")). This is idiomatic because
   * spray-routing natively supports Try() on all Futures (including asks), and
   * so you can either complete with a root object (e.g. this Ack class) or with
   * a failure. PS-I don't know what the hell I'm doing with mixing Try and scala-routing magic...
   */
  case class Ack(result: Boolean = true)
  implicit val ackFormat = jsonFormat(Ack, "result")

}