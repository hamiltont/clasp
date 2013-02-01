/**
 *
 */
/**
 * @author hamiltont
 *
 */

import java.io.File

class AbstractDevice(val SerialID:String) {
  def install_package(apk_path:String):Boolean = {
    val f: File  = new File(apk_path)
    if (f.exists())
    	AdbProxy.install_package(SerialID, apk_path)
	else
		false
  }
}

class Emulator(SerialID:String) extends AbstractDevice(SerialID) {
	override def toString = "Emulator " + SerialID
}

/* Physical hardware */
class Device(SerialID:String) extends AbstractDevice(SerialID) {
  override def toString = "Device " + SerialID
  
  def setup {
    // Trigger the 4.2.1 verify apps dialog to allow choice to enable/disable
  }
}

