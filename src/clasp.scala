package clasp

import scala.collection.mutable.MutableList

import scala.sys.process._
import scala.language.postfixOps

import scala.concurrent.{Future,Await}
import scala.concurrent.duration._

import org.slf4j.LoggerFactory

import com.typesafe.config.ConfigFactory

import akka.actor._
import akka.pattern.Patterns.ask

import clasp.core._
import clasp.core.sdktools.sdk
import clasp.core.sdktools.EmulatorOptions

/*
 * Example of using Clasp. The App will be packaged and deployed to both the server and 
 * the client machines. Note that you should never be the one specifying --client on the 
 * command line. Starting the server will, with proper configuration parameters, start all
 * of the worker machines. The corollary is that stopping the server is a graceful operation
 * and all of the worker machines will be properly shutdown as soon as they are done processing
 * their current message. If your messages are huge this might take a while, but this is 
 * much better than logging into each machine and force killing all of the processes associated
 * with running clasp. If you manually shutdown instead of using the graceful operation then 
 * you had better know how to clean up the proper processes, because the system may refuse to 
 * restart on any computers that have an incorrect start state!
 */
object ClaspRunner extends App {
  // If you would like logging on remote machines(all workers), then use these logging methods
  // in your codebase. The logs will (eventually) be pulled back to the master, so that you don't
  // need to manually aggregrate your logs
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}

  // Used to parse the command line arguments
  val conf = new ClaspConf(args)

  // Check if you are the server, or one of the workers 
  var ip: String = "none"
  if (conf.ip.get == None)
    ip = "10.0.2." + "hostname".!!
  else
    ip = conf.ip()
  // TODO move this into scallop or the core so that we don't have to require a user to fix their entry 
  // strings
  ip = ip.stripLineEnd

  // TODO: Add a way for emulator options to be nonuniform?
  // Setup some options for the emulator
  val opts = new EmulatorOptions
  opts.noWindow = true
  // Create a new instance of the framework. There should be at least one instance of Clasp
  // started per computer in your cluster, although you should probably let the master handle
  // starting all of the workers
  
  val client_ips = List("10.0.2.26")

  if (conf.client()) {
    if (conf.mip.get == None) {
      error("Client started without an ip address for the master node, aborting")
      System.exit(1)
    }
    val masterip = conf.mip().stripLineEnd
    new ClaspClient(ip, masterip, opts)
  } else {

    var clasp = new ClaspMaster(ip, client_ips)
  
    // TODO should we update the system so the user extends "ClaspServer" and only writes this 
    // part of the codebase? Everything above seems like boilerplate code
    // TODO: We shouldn't have to sleep like this.
    //       Add an option to wait until all emulators are alive,
    //       but not necessarily entirely booted.
    // @Brandon - this is a great idea for the object-level API! Something like waitForEmulators(10)
    // that will be called when there are 10 emulators alive and ready. Or perhaps that's how we build
    // the main action loop - have a waitForEmulator(new Task() { // do something with emulator here })
    Thread.sleep(15000)

    printf("\n\n\n=====================================\n")
    val devices = clasp.get_devices
    if (devices isEmpty) {
      println("Found no devices, aborting to avoid errors!")
      clasp.kill
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
    
  } // End of clasp master logic
}
 

/* TODO make it possible to support sending EmulatorOptions across the network and starting emulators
 * with different options
 */
class ClaspClient(val ip: String, val masterip: String, val emuOpts: EmulatorOptions) {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}
  info("I am a client!")  
  val clientConf = ConfigFactory
    .parseString(s"""akka.remote.netty.hostname="$ip"""")
    .withFallback(ConfigFactory.load("client"))
  val system = ActorSystem("clasp", clientConf)

  var n = system.actorOf(Props(new Node(ip, masterip, emuOpts)), name=s"node-$ip")
  info(s"Created Node for $ip")
}


/*
 * Interface to the clasp system. Creating this will start a clasp worker on all clients 
 * 
 */
class ClaspMaster(val ip: String, val client_ips: Seq[String]) {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}

  info(s"Using IP $ip")
  info("I am the master!")
  val serverConf = ConfigFactory
      .parseString(s"""akka.remote.netty.hostname="$ip" """)
      .withFallback(ConfigFactory.load("master"))

  var system:ActorSystem = null
  try {
    system = ActorSystem("clasp", serverConf)
  } catch {
    case inuse: org.jboss.netty.channel.ChannelException => {
      error(s"Another person is using $ip as a master node!")
      error("Refusing to start here")
      error("Here is some debug info to help detect what is running:")
      val ps_list: String = "ps -o user,cmd -C java".!!.stripLineEnd
      error(s"Process List (including you!): \n$ps_list")
      
      error("User List (including you!):")
      val user_list: String = "ps --no-headers -o user -C java".!!
      import scala.collection.immutable.StringOps
      (new StringOps(user_list)).lines.foreach(line => {
        val user: String = (s"getent passwd $line").!!
        error(user.stripLineEnd)
      })
      System.exit(0)
    }
  }
  
  
  val manager = system.actorOf(Props(new NodeManger(ip, client_ips)), name="nodemanager")
  info("Created NodeManger")
  
  sys addShutdownHook(shutdown_listener(manager))

  def get_devices: List[Emulator] = {
    info("Getting available devices.")
    val f = ask(manager, "get_devices", 60000).mapTo[MutableList[ActorRef]]
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
    shutdown_listener(manager)
  }

  private def shutdown_listener(manager: ActorRef) {
    // TODO: Context.system.shutdown somewhere?
    if (manager != null && !manager.isTerminated) {
      manager ! "shutdown"
    }

    var timeSlept = 0.0d; var timeout = 10.0d
    while (manager != null && !manager.isTerminated) {
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

/*
 * An object-oriented way to interface with an Actor. This caches multiple items 
 * from the emulator process underneath the actor, and therefore may become 
 * invalid if the emulator dies. We may want to avoid caching in the future and
 * just have this know how to ask the emulator for the right things
 */
class Emulator(emulatorActor: ActorRef) extends Serializable {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}

  // TODO: There's possibly a better way to do this?
  // @Brandon - I think the normal scala-ism is to use Option
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
