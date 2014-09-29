/**
 * @author hamiltont
 *
 */
package clasp.core.sdktools

import org.slf4j.LoggerFactory
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import scala.sys.process._
import scala.concurrent._
import scala.concurrent.duration._
import ExecutionContext.Implicits.global
import akka.actor._
import akka.actor.ActorDSL._
import akka.actor.ActorSystem
import akka.pattern.ask
import java.nio.file.Files
import java.nio.file.Paths
import clasp.utils.FancyFuture._
import scala.util.Failure
import scala.util.Success
import clasp.core.sdktools._

/**
 * The entire SDK tool facade!
 */
object sdk extends AndroidProxy
  with EmulatorProxy
  with AdbProxy
  with TelnetProxy
  with AaptProxy {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{ error, debug, info, trace }

  // TODO extend to all
  override def valid = {
    super[AndroidProxy].valid && super[EmulatorProxy].valid
  }

  /**
   * Blocking method that will periodically check if emulator is
   * registered with adb and reporting boot is complete
   */
  def wait_for_emulator(serialID: String,
    max_wait: FiniteDuration = 35.second)(implicit system: ActorSystem): Boolean = {

    /**
     * Will be run on background thread
     */
    def poll_adb(command: String): Boolean = {
      // Capture out and err
      val outbuffer = new StringBuilder("")
      val logger = ProcessLogger(
        out => {
          debug(s"ADB reported '$out' for '$command'")
          outbuffer.append(out)
        },
        err => {
          debug(s"ADB reported '$err' for '$command'")
          outbuffer.append(err)
        })

      val proc = Process(command).run(logger)
      proc.exitValue

      debug(s"ADB poll  returning ${outbuffer.toString.startsWith("1")}")
      outbuffer.toString.startsWith("1")
    }

    /**
     * Evaluates the passed expression on a background thread, and 
     * then returns the results on the foreground thread. Returns 
     * false if there was a timeout anywhere
     */
    def run_with_cancel_and_timeout(what: => Boolean): Boolean = {
      val (fut, cancel) = CancelableFuture(what)
      try {
        Await.ready(fut, 25.seconds)
      } catch {
        case _: TimeoutException => {}
        case _: InterruptedException => {}
      }

      // Force cancel, as we've waited long enough
      val wasCancelled = cancel()
      
      // Return the result
      try {
        return Await.result(fut, 5.seconds)
      } catch {
        case any:Throwable => {
          debug(s"Returning ADB result failed with $any")
          false
        }
      }
    }

    // Until time is up or the system is online, 
    val end_time = System.currentTimeMillis() + max_wait.toMillis
    while (System.currentTimeMillis() < end_time) {

      def one = poll_adb(s"$adb -s $serialID shell getprop dev.bootcomplete")

      if (run_with_cancel_and_timeout(one)) {
        debug("Emulator online success")
        return true
      }

      // Sleep before hitting ADB again
      Thread.sleep(1000)

      def two = poll_adb(s"$adb -s $serialID shell getprop sys.boot_completed")
      if (run_with_cancel_and_timeout(two)) {
        debug("Emulator online success")
        return true
      }

      // Sleep before hitting ADB again
      Thread.sleep(2000)
    }

    error("Waiting on emulator timed out")
    false
  }
}

object sdk_config {

  val aapt_config = "sdk.aapt"
  val adb_config = "sdk.adb"
  val android_config = "sdk.android"
  val emulator_config = "sdk.emulator"
  val mksdcard_config = "sdk.mksdcard"
  val config: Config = ConfigFactory.load()

  // TODO make this return the proper class at runtime.
  // Currently only returns clasp.core.sdk_config
  lazy val log = LoggerFactory.getLogger(getClass())
}

