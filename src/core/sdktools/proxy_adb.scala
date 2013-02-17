package core.sdktools

import scala.sys.process.stringToProcess

import sdk_config.log.debug
import sdk_config.log.error
import sdk_config.log.info


trait AdbProxy {
  val adb:String = sdk_config.config.getString(sdk_config.adb_config)
  import sdk_config.log.{error, debug, info, trace}
    
  // TODO: Improve and test
  def get_device_list: Vector[String] = {
    val command = s"$adb devices"
    val output: String = command !!;
    // TODO: There must be a better way to do this.
    val regex = """([^\n\t ]*)[\t ]*device[^s]""".r
    
    var result = for (regex(name) <- regex findAllIn output) yield name
    result.toVector
  }
  
  // TODO: Test
  def tcpip_connect(host: String, port: String) {
    val command = s"$adb connect $host:$port"
    var output: String = command !!
    
    info(output);
  }
  
  // TODO: Test
  def tcpip_disconnect(host: String, port: String) {
    val command = s"$adb disconnect $host:$port"
    var output: String = command !!
    
    info(output);
  }
  
  // TODO: Test
  def push_to_device(serial: String, localPath: String, remotePath: String) {
    val command = s"$adb -s $serial adb push $localPath $remotePath"
    var output: String = command !!
    
    info(output);
  }
  
  // TODO: Test
  def pull_from_device(serial: String, remotePath: String, localPath: String) {
    val command = s"$adb -s $serial adb pull $remotePath $localPath"
    var output: String = command !!
    
    info(output);
  }
  
  // TODO: Test
  def sync_to_device(serial: String, directory: String) {
    val command = s"$adb -s $serial adb sync $directory"
    var output: String = command !!
    
    info(output);    
  }
  
  // TODO: Test
  def remote_shell(serial: String, shellCommand: String) {
    val command = s"$adb -s $serial shell $shellCommand"
    var output: String = command !!
    
    info(output);  
  }
  
  // TODO: Test
  def emulator_console(serial: String, emuCommand: String) {
    val command = s"$adb -s $serial emu $emuCommand"
    var output: String = command !!
    
    info(output);
  }
  
  // TODO: Test
  def forward_socket(serial: String, local: String, remote: String) {
    val command = s"$adb -s $serial forward $local $remote"
    var output: String = command !!
    
    info(output);
  }
  
  // TODO: Test
  def get_jdwp(serial:String): Vector[String] = {
    val command = s"$adb devices"
    val output: String = command !!;
    val regex = """([0-9]*.*""".r
    
    var result = for (regex(name) <- regex findAllIn output) yield name
    result.toVector
  }
  
  // TODO: In general needs a method to timeout
  def install_package(serial: String, apk_path: String): Boolean = {
    val command = s"""$adb -s "$serial" install $apk_path"""
    println(command)
    val output: String = command !!

    println(output)
    return output.contains("Success")
  }
  
  // TODO: In general needs a method to timeout
  def uninstall_package(serial: String, pkg: String, keepData: Boolean = false): Boolean = {
    var command = s"""$adb -s "$serial" uninstall $pkg"""
    if (keepData) command += s" -k"
    val output: String = command !!

    println(output)
    return output.contains("Success")
  }
  
  // TODO: Test
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
    var command = s"""$adb -s "$serial" backup -f $file"""
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
  
  // TODO: Test
  def restore_device(serial: String, file: String) {
    val command = s"""$adb -s "$serial" restore -f $file"""
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
  
  def wait_for_device(serial: String) {
    //TODO: Block until this returns.
    val command = s"""$adb -s "$serial" wait-for-device"""
    val output: String = command !!
  }
  
  def start_adb {
    val command = s"$adb start-server"
    val output: String = command !!
  }
  
  def kill_adb {
    val command = s"$adb kill-server"
    val output: String = command !!
  }
  
  def get_state(serial: String): String = {
    val command = s"""$adb "$serial" get-state"""
    val output: String = command !!
    
    output
  }
  
  def get_devpath(serial: String): String = {
    val command = s"""$adb "$serial" get-devpath"""
    val output: String = command !!
    
    output
  }
  
  def remount_system(serial: String) = {
    val command = s"""$adb "$serial" remount"""
    val output: String = command !!
  }
  
  def reboot_normal(serial: String) {
    val command = s"""$adb "$serial" reboot"""
    val output: String = command !!
  }
  
  def reboot_bootloader(serial: String) {
    val command = s"""$adb "$serial" reboot-bootloader"""
    val output: String = command !!
  }
  
  def reboot_recovery(serial: String) {
    val command = s"""$adb "$serial" reboot recovery"""
    val output: String = command !!
  }
  
  def restart_adb_root {
    val command = s"""$adb root"""
    val output: String = command !!
  }
  
  def restart_adb_usb {
    val command = s"""$adb usb"""
    val output: String = command !!
  }
  
  def restart_adb_tcpip(port: String) {
    val command = s"""$adb tcpip $port"""
    val output: String = command !!
  }
  
  // TODO: Test
  def get_installed_packages(serial: String) {
    val command = s"$adb -s $serial shell pm list packages"
    println(command)
    var output: String = command !!

    println(output)
  }

  def is_adb_available: Boolean = {
    val output: String = s"$adb version" !!

    return output.contains("Android")
  }
  
  def kill_emulator(serial: String) {
    emulator_console(serial, "kill")
  }
}
