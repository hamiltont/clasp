package clasp // TODO: If this isn't `core`, where should it go?

import scala.collection.mutable.MutableList

import scala.sys.process._
import scala.language.postfixOps

import scala.concurrent.{Future,Await}
import scala.concurrent.duration._

import org.slf4j.LoggerFactory

import com.typesafe.config.ConfigFactory

import akka.actor._
import akka.pattern.Patterns.ask

import core._
import core.sdktools.sdk

/*
 * Example of using Clasp.
 */
object ClaspRunner extends App {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}

  val conf = new ClaspConf(args)

  // Was a hostname provided, or should we locate it?
  var ip: String = "none"
  if (conf.ip.get == None)
    ip = "10.0.2." + "hostname".!!
  else
    ip = conf.ip()
  ip = ip.stripLineEnd

  var clasp = new Clasp(ip, conf.client())
  if (!conf.client()) {
    // TODO: We shouldn't have to sleep like this.
    //       Add an option to wait until all emulators are alive,
    //       but not necessarily entirely booted.
    Thread.sleep(15000)

    printf("\n\n\n=====================================\n")
    val devices = clasp.get_devices
    if (devices isEmpty) {
      println("Found no devices! Expect errors.")
    }

    println("Device statuses:")
    for (device <- devices) {
      println(s"serialID: ${device.serialID}, isBusy: ${device.isBusy}")
    }
    
    println("Setting the first device as busy.")
    devices(0).setBusy(true)

    println("Device statuses:")
    for (device <- devices) {
      println(s"serialID: ${device.serialID}, isBusy: ${device.isBusy}")
    }

    info(s"Waiting for 5 minutes before installing packages.")
    Thread.sleep(60000*5)
    for (device <- devices) {
      device.installApk("examples/antimalware/Profiler.apk")
    }

    //println("Let's wait for 1 minute and hope the emulators load! :D")
    //Thread.sleep(60000)

    printf("=====================================\n\n\n")

    clasp.kill
  }
}

/*
 * Handles starting nodes, either to represent this 
 * computer or other computers on the network. If this 
 * computer, we can start the node directly (granted, at some point I may want to put it in a separate process for sandboxing, but that's not important now). If another 
 * computer, we have to send a message across the 
 * communication mechanism and await the callback 
 * response
 */
class Clasp(val ip: String, val isClient: Boolean) {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}
  private var launcher: ActorRef = null

  info(s"Using IP $ip")

  if (isClient) {
    run_client(ip, "10.0.2.6")
  } else {
    run_master(ip, List("10.0.2.1")) //, "10.0.2.2", "10.0.2.4", "10.0.2.5"))
  }

  def get_devices: List[Emulator] = {
    if (isClient) {
      info("'get_devices' called on a client!")
      return null
    }
    info("Getting available devices.")
    val f = ask(launcher, "get_devices", 60000).mapTo[MutableList[ActorRef]]
    val emulator_actors = Await.result(f, 100 seconds)
    //println(emulator_actors)
    for (actor <- emulator_actors) {
      val f = ask(actor, "get_serialID", 60000).mapTo[String]
      val serialID = Await.result(f, 100 seconds)
    }
    val emulators = emulator_actors.map(new Emulator(_)).toList
    //println(emulators)
    return emulators
  }

  def kill {
    info("Killing Clasp and emulators.")
    kill_master(launcher)
  }

  private def run_client(hostname:String, server: String) {
    info("I am a client!")
    
    val clientConf = ConfigFactory
      .parseString(s"""
        akka {
          remote {
            netty {
              hostname = "$hostname"
              port     = 2553
            }
          }
        }
        """)
      .withFallback(ConfigFactory.load)
    val system = ActorSystem("clasp", clientConf)

    // Create new node, which will auto-register with
    // the NodeManger
    var n = system.actorOf(Props(new Node(hostname, server)), 
      name=s"node-$hostname")
    //n ! "rundefault"
    info("Created Node.")
    info("Chilling out..I will stay alive until killed.")
  }

  private def run_master(hostname: String, clients: Seq[String]) {
    info("I am the system manager!")

    val serverConf = ConfigFactory
      .parseString(s"""akka.remote.netty.hostname="$hostname" """)
      .withFallback(ConfigFactory.load)
    val system = ActorSystem("clasp", serverConf)

    // Create NodeManger, which will automatically 
    // ssh into each worker computer and properly 
    // run clasp as a client
    launcher = system.actorOf(Props(new NodeManger(clients)), 
      name="nodelauncher")

    info("Created NodeManger")
    sys addShutdownHook(kill_master(launcher))
  }

  private def kill_master(launcher: ActorRef) {
    // TODO: Context.system.shutdown somewhere?
    if (launcher != null && !launcher.isTerminated) {
      launcher ! "shutdown"
    }

    var timeSlept = 0.0d; var timeout = 10.0d
    while (launcher != null && !launcher.isTerminated) {
      Thread.sleep(500)
      timeSlept += 0.5d
      if (timeSlept >= timeout) {
        val input = readLine(
          s"""|Waited for $timeSlept seconds, and all nodes have not responded.
              |Continue waiting (y)/n? """.stripMargin)
        if (input.toLowerCase.charAt(0) == 'n') {
          println("Exiting.")
          println("Warning: Nodes may still have emulators running on them!")
          return;
        } else {
          println("Waiting for 10 more seconds.")
          timeout += 10.0d
        }
      }
    }
  }
}

class Emulator(emulatorActor: ActorRef) extends Serializable {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}

  // TODO: There's possibly a better way to do thits?
  var serialID = "unset"

  {
    val f = ask(emulatorActor, "get_serialID", 60000).mapTo[String]
    serialID = Await.result(f, 100 seconds)
  }

  def setBusy(isBusy: Boolean) {
    if (isBusy) {
      emulatorActor ! "set_busy"
    } else {
      emulatorActor ! "unset_busy"
    }
  }

  def isBusy: Boolean = {
    val f = ask(emulatorActor, "is_busy", 60000).mapTo[Boolean]
    return Await.result(f, 100 seconds)
  }

  def installApk(path: String, setBusy: Boolean = true) {
    info(s"Installing package: $path.")
    emulatorActor ! Execute(() => sdk.install_package(serialID, path), setBusy)
  }
}
