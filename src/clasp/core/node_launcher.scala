
package clasp.core

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.TraversableOnce.flattenTraversableOnce
import scala.collection.mutable.ArrayStack
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.MutableList
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.future
import scala.language.postfixOps
import scala.sys.process.Process
import scala.sys.process.ProcessLogger
import scala.sys.process.stringToProcess
import scala.util.Random

import org.slf4j.LoggerFactory
import com.typesafe.config.ConfigFactory

import akka.actor.Actor
import akka.actor.ActorIdentity
import akka.actor.ActorInitializationException
import akka.actor.ActorKilledException
import akka.actor.ActorRef
import akka.actor.ActorSelection.toScala
import akka.actor.Identify
import akka.actor.OneForOneStrategy
import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.SupervisorStrategy.Escalate
import akka.actor.SupervisorStrategy.Stop
import akka.actor.Terminated
import akka.actor.actorRef2Scala
import clasp.ClaspConf
import clasp.core.sdktools.EmulatorOptions
import clasp.core.sdktools.sdk
import clasp.utils.ActorLifecycleLogging

// Main actor for managing the entire system
// Starts, tracks, and stops nodes

object NodeManager {
  // External commands 
  case class Shutdown(ifempty: Boolean = false)
  case class BootNode()
  case class NodeList()
  case class FindNodesForLaunch(count: Int = 1)
  
  // Responses from Node
  case class NodeUp(description: Node.NodeDescription)
  case class NodeStartError(nodeip: String, debuglog: String)
  
  // Internal usage
  case class NodeBootExpected(nodeip: String)
}
class NodeManager(val conf: ClaspConf) extends Actor with ActorLifecycleLogging {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{ error, debug, info, trace }
  import NodeManager._

  // Build pool of potential worker nodes
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
  conf.workers.foreach(_ => self ! BootNode())

  val nodes = ListBuffer[Node.NodeDescription]()
  var outstandingList = ListBuffer[String]()

  // Shut ourselves down if no Nodes start within 10 minutes
  // Deploy+compile can take some time
  context.system.scheduler.scheduleOnce(10.minutes, self, Shutdown(true))

  // Start in monitor mode.
  def receive = monitoring
  def monitoring: Receive = {
    case NodeUp(description) => {
      nodes += description
      info(s"${nodes.length}: Node ${sender.path} has registered!")
      outstandingList -= description.ip
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
        info(s"Requesting a new node to replace $nodeip")
        outstandingList -= nodeip

        self ! BootNode()
      } else
        debug(s"Ignoring boot timeout message for $nodeip, reply already received")
    }
    case _: NodeList => { sender ! nodes.toList }
    case _: BootNode => boot_node
    case Shutdown(ifempty: Boolean) => {
      debug(s"Received Shutdown request, with force=${!ifempty}")
      if (nodes.isEmpty && outstandingList.isEmpty) {
        // Terminate if there are no nodes or outstanding requests 
        info("No nodes active, terminating")
        self ! PoisonPill
        sender ! true
      } else if (!ifempty) {
        // Otherwise, reap the nodes

        // 1. Register to watch all nodes.
        nodes.foreach(node => context.watch(node.actor))
        info("Nodes active, forcing shutdown")

        // 2. Transition our receive loop into a Reaper
        context.become(reaper)
        info("Transitioned to a reaper")

        // 3. Ask all of our nodes to stop.
        nodes.foreach(n => n.actor ! PoisonPill)
        info("Pill sent to all nodes")
        sender ! true
      } else {
        debug("Nodes active, ignoring shutdown")
        sender ! false
      }
    }
    case unknown => error(s"Received unknown message from ${sender.path}: $unknown")
  }

  def reaper: Receive = {
    case Terminated(ref) => {
      var target: Node.NodeDescription = null
      nodes.foreach(n => if (n.actor == ref) target = n)
      if (target != null) {
        nodes -= target
        info(s"Reaper: Termination received for ${ref.path}")
        info(s"Reaper: ${nodes.length} nodes and ${outstandingList.length} outstanding")
        terminateIfReaped
      }
    }
    case NodeUp(description) => {
      outstandingList -= description.ip
      info(s"Reaper: Node registered at ${sender.path}")
      nodes += description
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
    context.system.shutdown
    super.postStop
  }

  def boot_node = {
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
        val copyLogger = ProcessLogger ( line => info(s"deploy:${client_ip}:out: $line"), 
          		line => error(s"deploy:${client_ip}:err: $line") )
        val copyProc = Process(copy).run(copyLogger)
        copyProc.exitValue

        // val build = s"ssh -oStrictHostKeyChecking=no $client_ip sh -c 'cd $workspaceDir && sbt -Dsbt.log.noformat=true clean && sbt -Dsbt.log.noformat=true stage'"
        val build = s"ssh -oStrictHostKeyChecking=no $client_ip sh -c 'cd $workspaceDir && sbt -Dsbt.log.noformat=true stage'"
        info(s"Building using $build")
        val buildLogger = ProcessLogger ( line => info(s"build:${client_ip}:out: $line"), 
          		line => error(s"build:${client_ip}:err: $line") )
        val buildProc = Process(build).run(buildLogger)
        buildProc.exitValue
          		
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
}

// Used to pass a bunch of static node information to each EmulatorActor
case class NodeDetails(nodeip: String, ostype: String, masterip: String, node: ActorRef)

// Manages the running of the framework on a single node
object Node {
  case class LaunchEmulator(count: Int = 1)
  case object Shutdown
  case class NodeDescription(val ip: String, val actor: ActorRef, val onlineEmulators: Int, val asOf: Long = System.currentTimeMillis)
}
// TODO push updated NodeDescriptions to NodeManager whenever our internal state changes
// TODO combine NodeDescription and NodeDetails
class Node(val ip: String, val serverip: String, val numEmulators: Int)
  extends Actor with ActorLifecycleLogging {
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
    
    super.postStop
  }

  // Wait until we are connected to the nodemanager with an ActorRef
  def receive = {
    case ActorIdentity(`managerId`, Some(manager)) =>
      debug(s"Found ActorRefFor NodeManger of ${manager}")

      // We need to kill ourself if the manager dies
      context.watch(manager)

      info(s"Node online: ${self.path}")
      manager ! NodeManager.NodeUp(Node.NodeDescription(ip, self, 0))
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
      info(s"Emulator launch requested by $sender")
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
    debug(s"Booting new emulator with ID $current_emulator_ID")
    // TODO allow this to be passed in
    val emuOpts = new EmulatorOptions
    val result = context.actorOf(
      Props(new EmulatorActor(current_emulator_ID, emuOpts, me)),
      s"emulator-${5554 + 2 * current_emulator_ID}")
    current_emulator_ID = current_emulator_ID + 1
    result
  }

  def get_os_type(): String = {
    val name = System.getProperty("os.name").toLowerCase()
    if (name.contains("darwin") || name.contains("mac")) { return "mac" }
    else if (name.contains("nux")) { return "linux" }
    else if (name.contains("win")) { return "windows" }
    else { return "unknown" }
  }
}


