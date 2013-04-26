
package clasp.core

import scala.collection.mutable.ListBuffer
import scala.collection.mutable.MutableList
import scala.collection.mutable.ArrayStack
import scala.collection.JavaConverters._

//import org.hyperic.sigar.Sigar
import org.slf4j.LoggerFactory

import clasp.core.sdktools.sdk
import clasp.core.sdktools.EmulatorOptions

import akka.actor._
import akka.pattern.Patterns.ask

// For SSH into remotes and starting clients
import scala.sys.process._
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException

import com.typesafe.config.ConfigFactory

// Used to delay shutting down the system. Termination 
// conditions are a bit hard to come by currently because 
// the server is designed to stay alive
import scala.concurrent.duration._
import scala.concurrent.{Future,Await}
import scala.language.postfixOps

// Used for command line parsing
//import org.rogach.scallop._

import System.currentTimeMillis

// Main actor for managing the entire system
// Starts, tracks, and stops nodes
class NodeManger(val ip: String, val initial_workers: Int) extends Actor {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}

  val booter = context.actorOf(Props(new NodeBooter(ip)), name="booter")
  for (i <- 1 to initial_workers) yield (booter ! BootNode)

  val nodes = ListBuffer[ActorRef]()

  def monitoring: Receive = {
   case "node_up" => { 
      info(s"${nodes.length}: Node ${sender.path} has registered!")
      nodes += sender

      if (nodes.length == initial_workers)
        info("All nodes are awake and registered.")
    }
    case "node_down" => {
      nodes -= sender
      info(s"${nodes.length}: Node ${sender.path} has deregistered!")
    }
    
    case "shutdown" => {
      // First register to watch all nodes
      nodes.foreach(node => context.watch(node))
      info("Shutdown requested.")

      //info("Sleeping for ~15 more seconds so the user can see if emulator CPU has stabilized")
      //Thread.sleep(15000)

      // Second, transition our receive loop into a Reaper
      context.become(reaper)
      info("Transitioned to a reaper.")

      // Second, ask all of our nodes to stop
      nodes.foreach(n => n ! PoisonPill)
      info("Pill sent to all nodes.")

      // TODO lookup PP semantics and potentially remove this line if it's redundant 
      booter ! PoisonPill
    }
  }

  def reaper: Receive = {
    case Terminated(ref) =>
      nodes -= ref
      info("Node " + ref.path + " Termination received.")
      if (nodes.isEmpty) {
        info("No more nodes, killing self.")
        self ! PoisonPill
      }
    case _ =>
      info("In reaper mode, ignoring messages.")
  }

  override def postStop = {
    context.system.registerOnTermination {
      info("System shutdown achieved at " + System.currentTimeMillis ) }
    info("postStop. Requesting system shutdown at " + System.currentTimeMillis )
    context.system.shutdown()
  }

  // Start in monitor mode.
  def receive = monitoring
}


class NodeBooter(val master_ip: String) extends Actor {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}
  
  val config = ConfigFactory.load("client")
  // TODO randomly sort this
  val pool_list = config.getStringList("clasp.workerpool")
  val it = pool_list.iterator
  val pool: ArrayStack[String] = (new ArrayStack) union pool_list.asScala

  def receive = { 
    case BootNode => boot_any
    case NodeBusy(nodeip, nodelog) => {
      info(s"Node $nodeip has declared itself busy")
      info(s"Debug log for node $nodeip:\n$nodelog")
      info("Requesting a new node to replace $nodeip")
      boot_any
    }
  }

  def boot_any = {
    info("Node boot requested")
    if (pool.length != 0)
      bootstrap(pool.pop)
    else 
      error("Node boot request failed - worker pool is empty")
  }

  def bootstrap(client_ip:String) = {
    // TODO put this somewhere besides the home directory. Best option
    // would be to pass this directly into SSH and forgo the entire 
    // temporary file stuff. There is a race condition where the same
    // file might be used multiple times
    try {
      // Write a script in our home directory.
      // We assume all client_ips share the home directory.
      val home: String = System.getProperty("user.home")

      // Locate our working directory
      val directory: String = "pwd" !!;

      val file: File = new File(home + "/bootstrap-clasp.sh")
      info("Building file " + file.getCanonicalPath )
      if (!file.exists())
        file.createNewFile();
      val fw: FileWriter = new FileWriter(file.getAbsoluteFile())
      val bw: BufferedWriter = new BufferedWriter(fw)
      bw.write(s"""#!/bin/sh\n
        \n
        cd $directory \n
        echo "Starting node using:"
        echo "target/start --client --ip $$1 --mip $$2 >> nohup.$$1 2>&1 &"\n
        nohup target/start --client --ip $$1 --mip $$2 >> nohup.$$1 2>&1 & \n
        echo "Done"
        """)
      bw.close()
      val command: String = s"ssh -oStrictHostKeyChecking=no $client_ip sh bootstrap-clasp.sh $client_ip $master_ip"
      info(s"Starting $client_ip using $command")

      command.!!.stripLineEnd

      // Remove bootstrapper
      val file2: File = new File("~/bootstrap-clasp.sh")
      file2.delete()

    } catch {
      case e: IOException => {
        error("Unable to write bootstrapper, aborting.")
        e.printStackTrace
      }
    }
  } // End bootstrap

}
case class BootNode()
case class NodeBusy(nodeid: String, debuglog: String)

// Manages the running of the framework on a single node,
// including ?startup?, shutdown, etc.
class Node(val ip: String, val serverip: String,
    val emuOpts: EmulatorOptions) extends Actor {
  val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}
  
  val manager = context.actorFor("akka://clasp@" + serverip + ":2552/user/nodemanager")
  val devices: MutableList[ActorRef] = MutableList[ActorRef]()
  var base_emulator_port = 5555

  // Moved outside of Node so the options can be set with the API.
  // val opts = new EmulatorOptions
  // opts.noWindow = true

  // TODO: Better ways to ensure device appear online?
  sdk.kill_adb
  sdk.start_adb

  // TODO: This way causes all emulators to be started with port=5561!
  //       I have to idea why, but using the loop below causes the
  //       emulators to be started on the right port.
  //       I am so confused! Would love to know why the commented
  //       way isn't working.
  /*
  var current_emulator_port = 5555
  context.actorOf(Props(new EmulatorActor(5555, opts)),
    s"emulator-$current_emulator_port")
  current_emulator_port += 2
  context.actorOf(Props(new EmulatorActor(5557, opts)),
    s"emulator-$current_emulator_port")
  current_emulator_port += 2
  context.actorOf(Props(new EmulatorActor(5559, opts)),
    s"emulator-$current_emulator_port")
  current_emulator_port += 2
  */

  /*
  context.actorOf(Props(new EmulatorActor(base_emulator_port,
    opts)), s"emulator-${base_emulator_port}")
  */
  for (i <- 0 to 2) {
    devices += context.actorOf(Props(new EmulatorActor(base_emulator_port + 2*i,
      emuOpts, serverip)), s"emulator-${base_emulator_port+2*i}")
  }

  override def preStart() = {
    info(s"Node online: ${self.path}")
    manager ! "node_up"
  }

  override def postStop() = {
    info("Node " + self.path + " has stopped.");

    devices.foreach(phone => phone ! PoisonPill)
    info("Requested emulators to halt.")

    context.system.registerOnTermination {
      info("System shutdown achieved at " + System.currentTimeMillis) }
    info("Requesting system shutdown at " + System.currentTimeMillis)
    context.system.shutdown()
  }

  def receive = {
    case _ => info("Node received a message, but it doesn't do anything!")
  }
}
