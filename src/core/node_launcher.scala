
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

// Main actor for managing the entire system
// Starts, tracks, and stops nodes
class NodeManager(val ip: String, val initial_workers: Int,
  manual_pool: Option[String] = None, val numEmulators: Int,
  val local: Boolean = false) extends Actor {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{ error, debug, info, trace }

  var pool: ArrayStack[String] = null
  manual_pool match {
    case Some(mpool) => {
      pool = (new ArrayStack) union mpool.split(',')
    }
    case None => {
      // Build a pool of worker IP addresses
      val config = ConfigFactory.load("client")
      val pool_list = config.getStringList("clasp.workerpool")
      val it = pool_list.iterator
      pool = Random.shuffle((new ArrayStack) union pool_list.asScala)
    }
  }

  for (i <- 1 to initial_workers) yield (self ! BootNode)

  val nodes = ListBuffer[ActorRef]()
  // Tracks how many nodes have been triggered but have not yet replied
  var outstanding: AtomicInteger = new AtomicInteger

  def monitoring: Receive = {
    case NodeUp => {
      nodes += sender
      info(s"${nodes.length}: Node ${sender.path} has registered!")
      outstanding.decrementAndGet

      if (outstanding.get == 0)
        info("All nodes requested (to this point) are awake and registered")
    }
    case NodeBusy(nodeip, nodelog) => {
      info(s"Node $nodeip has declared itself busy")
      info(s"Debug log for node $nodeip:\n$nodelog")
      info("Requesting a new node to replace $nodeip")
      outstanding.decrementAndGet
      boot_any
    }
    case BootNode => boot_any
    case Shutdown => {
      if (nodes.size == 0) {
        // Terminate if there are no nodes.
        info("Killing self.")
        self ! PoisonPill
      } else {
        // Otherwise, reap the nodes.

        // 1. Register to watch all nodes.
        nodes.foreach(node => context.watch(node))
        info("Shutdown requested.")

        // 2. Transition our receive loop into a Reaper
        context.become(reaper)
        info("Transitioned to a reaper.")

        // 3. Ask all of our nodes to stop.
        nodes.foreach(n => n ! PoisonPill)
        info("Pill sent to all nodes.")
      }
    }
    case _ => error("Received unknown message!")
  }

  def reaper: Receive = {

    case Terminated(ref) => {
      nodes -= ref
      info(s"Reaper: Termination received for ${ref.path}")
      info(s"Reaper: ${nodes.length} nodes and ${outstanding.get} outstanding")
      if (nodes.isEmpty && outstanding.get == 0) {
        info("No more remote nodes or outstanding boot requests, killing self.")
        self ! PoisonPill
      }
    }
    case NodeUp => {
      outstanding.decrementAndGet
      info(s"Reaper: Node registered at ${sender.path}")
      nodes += sender
      info(s"Reaper: Replying with a PoisonPill")
      info(s"Reaper: ${nodes.length} nodes and ${outstanding.get} outstanding")
      context.watch(sender)
      sender ! PoisonPill
    }
    case BootNode => info("Reaper: Ignoring boot request")
    case NodeBusy(id, log) => {
      outstanding.decrementAndGet
      info(s"Reaper: Ignoring busy node $id")
      info(s"Reaper: ${nodes.length} nodes and ${outstanding.get} outstanding")
    }
    case Shutdown => info("Reaper: Ignoring shutdown")
  }

  override def postStop = {
    context.system.registerOnTermination {
      info("System shutdown achieved at " + System.currentTimeMillis)
    }
    info("postStop. Requesting system shutdown at " + System.currentTimeMillis)
    context.system.shutdown()
  }

  def boot_any = {
    info("Node boot requested")
    if (pool.length != 0)
      bootstrap(pool.pop)
    else
      error("Node boot request failed - worker pool is empty")
  }

  def bootstrap(client_ip: String): Unit = {
    import ExecutionContext.Implicits.global
    val f = future {
      val directory: String = "pwd".!!.stripLineEnd
      val username = "whoami".!!.stripLineEnd
      val workspaceDir = s"/tmp/clasp/$username"
      val export = if (local) ":0" else "localhost:10.0"
      val localFlag = if (local) "--local" else ""
      val command: String = s"ssh -oStrictHostKeyChecking=no $client_ip " +
        s"sh -c 'export DISPLAY=$export; " +
        s"cd $directory; " +
        s"mkdir -p $workspaceDir ; " +
        s"nohup target/start --client $localFlag --ip $client_ip --mip $ip " +
        s"--num-emulators $numEmulators " +
        s"> /tmp/clasp/$username/nohup.$client_ip 2>&1 &' "
      info(s"Starting $client_ip using $command")
      command.!!
      outstanding.incrementAndGet
    }
  }

  // Start in monitor mode.
  def receive = monitoring
}
sealed trait NM_Message
case class Shutdown() extends NM_Message
case class NodeUp() extends NM_Message
case class BootNode() extends NM_Message
case class NodeBusy(nodeid: String, debuglog: String) extends NM_Message

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

  // TODO: Better ways to ensure devices appear online?
  sdk.kill_adb
  sdk.start_adb

  private def bootEmulator(i: Int): ActorRef =
    context.actorOf(Props(new EmulatorActor(
      base_emulator_port + 2 * i, emuOpts, serverip, user, self)),
      s"emulator-${base_emulator_port + 2 * i}")

  var i = 0
  while (i < numEmulators ) {
    devices += bootEmulator(i);
    i += 1;
  }

  override def preStart() = {
    info(s"Node online: ${self.path}")
    manager ! NodeUp
  }

  override def postStop() = {
    info(s"Node ${self.path} has stopped");

    devices.foreach(phone => phone ! PoisonPill)
    info("Requested emulators to halt")

    context.system.registerOnTermination {
      info(s"System shutdown achieved at ${System.currentTimeMillis}")
    }
    info(s"Requesting system shutdown at ${System.currentTimeMillis}")
    context.system.shutdown()
  }

  def receive = {
    case BootEmulator => devices += bootEmulator(i); i += 1
    case _ => info("Node received a message, but it doesn't do anything!")
  }
}

case class BootEmulator()
