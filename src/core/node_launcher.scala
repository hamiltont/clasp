
package core

import scala.collection.mutable.ListBuffer
import scala.collection.mutable.MutableList

//import org.hyperic.sigar.Sigar
import org.slf4j.LoggerFactory

import core.sdktools.sdk
import core.sdktools.EmulatorOptions

import akka.actor._

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
import scala.language.postfixOps

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
  // Was a hostname provided, or should we locate it? 
  var ip: String = "none"
  if (conf.ip.get == None)
    ip = "10.0.2." + "hostname".!!
  else
    ip = conf.ip()
  ip = ip.stripLineEnd
  info(s"Using IP $ip")

  if (conf.client()) 
    run_client(ip, "10.0.2.6")
  else
    // TODO make server a command line argument
    run_master(ip, List("10.0.2.6","10.0.2.7")) //,"10.0.2.8","10.0.2.9","10.0.2.10","10.0.2.11"))

  def run_client(hostname:String, server: String) {
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
    info("Created Node")
    info("Chilling out..I will stay alive until killed")
  }

  def run_master(hostname: String, clients: Seq[String]) {
    info("I am the system manager!")

    val serverConf = ConfigFactory
      .parseString(s"""akka.remote.netty.hostname="$hostname"""")
      .withFallback(ConfigFactory.load)
    val system = ActorSystem("clasp", serverConf)

    // Create NodeManger, which will automatically 
    // ssh into each worker computer and properly 
    // run clasp as a client
    val launcher = system.actorOf(Props(new NodeManger(clients)), 
      name="nodelauncher")

    info("Created NodeManager")
    info("Chilling out until someone kills me")
  }
}

// Main actor for managing the entire system
// Starts, tracks, and stops nodes
class NodeManger(clients: Seq[String]) extends Actor {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}
  val nodes = ListBuffer[ActorRef]()
  // TODO start all clients via SSH
  start_clients(clients)

  def start_clients(clients:Seq[String]):Unit = {
    // Locate our working directory
    val directory: String = "/home/hamiltont/clasp" //"pwd" !!

    try {
      // Write a script in our home directory 
      // We assume all clients share the home directory
      val home: String = System.getProperty("user.home")
      val file: File = new File(home + "/bootstrap-clasp.sh")
      info("Building file " + file.getCanonicalPath )
      if (!file.exists())
        file.createNewFile();
      val fw: FileWriter = new FileWriter(file.getAbsoluteFile())
      val bw: BufferedWriter = new BufferedWriter(fw)
      bw.write(s"""#!/bin/sh\n
        \n
        cd $directory \n
        nohup target/start --client &> nohup.$$1 & \n
        """)
      bw.close()

      // Start each client
      clients.foreach(ip => {
          val command: String = s"ssh $ip sh bootstrap-clasp.sh $ip"
          info(s"Starting $ip using $command")
          val exit = command.!
          if (exit != 0)
            error(s"There was some error starting $ip")
        })

      // Remove bootstrapper
      val file2: File = new File("~/bootstrap-clasp.sh")
      file2.delete()

    } catch {
      case e: IOException => {
        error("Unable to write bootstrapper, aborting")
        e.printStackTrace
        return
      }
    }
  }

  var emulators: Int = 0
  def monitoring: Receive = {
    case "emulator_up" => {
      info(s"Received hello from ${sender.path}!")
      emulators += 1
      info(s"${emulators} emulators awake")
    }
    case "node_up" => { 
      info(s"${nodes.length}: Node ${sender.path} has registered!")
      nodes += sender

      if (nodes.length == 2) {
        info("All nodes are awake and registered")
        info("In 1 minute, I'm going to shutdown the server")
        info("The delay should allow emulators to start")

        val launcher = context.system.actorFor("akka://clasp@10.0.2.6:2552/user/nodelauncher")
        import context.dispatcher
        context.system.scheduler.scheduleOnce(60 seconds) {
          launcher ! "shutdown" 
        }
      }
    }
    case "node_down" => {
      nodes -= sender
      info(s"${nodes.length}: Node ${sender.path} has deregistered!")
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
  val devices: MutableList[ActorRef] = MutableList[ActorRef]()
  var current_emulator_port = 5555
  val opts = new EmulatorOptions
  opts.noWindow = true
  context.actorOf(Props(new EmulatorActor(current_emulator_port, opts)),
    s"emulator-$current_emulator_port")
  current_emulator_port += 2

  override def preStart() = {
    info(s"Node online: ${self.path}")
    launcher ! "node_up"
  }

  override def postStop() = {
    info("Node " + self.path + " has stopped");

    devices.foreach(phone => phone ! PoisonPill)
    info("Requested emulators to halt")

    context.system.registerOnTermination {
      info("System shutdown achieved at " + System.currentTimeMillis) }
    info("Requesting system shutdown at " + System.currentTimeMillis)
    context.system.shutdown()
  }

  def receive = {
    case "register" => {
      info(s"Emulator online: ${sender.path}")
      devices += sender
    }
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
  val ip     = opt[String] (descr = "Informs Clasp of the IP address it should bind to." + 
    "If no explicit IP is provided, then 10.0.2.{hostname} will be used")
}

