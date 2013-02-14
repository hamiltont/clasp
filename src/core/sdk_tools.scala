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
  // android
  def get_avd_names: Vector[String] = AndroidProxy.get_avd_names
  def get_targets: Vector[String] = AndroidProxy.get_targets
  def get_sdk: Vector[String] = AndroidProxy.get_sdk
  
  def create_avd(name: String, target: String, force: Boolean = false): Boolean =
    AndroidProxy.create_avd(name, target, force)
  def move_avd(name: String, path: String, newName: String = null): Boolean =
    AndroidProxy.move_avd(name, path, newName)
  def delete_avd(name: String): Boolean = AndroidProxy.delete_avd(name)
  def update_avd(name: String) = AndroidProxy.update_avd(name)
  
  def create_project(name: String,
                     target: String,
                     path: String,
                     pkg: String,
                     activity: String) =
    AndroidProxy.create_project(name, target, path, pkg, activity)
  
  def update_project(path: String,
                     library: String = null,
                     name: String = null,
                     target: String = null,
                     subprojects: Boolean = false) =
    AndroidProxy.update_project(library, path, name, target, subprojects)
  
  def create_test_project(path: String, name: String, main:String) =
    AndroidProxy.create_test_project(path, name, main)
  
  def update_test_project(main: String, path: String) =
    AndroidProxy.update_test_project(main, path)
    
  def create_lib_project(name: String,
                         target: String,
                         pkg: String,
                         path: String) =
    AndroidProxy.create_lib_project(name, target, pkg, path)
  
  def update_lib_project(path: String, target: String = null) =
    AndroidProxy.update_lib_project(path, target)
  
  def create_uitest_project(name: String, path: String, target: String) =
    AndroidProxy.create_uitest_project(name, path, target)
    
  def update_adb = AndroidProxy.update_adb
  
  def update_sdk(filter: String = null,
                 noHttps: Boolean = false,
                 all: Boolean = false,
                 force: Boolean = false) =
    AndroidProxy.update_sdk(filter, noHttps, all, force)
    
  // emulator
  def start_emulator(avd_name: String, port: Int, opts: EmulatorOptions = null): (Process, String) =
    EmulatorProxy.start_emulator(avd_name, port, opts)
  def get_snapshot_list(avd_name: String): Vector[String] =
    EmulatorProxy.get_snapshot_list(avd_name)
  def get_webcam_list(avd_name: String): Vector[String] =
    EmulatorProxy.get_webcam_list(avd_name)
  def get_emulator_version(): String = EmulatorProxy.get_emulator_version

  // adb
  def get_device_list: Vector[String] = AdbProxy.get_device_list
  def tcpip_connect(host: String, port: String) = AdbProxy.tcpip_connect(host, port)
  def tcpip_disconnect(host: String, port: String) = AdbProxy.tcpip_disconnect(host, port)
  def push_to_device(serial: String, localPath: String, remotePath: String) =
    AdbProxy.push_to_device(serial, localPath, remotePath)
  def pull_from_device(serial: String, remotePath: String, localPath: String) =
    AdbProxy.pull_from_device(serial, remotePath, localPath)
  def sync_to_device(serial: String, directory: String) =
    AdbProxy.sync_to_device(serial, directory)
  def remote_shell(serial: String, shellCommand: String) =
    AdbProxy.remote_shell(serial, shellCommand)
  def emulator_console(serial: String, emuCommand: String) =
    AdbProxy.emulator_console(serial, emuCommand)
  def forward_socket(serial: String, local: String, remote: String) =
    AdbProxy.forward_socket(serial, local, remote)
  def get_jdwp(serial:String): Vector[String] = AdbProxy.get_jdwp(serial)
  def install_package(serial: String, apk_path: String): Boolean =
    AdbProxy.install_package(serial, apk_path)
  def uninstall_package(serial: String, pkg: String, keepData: Boolean = false): Boolean = 
    AdbProxy.uninstall_package(serial, pkg, keepData)
  def backup_device(serial: String,
                    file: String,
	                apk: Boolean = false,
	                sharedStorage: Boolean = false,
	                all: Boolean = false,
	                system: Boolean = true,
	                packages: String = null) =
    AdbProxy.backup_device(serial, file, apk, sharedStorage, all, system, packages)
  def restore_device(serial: String, file: String) =
    AdbProxy.restore_device(serial, file)
    
  def wait_for_device(serial: String) = AdbProxy.wait_for_device(serial)
  def start_adb = AdbProxy.start_adb
  def kill_adb = AdbProxy.kill_adb
  def get_state(serial: String): String = AdbProxy.get_state(serial)  
  def get_devpath(serial: String): String = AdbProxy.get_devpath(serial)
  def remount_system(serial: String) = AdbProxy.remount_system(serial)
  def reboot_normal(serial: String) = AdbProxy.reboot_normal(serial)
  def reboot_bootloader(serial: String) = AdbProxy.reboot_bootloader(serial)
  def reboot_recovery(serial: String) = AdbProxy.reboot_recovery(serial)
  
  def restart_adb_root = AdbProxy.restart_adb_root
  def restart_adb_usb = AdbProxy.restart_adb_usb
  def restart_adb_tcpip(port: String) = AdbProxy.restart_adb_tcpip(port)
    
  def get_adb_version: String = AdbProxy.get_adb_version
  def get_installed_packages(serial: String) =
    AdbProxy.get_installed_packages(serial)
  def is_adb_available: Boolean = AdbProxy.is_adb_available
  def kill_emulator(serial: String) = AdbProxy.kill_emulator(serial)
}

object AndroidProxy {
  val android = sdk.android + " "
  
  def get_avd_names: Vector[String] = {
    val command = s"$android list avd";
    val output: String = command !!
    val regex = """Name: (.*)""".r

    val result = for (regex(name) <- regex findAllIn output) yield name
    result.toVector
  }
  
  def get_targets: Vector[String] = {
    val command = s"$android list targets";
    val output: String = command !!
    val regex = """id: [0-9]* or \"(.*)\"""".r
    
    val result = for (regex(target) <- regex findAllIn output) yield target
    result.toVector
  }
  
  def get_sdk: Vector[String] = {
    val command = s"$android list sdk";
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
    
    var command = s"$android create avd -n $name -t $target"
    if (force) {
      command += " --force"
    }
    
    val output: String = "echo no" #| command !!;
    Log.log(output)
    true
  }

  def move_avd(name: String,
               path: String,
               newName: String = null): Boolean = {
    var command = s"$android move avd -n $name -p $path"
    if (newName != null) {
      command += s" -r $newName"
    }
    
    val output: String = command !!;
    true
  }

  def delete_avd(name: String): Boolean = {
    if (!(get_avd_names contains name)) {
      System.err.println("Error: AVD '" + name + "'" + " does not exist.")
      return false
    }
    
    val command = s"$android delete avd -n $name"
    val output: String = command !!
    
    Log.log(output)
    true
  }
  
  def update_avd(name: String) {
    val command = s"$android update avd -n $name"
    val output: String = command !!
    
    Log.log(output)
  }
  
  def create_project(name: String,
                     target: String,
                     path: String,
                     pkg: String,
                     activity: String) {
    var command = s"$android create project"
    command += s" -n $name"
    command += s" -t $target"
    command += s" -p $path"
    command += s" -k $pkg"
    command += s" -a $activity"
    val output: String = command !!
    
    Log.log(output)
  }
  
  def update_project(path: String,
                     library: String = null,
                     name: String = null,
                     target: String = null,
                     subprojects: Boolean = false) {
    var command = s"$android update project -p $path"
    if (library != null) command += s" -l $library"
    if (name != null) command += s" -n $name"
    if (target != null) command += s" -t $target"
    if (subprojects) command += " -s"
    val output: String = command !!
    
    Log.log(output)
  }
  
  def create_test_project(path: String, name: String, main:String) {
    val command = s"$android create test-project -p $path -n $name -m $main"
    val output: String = command !!
    
    Log.log(output)
  }
  
  def update_test_project(main: String, path: String) {
    val command = s"$android update test-project -m $main -p $path"
    val output: String = command !!
    
    Log.log(output)
  }
  
  def create_lib_project(name: String,
                         target: String,
                         pkg: String,
                         path: String) {
    val command = s"$android create lib-project -n $name " +
      s" -t $target -k $pkg -p $path"
    val output: String = command !!
    
    Log.log(output)
  }
  
  def update_lib_project(path: String, target: String = null) {
    var command = s"$android update lib-project -p $path"
    if (target != null) command += s" -t $target"
    val output: String = command !!
    
    Log.log(output)
  }
  
  def create_uitest_project(name: String, path: String, target: String) {
    val command = s"$android create uitest-project -n $name " +
      s" -p $path -t $target"
    val output: String = command !!
    
    Log.log(output)
  }
  
  def update_adb {
    val command = s"$android update adb"
    val output: String = command !!;
    Log.log(output)
  }
  
  def update_sdk(filter: String = null,
                 noHttps: Boolean = false,
                 all: Boolean = false,
                 force: Boolean = false) {
    var command = s"$android update sdk -u"
    if (filter != null) command += s" -t $filter"
    if (noHttps) command += " -s"
    if (all) command += " -a"
    if (force) command += " -f"
    val output: String = command !!;
    Log.log(output)
  }
}

object EmulatorProxy {
  val emulator = sdk.emulator + " "

  def start_emulator(avd_name: String, port: Int, opts: EmulatorOptions = null): (Process, String) = {
    var command = s"$emulator -ports $port,${port+1} @$avd_name"
    if (opts != null) {
		if (opts.sysdir != null) command += s" -sysdir ${opts.sysdir}"
		if (opts.system != null) command += s" -system ${opts.system}"
		if (opts.datadir != null) command += s" -datadir ${opts.datadir}"
		if (opts.kernel != null) command += s" -kernel ${opts.kernel}"
		if (opts.ramdisk != null) command += s" -ramdisk ${opts.ramdisk}"
		if (opts.image != null) command += s" -image ${opts.image}"
		if (opts.data != null) command += s" -datadir ${opts.data}"
		if (opts.partitionSize != null) command += s" -partition-size ${opts.partitionSize}"
		if (opts.cache != null) command += s" -cache ${opts.cache}"
		if (opts.cacheSize != null) command += s" -cache-size ${opts.cacheSize}"
		if (opts.sdCard != null) command += s" -sdcard ${opts.sdCard}"
		if (opts.snapStorage != null) command += s" -snapstorage ${opts.snapStorage}"
		if (opts.snapShot != null) command += s" -snapshot ${opts.snapShot}"
		if (opts.skinDir != null) command += s" -skindir ${opts.skinDir}"
		if (opts.skin != null) command += s" -skin ${opts.skin}"
		if (opts.memory != null) command += s" -memory ${opts.memory}"
		if (opts.netSpeed != null) command += s" -netspeed ${opts.netSpeed}"
		if (opts.netDelay != null) command += s" -netfast ${opts.netDelay}"
		if (opts.trace != null) command += s" -trace ${opts.trace}"
		if (opts.logcatTags != null) command += s" -logcat ${opts.logcatTags}"
		if (opts.audioBackend != null) command += s" -audio ${opts.audioBackend}"
		if (opts.radio != null) command += s" -radio ${opts.radio}"
		if (opts.onion != null) command += s" -onion ${opts.onion}"
		if (opts.onionAlpha != null) command += s" -onion-alpha ${opts.onionAlpha}"
		if (opts.scale != null) command += s" -scale ${opts.scale}"
		if (opts.dpiDevice != null) command += s" -dpi-device ${opts.dpiDevice}"
		if (opts.httpProxy != null) command += s" -http-proxy ${opts.httpProxy}"
		if (opts.timeZone != null) command += s" -timezone ${opts.timeZone}"
		if (opts.dnsServer != null) command += s" -dns-server ${opts.dnsServer}"
		if (opts.cpuDelay != null) command += s" -cpu-delay ${opts.cpuDelay}"
		if (opts.reportConsoleSocket != null) command += s" -report-console ${opts.reportConsoleSocket}"
		if (opts.gpsDevice != null) command += s" -gps ${opts.gpsDevice}"
		if (opts.keysetName != null) command += s" -keyset ${opts.keysetName}"
		if (opts.shellSerial != null) command += s" -shell-serial ${opts.shellSerial}"
		if (opts.tcpDump != null) command += s" -tcpdump ${opts.tcpDump}"
		if (opts.bootChartTimeout != null) command += s" -bootchart ${opts.bootChartTimeout}"
		if (opts.charmapFile != null) command += s" -charmap ${opts.charmapFile}"
		if (opts.propNameVal != null) command += s" -prop ${opts.propNameVal}"
		if (opts.sharedNetID != null) command += s" -shared-net-id ${opts.sharedNetID}"
		if (opts.nandLimits != null) command += s" -nand-limits ${opts.nandLimits}"
		if (opts.memCheckFlags != null) command += s" -memcheck ${opts.memCheckFlags}"
		if (opts.gpuMode != null) command += s" -gpu ${opts.gpuMode}"
		if (opts.cameraBackMode != null) command += s" -camera-back ${opts.cameraBackMode}"
		if (opts.cameraFrontMode != null) command += s" -camera-front ${opts.cameraFrontMode}"
		if (opts.screenMode != null) command += s" -screen ${opts.screenMode}"
		if (opts.qemuArgs != null) command += s" -qemu ${opts.qemuArgs}"
		  
		if(opts.noCache) command += s" -no-cache"
		if(opts.noSnapStorage) command += s" -no-snapstorage"
		if(opts.noSnapShot) command += s" -no-snapshot"
		if(opts.noSnapShotSave) command += s" -no-snapshot-save"
		if(opts.noSnapShotLoad) command += s" -no-snapshot-load"
		if(opts.noSnapShotUpdateTime) command += s" -no-snapshot-update-time"
		if(opts.wipeData) command += s" -wipe-data"
		if(opts.noSkin) command += s" -no-skin"
		if(opts.dynamicSkin) command += s" -dynamic-skin"
		if(opts.netFast) command += s" -netfast"
		if(opts.showKernel) command += s" -show-kernel"
		if(opts.shell) command += s" -shell"
		if(opts.noJni) command += s" -no-jni"
		if(opts.noAudio) command += s" -no-audio"
		if(opts.rawKeys) command += s" -raw-keys"
		if(opts.noBootAnim) command += s" -no-boot-anim"
		if(opts.noWindow) command += s" -no-window"
		if(opts.force32Bit) command += s" -force-32bit"
    }
    
    println(command)
    val builder = Process(command)
    return (builder.run, "emulator-" + port)

    // TODO - read in the output and ensure that the emulator actually started

    // TODO - link a process logger with some central logging mechanism, so that our 
    // framework can have debugging

  }

  def get_snapshot_list(avd_name: String): Vector[String] = {
    val command = s"$emulator @$avd_name -snapshot-list"
    val output: String = command !!;
    val regex = """\[[0-9]+\][ ]*(.*)""".r
    
    val result = for (regex(name) <- regex findAllIn output) yield name
    result.toVector
  }
  
  def get_webcam_list(avd_name: String): Vector[String] = {
    val command = s"$emulator @$avd_name -webcam-list"
    val output: String = command !!;
    val regex = """Camera '([^']*)'""".r
    
    val result = for (regex(name) <- regex findAllIn output) yield name
    result.toVector
  }
  
  def get_emulator_version: String = {
    val command = s"$emulator -version"
    val output: String = command !!;
    val regex = """Android emulator version ([0-9.]*)""".r
    
    val result = for (regex(name) <- regex findAllIn output) yield name
    result.toVector.last
  }
}

class EmulatorOptions {
  var sysdir, system, datadir, kernel, ramdisk, image,
       data, partitionSize, cache, cacheSize, sdCard,
       snapStorage, snapShot, skinDir, skin, memory,
       netSpeed, netDelay, trace, logcatTags, audioBackend,
       radio, onion, onionAlpha, scale, dpiDevice,
       httpProxy, timeZone, dnsServer, cpuDelay,
       reportConsoleSocket, gpsDevice, keysetName,
       shellSerial, tcpDump, bootChartTimeout, charmapFile,
       propNameVal, sharedNetID, nandLimits, memCheckFlags,
       gpuMode, cameraBackMode, cameraFrontMode, screenMode,
       qemuArgs: String = null
  
  var noCache, noSnapStorage, noSnapShot, noSnapShotSave,
      noSnapShotLoad, noSnapShotUpdateTime, wipeData, noSkin,
      dynamicSkin, netFast, showKernel, shell, noJni, noAudio,
      rawKeys, noBootAnim, noWindow, force32Bit = false
}

object AdbProxy {
  val adb = sdk.adb + " "
  
  // TODO: Improve and test
  def get_device_list: Vector[String] = {
    val command = s"$adb devices"
    val output: String = command !!;
    // TODO: There must be a better way to right this.
    val regex = """([^\n\t ]*)[\t ]*device[^s]""".r
    
    var result = for (regex(name) <- regex findAllIn output) yield name
    result.toVector
  }
  
  // TODO: Test
  def tcpip_connect(host: String, port: String) {
    val command = s"$adb connect $host:$port"
    var output: String = command !!
    
    Log.log(output);
  }
  
  // TODO: Test
  def tcpip_disconnect(host: String, port: String) {
    val command = s"$adb disconnect $host:$port"
    var output: String = command !!
    
    Log.log(output);
  }
  
  // TODO: Test
  def push_to_device(serial: String, localPath: String, remotePath: String) {
    val command = s"$adb -s $serial adb push $localPath $remotePath"
    var output: String = command !!
    
    Log.log(output);
  }
  
  // TODO: Test
  def pull_from_device(serial: String, remotePath: String, localPath: String) {
    val command = s"$adb -s $serial adb pull $remotePath $localPath"
    var output: String = command !!
    
    Log.log(output);
  }
  
  // TODO: Test
  def sync_to_device(serial: String, directory: String) {
    val command = s"$adb -s $serial adb sync $directory"
    var output: String = command !!
    
    Log.log(output);    
  }
  
  // TODO: Test
  def remote_shell(serial: String, shellCommand: String) {
    val command = s"$adb -s $serial shell $shellCommand"
    var output: String = command !!
    
    Log.log(output);  
  }
  
  // TODO: Test
  def emulator_console(serial: String, emuCommand: String) {
    val command = s"$adb -s $serial emu $emuCommand"
    var output: String = command !!
    
    Log.log(output);
  }
  
  // TODO: Test
  def forward_socket(serial: String, local: String, remote: String) {
    val command = s"$adb -s $serial forward $local $remote"
    var output: String = command !!
    
    Log.log(output);
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
      System.err.println("Error: Iff the -all or -shared flags are passed, "+
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

    println(output)
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