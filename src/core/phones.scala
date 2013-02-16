/**
 * @author hamiltont
 *
 */

package core

import scala.sys.process.Process
import org.hyperic.sigar.Sigar
import org.hyperic.sigar.ptql.ProcessFinder
import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Props
import scala.concurrent.duration._
import akka.event.Logging
import org.hyperic.sigar.ProcTime

case class Load_Tick
class EmulatorLoadMonitor(pid: Long) extends Actor {
  val log = Logging(context.system, this)
  import log.{info, debug, error}
  

  var s: Sigar = new Sigar
  val pt: ProcTime = new ProcTime


  override def preStart {
    // someService | Register(self)
  }

  def receive = {
    case "test" => info("received test")
    case Load_Tick => {
      info("Load Tick")
      pt.gather(s, pid)
      info("Emulator: " + pt)
    }

  }
}

class Emulator(process: Process, SerialID: String) {
  val s: Sigar = new Sigar
  val pf: ProcessFinder = new ProcessFinder(s)
  val emulator_pid: Long = pf.findSingleProcess("Args.*.re=5555.5556")
  var telnetPort: Int = 0
  val system = ActorSystem("EmulatorSystem")
  val actor = system.actorOf(Props(new EmulatorLoadMonitor(emulator_pid)), name = "emulator-monitor")

  import system.dispatcher
  
  val load_tick_timer = system.scheduler.schedule(1.seconds, 1.seconds, actor, Load_Tick)
 
  override def toString = "Emulator " + SerialID

  def cleanup {
    load_tick_timer.cancel
    process.destroy
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
  def build(avd_name: String, port: Int): Emulator = {
    val (process: Process, serial: String) = sdk.start_emulator(avd_name, port);
    new Emulator(process, serial)
  }

  def build(port: Int): Emulator = {
    val avds = sdk.get_avd_names
    if (avds.length != 0)
      return build(avds.head, port)

    sdk.create_avd("initial", "1")
    build("initial", port)
  }
}

