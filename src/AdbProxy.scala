/**
 *
 */
/**
 * @author hamiltont
 *
 */
import scala.sys.process._

object AdbProxy {
  val adb = "/Development/adt-bundle-mac-x86_64/platform-tools/adb "
  
  
  def get_installed_packages(serial: String) {
	  
    val command = adb+"-s "+serial+" shell pm list packages"
    println(command)
    var output:String = command !!
	
    println(output)
  }
  
  // In general needs a method to timeout
  def install_package(serial: String, apk_path:String):Boolean = {
    val command = adb+"-s "+serial+" install " + apk_path
    println(command)
    val output:String = command !!
    
    println(output)
    return output.contains("Success")
  }

  
}