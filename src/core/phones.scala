/**
 * @author hamiltont
 *
 */
package clasp.core

import scala.collection.mutable.ListBuffer
import scala.sys.process.Process
import scala.sys.process._ // TODO: Might not need...
//import org.hyperic.sigar.Sigar
//import org.hyperic.sigar.ptql.ProcessFinder
import akka.actor._

import scala.concurrent.duration._
import akka.event.Logging
//import org.hyperic.sigar.ProcTime
import clasp.core.sdktools._
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
  }
}

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
    // TODO: Add the option to reboot and refresh an emulator.
    // case "reboot" => {
    // }
    case Execute(func) => {
      info(s"Executing function.")
      sender ! func()
    }
    case _ => {
      info(s"EmulatorActor ${self.path} received unknown message")
    }
  }

  // TODO: This might not be the best way to include options within
  // an emulator.
  //
  // It would be nice to have all of the SDK commands that take
  // an option within the emulator class so the user doesn't need
  // to obtain the serial from the emulator and then pass it to the sdk.
  //
  // TODO @Hamilton, thoughts?
  // @Brandon - let's differentiate the core API from the core
  // implementation, and have an EmulatorActor (used internally)
  // and an Emulator object (used externally). The Emulator object
  // is created from the EmulatorActor and knows how to complete
  // method calls such as the ones below. It can make all calls
  // directly on the sdk object, using the ActorRef when it needs
  // to retrieve or permanently store data
  /*
  def installApk(path: String) {
    sdk.install_package(serialID, path)
  }

  def startActivity(mainActivity: String) {
    val amStart = s"am start -a android.intent.action.MAIN -n $mainActivity"
    sdk.remote_shell(serialID, amStart)
  }

  def remoteShell(command: String) {
    sdk.remote_shell(serialID, command)
  }

  def pull(remotePath: String, localPath: String) {
    sdk.pull_from_device(serialID, remotePath, localPath)
  }

  def stopPackage(name: String) {
    sdk.remote_shell(serialID,
      s"""am force-stop "$name" """);
  }
  */
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
    sdk.create_avd(avdName, "1", "armeabi", true)

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
