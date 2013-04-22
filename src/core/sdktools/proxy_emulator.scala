package clasp.core.sdktools

import scala.language.postfixOps

import scala.sys.process.Process
import scala.sys.process.stringToProcess
import scala.sys.process.ProcessLogger

import sdk_config.log.info

import clasp.core.AsynchronousCommand

/**
 * Provides an interface to the
 * [[http://developer.android.com/tools/help/emulator.html `emulator`]]
 * command line tool.
 * 
 * This, along with other components of the Android SDK, is included in
 * [[clasp.core.sdktools.sdk]].
 */
trait EmulatorProxy {
  val emulator:String = sdk_config.config.getString(sdk_config.emulator_config)
  val mksdcard:String = sdk_config.config.getString(sdk_config.mksdcard_config)
  import sdk_config.log.{error, debug, info, trace}

  /**
   * Start an emulator with the given options.
   */
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
		if(opts.verbose) command += s" -verbose"
  }
    
    info(command)
    val builder = Process(command)
    val serial = "emulator-" + port
    val logger = ProcessLogger ( line => info(serial + ":out: " + line), 
      line => info(serial + ":err: " + line) )
    val process = builder.run(logger)

    info("Process started")

    return (process, serial)

    // TODO - read in the output and ensure that the emulator actually started
  }

  /**
   * Return a list of available snapshots.
   */
  def get_snapshot_list(avd_name: String): Vector[String] = {
    val command = s"$emulator @$avd_name -snapshot-list"
    val regex = """\[[0-9]+\][ ]*(.*)""".r
    AsynchronousCommand.resultsOf(command, regex) getOrElse Vector()
  }
  
  /**
   * Return a list of web cameras available for emulation.
   */
  def get_webcam_list(avd_name: String): Vector[String] = {
    val command = s"$emulator @$avd_name -webcam-list"
    val regex = """Camera '([^']*)'""".r
    AsynchronousCommand.resultsOf(command, regex) getOrElse Vector()
  }
  
  def get_emulator_version: String = {
    val command = s"$emulator -version"
    val regex = """Android emulator version ([0-9.]*)""".r
    AsynchronousCommand.resultsOf(command, regex).map(_.last) getOrElse ""
  }

  def mksdcard(size: String, path: String) {
    val command = s"$mksdcard $size $path"
    AsynchronousCommand.resultOf(command)
  }
}

/**
 * Contains various settings and flags for the emulator.
 */
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
  
  var verbose = true 
}
