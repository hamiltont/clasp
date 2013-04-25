
package clasp.core

import scala.collection.mutable.ListBuffer
import scala.collection.mutable.MutableList

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
class NodeManger(val clients: Seq[String]) extends Actor {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}
  val nodes = ListBuffer[ActorRef]()
  // TODO start all clients via SSH
  start_clients(clients)

  def start_clients(clients:Seq[String]):Unit = {
    // Locate our working directory
    //val directory: String = "/home/hamiltont/clasp" //"pwd" !!
    //val directory: String = "/home/brandon/programs/clasp"
    val directory: String = "pwd" !!;

    try {
      // Write a script in our home directory.
      // We assume all clients share the home directory.
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

      // Start each client.
      clients.foreach(ip => {
          val command: String = s"ssh $ip sh bootstrap-clasp.sh $ip"
          info(s"Starting $ip using $command.")
          val exit = command.!
          if (exit != 0)
            error(s"There was some error starting $ip")
        })

      // Remove bootstrappea.r
      val file2: File = new File("~/bootstrap-clasp.sh")
      file2.delete()

    } catch {
      case e: IOException => {
        error("Unable to write bootstrapper, aborting.")
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
      info(s"${emulators} emulators awake.")
    }
    case "node_up" => { 
      info(s"${nodes.length}: Node ${sender.path} has registered!")
      nodes += sender

      if (nodes.length == clients.length) {
        info("All nodes are awake and registered.")
        /*
        info("In 1 minute, I'm going to shutdown the server")
        info("The delay should allow emulators to start")

        val launcher = context.system.actorFor("akka://clasp@10.0.2.6:2552/user/nodelauncher")
        import context.dispatcher
        context.system.scheduler.scheduleOnce(60 seconds) {
          launcher ! "shutdown" 
        }
        */
      }
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
    }
    case "get_devices" => {
      var devices: MutableList[ActorRef] = MutableList[ActorRef]()
      for (node <- nodes) {
        val f = ask(node, "get_devices", 60000).mapTo[MutableList[ActorRef]]
        devices ++= Await.result(f, 100 seconds)
      }
      sender ! devices
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
 
// Manages the running of the framework on a single node,
// including ?startup?, shutdown, etc.
class Node(val ip: String, val serverip: String,
    val emuOpts: EmulatorOptions) extends Actor {
  val log = LoggerFactory.getLogger(getClass())
  val launcher = context.actorFor("akka://clasp@" + serverip + ":2552/user/nodelauncher")
  import log.{error, debug, info, trace}
  import clasp.core.sdktools.EmulatorOptions
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
    launcher ! "node_up"
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
    case "register" => {
      info(s"Emulator online: ${sender.path}")
      devices += sender
    }
    case "get_devices" => {
      sender ! devices
    }
  }
}
