package core.sdktools

import scala.language.postfixOps

import scala.sys.process.stringToProcess
import scala.sys.process.Process

import sdk_config.log.debug
import sdk_config.log.error
import sdk_config.log.info

/**
 * Provides an interface to the
 * [[https://developer.android.com/tools/help/aapt.html aapt]]
 * command line tool.
 * 
 * This, along with other components of the Android SDK, is included in
 * [[core.sdktools.sdk]].
 */
trait AaptProxy {
  val aapt:String = sdk_config.config.getString(sdk_config.aapt_config)
  import sdk_config.log.{error, debug, info, trace}
    
  def aapt_dump(flag: String, apk: String): String = {
    val command = s"$aapt dump $flag $apk"
    val output: String = command !!;
    output
  }
}
