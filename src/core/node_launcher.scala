
package core

import scala.collection.mutable.ListBuffer
import scala.collection.mutable.MutableList

//import org.hyperic.sigar.Sigar
import org.slf4j.LoggerFactory

import core.sdktools.sdk
import core.sdktools.EmulatorOptions

import akka.actor._

import com.typesafe.config.ConfigFactory

// Used for command line parsing
import org.rogach.scallop._

import System.currentTimeMillis

/*
 * Handles starting nodes, either to represent this 
 * computer or other computers on the network. If this 
 * computer, we can start the node directly (granted, at some point I may want to put it in a separate process for sandboxing, but that's not important now). If another 
 * computer, we have to send a message across the 
 * communication mechanism and await the callback 
 * response
 * 
 */
object Clasp extends App {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}
 

  val conf = new Conf(args)
  if (conf.client()) 
    run_client
  else
    run_master
  info("App constructor is done")
  // TODO ask configuration if we should create a local node object

  def run_client {
    info("I am a client!") 

    //val command = s"$adb connect $host:$port"
    val hostname = "10.0.2.7"
    val port = "2253"
    val clientConf = ConfigFactory
      .parseString(s"""
        akka {
          remote {
            netty {
              hostname = "$hostname"
              port = $port
            }
          }
        }
        """)
      .withFallback(ConfigFactory.load)
    val system = ActorSystem("clasp", clientConf)

    // Create new node, which will auto-register with
    // the NodeManger
    var n = system.actorOf(Props(new Node(hostname, "10.0.2.6")), 
      name=s"node-$hostname")
    //n ! "rundefault"
    info("Created Node")
    info("Chilling out for a while")
    Thread.sleep(2000)
  
    info("I'm going to try and bring down the entire system!")
    val launcher = system.actorFor("akka://clasp@10.0.2.6:2552/user/nodelauncher")

    launcher ! "shutdown"
  }

  def run_master {
    info("I am the system manager!")

    val hostname = "10.0.2.6"
    val serverConf = ConfigFactory
      .parseString(s"""akka.remote.netty.hostname="$hostname"""")
      .withFallback(ConfigFactory.load)
    val system = ActorSystem("clasp", serverConf)

    // Create NodeManger, which will automatically 
    // ssh into each worker computer and properly 
    // run clasp as a client
    val launcher = system.actorOf(Props[NodeManger], name="nodelauncher")

    info("Created the launcher")
    info("Chilling out until someone kills me")
    // Create the local Node

    //info("Shutting down system")
    //system.shutdown
  }
}

// Main actor for managing the entire system
// Starts, tracks, and stops nodes
class NodeManger extends Actor {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}
  val nodes = ListBuffer[ActorRef]()
  // TODO start all clients via SSH

  // TODO replace this with monitoring child lifecycle
  def monitoring: Receive = {
    case "node_up" => { 
      info("A node has registered!")
      nodes += sender 
    }
    case "node_down" => {
      info("A node has deregistered")
      nodes -= sender
    }
    case "shutdown" => {
      // First register to watch all nodes
      nodes.foreach(node => context.watch(node))
      info("Shutdown requested")

      // Second, transition our receive loop into a Reaper
      context.become(reaper)
      info("Transitioned to a reaper")

      // Second, ask all of our nodes to stop
      nodes.foreach(n => n ! PoisonPill)
      info("Pill sent to all nodes")
    }
  }

  def reaper: Receive = {
    case Terminated(ref) =>
      nodes -= ref
      info("Node " + ref.path + " Termination received")
      if (nodes.isEmpty) {
        info("No more nodes, killing self")  
        self ! PoisonPill
      }
    case _ =>
      info("In reaper mode, ignoring messages")
  }

  override def postStop = {
    context.system.registerOnTermination {
      info("System shutdown achieved at " + System.currentTimeMillis ) }
    info("postStop. Requesting system shutdown at " + System.currentTimeMillis )
    context.system.shutdown()
  }

  // Start in monitor mode
  def receive = monitoring

}
 
// Manages the running of the framework on a single node,
// including ?startup?, shutdown, etc
class Node(val ip: String, val serverip: String) extends Actor {
  val log = LoggerFactory.getLogger(getClass())
  val launcher = context.actorFor("akka://clasp@" + serverip + ":2552/user/nodelauncher")
  import log.{error, debug, info, trace}
  import core.sdktools.EmulatorOptions
 
  override def preStart() = {
    launcher ! "node_up"
  }

  override def postStop() = {
    info("Node " + self.path + " has stopped");

    context.system.registerOnTermination {
      info("System shutdown achieved at " + System.currentTimeMillis) }
    info("Requesting system shutdown at " + System.currentTimeMillis)
    context.system.shutdown()
  }

  val devices: MutableList[ActorRef] = MutableList[ActorRef]()
  var current_emulator_port = 5555
  info("A new Node is being constructed")

  def receive = {
    case "rundefault" => {
      val opts = new EmulatorOptions
      opts.noWindow = true
      run_emulator(opts, context)
    }
    case "cleanup" => {cleanup}
  }

  // TODO: Option to run a specific AVD.
  def run_emulator(opts: EmulatorOptions = null, context: ActorContext): ActorRef = {
    info("Running an emulator")
    devices += EmulatorBuilder.build(current_emulator_port, opts, context)
    info("emulator built")
    current_emulator_port += 2
    devices.last
  }

  def cleanup {
    devices.foreach(phone => phone ! "cleanup")
  }
}

class Conf(arguments: Seq[String]) extends ScallopConf(arguments) {
  version("Clasp 0.0.0")
  banner("""Usage: clasp [-c|--client]
    |By default clasp runs as though it was a server with only 
    |the local node. This makes it easier for people running in
    |a non-distributed manner. If you use sbt, then to run a 
    |client use sbt "run --client". To run a whole system you
    |need a server running on the main node and then clients on
    |all other nodes
    |""".stripMargin)

  val client = opt[Boolean](descr = "Should this run as a client instance")
}

