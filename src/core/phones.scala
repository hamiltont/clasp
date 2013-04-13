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
class EmulatorActor(val process: Process, val SerialID: String, val telnetPort: Int) extends Actor {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}

  override def toString = "Emulator " + SerialID

  override def postStop = {
    info("Stopping emulator " + SerialID)
    // TODO can we stop politely by sending a command to the emulator? 
    process.destroy
    process.exitValue // block until destroyed
    info("Emulator " + SerialID + " stopped")
  }
  
  override def preStart() {
    //context.actorSelection("") ! msg
    //someService ! Register(self)
  }

  def receive = {
    case _ => { info("EmulatorActor " + SerialID + " received unknown message") }
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
    sdk.install_package(SerialID, path)
  }

  def startActivity(mainActivity: String) {
    val amStart = s"am start -a android.intent.action.MAIN -n $mainActivity"
    sdk.remote_shell(SerialID, amStart)
  }

  def remoteShell(command: String) {
    sdk.remote_shell(SerialID, command)
  }

  def pull(remotePath: String, localPath: String) {
    sdk.pull_from_device(SerialID, remotePath, localPath)
  }

  def stopPackage(name: String) {
    sdk.remote_shell(SerialID,
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
  
  def build(avd_name: String,
            port: Int,
            opts: EmulatorOptions,
            context: ActorContext): ActorRef = {
    info("Building a new emulator")
    val (process: Process, serial: String) =
      sdk.start_emulator(avd_name, port, opts);
    
    info("Emulator built, creating actor")


    val actor:ActorRef = context.actorOf(new Props(new UntypedActorFactory() {
            def create = { new EmulatorActor(process, serial, port) }
          }), serial)

    info("Emulator actor created, returning")
    return actor
  }
   
   def build(port: Int,
            opts: EmulatorOptions,
            context: ActorContext): ActorRef = {

    val avds = sdk.get_avd_names
    if (avds.length != 0)
      return build(avds.head, port, opts, context)

    info("No AVDs exist: Building default one...")
    sdk.create_avd("initial", "1", "armeabi-v7a")
    build("initial", port, opts, context)
  }
}

