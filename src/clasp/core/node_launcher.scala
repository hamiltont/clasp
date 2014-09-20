
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
import clasp.ClaspConf
import akka.actor.SupervisorStrategy._
import akka.remote.RemotingLifecycleEvent
import akka.remote.DisassociatedEvent
import akka.remote.RemotingShutdownEvent
import akka.remote.RemotingShutdownEvent
import akka.remote.ShutDownAssociation
import akka.remote.transport.AssociationHandle.Disassociated
import akka.dispatch.Foreach

// Main actor for managing the entire system
// Starts, tracks, and stops nodes
class NodeManager(val conf: ClaspConf) extends Actor {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{ error, debug, info, trace }

  //  val ip: String,
  //  val initial_workers: Int,
  //  val numEmulators: Int,
  //  val local: Boolean = false

  var pool: ArrayStack[String] = null
  conf.pool.get match {
    case Some(mpool) => {
      debug(s"Worker pool override: $mpool")
      pool = (new ArrayStack) union mpool.split(',')
    }
    case None => {
      // Build a pool of worker IP addresses
      val config = ConfigFactory.load("master")

      val pool_list = config.getStringList(conf.poolKey())
      val it = pool_list.iterator
      pool = Random.shuffle((new ArrayStack) union pool_list.asScala)
      debug("Worker pool from config file: " + pool_list)
    }
  }

  for (i <- 1 to conf.workers()) yield (self ! BootNode)

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
      info(s"Requesting a new node to replace $nodeip")

      self ! BootNode()
    }
    case NodeBootExpected(nodeip) => {
      if (outstandingList.contains(nodeip)) {
        info(s"Node $nodeip appears to have failed to boot")
        info("Requesting a new node to replace $nodeip")
        outstandingList -= nodeip

        self ! BootNode()
      } else
        debug(s"Ignoring boot timeout message for $nodeip, reply already received")
    }
    case BootNode => boot_any
    case Shutdown(ifempty: Boolean) => {
      debug(s"Received Shutdown request, with force=${!ifempty}")
      if (nodes.isEmpty && outstandingList.isEmpty) {
        // Terminate if there are no nodes or outstanding requests 
        info("No nodes active, terminating")
        self ! PoisonPill
      } else if (!ifempty) {
        // Otherwise, reap the nodes

        // 1. Register to watch all nodes.
        nodes.foreach(node => context.watch(node))
        info("Nodes active, forcing shutdown")

        // 2. Transition our receive loop into a Reaper
        context.become(reaper)
        info("Transitioned to a reaper")

        // 3. Ask all of our nodes to stop.
        nodes.foreach(n => n ! PoisonPill)
        info("Pill sent to all nodes")
      } else {
        debug("Nodes active, ignoring shutdown")
      }
    }
    case unknown => error(s"Received unknown message from ${sender.path}: $unknown")
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
    case unknown => error(s"Reaper: Ignoring unknown message from ${sender.path}: $unknown")
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

        val mkdir = s"ssh -oStrictHostKeyChecking=no $client_ip sh -c 'mkdir -p $workspaceDir'"
        mkdir.!!

        val copy = s"rsync --verbose --archive --exclude='.git/' --exclude='*.class' . $client_ip:$workspaceDir"
        info(s"Deploying using $copy")
        copy.!!

        val build = s"ssh -oStrictHostKeyChecking=no $client_ip sh -c 'cd $workspaceDir && sbt clean && sbt stage'"
        info(s"Building using $build")
        val buildtxt = build.!!
        // debug(s"Build Output: $buildtxt")

        // TODO use ssh port forwarding to punch connections in any NAT and 
        // ensure connectivity between master and client. PS - nastyyyyy

        val localFlag = if (conf.local()) "--local" else ""
        val command: String = s"ssh -oStrictHostKeyChecking=no $client_ip " +
          s"sh -c 'cd $workspaceDir; " +
          s"nohup target/start --client $localFlag --ip $client_ip --mip ${conf.ip()} " +
          s"--num-emulators ${conf.numEmulators()} " +
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

// Used to pass a bunch of static node information to each EmulatorActor
case class NodeDetails(nodeip: String, ostype: String, masterip: String, node: ActorRef)

// Manages the running of the framework on a single node
object Node {
  case class LaunchEmulator(count: Int = 1) extends NM_Message
  case class Shutdown
}
class Node(val ip: String, val serverip: String,
  val emuOpts: EmulatorOptions, val numEmulators: Int)
  extends Actor {
  val log = LoggerFactory.getLogger(getClass())
  import log.{ error, debug, info, trace }

  val managerId = "manager"
  context.actorSelection(s"akka.tcp://clasp@$serverip:2552/user/nodemanager") ! Identify(managerId)

  val devices: MutableList[ActorRef] = MutableList[ActorRef]()
  var current_emulator_ID = 0

  // Restart ADB with the node
  sdk.kill_adb
  sdk.start_adb

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 2, withinTimeRange = 3.minutes) {
      case _: ActorInitializationException => {
        // TODO if we are starting up and the failed EmulatorActor was our only
        // child, then we need to terminate
        debug(s"The EmulatorActor failed to initialize")
        Stop
      }
      case _: ActorKilledException => Stop
      case _: Exception => Escalate
    }

  override def postStop() = {
    info(s"NodeActor has stopped ($self)");
    info("Stopping ActorSystem on this node");
   
    info(s"Requesting system shutdown at ${System.currentTimeMillis}")
    context.system.registerOnTermination {
      info(s"System shutdown achieved at ${System.currentTimeMillis}")
    }

    context.system.shutdown
  }

  // Wait until we are connected to the nodemanager with an ActorRef
  def receive = {
    case ActorIdentity(`managerId`, Some(manager)) =>
      debug(s"Found ActorRefFor NodeManger of ${manager}")

      // We need to kill ourself if the manager dies
      context.watch(manager)

      info(s"Node online: ${self.path}")
      manager ! NodeUp(ip)
      context.become(active(manager))

      // Launch initial emulators
      self ! Node.LaunchEmulator(numEmulators)
    case ActorIdentity(`managerId`, None) => {
      debug(s"No Identity Received For NodeManger, terminating")
      context.system.shutdown
    }
  }

  def active(manager: ActorRef): Actor.Receive = {
    case Terminated(`manager`) => {
      info(s"NodeManager has terminated (${sender})")
      info("Terminating ourself in response")
      context.become(reaper)
      self ! Node.Shutdown
    }
    case Node.LaunchEmulator(count) => {
      for (_ <- 1 to count)
        devices += bootEmulator()
    }
    case unknown => info(s"Received unknown message from ${sender.path}: $unknown")
  }
  
  def reaper(): Actor.Receive = {
    case Node.Shutdown => {
      info("Stopping all emulators")
      context.children.foreach(context.stop)
    }
    case Terminated(child) => {
      info(s"Node Reaper: Termination from $child")
      if (context.children.isEmpty)
        context.stop(self)
    }
  }


  private def bootEmulator(): ActorRef = {
    val me = NodeDetails(ip, get_os_type, serverip, self)
    current_emulator_ID = current_emulator_ID + 1
    debug(s"Booting new emulator with ID $current_emulator_ID")
    context.actorOf(
      Props(new EmulatorActor(current_emulator_ID, emuOpts, me)),
      s"emulator-${5555 + 2 * current_emulator_ID}")
  }

  def get_os_type(): String = {
    val name = System.getProperty("os.name").toLowerCase()
    if (name.contains("darwin") || name.contains("mac")) { return "mac" }
    else if (name.contains("nux")) { return "linux" }
    else if (name.contains("win")) { return "windows" }
    else { return "unknown" }
  }
}


