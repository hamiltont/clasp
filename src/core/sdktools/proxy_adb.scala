package clasp.core.sdktools

import scala.language.postfixOps

import scala.sys.process.stringToProcess
import scala.sys.process.Process

import sdk_config.log.debug
import sdk_config.log.error
import sdk_config.log.info

import clasp.core.AsynchronousCommand

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
  def tcpip_connect(host: String, port: String): Boolean = {
    val command = s"$adb connect $host:$port"
    AsynchronousCommand.resultOf(command).isDefined
  }
  
  // TODO: Unsure how to test this.
  /**
   * Disconnect from the device via TCP/IP.
   */
  def tcpip_disconnect(host: String, port: String): Boolean = {
    val command = s"$adb disconnect $host:$port"
    AsynchronousCommand.resultOf(command).isDefined
  }
  
  /**
   * Push a file or directory to the device.
   */
  def push_to_device(serial: String, localPath: String,
      remotePath: String): Boolean = {
    val command = Seq(s"$adb", "-s", s"$serial", "push", s"$localPath", s"$remotePath")
    AsynchronousCommand.resultOfSeq(command).isDefined
  }
  
  /**
   * Pull a file or directory from a device.
   */
  def pull_from_device(serial: String, remotePath: String,
      localPath: String): Boolean = {
    val command = Seq(s"$adb", "-s", s"$serial", "pull", s"$remotePath", s"$localPath")
    AsynchronousCommand.resultOfSeq(command).isDefined
  }
  
  /**
   * Copy from host to device only if changed.
   */
  def sync_to_device(serial: String, directory: String): Boolean = {
    val command = Seq(s"$adb", "-s", s"$serial", "sync", s"$directory")
    AsynchronousCommand.resultOfSeq(command).isDefined
  }
  
  /**
   * Run a remote shell command.
   */
  def remote_shell(serial: String, shellCommand: String): Boolean = {
    val command = s"$adb -s $serial shell $shellCommand"
    AsynchronousCommand.resultOf(command).isDefined
  }
  
  /**
   * Run an emulator console command.
   */
  def emulator_console(serial: String, emuCommand: String): Boolean = {
    val command = s"$adb -s $serial emu $emuCommand"
    AsynchronousCommand.resultOf(command).isDefined
  }
  
  // TODO: Unsure how to test this.
  /**
   * Forward socket connections.
   */
  def forward_socket(serial: String, local: String,
      remote: String): Boolean = {
    val command = s"$adb -s $serial forward $local $remote"
    AsynchronousCommand.resultOf(command).isDefined
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
  def get_jdwp(serial:String): Vector[String] = {
    val command = s"$adb devices"
    val output: String = command !!;
    val regex = """([0-9]*.*""".r
    
    var result = for (regex(name) <- regex findAllIn output) yield name
    result.toVector
  }
  
  /**
   * Push a package file to the device and install it.
   */
  def install_package(serial: String, apk_path: String): Boolean = {
    val command = s"""$adb -s $serial install $apk_path"""
    AsynchronousCommand.resultOf(command).isDefined
  }
  
  /**
   * Remove a package from a device.
   */
  def uninstall_package(serial: String, pkg: String,
      keepData: Boolean = false): Boolean = {
    var command = s"""$adb -s $serial uninstall $pkg"""
    if (keepData) command += s" -k"
    val output = AsynchronousCommand.resultOf(command)
    return output.get.contains("Success")
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
	                system: Boolean = true,
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
    if (system) command += " -system"
      else command += " -nosystem"
    if (packages != null) command += s" $packages"
    AsynchronousCommand.resultOf(command).isDefined
  }
  
  /**
   * Restore device contents from the backup archive.
   */
  def restore_device(serial: String, file: String) {
    val command = s"""$adb -s $serial restore -f $file"""
    AsynchronousCommand.resultOf(command).isDefined
  }
  
  def get_adb_version: String = {
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
    AsynchronousCommand.resultOf(command).isDefined
  }
  
  /**
   * Ensure the `adb` server is running.
   */
  def start_adb {
    val command = s"$adb start-server"
    AsynchronousCommand.resultOf(command).isDefined
  }
  
  /**
   * Kill the `adb` server if it is running.
   */
  def kill_adb {
    val command = s"$adb kill-server"
    AsynchronousCommand.resultOf(command).isDefined
  }
  
  /**
   * Return the state of the device.
   */
  def get_state(serial: String): String = {
    val command = s"$adb $serial get-state"
    AsynchronousCommand.resultOf(command).get
  }
  
  /**
   * Return the device path of the device.
   */
  def get_devpath(serial: String): String = {
    val command = s"$adb $serial get-devpath"
    AsynchronousCommand.resultOf(command).get
  }
  
  /**
   * Remounts the `/system` partition on the device read-write.
   */
  def remount_system(serial: String): Boolean = {
    val command = s"""$adb $serial remount"""
    AsynchronousCommand.resultOf(command).isDefined
  }
  
  /**
   * Reboots the device normally.
   */
  def reboot_normal(serial: String): Boolean = {
    val command = s"""$adb $serial reboot"""
    AsynchronousCommand.resultOf(command).isDefined
  }
  
  /**
   * Reboots the device into the bootloader.
   */
  def reboot_bootloader(serial: String): Boolean = {
    val command = s"""$adb $serial reboot-bootloader"""
    AsynchronousCommand.resultOf(command).isDefined
  }
  
  /**
   * Reboots the device into recovery mode.
   */
  def reboot_recovery(serial: String): Boolean = {
    val command = s"""$adb $serial reboot recovery"""
    AsynchronousCommand.resultOf(command).isDefined
  }
  
  /**
   * Restart the `adbd` daemon with root permissions.
   */
  def restart_adb_root: Boolean = {
    val command = s"""$adb root"""
    AsynchronousCommand.resultOf(command).isDefined
  }
  
  /**
   * Restart the `adbd` daemon listening on USB.
   */
  def restart_adb_usb: Boolean = {
    val command = s"""$adb usb"""
    AsynchronousCommand.resultOf(command).isDefined
  }
  
  /**
   * Restart the `adbd` daemon listening on TCP on the specified port.
   */
  def restart_adb_tcpip(port: String): Boolean = {
    val command = s"""$adb tcpip $port"""
    AsynchronousCommand.resultOf(command).isDefined
  }
  
  /**
   * Return the installed packages.
   */
  def get_installed_packages(serial: String) : Vector[String] = {
    val command = s"$adb -s $serial shell pm list packages"
    println(command)
    var output: String = command !!
    // TODO: Async

    val regex = """package:(.*)""".r
    
    var result = for (regex(name) <- regex findAllIn output) yield name
    result.toVector
  }

  def is_adb_available: Boolean = {
    val output: String = s"$adb version" !!

    return output.contains("Android")
    // TODO: Async
  }
  
  /**
   * Kill an emulator's process.
   */
  def kill_emulator(serial: String) {
    emulator_console(serial, "kill")
  }
}
