/**
 * @author hamiltont
 *
 */
package core

import scala.sys.process.Process
//import org.hyperic.sigar.Sigar
//import org.hyperic.sigar.ptql.ProcessFinder
import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Props
import scala.concurrent.duration._
import akka.event.Logging
//import org.hyperic.sigar.ProcTime
import core.sdktools._

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

class Emulator(process: Process, val SerialID: String, val telnetPort: Int) {
  //val s: Sigar = new Sigar
  //val pf: ProcessFinder = new ProcessFinder(s)
  //val emulator_pid: Long = pf.findSingleProcess("Args.*.re=5555.5556")
  val system = ActorSystem("EmulatorSystem")
  //val actor = system.actorOf(Props(new EmulatorLoadMonitor(emulator_pid)), name = "emulator-monitor")

  import system.dispatcher
  
  //val load_tick_timer = system.scheduler.schedule(1.seconds, 1.seconds, actor, Load_Tick)
 
  override def toString = "Emulator " + SerialID

  def cleanup {
    //load_tick_timer.cancel
    process.destroy
    process.exitValue // Block until process is destroyed.
  }

  // TODO: This might not be the best way to include options within
  // an emulator.
  //
  // It would be nice to have all of the SDK commands that take
  // an option within the emulator class so the user doesn't need
  // to obtain the serial from the emulator and then pass it to the sdk.
  //
  // TODO @Hamilton, thoughts?
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
  def build(avd_name: String,
            port: Int,
            opts: EmulatorOptions ): Emulator = {
    val (process: Process, serial: String) =
      sdk.start_emulator(avd_name, port, opts);
    new Emulator(process, serial, port)
  }

  def build(port: Int, opts: EmulatorOptions = null): Emulator = {
    val avds = sdk.get_avd_names
    if (avds.length != 0)
      return build(avds.head, port, opts)

    // TODO
    // @Hamilton, how should the abi be specified?
    // Should it be a configuration option that defaults to this,
    // or, should it be hard coded in here?
    //
    // I was running into problems running this with multiple ABIs.
    sdk.create_avd("initial", "1", "armeabi-v7a")
    build("initial", port, opts)
  }
}

