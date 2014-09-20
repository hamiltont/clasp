/**
 * @author hamiltont
 *
 */
package clasp.core

import java.util.UUID
import scala.collection.immutable.Map
import scala.collection.mutable.ListBuffer
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.sys.process._
import scala.sys.process.Process
import scala.util.Failure
import scala.util.Success
import org.slf4j.LoggerFactory
import akka.actor._
import akka.dispatch.OnFailure
import akka.dispatch.OnSuccess
import clasp.modules.Personas
import clasp.Emulator
import clasp.core.sdktools.sdk
import clasp.core.sdktools.EmulatorOptions
import clasp.core.sdktools.avd

class EmulatorManager extends Actor {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{ error, debug, info, trace }

  val emulators = ListBuffer[ActorRef]()

  // Tasks that have been delivered but not fulfilled
  val outstandingTasks: scala.collection.mutable.Map[String, Promise[Map[String, Any]]] = scala.collection.mutable.Map()

  // Contains tasks that need to be delivered to workers
  val undeliveredTasks: scala.collection.mutable.Queue[EmulatorTask] = scala.collection.mutable.Queue()

  def sendTask(to: ActorRef) = {
    if (undeliveredTasks.length != 0) {
      info(s"Dequeuing task for ${to.path}")
      // Only works because the remote system has the same classpath loaded, 
      // so all anonymous functions exist on both systems. If we want this to 
      // work in a dynamic manner we would have to serialize the class that's 
      // associated with the anonymous function, send that
      // across the wire & load it.
      // See http://doc.akka.io/docs/akka/snapshot/scala/serialization.html#serialization-scala
      // See http://www.scala-lang.org/node/10566
      // Alternatively, just copy all *.class files
      to ! undeliveredTasks.dequeue
    } else
      error("Emulator came online, but no work was available.")
  }

  def receive = {
    case EmulatorReady(emu) => {
      emulators += emu
      info(s"${emulators.length} emulators awake: ${emu.path}")
      sendTask(emu)
    }
    case EmulatorFailed(emu) => {
      emulators -= emu
      info(s"Emulator failed to boot: ${emu.path}")
    }
    case EmulatorCrashed(emu) => {
      emulators -= emu
      info(s"Emulator crashed: ${emu.path}")
    }
    case QueueEmulatorTask(task, promise) => {
      // Note that this is not strictly threadsafe.
      // Consider using twitter's snowflake library
      val id = UUID.randomUUID().toString()
      outstandingTasks(id) = promise
      info(s"Enqueued new task: $id")

      // TODO does not work if tasks arrive after emulator comes online.
      // Currently there's little danger of that, but still....
      undeliveredTasks.enqueue(new EmulatorTask(id, task))
    }
    case TaskSuccess(id, data, emu, emuObj, node) => {
      info(s"Task $id has completed")
      val promise_option = outstandingTasks remove id
      promise_option.get success data
      if (emuObj.rebootWhenFinished) {
        info(s"Rebooting emulator ${emuObj.serialID}.")
        emu ! PoisonPill
        node ! BootEmulator
      } else {
        sendTask(emu)
      }
    }
    case TaskFailure(id, err, emu) => {
      info(s"Task $id has failed")
      val promise_option = outstandingTasks remove id
      promise_option.get failure err
      sendTask(emu)
    }
    case unknown => error(s"Received unknown message from ${sender.path}: $unknown")
  }
}

case class EmulatorHeartbeat()
case class EmulatorReady(emu: ActorRef)
case class EmulatorFailed(emu: ActorRef)
case class EmulatorCrashed(emu: ActorRef)
case class EmulatorTask(taskid: String, task: Emulator => Map[String, Any])
case class QueueEmulatorTask(function: Emulator => Map[String, Any], promise: Promise[Map[String, Any]])
// TODO can I make Any require serializable? 
case class TaskSuccess(taskId: String, data: Map[String, Any],
  emulator: ActorRef, emulatorObj: Emulator, node: ActorRef)
case class TaskFailure(taskId: String, err: Exception, emulator: ActorRef)

// An always-on presence for a single emulator process. 
//  - Monitors process state (STARTED, READY, etc)
//  - Can hibernate and resume process internally and transparently to the rest of clasp
//  - Can receive, queue, and eventually deliver actions on an emulator process
// Eventually we will have an Emulator object, which will be a proxy that allows 
// others to interface with the EmulatorActor without having to understand its 
// interface
// TODO make EmulatorActor a FSM with states Booted, Booting, and Not Booted
class EmulatorActor(val id: Int, val opts: EmulatorOptions,
  val node: NodeDetails) extends Actor {
  
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{ error, debug, info, trace }
  
  // Emulator ports (each needs two)
  val base_emulator_port = 5555
  val port = base_emulator_port + 2 * id
  
  // Display port (1 is 5601, 99 is 5699)
  val base_display_port = 1
  val display = base_display_port + id
  
  // Emulator manager reference
  val emanager = context.system.actorFor(s"akka.tcp://clasp@${node.masterip}:2552/user/emulatormanager")

  // Processes for display management on X11 based system
  var XvfbProcess: Option[Process] = None
  var x11vncProcess: Option[Process] = None
 
  var heartbeatSchedule: Cancellable = null
  
  // Build sdcard, avd, and start emulator
  var avd:avd = null
  val buildTime = System.currentTimeMillis
  val (process, serialID) = build()

  // TODO why is getting a PoisonPill not calling this?
  override def postStop = {
    info(s"Halting emulator process ($id,$serialID,$port,${self.path})")
    process.destroy
    process.exitValue // block until destroyed
    info(s"Halted emulator process")
    
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
        
    info(s"Removing AVD")
    avd.delete
    
    if (heartbeatSchedule != null)
      heartbeatSchedule.cancel
    else
      debug("Heartbeat schedule was null")    
  }

  override def preStart() {
    implicit val system = context.system
    val boot = future {
      info(s"Waiting for emulator ($id,$port) to come online")
      if (false == sdk.wait_for_emulator(serialID, 200 second))
        throw new IllegalStateException("Emulator has not booted")
    } 
    
    boot onSuccess { case _ =>
      val bootTime = System.currentTimeMillis
      info(s"Emulator $port is awake at $bootTime, took ${bootTime - buildTime}")

      // Apply all personas
      Personas.applyAll(serialID, opts)

      // Start the heartbeats
      heartbeatSchedule = system.scheduler.schedule(0 seconds, 1 seconds, self, EmulatorHeartbeat)
      Thread.sleep(5000)

      emanager ! EmulatorReady(self)
    } 
    
    boot onFailure { case _ =>
      val failTime = System.currentTimeMillis
      info(s"Emulator $port failed to boot. Reported failure at $failTime");
      emanager ! EmulatorFailed(self)
      context.stop(self)
    }
  }

  def receive = {
    case EmulatorHeartbeat => {
      // This heartbeat is sent every second and is initiated
      // once the emulator has booted.
      //
      // Executing a command on the emulator shell provides a better
      // mechanism to determine if the device is online
      // than just making sure the process is alive.
      info(s"Sending heartbeat to $serialID.")
      val ret = future { sdk.remote_shell(serialID, "echo alive", 5 seconds) }
      ret onComplete {
        case Success(out) => {
          if (out.isEmpty) {
            error(s"Emulator $serialID heartbeat failed. Destroying.")
            emanager ! EmulatorCrashed(self)
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
      val emu = new Emulator(serialID)

      val data = future { callback(emu) }
      data onSuccess {
        case map => emanager ! TaskSuccess(id, map, self, emu, node.node)
      }
      data onFailure {
        case e: Exception => emanager ! TaskFailure(id, e, self)
        case t => error("Obtained a Throwable.")
      }
    }
    case unknown => {
      info(s"EmulatorActor ${self.path} received unknown message from ${sender.path}: $unknown")
    }
  }

  // TODO Put this all inside a future
  def build(): (Process, String) = {
    info(s"Building and starting emulator $id on port $port")

    val avds = sdk.get_avd_names

    // Give each emulator a unique sdcard.
    // TODO: Where should this be put?
    //       Putting it here seemed logical (and easy) to me.
    // TODO: Make this work for multiple nodes.
    var hostname = "hostname".!!.stripLineEnd;

    val avdName = s"$hostname-$port"
    val target = opts.avdTarget getOrElse "android-18"
    val abi = opts.abiName getOrElse "x86"
    
    info(s"Building AVD `$avdName` for ABI `$abi` Target `$target`")
    // TODO we must lookup the eabi for the target or this will likely fail
    // TODO check for failure

    val username = "whoami".!!.stripLineEnd
    val workdir = s"/tmp/clash/$username/avds"

    avd = new avd(avdName, target, abi)

    val workspaceDir = s"/tmp/clasp/$username/sdcards"
    s"mkdir -p $workspaceDir" !!

    // Create SD card
    val sdcardName = s"$workspaceDir/sdcard-$hostname-$port.img"
    info(s"Creating sdcard: $sdcardName")
    sdk.mksdcard("9MB", sdcardName)
    if (opts.sdCard != null)
      // TODO: What should be done in this case?
      info("Warning: Overriding provided sdcard option.")
    
    opts.sdCard = sdcardName
    
    // Determine display type
    node.ostype match {
      case "linux" => {
        debug("Running on Linux, assuming headless. Searching for Xvfb and x11vnc")
        val xvfb = "which Xvfb".! == 0
        val x11vnc = "which x11vnc".! == 0
        if (xvfb && x11vnc) {
          debug(s"Found, will run emulator in graphical mode using DISPLAY=:$id")
          opts.noWindow = false
           
          // Start a virtual frame buffer
          val screen = avd.get_skin_dimensions
          val xvfbCommand = s"Xvfb :${id} -screen 0 ${screen}x16"
          val xvfbProcess = Process(xvfbCommand)
          val xvfbLogger = ProcessLogger ( line => info(s"xvfb:${id}:out: $line"), 
          		line => error(s"xvfb:${id}:err: $line") )
          debug(s"Running Xvfb using: $xvfbCommand")
          val xvfb = xvfbProcess.run(xvfbLogger)
          XvfbProcess = Some(xvfb)
          
          // Ensure Xvfb is started before being used
          Thread.sleep(850)
          
          // Start a VNC server for the frame buffer
          val xvncCommand = s"x11vnc -display :${id} -nopw -listen 0.0.0.0 -forever -shared -xkb"
          val xvncProcess = Process(xvncCommand)
          val xvncLogger = ProcessLogger ( line => info(s"x11vnc:${id}:out: $line"), 
          		line => error(s"x11vnc:${id}:err: $line") )
          debug(s"Running x11vnc using: $xvncCommand")
          val xvnc = xvncCommand.run(xvncLogger)
          x11vncProcess = Some(xvnc)
          
          // Set the DISPLAY variable used when starting the emulator
          opts.display = Some(id)
          
          // Toss out the extra stuff and force a framebuffer 
          // that's exactly the size we want
          opts.skin = screen 
          opts.scale = "1"
        } else {
          debug("Not found, running emulator headless")
          opts.noWindow = true
        }
      }
      case _ => {
        debug("Running emulator on non-linux platform")
        debug("Showing window, but VNC will not work")
      }
    } 

    // TODO spawn thread to watch for premature exit
    return sdk.start_emulator(avdName, port, opts);
  }

}

/* Physical hardware */
class Device(SerialID: String) {
  override def toString = "Device " + SerialID

  def setup {
    // Trigger the 4.2.1 verify apps dialog to allow choice to enable/disable
  }
}
