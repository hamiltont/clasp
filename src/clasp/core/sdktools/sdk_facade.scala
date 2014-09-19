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

/**
 * The entire SDK tool facade!
 */
object sdk extends AndroidProxy 
              with EmulatorProxy
              with AdbProxy
              with TelnetProxy
              with AaptProxy {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}
  
  // TODO extend to all
  override def valid = {
    super[AndroidProxy].valid && super[EmulatorProxy].valid
  }

  //val adb:String = sdk_config.config.getString(sdk_config.adb_config)
  
  // Blocking call, but guaranteed to return within max_wait.
  // Will report if emulator is both booted and showing up in adb.
  // If result is false there's no way to know what went wrong
  def wait_for_emulator(serialID: String,
      max_wait: FiniteDuration = 35.second)
      (implicit system:ActorSystem) : Boolean = {
    val a = actor(new Act {
        // TODO create get_property method in proxy_adb, and create separate parser functions for each property
        val command:String = s"$adb -s $serialID shell getprop dev.bootcomplete"
        val command2 = s"$adb -s $serialID shell getprop sys.boot_completed" 
        //val command3 = s"$adb -s $serialID shell getprop init.svc.bootanim"
        val timeout = system.scheduler.scheduleOnce(max_wait, self,
          "timeout")
        var resultActor: ActorRef = null

        def retry = {
          val outbuffer = new StringBuilder("")
          val logger = ProcessLogger(out => outbuffer.append(out),
            err => outbuffer.append(err))
          Process(command).!(logger)
          Process(command2).!(logger)
          //Process(command3).!(logger)
          val out = outbuffer.toString

          if (out.contains("1")) {
            timeout.cancel
            resultActor ! true
          } else 
            system.scheduler.scheduleOnce(1.second, self, "retry")
        }

        become {
          case "result"  => { resultActor = sender; retry }
          case "timeout" => resultActor ! false
          case "retry"   => retry
        }
      })

    val result = a.ask("result")(max_wait)
    val realResult = Await.result(result, max_wait).asInstanceOf[Boolean]
    realResult
  }

}

object sdk_config {

  val aapt_config     = "sdk.aapt"
  val adb_config      = "sdk.adb"
  val android_config  = "sdk.android"
  val emulator_config = "sdk.emulator"
  val mksdcard_config = "sdk.mksdcard"
  val config: Config  = ConfigFactory.load()
  
  // TODO make this return the proper class at runtime.
  // Currently only returns clasp.core.sdk_config
  lazy val log = LoggerFactory.getLogger(getClass())
}

