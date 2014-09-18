package core.sdktools

import clasp.core.sdktools._
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.charset.StandardCharsets

class avd(val name: String,
    val target: String,
    val abi: String) {

  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{ error, debug, info, trace }

  debug(s"Building AVD $name for $abi $target")
  if (!sdk.create_avd(name, target, abi, true))
    throw new IllegalStateException("Unable to create AVD")
  
  val properties = sdk.list_avd(name)
  val path = properties("Path")
  val skin = properties("Skin")
  
  // Need to use Map.contains in case these dont exist
  //      Name: Non-Hardware-Accel
  //  Device: 5.1in WVGA (Generic)
  //    Path: /Users/hamiltont/.android/avd/Non-Hardware-Accel.avd
  //  Target: Android 4.0.3 (API level 15)
  // Tag/ABI: default/x86
  //    Skin: 480x800
  //  Sdcard: 16M 
  
  // Ensure the emulator starts at 0,0
  val emulatorIni = s"${path}/emulator-user.ini"
  Files.write(Paths.get(emulatorIni), "window.x = 0\nwindow.y = 0\n".getBytes(StandardCharsets.UTF_8))
  
  def get_skin_name = {
    skin
  }
  
  def delete = {
    sdk.delete_avd(name)
  }
  
  def get_skin_dimensions = {
    skin match {
      case "QVGA" => "240x320" 
      case "WQVGA400" => "240x400"
      case "WQVGA432" => "240x432"
      case "HVGA" => "320x480"
      case "WVGA800" => "480x800"
      case "WVGA854" => "480x854"
      case "WXGA720" => "1280x720"
      case "WXGA800-7in" => "1280x800"
      case "WXGA800" => "1280x800"
      case _ => { 
        info(s"Unknown skin: $skin - guessing 1280x800 dimensions") 
        "1280x800" }
    }
  }  
}