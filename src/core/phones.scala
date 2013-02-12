/**
 * @author hamiltont
 *
 */

package core

import java.io.File
import scala.sys.process._
import org.hyperic.sigar.Sigar
import org.hyperic.sigar.ptql.ProcessFinder

class Emulator(process: Process, SerialID:String) {
	val s: Sigar = new Sigar
	val pf: ProcessFinder = new ProcessFinder(s)
	val emulator_processid: Long = pf.findSingleProcess("Args.*.re=5555.5556")
	var telnetPort: Int = 0
	
	
	override def toString = "Emulator " + SerialID
	
	def cleanup {
	  process.destroy
	}
}

/* Physical hardware */
class Device(SerialID:String) {
  override def toString = "Device " + SerialID
  
  def setup {
    // Trigger the 4.2.1 verify apps dialog to allow choice to enable/disable
  }
}

object EmulatorBuilder {
  def build(avd_name: String, port: Int): Emulator = {
	val (process: Process, serial: String) = ToolFacade.start_emulator(avd_name, port);
	new Emulator(process, serial)
  }
  
  def build(port: Int): Emulator = {
	val avds = ToolFacade.get_avd_names
	if (avds.length != 0)
	  return build(avds.head, port)
	  
	ToolFacade.create_avd("initial","1")
	build("initial", port)
  }
}
