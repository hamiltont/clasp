
package clasp.core

import scala.collection.mutable.ListBuffer
import scala.collection.mutable.MutableList
import scala.collection.mutable.ArrayStack
import scala.collection.JavaConverters._
import scala.util.Random
import scala.collection.immutable.StringOps
import org.slf4j.LoggerFactory
import clasp.core.sdktools.sdk
import clasp.core.sdktools.EmulatorOptions
import akka.actor._
import akka.pattern.Patterns.ask
import scala.sys.process._
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import com.typesafe.config.ConfigFactory
import scala.concurrent._
import scala.language.postfixOps
import scala.util.Random
import System.currentTimeMillis
import scala.concurrent.duration._
import ExecutionContext.Implicits.global
import akka.remote.RemoteClientLifeCycleEvent
import akka.remote.RemoteClientShutdown
import akka.remote.RemoteClientConnected

// Main actor for managing the entire system
// Starts, tracks, and stops nodes
class NodeManager(val ip: String,
  val initial_workers: Int,
  manual_pool: Option[String] = None,
  val numEmulators: Int,
  val local: Boolean = false) extends Actor {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{ error, debug, info, trace }

  var pool: ArrayStack[String] = null
  manual_pool match {
    case Some(mpool) => {
      pool = (new ArrayStack) union mpool.split(',')
      debug("Worker pool override: " + mpool)
    }
    case None => {
      // Build a pool of worker IP addresses
      val config = ConfigFactory.load("client")
      val pool_list = config.getStringList("clasp.workerpool")
      val it = pool_list.iterator
      pool = Random.shuffle((new ArrayStack) union pool_list.asScala)
      debug("Worker pool from config file: " + pool_list)
    }
  }

  for (i <- 1 to initial_workers) yield (self ! BootNode)

  val nodes = ListBuffer[ActorRef]()

  var outstandingList = ListBuffer[String]()
  
  // Shut ourselves down if no Nodes start
  context.system.scheduler.scheduleOnce(120 seconds, self, Shutdown(true))

  def monitoring: Receive = {
    case NodeUp(nodeip) => {
      nodes += sender
      info(s"${nodes.length}: Node ${sender.path} has registered!")
      outstandingList -= nodeip
      if (outstandingList.isEmpty)
        info("All nodes requested to this point are awake and registered")
    }
    case NodeStartError(nodeip, nodelog) => {
      outstandingList -= nodeip
      info(s"Node $nodeip (${sender.path}) has declared it had an error starting")
      info(s"Debug log for node $nodeip:\n$nodelog")
      info("Requesting a new node to replace $nodeip")

      boot_any
    }
    case NodeBootExpected(nodeip) => {
      if (outstandingList.contains(nodeip)) {
        info(s"Node $nodeip appears to have failed to boot")
        info("Requesting a new node to replace $nodeip")
        outstandingList -= nodeip

        boot_any
      } else
        debug(s"Ignoring boot timeout message for $nodeip, reply already received")
    }
    case BootNode => boot_any
    case Shutdown(ifempty: Boolean) => {
      if (nodes.isEmpty && outstandingList.isEmpty) {
        // Terminate if there are no nodes or outstanding requests 
        info("Shutdown requested. No nodes active, terminating")
        self ! PoisonPill
      } else if (!ifempty) {
        // Otherwise, reap the nodes

        // 1. Register to watch all nodes.
        nodes.foreach(node => context.watch(node))
        info("Shutdown requested")

        // 2. Transition our receive loop into a Reaper
        context.become(reaper)
        info("Transitioned to a reaper")

        // 3. Ask all of our nodes to stop.
        nodes.foreach(n => n ! PoisonPill)
        info("Pill sent to all nodes")
      } else {
        debug("Request : 'If not empty, then shutdown'")
        debug("Response: 'Not empty, ignoring'")
      }
    }
    case _ => error("Received unknown message!")
  }

  def reaper: Receive = {
    case Terminated(ref) => {
      nodes -= ref
      info(s"Reaper: Termination received for ${ref.path}")
      info(s"Reaper: ${nodes.length} nodes and ${outstandingList.length} outstanding")
      terminateIfReaped
    }
    case NodeUp(nodeip) => {
      outstandingList -= nodeip
      info(s"Reaper: Node registered at ${sender.path}")
      nodes += sender
      info(s"Reaper: Replying with a PoisonPill")
      info(s"Reaper: ${nodes.length} nodes and ${outstandingList.length} outstanding")
      context.watch(sender)
      sender ! PoisonPill
    }
    case NodeStartError(ip, log) => {
      outstandingList -= ip
      info(s"Reaper: Ignoring busy node $ip")
      info(s"Reaper: ${nodes.length} nodes and ${outstandingList.length} outstanding")
      terminateIfReaped
    }
    case NodeBootExpected(ip) => {
      outstandingList -= ip
      info(s"Reaper: Received boot timeout check message for $ip")
      info(s"Reaper: ${nodes.length} nodes and ${outstandingList.length} outstanding")
      terminateIfReaped
    }
    // TODO log message type
    case _ => info("Reaper: Ignoring message")
  }

  def terminateIfReaped() = {
    if (nodes.isEmpty && outstandingList.isEmpty) {
      info("No more remote nodes or outstanding boot requests, terminating")
      self ! PoisonPill
    }
  }

  override def postStop = {
    info("NodeManager halted. Triggering full ActorSystem shutdown")
    context.system.shutdown()
  }

  def boot_any = {
    info(s"Node boot requested (by ${sender.path})")
    if (pool.length == 0)
      error("Node boot request failed - worker pool is empty")
    else {
      val client_ip = pool.pop

      val f = future {
        val directory: String = "pwd".!!.stripLineEnd
        val username = "whoami".!!.stripLineEnd
        val workspaceDir = s"/tmp/clasp/$username"

        val copy = s"rsync --archive --exclude='.git/' . localhost:$workspaceDir"
        info(s"Deploying using $copy")
        copy.!!

        val build = s"ssh -oStrictHostKeyChecking=no $client_ip sh -c 'cd $workspaceDir && sbt clean && sbt stage'"
        info(s"Building using $build")
        val buildtxt = build.!!
        // debug(s"Build Output: $buildtxt")

        val export = if (local) ":0" else "localhost:10.0"
        val localFlag = if (local) "--local" else ""
        val command: String = s"ssh -oStrictHostKeyChecking=no $client_ip " +
          s"sh -c 'cd $workspaceDir; " +
          s"nohup target/start --client $localFlag --ip $client_ip --mip $ip " +
          s"--num-emulators $numEmulators " +
          s"> /tmp/clasp/$username/nohup.$client_ip 2>&1 &' "
        info(s"Starting $client_ip using $command")
        command.!!
        outstandingList += client_ip

        // Check that we've heard back
        context.system.scheduler.scheduleOnce(30 seconds, self, NodeBootExpected(client_ip))
      }
    }
  }

  // Start in monitor mode.
  def receive = monitoring
}
sealed trait NM_Message
case class Shutdown(ifempty: Boolean = false) extends NM_Message
case class NodeUp(nodeip: String) extends NM_Message
case class BootNode() extends NM_Message
case class NodeStartError(nodeip: String, debuglog: String) extends NM_Message
case class NodeBootExpected(nodeip: String) extends NM_Message

// Manages the running of the framework on a single node,
// including ?startup?, shutdown, etc.
class Node(val ip: String, val serverip: String,
  val emuOpts: EmulatorOptions, val numEmulators: Int, val user: String)
  extends Actor {
  val log = LoggerFactory.getLogger(getClass())
  import log.{ error, debug, info, trace }

  val manager = context.actorFor(
    s"akka://$user@$serverip:2552/user/nodemanager")
  val devices: MutableList[ActorRef] = MutableList[ActorRef]()
  var base_emulator_port = 5555
  context.system.eventStream.subscribe(self, classOf[RemoteClientLifeCycleEvent])

  // TODO: Better ways to ensure devices appear online?
  sdk.kill_adb
  sdk.start_adb

  private def bootEmulator(i: Int): ActorRef =
    context.actorOf(Props(new EmulatorActor(
      base_emulator_port + 2 * i, emuOpts, serverip, user, self)),
      s"emulator-${base_emulator_port + 2 * i}")

  var i = 0
  while (i < numEmulators) {
    devices += bootEmulator(i);
    i += 1;
  }

  override def preStart() = {
    info(s"Node online: ${self.path}")
    manager ! NodeUp(ip)
  }

  override def postStop() = {
    info(s"Node ${self.path} has stopped");

    devices.foreach(phone => phone ! PoisonPill)
    info("Requested emulators to halt")

    context.system.registerOnTermination {
      info(s"System shutdown achieved at ${System.currentTimeMillis}")
    }
    info(s"Requesting system shutdown at ${System.currentTimeMillis}")
    context.system.shutdown
  }

  def receive = {
    case BootEmulator =>
      devices += bootEmulator(i); i += 1
	case _: RemoteClientShutdown    => {
	  info("The master appears to have terminated")
	  info("Terminating ourself in response")
	  context.system.shutdown
	}
    case _ => info("Node received a message, but it doesn't do anything!")
  }
}

case class BootEmulator()
