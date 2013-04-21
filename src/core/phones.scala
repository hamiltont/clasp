/**
 * @author hamiltont
 *
 */
package core

import scala.sys.process.Process
//import org.hyperic.sigar.Sigar
//import org.hyperic.sigar.ptql.ProcessFinder
import akka.actor._

import scala.concurrent.duration._
import akka.event.Logging
//import org.hyperic.sigar.ProcTime
import core.sdktools._
import org.slf4j.LoggerFactory


case class Load_Tick()
class EmulatorLoadMonitor(pid: Long) extends Actor {
  val log = Logging(context.system, this)
  import log.{info, debug, error}
  

  //var s: Sigar = new Sigar
  //val pt: ProcTime = new ProcTime


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
}

// An always-on presence for a single emulator process. 
//  - Monitors process state (STARTED, READY, etc)
//  - Can hibernate and resume process internally and transparently to the rest of clasp
//  - Can receive, queue, and eventually deliver actions on an emulator process
// Eventually we will have an Emulator object, which will be a proxy that allows 
// others to interface with the EmulatorActor without having to understand its 
// interface
class EmulatorActor(val port: Int, val opts: EmulatorOptions) extends Actor {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}

  info(s"Port is: $port")
  val (process, serialID) = EmulatorBuilder.build(port, opts)

  override def postStop = {
    info(s"Stopping emulator ${self.path}")
    // TODO can we stop politely by sending a command to the emulator? 
    // TODO verify that all emulator processes have been killed, potentially force kill
    process.destroy
    process.exitValue // block until destroyed
    info(s"Emulator ${self.path} stopped")
  }
  
  override def preStart() {
    context.parent ! "register"
    //someService ! Register(self)

    // TODO register with a proper manager, not the nodemanager
    val launcher = context.system.actorFor("akka://clasp@10.0.2.6:2552/user/nodelauncher")
    launcher ! "emulator_up"
  }

  def receive = {
    case "get_serialID" => {
      sender ! serialID
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
    if (avds.length != 0)
      return sdk.start_emulator(avds.head, port, opts);
      
    info("No AVDs exist: Building default one...")
    sdk.create_avd("initial", "1", "armeabi-v7a")
    sdk.start_emulator("initial", port, opts);
  }
}

