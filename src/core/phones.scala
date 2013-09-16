/**
 * @author hamiltont
 *
 */
package clasp.core

import scala.util.{Success, Failure}
import scala.collection.mutable.ListBuffer
import scala.collection.immutable.Map
import scala.sys.process.Process
import scala.sys.process._ 
import akka.actor._
import akka.serialization._

import scala.concurrent._
import scala.concurrent.duration._
import akka.event.Logging
//import org.hyperic.sigar.ProcTime
import clasp.core.sdktools._
import clasp.Emulator
import org.slf4j.LoggerFactory

import java.util.UUID
import java.util.concurrent.TimeUnit;
import scala.language.postfixOps

import ExecutionContext.Implicits.global

class EmulatorManager extends Actor {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}

  val emulators = ListBuffer[ActorRef]()

  // Tasks that have been delivered but not fulfilled
  val outstandingTasks: scala.collection.mutable.Map[String, Promise[Map[String,Any]]] = scala.collection.mutable.Map() 

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
      error("Emulator came online, but no work was available. Permanently wasted!")
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
    case TaskSuccess(id, data, emu) => {
      info(s"Task $id has completed")
      val promise_option = outstandingTasks remove id
      promise_option.get success data
      sendTask(emu)
    }
    case TaskFailure(id, err, emu) => {
      info(s"Task $id has failed")
      val promise_option = outstandingTasks remove id
      promise_option.get failure err
      sendTask(emu)
    }
  }
}
case class EmulatorReady(emu: ActorRef)
case class EmulatorFailed(emu: ActorRef)
case class EmulatorTask(taskid: String, task: Emulator => Map[String, Any])
case class QueueEmulatorTask(function: Emulator => Map[String, Any], promise: Promise[Map[String,Any]])
// TODO can I make Any require serializable? 
case class TaskSuccess(taskId: String, data: Map[String, Any], emulator: ActorRef)
case class TaskFailure(taskId: String, err: Exception, emulator: ActorRef)

// An always-on presence for a single emulator process. 
//  - Monitors process state (STARTED, READY, etc)
//  - Can hibernate and resume process internally and transparently to the rest of clasp
//  - Can receive, queue, and eventually deliver actions on an emulator process
// Eventually we will have an Emulator object, which will be a proxy that allows 
// others to interface with the EmulatorActor without having to understand its 
// interface
class EmulatorActor(val port: Int, val opts: EmulatorOptions,
    serverip: String) extends Actor {

  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}

  val buildTime = System.currentTimeMillis
  info(s"Building emulator for port $port at $buildTime")
  val (process, serialID) = EmulatorBuilder.build(port, opts)

  val emanager = context.system.actorFor(
    "akka://clasp@" + serverip + ":2552/user/emulatormanager")

  override def postStop = {
    info(s"Stopping emulator ${self.path}")

    // TODO can we stop politely by sending a command to the emulator? 
    process.destroy
    process.exitValue // block until destroyed
    info(s"Emulator ${self.path} stopped")
    
    emanager ! EmulatorFailed(self)
  }
  
  override def preStart() {
    var me: ActorRef = self
    implicit val system = context.system
    val f = future {
      info(s"Waiting for emulator $port to come online")
      if (false == sdk.wait_for_emulator(serialID, 200.second))
        throw new IllegalStateException("Emulator has not booted")
    } onComplete { 
      case Success(_) => {
        val bootTime = System.currentTimeMillis
        info(s"Emulator $port is awake at $bootTime, took ${bootTime - buildTime}");
        system.scheduler.schedule(0 seconds, 1 seconds, self, "heartbeat");
        Thread.sleep(5000)
        emanager ! EmulatorReady(me) }
      case Failure(_) => { 
        val failTime = System.currentTimeMillis
        info(s"Emulator $port failed to boot. Reported failure at $failTime, took ${failTime - buildTime}");    
        context.stop(self)}
    }
  }

  def receive = {
    case "get_serialID" => {
      sender ! serialID
    }
    case "heartbeat" => {
      // This heartbeat is sent every second and is initiated
      // once the emulator has booted.
      //
      // Executing a command on the emulator shell provides a better
      // mechanism to determine if the device is online
      // than just making sure the process is alive.
      info(s"Sending heartbeat to $serialID.")
      val ret = future{ sdk.remote_shell(serialID, "echo", 5 seconds) }
      ret onFailure {
        case e => {
          error(s"Emulator $serialID heartbeat failed. Destroying.")
          // TODO: What if we're in the middle of a task?
          context.stop(self)
        }
      }
    }
    case EmulatorTask(id, callback) => {
      info(s"Performing task $id")
      val emu = new Emulator(serialID)

      val data = future{ callback(emu) }
      data onSuccess {
        case map => emanager ! TaskSuccess(id, map, self)
      }
      data onFailure {
        case e: Exception => TaskFailure(id, e, self)
        case t => error("Obtained a Throwable.")
      }
    }
    case _ => {
      info(s"EmulatorActor ${self.path} received unknown message")
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

object EmulatorBuilder {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}
  
  def build(port: Int, opts: EmulatorOptions): (Process, String) = {
    info("Building and starting emulator.")

    val avds = sdk.get_avd_names
      
    // Give each emulator a unique sdcard.
    // TODO: Where should this be put?
    //       Putting it here seemed logical (and easy) to me.
    // TODO: Make this work for multiple nodes.
    var hostname = "hostname".!!.stripLineEnd;

    val avdName = s"$hostname-$port"
    info(s"Building unique AVD $avdName")
    // TODO we must lookup the eabi for the target or this will likely fail
    sdk.create_avd(avdName, "android-17", "armeabi-v7a", true)

    // TODO update this to use some working directory
    val username: String = "logname".!!.stripLineEnd
    val workspaceDir = s"/tmp/clasp/$username"
    s"mkdir -p $workspaceDir" !!

    val sdcardName = workspaceDir + "/sdcard-" + hostname + "-" +
      port + ".img"
    info(s"Creating sdcard: '$sdcardName'")
    sdk.mksdcard("9MB", sdcardName)
    if (opts.sdCard != null) {
      // TODO: What should be done in this case?
      info("Warning: Overriding default sdcard option.")
    }
    opts.sdCard = sdcardName

    return sdk.start_emulator(avdName, port, opts);
  }
}

/*
class EmulatorHeartbeat(serial: String, emu: ActorRef) extends Actor {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}

  def receive = {
    case "ping" => {
      info("Checking status.")
      sender ! "pingFailure"
    }
    case _ => {
      error("Heartbeat received unknown message.")
    }
  }
}
*/
