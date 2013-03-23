/**
 * @author hamiltont
 *
 */
package core.sdktools

import org.slf4j.LoggerFactory

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

/**
 * The entire SDK tool facade!
 */
object sdk extends AndroidProxy 
              with EmulatorProxy
              with AdbProxy
              with TelnetProxy
              with AaptProxy {}

object sdk_config {
  // TODO run checks to ensure that all three of these can be accessed
  val android_config  = "sdk.android"
  val emulator_config = "sdk.emulator"
  val adb_config      = "sdk.adb"
  val aapt_config      = "sdk.aapt"
  val config: Config  = ConfigFactory.load()
  // TODO make this return the proper class at runtime.
  // Currently only returns core.sdk_config
  lazy val log = LoggerFactory.getLogger(getClass()) 
}

