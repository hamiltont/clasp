package clasp.core.sdktools

import scala.concurrent.duration._

import scala.language.postfixOps

import scala.sys.process.stringToProcess
import scala.sys.process.Process

import sdk_config.log.debug
import sdk_config.log.error
import sdk_config.log.info

import akka.actor.ActorSystem

/**
 * Provides an interface to the
 * [[http://developer.android.com/tools/help/adb.html Android Debug Bridge]]
 * command line tool.
 * 
 * This, along with other components of the Android SDK, is included in
 * [[clasp.core.sdktools.sdk]].
 */
trait AdbProxy {
  val adb:String = sdk_config.config.getString(sdk_config.adb_config)
  import sdk_config.log.{error, debug, info, trace}
    
  /**
   * Return the current devices.
   */
  def get_device_list: Vector[String] = {
    val command = s"$adb devices"
    val output: String = command !!;
    // TODO: There must be a better way to do this.
    val regex = """([^\n\t ]*)[\t ]*device[^s]""".r
    
    var result = for (regex(name) <- regex findAllIn output) yield name
    result.toVector
    // TODO: Make this use AsyncCommand
  }
  
  // TODO: Unsure how to test thihs.
  /**
   * Connect to the device via TCP/IP.
   */
  def tcpip_connect(host: String, port: String)
      : Option[String] = {
    val command = s"$adb connect $host:$port"
    Command.run(command)
  }
  
  // TODO: Unsure how to test this.
  /**
   * Disconnect from the device via TCP/IP.
   */
  def tcpip_disconnect(host: String, port: String)
      : Option[String] = {
    val command = s"$adb disconnect $host:$port"
    Command.run(command)
  }
  
  /**
   * Push a file or directory to the device.
   */
  def push_to_device(serial: String, localPath: String,
      remotePath: String)
      : Option[String] = {
    val command = Seq(s"$adb", "-s", s"$serial", "push", s"$localPath",
      s"$remotePath")
    Command.runSeq(command)
  }
  
  /**
   * Pull a file or directory from a device.
   */
  def pull_from_device(serial: String, remotePath: String,
      localPath: String)
      : Option[String] = {
    val command = Seq(s"$adb", "-s", s"$serial", "pull", s"$remotePath",
      s"$localPath")
    Command.runSeq(command)
  }
  
  /**
   * Copy from host to device only if changed.
   */
  def sync_to_device(serial: String, directory: String)
      : Option[String] = {
    val command = Seq(s"$adb", "-s", s"$serial", "sync", s"$directory")
    Command.runSeq(command)
  }
  
  /**
   * Run a remote shell command.
   */
  def remote_shell(serial: String, shellCommand: String,
      timeout: FiniteDuration = 0 millis )
      : Option[String] = {
    val command = s"$adb -s $serial shell $shellCommand"
    Command.run(command, timeout)
  }
  
  def send_keyevent(serial: String, key: AndroidKey) {
    sdk.remote_shell(serial, s"input keyevent ${key.key}")
  }
  
  /**
   * Run an emulator console command.
   */
  def remote_emulator_console(serial: String, emuCommand: String)
    : Option[String] = {
    val command = s"$adb -s $serial emu $emuCommand"
    Command.run(command)
  }
  
  // TODO: Unsure how to test this.
  /**
   * Forward socket connections.
   */
  def forward_socket(serial: String, local: String,
      remote: String)
      : Option[String] = {
    val command = s"$adb -s $serial forward $local $remote"
    Command.run(command)
  }
  

  /**
   * Blocks until a pattern is matched in logcat.
   */
  def logcat_regex(serial: String, regex: String) {
    val command = s"""$adb -s $serial logcat"""
    // TODO: Platform independent way?
    val grepCmd = Seq("grep", "-q",  "-m",  "1", s"$regex")
    // TODO: What should the timeout be?
    var output: String = runWithTimeout(60000, "Process timed out.") {
      command #| grepCmd !!
    }
    // TODO: Make this use AsyncCommand
  }
  
  // TODO: Unsure how to test this.
  /**
   * List PIDs of processes hosting a JDWP transport.
   */
  def get_jdwp(serial:String)
      : Vector[String] = {
    val command = s"$adb devices"
    val output: String = command !!;
    val regex = """([0-9]*.*""".r
    
    var result = for (regex(name) <- regex findAllIn output) yield name
    result.toVector
  }
  
  /**
   * Push a package file to the device and install it.
   */
  def install_package(serial: String, apk_path: String,
      reinstall: Boolean = false)
      : Option[String] = {
    var reinstallStr = ""
    if (reinstall) reinstallStr = "-r"
    val command = s"""$adb -s $serial install $reinstallStr $apk_path"""
    Command.run(command)
  }
  
  /**
   * Remove a package from a device.
   */
  def uninstall_package(serial: String, pkg: String,
      keepData: Boolean = false)
      : Option[String] = {
    var command = s"""$adb -s $serial uninstall $pkg"""
    if (keepData) command += s" -k"
    Command.run(command)
    // TODO: Return something better?
  }
  
  // TODO: It might not be necessary to include this.
  // When `adb backup ...` is called, it displays a password
  // prompt on the emulator. We could rewrite this to send keys
  // to the emulator.
  /**
   * Write an archive of the device's data to disk.
   */
  def backup_device(serial: String,
                    file: String,
	                apk: Boolean = false,
	                sharedStorage: Boolean = false,
	                all: Boolean = false,
	                incSystem: Boolean = true,
	                packages: String = null) {
    if ( !(all || sharedStorage) && packages == null) {
      error("Error: Iff the -all or -shared flags are passed, "+
          "then the package list is optional.")
    }
    var command = s"""$adb -s $serial backup -f $file"""
    if (apk) command += " -apk"
      else command += " -noapk"
    if (sharedStorage) command += " -shared"
      else command += " -noshared"
    if (all) command += " -all"
    if (incSystem) command += " -system"
      else command += " -nosystem"
    if (packages != null) command += s" $packages"
    Command.run(command)
  }
  
  /**
   * Restore device contents from the backup archive.
   */
  def restore_device(serial: String, file: String)
     {
    val command = s"""$adb -s $serial restore -f $file"""
    Command.run(command)
  }
  
  def get_adb_version : String = {
    val command = s"$adb -version"
    val output: String = command !!;
    val regex = """Android Debug Bridge version ([0-9.]*)""".r
    
    val result = for (regex(name) <- regex findAllIn output) yield name
    result.toVector.last
  }
  
  /**
   * Block until device is online.
   */
  def wait_for_device(serial: String) {
    val command = s"$adb -s $serial wait-for-device"
    Command.run(command)
  }
  
  /**
   * Ensure the `adb` server is running.
   */
  def start_adb {
    val command = s"$adb start-server"
    Command.run(command)
  }
  
  /**
   * Kill the `adb` server if it is running.
   */
  def kill_adb {
    val command = s"$adb kill-server"
    Command.run(command)
  }
  
  /**
   * Return the state of the device.
   */
  def get_state(serial: String)
      : String = {
    val command = s"$adb $serial get-state"
    Command.run(command).get
  }
  
  /**
   * Return the device path of the device.
   */
  def get_devpath(serial: String)
      : String = {
    val command = s"$adb $serial get-devpath"
    Command.run(command).get
  }
  
  /**
   * Remounts the `/system` partition on the device read-write.
   */
  def remount_system(serial: String)
      : Option[String] = {
    val command = s"""$adb $serial remount"""
    Command.run(command)
  }
  
  /**
   * Reboots the device normally.
   */
  def reboot_normal(serial: String)
      : Option[String] = {
    val command = s"""$adb $serial reboot"""
    Command.run(command)
  }
  
  /**
   * Reboots the device into the bootloader.
   */
  def reboot_bootloader(serial: String)
      : Option[String] = {
    val command = s"""$adb $serial reboot-bootloader"""
    Command.run(command)
  }
  
  /**
   * Reboots the device into recovery mode.
   */
  def reboot_recovery(serial: String)
      : Option[String] = {
    val command = s"""$adb $serial reboot recovery"""
    Command.run(command)
  }
  
  /**
   * Restart the `adbd` daemon with root permissions.
   */
  def restart_adb_root: Option[String] = {
    val command = s"""$adb root"""
    Command.run(command)
  }
  
  /**
   * Restart the `adbd` daemon listening on USB.
   */
  def restart_adb_usb: Option[String] = {
    val command = s"""$adb usb"""
    Command.run(command)
  }
  
  /**
   * Restart the `adbd` daemon listening on TCP on the specified port.
   */
  def restart_adb_tcpip(port: String)
      : Option[String] = {
    val command = s"""$adb tcpip $port"""
    Command.run(command)
  }
  
  /**
   * Return the installed packages.
   */
  def get_installed_packages(serial: String):
      Vector[String] = {
    val command = s"$adb -s $serial shell pm list packages"
    println(command)
    var output: String = command !!
    // TODO: Async

    val regex = """package:(.*)""".r
    
    var result = for (regex(name) <- regex findAllIn output) yield name
    result.toVector
  }

  def is_adb_available: Option[String] = {
    val command: String = s"$adb version"

    Command.run(command)
    // TODO: Async
    // return output // TODO .contains("Android")
  }
  
  /**
   * Kill an emulator's process.
   */
  def kill_emulator(serial: String) {
    remote_emulator_console(serial, "kill")
  }
}
