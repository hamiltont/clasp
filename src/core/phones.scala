/**
 * @author hamiltont
 *
 */
package clasp.core

import scala.collection.mutable.ListBuffer
import scala.sys.process.Process
import scala.sys.process._ 
//import org.hyperic.sigar.Sigar
//import org.hyperic.sigar.ptql.ProcessFinder
import akka.actor._
import akka.serialization._

import scala.concurrent.duration._
import akka.event.Logging
//import org.hyperic.sigar.ProcTime
import clasp.core.sdktools._
import clasp.Emulator
import org.slf4j.LoggerFactory

import scala.language.postfixOps

case class Execute (f: () => Any) extends Serializable 


class EmulatorManager extends Actor {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}

  val emulators = ListBuffer[ActorRef]()

  def receive = {
    case "emulator_up" => {
      emulators += sender
      info(s"${emulators.length} emulators awake: ${sender.path}")
    }
    case "emulator_down" => {
      emulators -= sender
      info(s"${emulators.length} still awake: Lost ${sender.path}")
    }
    case "get_devices" => {
      sender ! emulators.toList
    }
    // Only works because the remote system has the same classpath, 
    // so any anonymous functions exist on both systems. If we 
    // want this to work in the future we will have to serialize the 
    // class that's associated with the anonymous function, send that
    // across the wire first, and then load it, all before we can 
    // do this. See http://doc.akka.io/docs/akka/snapshot/scala/serialization.html#serialization-scala
    // See http://www.scala-lang.org/node/10566
    case message: EmulatorReadyCallback => {
      info(s"Received callback, sending to ${emulators.head}")

      // if emulators.size != 0
      If there are enulators available, then we have to send the callback to an
      emulator actor. if there are no emulators available, then we have to wait
      on an emulator
      Each emulator can be sent a number of callbacks back to back. Eventually a 
      callback should have requirements, such as a clean emulator.

      emulators.head ! message
    }
  }
}
case class EmulatorReadyCallback(function: Emulator => Unit)

// An always-on presence for a single emulator process. 
//  - Monitors process state (STARTED, READY, etc)
//  - Can hibernate and resume process internally and transparently to the rest of clasp
//  - Can receive, queue, and eventually deliver actions on an emulator process
// Eventually we will have an Emulator object, which will be a proxy that allows 
// others to interface with the EmulatorActor without having to understand its 
// interface
class EmulatorActor(val port: Int, val opts: EmulatorOptions, serverip: String) extends Actor {

  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}

  info(s"Port is: $port")
  val (process, serialID) = EmulatorBuilder.build(port, opts)

  var isBooted = false
  // TODO: Wait for the emulators to be booted and set an `isBooted` flag
  // when we're certain the emulators are _entirely_ booted.
  // Note: Only calling `wait_for_device` returns too early,
  // before the emulators can be fully interacted with.

  override def postStop = {
    info(s"Stopping emulator ${self.path}")
    
    // TODO can we stop politely by sending a command to the emulator? 
    process.destroy
    process.exitValue // block until destroyed
    info(s"Emulator ${self.path} stopped")
    
    val emanager = context.system.actorFor("akka://clasp@" + serverip + ":2552/user/emulatormanager")
    emanager ! "emulator_down"
  }
  
  override def preStart() {
    val emanager = context.system.actorFor("akka://clasp@" + serverip + ":2552/user/emulatormanager")
    emanager ! "emulator_up"
  }

  def receive = {
    case "get_serialID" => {
      sender ! serialID
    }
    case "is_booted" => {
      sender ! isBooted
    }
    case "reboot" => {
      postStop
      preStart
    }
    case Execute(func) => {
      info(s"Executing function.")
      sender ! func()
    }
    case EmulatorReadyCallback(callback) => {
      val emu = new Emulator(self)
      info("Executing callback")
      // TODO should this happen on a future so we don't block the receive loop?
      callback(emu)
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

    val avds = sdk.get_avd_names
      
    // Give each emulator a unique sdcard.
    // TODO: Where should this be put?
    //       Putting it here seemed logical (and easy) to me.
    // TODO: Make this work for multiple nodes.
    var hostname = "hostname" !!;
    hostname = hostname.trim

    val avdName = s"$hostname-$port"
    info(s"Building unique AVD $avdName")
    // TODO we must lookup the eabi for the target or this will likely fail
    sdk.create_avd(avdName, "android-17", "armeabi-v7a", true)

    // TODO update this to use some working directory
    val path:String = "pwd".!!.stripLineEnd
    val sdcardName = s"$path/sdcards/$hostname-$port"
    info(s"Creating sdcard: '$sdcardName'")
    sdk.mksdcard("32MB", sdcardName)
    if (opts.sdCard != null) {
      // TODO: What should be done in this case?
      info("Warning: Overriding default sdcard option.")
    }
    opts.sdCard = sdcardName

    return sdk.start_emulator(avdName, port, opts);
  }
}

/*case class Load_Tick()
class EmulatorLoadMonitor(pid: Long) extends Actor {
  val log = Logging(context.system, this)
  import log.{info, debug, error}

  override def preStart {
    // someService | Register(self)
  }

  def receive = {
    case "test" => info("received test")
    case Load_Tick => {
      info("Load Tick")
      //pt.gather(s, pid)
      //info("Emulator: " + pt)
    }

  }
}*/
