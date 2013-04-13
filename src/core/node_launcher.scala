
package core

import scala.collection.mutable.ListBuffer
import scala.collection.mutable.MutableList

//import org.hyperic.sigar.Sigar
import org.slf4j.LoggerFactory

import core.sdktools.sdk
import core.sdktools.EmulatorOptions

import akka.actor._

// Used for command line parsing
import org.rogach.scallop._

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

  val system = ActorSystem("clasp")

  val conf = new Conf(args)
  if (conf.client()) {
    info("I am a client!") 
    // Create new node, which will auto-register with
    // the NodeLauncher
    var n = system.actorOf(Props[Node], name="10.0.2.6")
    //n ! "rundefault"
    info("Created Node")
  }
  else {
    info("I am a server!")
    // Create NodeLauncher, which will automatically 
    // ssh into each worker computer and properly 
    // run clasp as a client
    val launcher = system.actorOf(Props[NodeLauncher], name="nodelauncher")
    
    // TODO ask configuration if we should create a local node object
  }

  info("sleeping")
  Thread.sleep(20000)

  info("Shutting down system")
  system.shutdown
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

// todo call into ssh and start all clients. They can
// then register back with the nodelauncher
class NodeLauncher extends Actor {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}
  val nodes = ListBuffer[ActorRef]()

  //var n = context.actorOf(Props[Node], name="10.0.2.6")
  //n ! "rundefault"
  //info("Created Node")

  // TODO replace this with monitoring child lifecycle
  def receive = {
    case "node_up" => { 
      info("A node has registered!")
      nodes += sender 
    }
    case "node_down" => nodes -= sender
  }

  // Send a PoisonPill to all Nodes
  override def postStop() = {

  }
}

class Node extends Actor {
  val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}
  import core.sdktools.EmulatorOptions
 
  override def preStart() = {
    // Lookup the NodeLauncher
    val launcher = context.actorFor("akka.tcp://clasp@10.0.2.6:2552/user/nodelauncher")
    launcher ! "node_up"
  }

  override def postRestart(reason: Throwable) = {
    preStart()
    // TODO search for emulators, manage them?
  }

  override def postStop() = {
    // TODO crash emulators? No....
    // TODO - should I even do this, or just watch for the Terminate message from my 
    // children in the NodeLauncher
    val launcher = context.actorSelection("akka.tcp://clasp@10.0.2.6:2552/nodelauncher")
    launcher ! "node_down"
    info("Node " + hostname + " has stopped");
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


