/**
 * @author hamiltont
 *
 */
package clasp.core

import java.io.Serializable
import java.util.Date
import java.util.UUID

import scala.collection.immutable.Map
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Promise
import scala.concurrent.duration.DurationInt
import scala.concurrent.future
import scala.concurrent.promise
import scala.language.postfixOps
import scala.sys.process.Process
import scala.sys.process.ProcessLogger
import scala.sys.process.stringToProcess
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.slf4j.LoggerFactory

import EmulatorActor._
import EmulatorLogger._
import EmulatorManager._
import akka.actor.Actor
import akka.actor.ActorIdentity
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Cancellable
import akka.actor.Identify
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.pattern.ask
import clasp.ClaspConf
import clasp.Emulator
import clasp.core.remoting.ChannelServer
import clasp.core.remoting.ClaspJson._
import clasp.core.remoting.ClaspJson.Ack
import clasp.core.remoting.WebSocketChannelManager._
import clasp.core.remoting.WebSocketChannelManager
import clasp.core.sdktools.EmulatorOptions
import clasp.core.sdktools.avd
import clasp.core.sdktools.sdk
import clasp.utils.ActorLifecycleLogging
import clasp.utils.ActorLifecycleLogging
import clasp.utils.ActorStack
import clasp.utils.ActorStack
import clasp.utils.Slf4jLoggingStack
import clasp.utils.Slf4jLoggingStack
import clasp.utils.Slf4jLoggingStack
import spray.json._

object EmulatorManager {
  case class EmulatorReady(emu: EmulatorDescription, bootTime: Long)
  case class EmulatorFailedBoot(actor: ActorRef)
  case class EmulatorCrashed(emu: EmulatorDescription)

  // Used to send commands to manager
  case class ListEmulators()
  case class LaunchEmulator()
  case class GetEmulatorOptions(uuid: String)

  // TODO move to task manager
  case class QueueEmulatorTask(function: Emulator => Map[String, Serializable], promise: Promise[Map[String, Serializable]])
  case class TaskSuccess(taskId: String, data: Map[String, Serializable], emulator: EmulatorDescription)
  case class TaskFailure(taskId: String, reason: Throwable, emulator: EmulatorDescription)
  case class CheckForTasks(emulator: EmulatorDescription)

  case class StructuredTaskResult(taskType: String, duration: Long)

  case class SerialStressTest(emulator: EmulatorDescription)

  case class MeasureTPS(taskCount: Long = 100000)
}
class EmulatorManager(val nodeManager: ActorRef, val conf: ClaspConf)
  extends Actor
  with ActorLifecycleLogging
  with ActorStack
  // with Slf4jLoggingStack
  with ChannelServer {

  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{ error, debug, info, trace }

  val emulators = ListBuffer[EmulatorDescription]()

  val chanName = "/emulatormanager"
  val taskChanName = "/tasks"
  implicit var channelManager: Option[ActorRef] = None
  // implicit var channelManager = Some(context.actorFor(s"akka.tcp://clasp@${conf.ip()}:2552/user/channelManager"))

  // Tasks that have been delivered but not fulfilled
  val outstandingTasks: scala.collection.mutable.Map[String, Promise[Map[String, Serializable]]] = scala.collection.mutable.Map()

  // Contains tasks that need to be delivered to workers
  val undeliveredTasks: scala.collection.mutable.Queue[EmulatorTask] = scala.collection.mutable.Queue()

  def sendTask(to: EmulatorDescription) = {
    if (undeliveredTasks.length != 0) {
      info(s"Dequeuing task for $to")
      // Only works because the remote system has the same classpath loaded, 
      // so all anonymous functions exist on both systems. If we want this to 
      // work in a dynamic manner we would have to serialize the class that's 
      // associated with the anonymous function, send that
      // across the wire & load it.
      // See http://doc.akka.io/docs/akka/snapshot/scala/serialization.html#serialization-scala
      // See http://www.scala-lang.org/node/10566
      // Alternatively, just copy all *.class files
      to.actor ! undeliveredTasks.dequeue
    } else
      context.system.scheduler.scheduleOnce(100.millisecond)(self ! CheckForTasks(to))
  }

  def wrappedReceive = {
    case ActorIdentity(WebSocketChannelManager, Some(manager)) =>
      {
        channelManager = Some(manager)
        debug(s"Channel manager arrived, registering")
        channelRegister(chanName)
        channelRegister(taskChanName)
        channelRegister("/measuretps")
      }
    case EmulatorReady(emulator, time) => {
      // Create a record of emulator boot time

      val o = JsObject("emulators" -> JsNumber(emulators.length),
        "boottime" -> JsNumber(time),
        "asOf" -> dateFormat.write(new Date))
      channelSend(chanName, o)
      info(s"Sent emulator ready message to channel manager")

      emulators += emulator
      info(s"Emulator ready: ${emulator}")
      info(s"${emulators.length} emulators awake")
      sendTask(emulator)

      // info(s"Will trigger stress test in 30 seconds")
      // context.system.scheduler.scheduleOnce(30.second){
      //  info("It's been 30 seconds, triggering a stress test")  
      //  self ! SerialStressTest(emulator)
      // }
    }
    case GetEmulatorOptions(uuid) => {
      val matched = emulators.filter(description => description.uuid.equals(uuid))
      if (matched.isEmpty) { // TODO

      } else
        matched.head.actor ! GetOptions(sender)
    }
    case EmulatorFailedBoot(actor) => {
      info(s"Emulator failed to boot: $actor")
    }
    case SerialStressTest(emulator) => {
      info(s"Stress test requested for $emulator (ignoring)")
      /*val task = (emu: Emulator) => {
        info("About to install APK")
        sdk.install_package(emu.serialID, "examples/antimalware/Profiler.apk")
        info("Installed APK, now uninstalling")
        sdk.uninstall_package(emu.serialID, "examples/antimalware/Profiler.apk")
        info("Uninstalled APK")
        Map[String, Serializable]()
      }
      for (a <- 1 to 9) {
        val result = promise[Map[String, Serializable]]
        self ! EmulatorManager.QueueEmulatorTask(task, result)
      }
      info(s"Queued basic stress tasks")
      val lastResult = promise[Map[String, Serializable]]
      self ! EmulatorManager.QueueEmulatorTask(task, lastResult)
      info(s"Queued final stress task")
      val finalResult = lastResult.future
      finalResult onComplete {
        case Success(value) => {
          info("Final task completed successfully")
        }
        case _ : Failure[_] => {
          info("Final task failed")
        }
      }

      info(s"Queued all tasks for stress test, scheduling task check in 10sec")
      val stressEmulator = emulator
      context.system.scheduler.scheduleOnce(10.second)(self ! CheckForTasks(stressEmulator))
      */
    }
    case CheckForTasks(emulator) => sendTask(emulator)
    case MeasureTPS(taskCount) => {

      future {
        val startTime = System.currentTimeMillis
        val noopTask = (emu: Emulator) => { Map[String, java.io.Serializable]() }
        for (i <- 0L to taskCount) {
          val result = promise[Map[String, Serializable]]
          self ! EmulatorManager.QueueEmulatorTask(noopTask, result)
        }
       
        val tasks = taskCount
        val finalTask = (emu: Emulator) => { Map[String, java.io.Serializable]("start" -> startTime, "tasks" -> tasks) }
        val finalResult = promise[Map[String, Serializable]]
        self ! EmulatorManager.QueueEmulatorTask(finalTask, finalResult)
        val whoAmI = self
        finalResult.future onComplete {
          case Success(value) => {
            val end = System.currentTimeMillis
            info(s"MeasureTPS has completed successfully at $end")
            val duration = end - java.lang.Long.parseLong(value("start").toString)
            info(s"MeasureTPS took $duration for ${value("tasks")} tasks")
            
            // Assumes all emulators are started by this pint
            val ecount = emulators.length

            val o = JsObject("tasks" -> JsNumber(value("tasks").toString),
              "duration" -> JsNumber(duration),
              "emulators" -> JsNumber(ecount))
            channelSend("/measuretps", o)
          }
          case Failure(reason) => info(s"MeasureTPS has failed: $reason")
        }
      }.onComplete {
        case Success(value) => info(s"MeasureTPS completed with $value")
        case Failure(reason) => info(s"MeasureTPS failed with $reason")
      }

      sender ! Ack()
    }
    case EmulatorCrashed(emulator) => {
      info(s"Emulator crashed: $emulator")
      emulators -= emulator
    }
    case _: ListEmulators => sender ! emulators.toList
    case QueueEmulatorTask(task, promise) => {
      // Note that this is not strictly threadsafe.
      // Consider using twitter's snowflake library
      val id = UUID.randomUUID().toString()
      outstandingTasks(id) = promise
      info(s"Enqueued new task: $id")
      undeliveredTasks.enqueue(new EmulatorTask(id, task))
    }
    case TaskSuccess(id, data, emulator) => {
      info(s"Task $id has completed")

      if (data.contains("type") && data.contains("duration")) {
        val result = StructuredTaskResult(data("type").toString, java.lang.Long.parseLong(data("duration").toString))
        channelSend(taskChanName, result)
      }

      val promise_option = outstandingTasks remove id
      promise_option.get success data
      sendTask(emulator)
    }
    case TaskFailure(id, reason, emulator) => {
      info(s"Task $id has failed")
      val promise_option = outstandingTasks remove id
      promise_option.get failure reason
      sendTask(emulator)
    }
    case _: LaunchEmulator => {
      info(s"Received launch emulator request from $sender - asking for nodes")

      val originalSender = sender
      val response = nodeManager.ask(NodeManager.FindNodesForLaunch(1))(15.seconds).mapTo[Map[Node.NodeDescription, Int]]
      response onComplete {
        case Success(result) => {
          val availableNodes = result.find(nodeMap => nodeMap._2 > 0)
          if (availableNodes.isEmpty) {
            debug(s"Unable to launch emulator, no available nodes")
            originalSender ! Try(new InstantiationException("No nodes available to run on"))
          } else {
            originalSender ! Ack()
            availableNodes.head._1.actor.get ! Node.LaunchEmulator(1)
          }
        }
        case Failure(reason) => {
          debug(s"Unable to launch emulator")
          originalSender ! Failure(reason)
        }
      }
    }
  }
}

object EmulatorActor {

  // Used to send commands to emulators
  case class EmulatorTask(taskid: String, task: Emulator => Map[String, Any])
  case class GetOptions(sendTo: ActorRef)

  // Used to hold static reference to emulator
  // publicip - publically routable IP address intended for direct communication from clasp-external 
  // nodes, such as web browsers. This will commonly equal the master node's IP address, and the 
  // master node will act as a TCP proxy directly to the servers the emulator is running (mainly VNC) 
  case class EmulatorDescription(publicip: String, port: Int, vncPort: Int, wsVncPort: Int, actor: ActorRef, uuid: String)

  // Used internally to update status
  case class BootSuccess()
  case class BootFailure()
  case class EmulatorHeartbeat()

}

// An always-on presence for a single emulator process. 
//  - Monitors process state (STARTED, READY, etc)
//  - Can hibernate and resume process internally and transparently to the rest of clasp
//  - Can receive, queue, and eventually deliver actions on an emulator process
// Eventually we will have an Emulator object, which will be a proxy that allows 
// others to interface with the EmulatorActor without having to understand its 
// interface
// TODO make EmulatorActor a FSM with states Booted, Booting, and Not Booted
class EmulatorActor(val nodeId: Int, var opts: EmulatorOptions,
  val node: Node) extends Actor
  with ActorLifecycleLogging
  with ActorStack
  with Slf4jLoggingStack {

  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{ error, debug, info, trace }

  // Assign a unique ID to each emulator
  val uuid = UUID.randomUUID().toString()
  val conf = node.conf

  // Emulator ports (each needs two)
  // WARNING: consolePort must be an even integer
  val base_emulator_port = 5556
  val consolePort = base_emulator_port + 2 * nodeId
  val adbPort = consolePort + 1

  opts = opts.copy(network = opts.network.copy(consolePort = Some(consolePort), adbPort = Some(adbPort)))
  val serialID = s"emulator-$consolePort"
  // Display port (1 is 5901, 99 is 5999)
  val base_display_num = 1
  val display_number = base_display_num + nodeId
  val base_display_port = 5900
  val display_port = base_display_port + display_number

  val base_wsDisplay_port = 6080
  val ws_display_port = base_wsDisplay_port + display_number

  val logger = context.actorOf(Props(new EmulatorLogger(serialID, node, this)), s"logger")
  // Emulator manager reference
  val emanager = context.system.actorFor(s"akka.tcp://clasp@${node.masterip}:2552/user/emulatormanager")

  // Static description of this emulator
  var description: Option[EmulatorDescription] = None

  // Processes for display management on X11 based system
  var XvfbProcess: Option[Process] = None
  var x11vncProcess: Option[Process] = None
  var websockifyProcess: Option[Process] = None

  var heartbeatSchedule: Cancellable = null

  // Build sdcard, avd, and start emulator
  var avd: avd = null
  val buildTime = System.currentTimeMillis
  val process = build()

  override def postStop = {
    super.preStart
    info(s"Halting emulator $this")
    process.destroy
    process.exitValue // block until destroyed
    info(s"Halted emulator process")

    if (!websockifyProcess.isEmpty) {
      info("Halting websockify")
      websockifyProcess.get.destroy
      websockifyProcess.get.exitValue
      info("Halted websockify")
    }

    if (!x11vncProcess.isEmpty) {
      info("Halting x11vnc")
      x11vncProcess.get.destroy
      x11vncProcess.get.exitValue
      info("Halted x11vnc")
    }

    if (!XvfbProcess.isEmpty) {
      info("Halting Xvfb")
      XvfbProcess.get.destroy
      XvfbProcess.get.exitValue
      info("Halted Xvfb")
    }

    if (conf.noClean.apply)
      info("Intentionally not cleaning up AVD")
    else {
      info(s"Removing AVD")
      avd.delete
    }

    if (heartbeatSchedule != null)
      heartbeatSchedule.cancel
    else
      debug("Heartbeat schedule was null")
  }

  override def preStart() {
    super.preStart

    implicit val system = context.system
    val boot = future {
      info(s"Waiting for emulator $this to come online")
      // Safety factor of ~3 (~173 seconds is normal for hardware-accelerated 
      // emulator)
      if (sdk.wait_for_emulator(serialID, 500.second))
        self ! BootSuccess()
      else
        self ! BootFailure()
    }
  }

  def wrappedReceive = {
    case _: EmulatorHeartbeat => {
      // Executing a command on the emulator to ensure it's 
      // alive and responding
      debug(s"Sending heartbeat to $serialID.")
      val ret = future { sdk.remote_shell(serialID, "echo alive", 5.seconds) }
      ret onComplete {
        case Success(out) => {
          if (out.isEmpty) {
            error(s"Emulator $serialID heartbeat failed. Destroying.")
            emanager ! EmulatorCrashed(description.get)
            context.stop(self)
          }
        }
        case Failure(e) => {
          error(s"Emulator $serialID heartbeat failed. Destroying.")
          // TODO: What if we're in the middle of a task?
          context.stop(self)
        }
      }
    }
    case EmulatorTask(id, callback) => {
      info(s"Performing task $id")
      val emu = new Emulator(serialID, consolePort)

      val data = future { callback(emu) }
      data.mapTo[Map[String, Serializable]] onComplete {
        case Success(result) => emanager ! TaskSuccess(id, result, description.get)
        case Failure(reason) => {
          error("Obtained a Throwable.")
          emanager ! TaskFailure(id, reason, description.get)
        }
      }
    }
    case GetOptions(sendTo) => sendTo ! opts
    case _: BootSuccess => {
      val bootTime = System.currentTimeMillis
      info(s"Emulator $consolePort is awake at $bootTime, took ${bootTime - buildTime}")

      // Apply all personas
      // Personas.applyAll(serialID, opts)

      // Start the heartbeats
      heartbeatSchedule = context.system.scheduler.schedule(0.seconds, 10.seconds, self, EmulatorHeartbeat())

      description = Some(EmulatorDescription(node.ip, consolePort, display_port, ws_display_port, self, uuid))
      emanager ! EmulatorReady(description.get, bootTime - buildTime)
    }
    case _: BootFailure => {
      val failTime = System.currentTimeMillis
      info(s"Emulator $this failed to boot. Reported failure at $failTime");
      emanager ! EmulatorFailedBoot(self)
      context.stop(self)
    }
  }

  // TODO Put this all inside a future
  def build(): Process = {
    info(s"Building and starting emulator $this")

    // Give each emulator a unique name and SDcard
    val hostname = "hostname".!!.stripLineEnd;
    val avdName = s"$hostname-$consolePort"
    val target = opts.clasp.avdTarget getOrElse "android-18"
    val abi = opts.clasp.abiName getOrElse "x86"

    info(s"Building AVD `$avdName` for ABI `$abi` target `$target`")
    // TODO we must lookup the eabi for the target or this will likely fail

    val username = "whoami".!!.stripLineEnd
    val workdir = s"/tmp/clash/$username/avds"

    avd = new avd(avdName, target, abi)

    val workspaceDir = s"/tmp/clasp/$username/sdcards"
    s"mkdir -p $workspaceDir" !!

    // Create SD card
    val sdcardName = s"$workspaceDir/sdcard-$hostname-$consolePort.img"
    info(s"Creating sdcard: $sdcardName")
    sdk.mksdcard("9MB", sdcardName)
    if (opts.disk.sdcard.isDefined)
      // TODO: What should be done in this case?
      info("Warning: Overriding provided sdcard option.")

    opts = opts.copy(disk = opts.disk.copy(sdcard = Some(sdcardName)))
    opts = opts.copy(debug = opts.debug.copy(verbose = Some(true)))

    // Determine display type
    node.get_os_type match {
      case "linux" => {
        debug("Running on Linux, assuming headless. Searching for Xvfb and x11vnc")
        val xvfb = "which Xvfb".! == 0
        val x11vnc = "which x11vnc".! == 0
        if (xvfb && x11vnc) {
          debug(s"Found, will run emulator in graphical mode using DISPLAY=:$display_number")
          opts = opts.copy(ui = opts.ui.copy(noWindow = Some(false)))

          // Start a virtual frame buffer
          val screen = avd.get_skin_dimensions
          val xvfbCommand = s"Xvfb :${display_number} -screen 0 ${screen}x16"
          val xvfbProcess = Process(xvfbCommand)
          val xvfbLogger = ProcessLogger(line => info(s"xvfb:${display_number}:out: $line"),
            line => error(s"xvfb:${display_number}:err: $line"))
          debug(s"Running Xvfb using: $xvfbCommand")
          val xvfb = xvfbProcess.run(xvfbLogger)
          XvfbProcess = Some(xvfb)

          // Ensure Xvfb is started before being used
          Thread.sleep(850)

          // Start a VNC server for the frame buffer
          val xvncCommand = s"x11vnc -display :${display_number} -nopw -listen 0.0.0.0 -forever -shared -rfbport $display_port -xkb"
          val xvncProcess = Process(xvncCommand)
          val xvncLogger = ProcessLogger(line => info(s"x11vnc:${display_number}:out: $line"),
            line => error(s"x11vnc:${display_number}:err: $line"))
          debug(s"Running x11vnc using: $xvncCommand")
          val xvnc = xvncCommand.run(xvncLogger)
          x11vncProcess = Some(xvnc)

          // Set the DISPLAY variable used when starting the emulator
          opts = opts.copy(clasp = opts.clasp.copy(displayNumber = Some(display_number)))

          // Ensure x11vnc is started before being used
          Thread.sleep(850)

          // Start a TCP<-->WebSocket proxy
          val webpCommand = s"./lib/noVNC/utils/websockify $ws_display_port 127.0.0.1:$display_port"
          val webpProcess = Process(webpCommand)
          val webpLogger = ProcessLogger(line => info(s"websockify:${display_number}:out: $line"),
            line => error(s"websockify:${display_number}:err: $line"))
          debug(s"Running websockify using: $webpCommand")
          val webp = webpCommand.run(webpLogger)
          websockifyProcess = Some(webp)

          // Toss out the extra stuff and force a framebuffer 
          // that's exactly the size we want
          opts = opts.copy(ui = opts.ui.copy(skin = Some(screen)))
          opts = opts.copy(ui = opts.ui.copy(scale = Some("1")))
        } else {
          debug("Not found, running emulator headless")
          opts = opts.copy(ui = opts.ui.copy(noWindow = Some(true)))
        }
      }
      case _ => {
        debug("Running emulator on non-linux platform")
        debug("Showing window, but VNC will not work")
      }
    }

    opts = opts.copy(network = opts.network.copy(consolePort = Some(consolePort)))
    opts = opts.copy(avdName = Some(avdName))
    return sdk.start_emulator(opts, Some(logger));
  }

  override def toString(): String = {
    return s"[Emulator, id: $uuid, serialId: $serialID, nodeId: $nodeId, consolePort: $consolePort, path: ${self.path}]"
  }
}

/**
 * Handles sending emulator output logs to console and to websocket channels
 */
object EmulatorLogger {
  case class StdOut(line: String)
  case class StdErr(line: String)
}
class EmulatorLogger(val serialID: String, val node: Node, val emulator: EmulatorActor)
  extends Actor
  with ActorLogging
  with ChannelServer
  with ActorStack
  with Slf4jLoggingStack {

  // channelIdentifyMaster(node.masterip)
  context.actorSelection(s"akka.tcp://clasp@${node.masterip}:2552/user/channelManager") ! Identify(channelManagerId)
  var channelManager: Option[ActorRef] = None
  val channelName = "/emulator/" + emulator.uuid + "/log"

  def wrappedReceive = {
    case StdOut(line) => {
      log.debug(s"$serialID:out: $line")
      channelManager.foreach(c => c ! Message(channelName, line, self))
    }
    case StdErr(line) => {
      log.debug(s"$serialID:err: $line")
      channelManager.foreach(c => c ! Message(channelName, line, self))
    }
    case ActorIdentity(`channelManagerId`, Some(manager)) => {
      channelManager = Some(manager)
      manager ! RegisterChannel(channelName, self)
    }
  }
}

/* Physical hardware */
class Device(SerialID: String) {
  override def toString = "Device " + SerialID

  def setup {
    // Trigger the 4.2.1 verify apps dialog to allow choice to enable/disable
  }
}
