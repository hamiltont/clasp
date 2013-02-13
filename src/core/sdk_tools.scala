/**
 * @author hamiltont
 *
 */
package core

import scala.sys.process.Process
import scala.sys.process.stringSeqToProcess
import scala.sys.process.stringToProcess
import com.typesafe.config.ConfigFactory
import scala.collection.mutable.ListBuffer

object sdk {
  val conf = ConfigFactory.load()
  val adb = conf getString ("sdk.adb")
  val emulator = conf getString ("sdk.emulator")
  val android = conf getString ("sdk.android")
  
  // TODO run checks to ensure that all three of these can be accessed
}

object ToolFacade {
  // android
  def get_avd_names: Vector[String] = AndroidProxy.get_avd_names
  def get_targets : Vector[String] = AndroidProxy.get_targets
  def get_sdk: Vector[String] = AndroidProxy.get_sdk
  
  def create_avd(name: String, target: String, force: Boolean = false): Boolean =
    AndroidProxy.create_avd(name, target, force)
  def delete_avd(name: String): Boolean = AndroidProxy.delete_avd(name)
  def move_avd(name: String, path: String, newName: String = null): Boolean =
    AndroidProxy.move_avd(name, path, newName)
  
  // emulator
  def start_emulator(avd_name: String, port: Int): (Process, String) =
    EmulatorProxy.start_emulator(avd_name, port)

  // adb
  def get_installed_packages(serial: String) =
    AdbProxy.get_installed_packages(serial)
  def install_package(serial: String, apk_path: String): Boolean =
    AdbProxy.install_package(serial, apk_path)
  def is_adb_available: Boolean = AdbProxy.is_adb_available
}

object AndroidProxy {
  val android = sdk.android + " "
  
  def get_avd_names: Vector[String] = {
    val command = android + "list avd";
    val output: String = command !!
    val regex = """Name: (.*)""".r

    val result = for (regex(name) <- regex findAllIn output) yield name
    result.toVector
  }
  
  def get_targets: Vector[String] = {
    val command = android + "list targets";
    val output: String = command !!
    val regex = """id: [0-9]* or \"(.*)\"""".r
    
    val result = for (regex(target) <- regex findAllIn output) yield target
    result.toVector
  }
  
  def get_sdk: Vector[String] = {
    val command = android + "list sdk";
    val output: String = command !!
    val regex = """[0-9]+- (.*)""".r
    
    val result = for (regex(target) <- regex findAllIn output) yield target
    result.toVector
  }

  def create_avd(name: String,
                 target: String,
                 force: Boolean = false): Boolean = {
    if (!force && (get_avd_names contains name)) {
      System.err.println("Error: AVD '" + name + "'" + " already exists.")
      return false
    }
    
    var command = ListBuffer(android, "create avd", "-n", name, "-t", target)
    if (force) {
      command.append("--force")
    }
    
    val output: String = "echo no" #| command.mkString(" ") !!;
    Log.log(output)
    true
  }
  
  def delete_avd(name: String): Boolean = {
    if (!(get_avd_names contains name)) {
      System.err.println("Error: AVD '" + name + "'" + " does not exist.")
      return false
    }
    
    val command = Seq(android, "delete avd -n", name)
    val output: String = command.mkString(" ") !!
    
    Log.log(output)
    true
  }
  
  def move_avd(name: String,
               path: String,
               newName: String = null): Boolean = {
    var command = ListBuffer(android, "move avd", "-n", name, "-p ", path)
    if (newName != null) {
      command += ("-r", newName)
    }
    
    //val output: String = query #| command !!
    true
  }
}

object EmulatorProxy {
  val emulator = sdk.emulator + " "

  def start_emulator(avd_name: String, port: Int): (Process, String) = {
    var command = s"$emulator -ports $port,${port+1} @$avd_name"  
    val builder = Process(command)
    
    return (builder.run, "emulator-" + port)

    // TODO - read in the output and ensure that the emulator actually started

    // TODO - link a process logger with some central logging mechanism, so that our 
    // framework can have debugging

  }
}

object AdbProxy {
  val adb = sdk.adb + " "

  def get_installed_packages(serial: String) {

    val command = Seq(adb, "-s", serial, "shell pm list packages")
    println(command)
    var output: String = command.mkString(" ") !!

    println(output)
  }

  // In general needs a method to timeout
  def install_package(serial: String, apk_path: String): Boolean = {
    val command = Seq(adb, "-s", serial, "install", apk_path)
    println(command)
    val output: String = command.mkString(" ") !!

    println(output)
    return output.contains("Success")
  }

  def is_adb_available: Boolean = {
    val output: String = adb + "version"!!

    return output.contains("Android")
  }

}