
package clasp.core

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.TraversableOnce.flattenTraversableOnce
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.MutableList
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.future
import scala.language.postfixOps
import scala.sys.process.Process
import scala.sys.process.ProcessLogger
import scala.sys.process.stringToProcess
import scala.util.Try
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
import clasp.core.remoting.ChannelServer
import clasp.core.remoting.ClaspJson
import clasp.core.remoting.ClaspJson._
import clasp.core.remoting.WebSocketChannelManager._
import clasp.core.remoting.WebSocketChannelManager
import clasp.core.sdktools.EmulatorOptions
import clasp.core.sdktools.sdk
import clasp.utils.ActorLifecycleLogging
import clasp.utils.ActorStack
import clasp.utils.Slf4jLoggingStack
import spray.json._
import java.util.UUID
import java.util.Calendar
import java.util.Date
import akka.actor.ActorLogging
import akka.actor.ActorIdentity
import org.hyperic.sigar.Sigar

// Main actor for managing the entire system
// Starts, tracks, and stops nodes

object NodeManager {
  // External commands 
  case class Shutdown(ifempty: Boolean = false)
  case class BootNode()
  case class NodeList(onlyOnline: Boolean = true)
  case class FindNodesForLaunch(count: Int = 1)

  // Responses from Node
  case class NodeUpdate(update: Node.NodeDescription)

  // Internal usage
  case class NodeBootExpected(node: Node.NodeDescription)
}
class NodeManager(val conf: ClaspConf)
  extends Actor
  with ActorLifecycleLogging
  with ActorStack
  with Slf4jLoggingStack
  with ChannelServer {

  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{ error, debug, info, trace }
  import NodeManager._

  // Build pool of potential worker nodes
  val nodes: ListBuffer[Node.NodeDescription] = conf.pool.get match {
    case Some(mpool) =>
      mpool.split(',').map(ip => Node.NodeDescription(ip, uuid = UUID.randomUUID)).to[ListBuffer]
    case None =>
      ConfigFactory.load("master").getStringList(conf.poolKey()).asScala
        .map(ip => Node.NodeDescription(ip, uuid = UUID.randomUUID)).to[ListBuffer]
  }
  nodes.foreach(_ => self ! BootNode())
  def nodesOffline = nodes.filter(n => (n.status == Node.Status.Offline || n.status == Node.Status.Failed))
  def nodesOnline = nodes.filter(n => n.status == Node.Status.Online)
  def nodesBooting = nodes.filter(n => n.status == Node.Status.Booting)
  def nodeByIp(ip: String) = nodes.filter(n => n.ip == ip).headOption
  def nodeByActorRef(actor: ActorRef) = nodes.filter(
    n => {
      debug(s"Checking if ${n.actor} is equal to ${actor}")
      debug(s"Full node is $n")
      n.actor == actor
    }).headOption

  def nodeUpdate(update: Node.NodeDescription) =
    nodeByIp(update.ip) match {
      case Some(old) => {
        if (old.asOf.before(update.asOf)) {
          debug(s"Node updating to $update from $old")
          nodes -= old
          nodes += update
          channelSend(channelNodeUpdates, update)
        } else error(s"Rejecting $update because $old is newer")
      }
      case None => {
        debug(s"Node update to $update")
        nodes += update
        channelSend(channelNodeUpdates, update)
      }
    }

  // Channels that we manager
  val channelNodeUpdates = "/nodemanager/nodeupdates"
  val channelResourceUsage = "/nodemanager/resources"

  // Shut ourselves down if no Nodes start within 10 minutes
  // Deploy+compile can take some time
  // context.system.scheduler.scheduleOnce(10.minutes, self, Shutdown(true))

  // Start in monitor mode
  def wrappedReceive = monitoring

  // For dynamic websocket-based messaging to web clients
  implicit var channelManager: Option[ActorRef] = None

  def monitoring: Receive = {
    case ActorIdentity(WebSocketChannelManager, Some(manager)) =>
      {
        channelManager = Some(manager)
        channelManager.foreach { x =>
          x ! RegisterChannel(channelNodeUpdates, self)
          x ! RegisterChannel(channelResourceUsage, self)
        }
      }
    case NodeUpdate(update) => nodeUpdate(update)
    case NodeBootExpected(node) => (nodesOnline.filter(n => n.ip == node.ip).isEmpty) match {
      case true => nodeUpdate(node.copy(status = Node.Status.Failed).stamp)
      case false => debug(s"Ignoring boot timeout message for $node, reply already received")
    }
    case NodeList(onlyOnline) => if (onlyOnline) sender ! nodesOnline.toList else sender ! nodes.toList
    case _: BootNode => sender ! boot_node
    case Shutdown(ifempty) => {
      debug(s"Received Shutdown request, with force=${!ifempty}")

      // Force shutdown detected
      if (!ifempty || (ifempty && nodesOnline.isEmpty)) {
        // TODO ensure reaper sends PoisonPill to any unexpected node arrivals
        info("Forcing shutdown")

        // 1. Register to watch all nodes.
        nodes.filter(n => n.actor.isDefined).foreach(n => context.watch(n.actor.get))

        // 2. Transition our receive loop into a Reaper
        context.become(wrapReceive(reaper))
        info("Transitioned to a reaper")

        // 3. Ask all of our nodes to stop.
        nodes.filter(n => n.actor.isDefined).foreach(n => n.actor.get ! PoisonPill)
        info("Pill sent to all nodes")

        sender ! true
      } else if (ifempty && !nodesOnline.isEmpty) {
        debug("Nodes active, ignoring shutdown")
        sender ! false
      } else
        error(s"Unexpected state reached with $ifempty and $nodes")
    }
    case FindNodesForLaunch(count) => {
      debug(s"Looking for nodes to boot $count emulators")
      if (nodesOnline.isEmpty) {
        debug(s"No nodes available")
        sender ! Map()
      } else {
        // TODO deal with multiple people requesting nodes before
        // they have had time to boot emulators and then update their 
        // descriptions with the master
        var result = collection.mutable.Map[Node.NodeDescription, Int]()
        nodes.foreach(n => result += (n -> n.onlineEmulators))
        val roundRobin = Iterator.continually(nodes).flatten
        var remaining = count
        def spaceLeft = result.exists(nodeMapping => nodeMapping._2 < 5)
        while (remaining > 0 && spaceLeft) {
          val next = roundRobin.next
          result(next) = result(next) + 1
          remaining -= 1
        }
        debug(s"Found nodes $result")
        sender ! result.toMap
      }
    }
    case unknown => error(s"Received unknown message from ${sender.path}: $unknown")
  }

  def reaper: Receive = {
    case Terminated(ref) =>
      nodeByActorRef(ref) match {
        case Some(node) => {
          info(s"Reaper: Termination received for $node")
          nodes -= node
        }
        case None => {
          error(s"Received termination message we could not decode from $sender - $ref")
        }
      }
    case NodeUpdate(update) => {
      nodeUpdate(update)
      info(s"Reaper: Replying with a PoisonPill")
      context.watch(sender)
      sender ! PoisonPill
    }
    case unknown => {
      error(s"Reaper: Ignoring message from ${sender.path}: $unknown")
      terminateIfReaped
    }
  }

  def terminateIfReaped() = {
    if (nodes.isEmpty) {
      info("Reaper: No more nodes, terminating self")
      self ! PoisonPill
    } else
      info(s"Reaper: remaining nodes $nodes")
  }

  override def postStop = {
    info("NodeManager halted. Triggering full ActorSystem shutdown")
    context.system.shutdown
    super.postStop
  }

  def boot_node(): Ack = {
    info(s"Node boot requested (by ${sender.path})")

    if (nodesOffline.isEmpty) {
      error("Node boot request failed, no more nodes to boot")
      throw new InstantiationException("No more nodes to launch")
    } else {
      val client = nodesOffline.head
      if (client.status == Node.Status.Failed)
        info(s"Warning: Attempting to boot node that previously failed - $client")
      val client_ip = client.ip
      nodeUpdate(client.copy(status = Node.Status.Booting).stamp)

      val deploy_and_boot = future {
        val directory: String = "pwd".!!.stripLineEnd
        val username = "whoami".!!.stripLineEnd
        val workspaceDir = s"/tmp/clasp/$username"

        val mkdir = s"ssh -oStrictHostKeyChecking=no $client_ip sh -c 'mkdir -p $workspaceDir'"
        if (mkdir.! != 0)
          throw new Exception("Connection to remote node failed, aborting boot")

        val copy = s"rsync --verbose --archive --exclude='.git/' --exclude='*.class' . $client_ip:$workspaceDir"
        info(s"Deploying using $copy")
        val shouldLogDeploy = false
        val copyLogger = ProcessLogger(line => if (shouldLogDeploy) info(s"deploy:${client_ip}:out: $line"),
          line => error(s"deploy:${client_ip}:err: $line"))
        val copyProc = Process(copy).run(copyLogger)
        copyProc.exitValue

        val cleanBuild = true
        val cleanBuildCmd = if (cleanBuild) "sbt -Dsbt.log.noformat=true clean && " else ""
        val build = s"ssh -oStrictHostKeyChecking=no $client_ip sh -c 'cd $workspaceDir && $cleanBuildCmd sbt -Dsbt.log.noformat=true stage'"
        info(s"Building using $build")
        val shouldLogBuild = true
        val buildLogger = ProcessLogger(line => if (shouldLogBuild) info(s"build:${client_ip}:out: $line"),
          line => error(s"build:${client_ip}:err: $line"))
        val buildProc = Process(build).run(buildLogger)
        val buildExit = buildProc.exitValue
        info(s"Build exit: $buildExit")
        if (buildExit != 0)
          throw new IllegalStateException("Build failed, cannot continue boot sequence")

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

        // Check that we've heard back
        context.system.scheduler.scheduleOnce(5.minutes, self, NodeBootExpected(client))
      }

      deploy_and_boot onFailure {
        case reason =>
          log.error(s"Node failed to boot : $reason")
          self ! NodeUpdate(client.copy(status = Node.Status.Failed).stamp)
      }

      Ack(true)
    }
  }
}

// Manages the running of the framework on a single node
object Node {
  case class LaunchEmulator(count: Int = 1)
  case object Shutdown
  object Status extends Enumeration {
    type Status = Value
    val Offline, Booting, Failed, Online = Value
  }
  import Status._
  case class NodeDescription(val ip: String,
    val actor: Option[ActorRef] = None,
    val status: Status = Offline,
    val onlineEmulators: Int = 0,
    val uuid: UUID,
    val asOf: Date = Calendar.getInstance.getTime) {

    def stamp = copy(asOf = Calendar.getInstance.getTime)
  }
}
// TODO push updated NodeDescriptions to NodeManager whenever our internal state changes
// TODO combine NodeDescription and NodeDetails
class Node(val ip: String, val masterip: String, val numEmulators: Int, val uuid: UUID)
  extends Actor
  with ActorLifecycleLogging
  with ActorStack
  with Slf4jLoggingStack 
  with ChannelServer {
  val log = LoggerFactory.getLogger(getClass())
  import log.{ error, debug, info, trace }

  val managerId = "manager"
  context.actorSelection(s"akka.tcp://clasp@$masterip:2552/user/nodemanager") ! Identify(managerId)
  
  implicit var channelManager: Option[ActorRef] = None
  val baseChannel = s"/node/$uuid"

  val devices: MutableList[ActorRef] = MutableList[ActorRef]()
  var current_emulator_ID = 0
  def description(status: Node.Status.Status = Node.Status.Online) = Node.NodeDescription(ip, Some(self), status, current_emulator_ID, uuid)

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
    info(s"I have stopped ($self)");
    info("Stopping ActorSystem on this node");

    info(s"Requesting system shutdown at ${System.currentTimeMillis}")
    context.system.registerOnTermination {
      info(s"System shutdown achieved at ${System.currentTimeMillis}")
    }

    context.system.shutdown

    super.postStop
  }

  // Wait until we are connected to the nodemanager with an ActorRef
  def wrappedReceive = {
    case ActorIdentity(`managerId`, Some(manager)) =>
      debug(s"Found NodeManger - ${manager}")

      // We need to kill ourself if the manager dies
      // TODO test that this is working as expected?
      context.watch(manager)

      info(s"Online at ${self.path}")
      manager ! NodeManager.NodeUpdate(description())
      context.become(wrapReceive(active(manager)))

      // Launch initial emulators
      self ! Node.LaunchEmulator(numEmulators)
      
      // Find channel server
      // identifyChannelMaster(masterip)
        context.actorSelection(s"akka.tcp://clasp@${masterip}:2552/user/channelManager") ! Identify(channelManagerId)
      debug(s"Requested channel manager identity from $masterip")
      
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
    case ActorIdentity(`channelManagerId`, Some(manager)) => {
      debug(s"Channel manager arrived! $manager")
      channelManager = Some(manager)
      channelRegister(baseChannel)
      context.actorOf(Props(new NodeResourceLogger(this, channelManager)), s"logger")
    }
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

    val me = description()

    debug(s"Booting new emulator with ID $current_emulator_ID")
    // TODO allow this to be passed in
    val emuOpts = new EmulatorOptions
    val result = context.actorOf(
      Props(new EmulatorActor(current_emulator_ID, emuOpts, this)),
      s"emulator-${5556 + 2 * current_emulator_ID}")
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


class NodeResourceLogger(val node: Node, 
    implicit val channelManager: Option[ActorRef]) 
	extends Actor 
	with ActorLogging
	with ActorLifecycleLogging 
	with ActorStack 
	with ChannelServer
	with Slf4jLoggingStack {
  
  val channelName = s"${node.baseChannel}/metrics"
  channelRegister(channelName)
  val sigar = new Sigar
  
  case class CheckCpu()
  context.system.scheduler.schedule(0.second, 15.second){ self ! CheckCpu() }
  
  def wrappedReceive = {
    case CheckCpu() => channelSend(channelName, sigar.getCpuPerc)
  }
  
}



