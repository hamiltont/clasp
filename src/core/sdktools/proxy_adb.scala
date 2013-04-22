package clasp.core.sdktools

import scala.language.postfixOps

import scala.sys.process.stringToProcess
import scala.sys.process.Process

import sdk_config.log.debug
import sdk_config.log.error
import sdk_config.log.info

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
  }
  
  // TODO: Unsure how to test thihs.
  /**
   * Connect to the device via TCP/IP.
   */
  def tcpip_connect(host: String, port: String) {
    val command = s"$adb connect $host:$port"
    var output: String = command !!
    
    info(output);
  }
  
  // TODO: Unsure how to test this.
  /**
   * Disconnect from the device via TCP/IP.
   */
  def tcpip_disconnect(host: String, port: String) {
    val command = s"$adb disconnect $host:$port"
    var output: String = command !!
    
    info(output);
  }
  
  /**
   * Push a file or directory to the device.
   */
  def push_to_device(serial: String, localPath: String, remotePath: String) {
    val command = Seq(s"$adb", "-s", s"$serial", "push", s"$localPath", s"$remotePath")
    var output: String = Process(command) !!
    
    info(output);
  }
  
  /**
   * Pull a file or directory from a device.
   */
  def pull_from_device(serial: String, remotePath: String, localPath: String) {
    val command = Seq(s"$adb", "-s", s"$serial", "pull", s"$remotePath", s"$localPath")
    var output: String = Process(command) !!
    
    info(output);
  }
  
  /**
   * Copy from host to device only if changed.
   */
  def sync_to_device(serial: String, directory: String) {
    val command = Seq(s"$adb", "-s", s"$serial", "sync", s"$directory")
    var output: String = Process(command) !!
    
    info(output);    
  }
  
  /**
   * Run a remote shell command.
   */
  def remote_shell(serial: String, shellCommand: String) {
    val command = s"$adb -s $serial shell $shellCommand"
    var output: String = command !!
    
    info(output);  
  }
  
  /**
   * Run an emulator console command.
   */
  def emulator_console(serial: String, emuCommand: String) {
    val command = s"$adb -s $serial emu $emuCommand"
    var output: String = command !!
    
    info(output);
  }
  
  // TODO: Unsure how to test this.
  /**
   * Forward socket connections.
   */
  def forward_socket(serial: String, local: String, remote: String) {
    val command = s"$adb -s $serial forward $local $remote"
    var output: String = command !!
    
    info(output);
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
    get_device_list.foreach(println)
    // TODO: no quote vs single vs double quote issues?
    // TODO: Could be device vs emulator issue?
    val command = s"""$adb -s $serial install $apk_path"""
    println(command)
    // TODO: Timeout duration?
    // TODO: Error with `runWithTimeout` in fat jar?
    // java.lang.NoClassDefFoundError: scala/runtime/AbstractFunction1$mcVL$sp
    // val output: String = runWithTimeout(60000, "Process timed out.") {
    //  command  !!
    //}

    // TODO: Futures?
    //import akka.dispatch.{Await,Future}
    //import akka.util.duration._
    //val myfuture =
    //  Future {
    //    command !!
    //`}
    //val result = Await.result(myfuture, 1 second)
    command !!

    // TODO: Get output.
    //println(output)
    //return output.contains("Success")
    true
  }
  
  /**
   * Remove a package from a device.
   */
  def uninstall_package(serial: String, pkg: String, keepData: Boolean = false): Boolean = {
    var command = s"""$adb -s $serial uninstall $pkg"""
    if (keepData) command += s" -k"
    // TODO: Timeout duration?
    val output: String = runWithTimeout(60000, "Process timed out.") {
      command  !!
    }

    println(output)
    return output.contains("Success")
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
    val output: String = command !!

    debug(output)
  }
  
  /**
   * Restore device contents from the backup archive.
   */
  def restore_device(serial: String, file: String) {
    val command = s"""$adb -s $serial restore -f $file"""
    val output: String = command !!

    println(output)
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
    command !!
  }
  
  /**
   * Ensure the `adb` server is running.
   */
  def start_adb {
    val command = s"$adb start-server"
    val output: String = command !!
  }
  
  /**
   * Kill the `adb` server if it is running.
   */
  def kill_adb {
    val command = s"$adb kill-server"
    val output: String = command !!;
  }
  
  /**
   * Return the state of the device.
   */
  def get_state(serial: String): String = {
    val command = s"$adb $serial get-state"
    val output: String = command !!
    
    output
  }
  
  /**
   * Return the device path of the device.
   */
  def get_devpath(serial: String): String = {
    val command = s"$adb $serial get-devpath"
    val output: String = command !!
    
    output
  }
  
  /**
   * Remounts the `/system` partition on the device read-write.
   */
  def remount_system(serial: String) = {
    val command = s"""$adb $serial remount"""
    val output: String = command !!
  }
  
  /**
   * Reboots the device normally.
   */
  def reboot_normal(serial: String) {
    val command = s"""$adb $serial reboot"""
    val output: String = command !!
  }
  
  /**
   * Reboots the device into the bootloader.
   */
  def reboot_bootloader(serial: String) {
    val command = s"""$adb $serial reboot-bootloader"""
    val output: String = command !!
  }
  
  /**
   * Reboots the device into recovery mode.
   */
  def reboot_recovery(serial: String) {
    val command = s"""$adb $serial reboot recovery"""
    val output: String = command !!
  }
  
  /**
   * Restart the `adbd` daemon with root permissions.
   */
  def restart_adb_root {
    val command = s"""$adb root"""
    val output: String = command !!
  }
  
  /**
   * Restart the `adbd` daemon listening on USB.
   */
  def restart_adb_usb {
    val command = s"""$adb usb"""
    val output: String = command !!
  }
  
  /**
   * Restart the `adbd` daemon listening on TCP on the specified port.
   */
  def restart_adb_tcpip(port: String) {
    val command = s"""$adb tcpip $port"""
    val output: String = command !!
  }
  
  /**
   * Return the installed packages.
   */
  def get_installed_packages(serial: String) : Vector[String] = {
    val command = s"$adb -s $serial shell pm list packages"
    println(command)
    var output: String = command !!

    val regex = """package:(.*)""".r
    
    var result = for (regex(name) <- regex findAllIn output) yield name
    result.toVector
  }

  def is_adb_available: Boolean = {
    val output: String = s"$adb version" !!

    return output.contains("Android")
  }
  
  /**
   * Kill an emulator's process.
   */
  def kill_emulator(serial: String) {
    emulator_console(serial, "kill")
  }
}
