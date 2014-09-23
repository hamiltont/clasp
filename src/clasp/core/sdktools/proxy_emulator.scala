package clasp.core.sdktools

import akka.actor.ActorSystem
import scala.language.postfixOps
import scala.sys.process.Process
import scala.sys.process.stringToProcess
import scala.sys.process.ProcessLogger
import sdk_config.log.info
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Provides an interface to the
 * [[http://developer.android.com/tools/help/emulator.html `emulator`]]
 * command line tool.
 *
 * This, along with other components of the Android SDK, is included in
 * [[clasp.core.sdktools.sdk]].
 */
trait EmulatorProxy {
  val emulator: String = sdk_config.config.getString(sdk_config.emulator_config)
  val mksdcard: String = sdk_config.config.getString(sdk_config.mksdcard_config)
  import sdk_config.log.{ error, debug, info, trace }

  def valid = {
    Files.exists(Paths.get(emulator)) && Files.exists(Paths.get(mksdcard))
  }

  /**
   * Start an emulator with the given options.
   */
  def start_emulator(opts: EmulatorOptions = new EmulatorOptions()): (Process, String) = {
    
    // Add all the conventional arguments into the command
    
    // options.avdName = Some(avd_name)
    var command = opts.applyToCommand(emulator)

    var builder = Process(command)
    if (opts.clasp.displayNumber.isDefined) {
      info(s"Emulator launch command: DISPLAY=:${opts.clasp.displayNumber.get} bash -c '$command'")
      builder = Process(Seq("bash", "-c", command), None, "DISPLAY" -> s":${opts.clasp.displayNumber.get}")
    } else
      info(s"Emulator launch command: $command")

    val serial = "emulator-" + opts.network.consolePort.getOrElse("XX")
    val logger = ProcessLogger(line => info(serial + ":out: " + line),
      line => info(serial + ":err: " + line))
    val process = builder.run(logger)
    info("Emulator process started")
    
    return (process, serial)
  }

  /**
   * Return a list of available snapshots.
   */
  def get_snapshot_list(avd_name: String): Vector[String] = {
    val command = s"$emulator @$avd_name -snapshot-list"
    val regex = """\[[0-9]+\][ ]*(.*)""".r
    Command.runAndParse(command, regex) getOrElse Vector()
  }

  /**
   * Return a list of web cameras available for emulation.
   */
  def get_webcam_list(avd_name: String): Vector[String] = {
    val command = s"$emulator @$avd_name -webcam-list"
    val regex = """Camera '([^']*)'""".r
    Command.runAndParse(command, regex) getOrElse Vector()
  }

  def get_emulator_version: String = {
    val command = s"$emulator -version"
    val regex = """Android emulator version ([0-9.]*)""".r
    Command.runAndParse(command, regex).map(_.last) getOrElse ""
  }

  def mksdcard(size: String, path: String) {
    val command = s"$mksdcard $size $path"
    Command.run(command)
  }

  // TODO rmsdcard
}




/**
 * Everything needed to launch the Android emulator process, including flags for 
 * the binary command and Clasp-specific settings
 *
 * <p>When using, it is helpful to realize that case classes come with a
 * nice copy operator for changing one value</p>
 *
 * <code>
 * val newDiskImageOptions = existingDiskImageOptions.copy(data = "/foo/path)
 * </code>
 *
 * <code>
 * val x: EmulatorOptions = opts.copy(network = opts.network.copy(consolePort = Some(port)))
 * </code>
 */
// TODO consider making each flag it's own class, which would enable much more powerful 
// syntax trees (e.g. Android 1.6 flags vs 3.0, etc)
// TODO add flags for snapshot options, dynamicSkin, etc
case class EmulatorOptions(
    avdName: Option[String] = None,
    clasp: ClaspOptions = ClaspOptions(),
    disk: DiskImageOptions = DiskImageOptions(), 
    debug: DebugOptions = DebugOptions(), 
    media: MediaOptions = MediaOptions(), 
    network: NetworkOptions = NetworkOptions(), 
    ui: UIoptions = UIoptions(),
    system: SystemOptions = SystemOptions()) extends UpdateCommandString {
  
  override def applyToCommand(command: String): String = {
    /* Debugging if you need :-)
    println(disk.applyToCommand(command)) 
    println(debug.applyToCommand(command)) 
    println(media.applyToCommand(command))
    println(network.applyToCommand(command)) 
    println(ui.applyToCommand(command)) 
    println(system.applyToCommand(command))
    */
    
    return command + 
      update(avdName, "-avd") +   
      disk.applyToCommand(command) + 
      debug.applyToCommand(command) + 
      media.applyToCommand(command) + 
      network.applyToCommand(command) + 
      ui.applyToCommand(command) + 
      // System needs to be last for QEMU args
      system.applyToCommand(command)
  }

}

  trait UpdateCommandString {
    def update[T](option: Option[T], flagName: String): String = {
      if (option.isEmpty)
        return ""
      else if (option.get.isInstanceOf[Boolean] && option.get.asInstanceOf[Boolean])
        return s" $flagName"
      else if (option.get.isInstanceOf[Int])
        return s" $flagName ${option.get.asInstanceOf[Int].toString}"
      else if (option.get.isInstanceOf[String])
        return s" $flagName ${option.get.asInstanceOf[String]}"
      return ""
    }
    
    def applyToCommand(command: String): String = {
      return ""
    }
  }

  /**
   * Static class to hold clasp options
   * 
   * @param randomContacts number of contacts to be injected
   * @param displayNumber X11 display variable, e.g. DISPLAY=:1
   */
  case class ClaspOptions(randomContacts: Option[Int] = None,
      randomCalendarEvents: Option[Int] = None,
      displayNumber: Option[Int] = None, 
      avdTarget: Option[String] = None,
      abiName: Option[String] = None) 

  /**
   * @param nocache If true, -no-cache will be passed to the emulator
   * @param wipedata If true,  -wipe-data will be passed
   */
  case class DiskImageOptions(cache: Option[String] = None,
    data: Option[String] = None,
    initdata: Option[String] = None,
    nocache: Option[Boolean] = Some(false),
    ramdisk: Option[String] = None,
    sdcard: Option[String] = None,
    wipeData: Option[Boolean] = Some(false)) extends UpdateCommandString {

    override def applyToCommand(command: String): String = {
      return update(cache, "-cache") + 
      update(data, "-data") + 
      update(initdata, "-initdata") + 
      update(nocache, "-nocache") +
      update(ramdisk, "-ramdisk") +
      update(sdcard, "-sdcard") +
      update(wipeData, "-wipe-data")
    }
  }

  /**
   * Note: -debug-tag and -debug-no-tag are not supported
   *
   * @param debug A string of the format expected by -debug <tags>
   * @param logcat A string of the format expected by -logcat <logtags>
   */
  case class DebugOptions(debug: Option[String] = None,
    logcat: Option[String] = None,
    shell: Option[Boolean] = None,
    shellSerial: Option[String] = None,
    showKernel: Option[String] = None,
    trace: Option[String] = None,
    verbose: Option[Boolean] = Some(true)) extends UpdateCommandString {
    
    override def applyToCommand(command: String): String = {
      return update(debug, "-debug") +
      update(logcat, "-logcat") + 
      update(shell, "-shell") + 
      update(shellSerial, "-shell-serial") + 
      update(showKernel, "-show-kernel") + 
      update(trace, "-trace") + 
      update(verbose, "-verbose")
    }
  }

  case class MediaOptions(audio: Option[String] = None,
    audioIn: Option[String] = None,
    audioOut: Option[String] = None,
    noaudio: Option[Boolean] = Some(true),
    radio: Option[String] = None,
    useAudio: Option[Boolean] = Some(false)) extends UpdateCommandString {
   
    override def applyToCommand(command: String): String = {
      return update(audio, "-audio") + 
      update(audioIn, "-audio-in") + 
      update(audioOut, "-audio-out") + 
      update(noaudio, "-noaudio") + 
      update(radio, "-radio") + 
      update(useAudio, "-useaudio")
   }
  }

  case class NetworkOptions(dnsServer: Option[String] = Some("8.8.8.8"),
    httpProxy: Option[String] = None,
    netDelay: Option[String] = None,
    netFast: Option[Boolean] = Some(true),
    netSpeed: Option[String] = None,
    consolePort: Option[Int] = None,
    adbPort: Option[Int] = None,
    reportConsole: Option[String] = None) extends UpdateCommandString { 
   
    override def applyToCommand(command: String): String = {
      var ports: Option[String] = Some("")
      
      if (consolePort.isDefined && adbPort.isDefined)
        ports = Some(s"${consolePort.get},${adbPort.get}")
      else if (consolePort.isDefined && adbPort.isEmpty)
        ports = Some(consolePort.get.toString)
      else
        ports = None
      
      var portCommand = if (ports.getOrElse("").contains(",")) "-ports" else "-port"
        
      return update(dnsServer, "-dns-server") + 
      update(httpProxy, "-http-proxy") + 
      update(netDelay, "-netdelay") + 
      update(netFast, "-netfast") +
      update(netSpeed, "-netspeed") +
      update(ports, portCommand) + 
      update(reportConsole, "-report-console")
    }
  }
    
  /**
   * @param qemu <b>Important: Must be last option specified</b>
   */
  case class SystemOptions(cpuDelay: Option[Int] = None,
      gpsDevice: Option[String] = None,
      noJNI: Option[Boolean] = None,
      useGPU: Option[Boolean] = None,
      radio: Option[String] = None,
      timezone: Option[String] = None,
      memory: Option[Int] = None,
      qemu: Option[String] = None) extends UpdateCommandString { 
    
    override def applyToCommand(command: String): String = {
      return update(cpuDelay, "-cpu-delay") + 
      update(gpsDevice, "-gps")  +
      update(noJNI, "-nojni") + 
      update(useGPU, "-gpu on") + 
      update(radio, "-radio") + 
      update(timezone, "-timezone") +
      update(memory, "-memory") +
      update(qemu, "-qemu")
    }
  }
      
  case class UIoptions(dpiDevice: Option[Int] = None,
      noBootAnim: Option[Boolean] = Some(false),
      noWindow: Option[Boolean] = Some(false),
      scale: Option[String] = None,
      rawKeys: Option[Boolean] = None,
      noSkin: Option[Boolean] = None,
      keySet: Option[String] = None,
      onion: Option[String] = None,
      onionAlpha: Option[Int] = None,
      onionRotation: Option[Int] = None,
      skin: Option[String] = None,
      skinDir: Option[String] = None) extends UpdateCommandString { 
     
    override def applyToCommand(command: String): String = {
        return update(dpiDevice, "-dpi-device") + 
        update(noBootAnim, "-no-boot-anim") + 
        update(noWindow, "-no-window") + 
        update(scale, "-scale") + 
        update(rawKeys, "-raw-keys") + 
        update(noSkin, "-noskin") + 
        update(keySet, "-keyset") + 
        update(onion, "-onion") + 
        update(onionAlpha, "-onion-alpha") + 
        update(onionRotation, "-onion-rotation") + 
        update(skin, "-skin") + 
        update(skinDir, "-skindir")
      }
    }
