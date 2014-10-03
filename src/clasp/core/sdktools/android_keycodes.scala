package clasp.core.sdktools

case class AndroidKey(key: Int)

abstract class AndroidKeySet {}
object `KeyCodes_v1.6` extends AndroidKeySet {
  val KEY_HOME = AndroidKey(0x0003)
}

object `KeyCodes_v3.0` extends AndroidKeySet {
  val keys = Map("KEY_HOME" -> AndroidKey(0x007a))
}

class AndroidKeys
object AndroidKeys {
  val adb:String = sdk_config.config.getString(sdk_config.adb_config)
  import sdk_config.log.{error, debug, info, trace}

  // Gets the default keycodes for the Android 
  // platform version, based on the default key layout
  // used
  // See https://source.android.com/devices/tech/input/keyboard-devices.html
  def get_keycodes(android_version: Double): AndroidKeySet = {
    android_version match {
      case 1.6 => {return `KeyCodes_v1.6`}
      case 3.0 => {return `KeyCodes_v3.0`}
      case _ => {
        info(s"Android platform $android_version unknown, returning keycodes for 1.6")
        `KeyCodes_v1.6`
      }
    }
  }

}
