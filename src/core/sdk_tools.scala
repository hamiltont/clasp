/**
 *
 */
/**
 * @author hamiltont
 *
 */
package core

import scala.sys.process.Process
import scala.sys.process.stringSeqToProcess
import scala.sys.process.stringToProcess

import com.typesafe.config.ConfigFactory

object sdk {
  
  val conf = ConfigFactory.load()
  val adb = conf getString ("sdk.adb")
  val emulator = conf getString ("sdk.emulator")
  val android = conf getString ("sdk.android")
  
  // TODO run checks to ensure that all three of these can be accessed
}

object ToolFacade {

  def get_avd_names: Vector[String] = AndroidProxy.get_avd_names

  def create_avd(name: String, target: String) = AndroidProxy.create_avd(name, target)

  def start_emulator(avd_name: String, port: Int): (Process, String) = EmulatorProxy.start_emulator(avd_name, port)

}

object AndroidProxy {
  val android = sdk.android + " "
  def get_avd_names: Vector[String] = {
    val command = android + "list avd";
    val output: String = command !!
    val regex = """Name: (\w+)""".r

    val result = for (regex(name) <- regex findAllIn output) yield name
    result.toVector
  }

  def create_avd(name: String, target: String) {
    val command = Seq(android, "create avd -n", name, "-t", target)
    val query = Seq("echo", "no")
    val output: String = query #| command !!

    Log.log(output)
  }
}

object EmulatorProxy {
  val emulator = sdk.emulator + " "

  def start_emulator(avd_name: String, port: Int): (Process, String) = {
    val builder = Process(emulator + "-ports " + port + "," + (port + 1) + " @" + avd_name)
    return (builder.run, "emulator-" + port)

    // TODO - read in the output and ensure that the emulator actually started

    // TODO - link a process logger with some central logging mechanism, so that our 
    // framework can have debugging

  }
}

object AdbProxy {
  val adb = sdk.adb + " "

  def get_installed_packages(serial: String) {

    val command = adb + "-s " + serial + " shell pm list packages"
    println(command)
    var output: String = command !!

    println(output)
  }

  // In general needs a method to timeout
  def install_package(serial: String, apk_path: String): Boolean = {
    val command = adb + "-s " + serial + " install " + apk_path
    println(command)
    val output: String = command !!

    println(output)
    return output.contains("Success")
  }

  def is_adb_available: Boolean = {
    val output: String = adb + "version"!!

    return output.contains("Android")
  }

}