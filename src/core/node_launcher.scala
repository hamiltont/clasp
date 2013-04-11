
package core

import scala.collection.mutable.ListBuffer
import scala.collection.mutable.MutableList

//import org.hyperic.sigar.Sigar
import org.slf4j.LoggerFactory

import core.sdktools.sdk
import core.sdktools.EmulatorOptions

import akka.actor._

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
  val launcher = system.actorOf(Props[NodeLauncher], name="nodelauncher")
}

class NodeLauncher extends Actor {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}
  val nodes = ListBuffer[ActorRef]()

  var n = context.actorOf(Props[Node], name="local")
  n ! "rundefault"
  info("Created Node")
  Thread.sleep(20000)
  n ! "cleanup"
  info("Cleaned  Node")


  def receive = {
    case "node_up" => nodes += sender
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
    context.parent ! "node_up"
  }

  override def postRestart(reason: Throwable) = {
    preStart()
    // TODO search for emulators, manage them?
  }

  override def postStop() = {
    // TODO crash emulators? No....
    context.parent ! "node_down"
  }

  val devices: MutableList[ActorRef] = MutableList[ActorRef]()
  var current_emulator_port = 5555
  info("A new Node is being constructed")

  def receive = {
    case "rundefault" => {
      val opts = new EmulatorOptions
      opts.noWindow = true
      run_emulator(opts)
    }
    case "cleanup" => {cleanup}
  }

  // TODO: Option to run a specific AVD.
  def run_emulator(opts: EmulatorOptions = null): ActorRef = {
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


