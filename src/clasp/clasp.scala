package clasp

import java.io.File
import scala.Array.canBuildFrom
import scala.collection.immutable.StringOps
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.concurrent.promise
import scala.sys.process.stringToProcess
import org.slf4j.LoggerFactory
import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.actorRef2Scala
import clasp.core.Node
import clasp.core.QueueEmulatorTask
import core.EmulatorManager
import core.NodeManager
import core.NodeStartError
import core.sdktools.EmulatorOptions
import core.sdktools.sdk
import scala.concurrent.ExecutionContext

import spray.routing.SimpleRoutingApp

/* Used to launch Clasp from the command line */
object ClaspRunner extends App {
  // If you would like logging on remote machines(all workers), then use these
  // logging methods in your codebase. The logs will (eventually) be pulled
  // back to the master, so that you don't need to manually aggregate your
  // logs
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{ error, debug, info, trace }

  // Used to parse the command line arguments
  val conf = new ClaspConf(args)
  conf.doModificationAfterParse

  // Setup some options for the emulator
  // TODO instead of passing arguments one at a time to the client 
  // (who then uses those options to setup the EmulatorOptions), 
  // can we just serialize EmulatorOptions on master and deliver 
  // then entire object? Would there be any benefit?!
  val opts = new EmulatorOptions

  // Create a new instance of the framework. There should be at least one
  // instance of Clasp started per computer in your cluster, although you
  // should probably let the master handle starting all of the workers.
  if (conf.client())
    new ClaspClient(conf, opts)
  else {
    var clasp = new ClaspMaster(conf)

    import ExecutionContext.Implicits.global
    // Any logging done inside the callback will show up in the remote host
    // nohup file Any exceptions thrown will be delivered to onFailure handler.
    val f = clasp.register_on_new_emulator((emu: Emulator) => {
      var result = scala.collection.mutable.Map[String, Any]()
      info("About to install")
      result("serialID") = emu.serialID
      result("node") = "hostname".!!.stripLineEnd

      sdk.install_package(emu.serialID, "examples/antimalware/Profiler.apk")
      info("Installed.")

      result.toMap
    })
    f onSuccess {
      case data => info(s"""Emulator Task completed successfully on Node ${data("node")}, emulator ${data("serialID")}""")
    }
    f onFailure {
      case t => error(s"Future failed due to ${t.getMessage}")
    }
    Thread.sleep(600000)
    clasp.kill
  } // End of clasp master logic
}

/* TODO make it possible to support sending EmulatorOptions across the
 * network and starting emulators with different options
 */
class ClaspClient(val conf: ClaspConf, val emuOpts: EmulatorOptions) {
  val ip = conf.ip()
  val clientConf = ConfigFactory
    .parseString(s"""akka.remote.netty.tcp.hostname="$ip"""")
    .withFallback(ConfigFactory.load("client"))

  // Turn on the actor system, avoiding logging until we are sure we can run so we 
  // don't mix our logs in with someone already running on this node
  var system: ActorSystem = null
  try {
    system = ActorSystem("clasp", clientConf)
  } catch {
    case inuse: org.jboss.netty.channel.ChannelException => {
      val logbuffer = new StringBuilder(s"Another person is using $ip as a client node!\n")
      try {
        // Print to local log
        print("Cannot start: Channel in use")

        logbuffer ++= "Refusing to start a new client here\n"
        logbuffer ++= "Here is some debug info to help detect what is running:\n"
        // TODO update this to include any sdk commands
        // TODO use java library for PS instead of shell commands to make this cross-platform
        val emu_cmds = "emulator,emulator64-arm,emulator64-mips,emulator64-x86,emulator-arm,emulator-mips,emulator-x86"
        val ps_list: String = s"ps -o user,cmd -C java,$emu_cmds".!!.stripLineEnd
        logbuffer ++= s"Process List (including this java process!): \n$ps_list\n"

        logbuffer ++= "User List (including you!):\n"
        val user_list: String = s"ps --no-headers -o user -C java,$emu_cmds".!!
        import scala.collection.immutable.StringOps
        (new StringOps(user_list)).lines.foreach(line => {
          val user: String = (s"getent passwd $line").!!.stripLineEnd
          logbuffer ++= user
        })
      } finally {
        shutdown(1, Some(logbuffer.toString))
      }
    }
  }

  // Try not to log above here. If we end up failing to start, there's no reason to 
  // clutter up the log of the clasp instance already running on this system
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{ error, debug, info, trace }
  info("----------------------------  Starting New Client -----------------------------")
  info(s"Using IP $ip")
  info("I am a client!")

  if (conf.mip.get == None) {
    error("Client started without an ip address for the master node, aborting")
    shutdown(1, Some("No master IP address passed"))
  }
  if (!sdk.valid) {
    error("Unable to locate SDK, refusing to create Node")
    shutdown(1, Some("Unable to locate SDK"))
  }

  val masterip = conf.mip().stripLineEnd
  var n = system.actorOf(Props(new Node(ip,
    masterip,
    emuOpts,
    conf.numEmulators.apply)), name = s"node-$ip")
  info(s"Created Node for $ip")

  def shutdown(exitcode: Int = 0, message: Option[String] = None) = {
    system.shutdown
    system.awaitTermination(30 second)

    // Do our best to preemptively notify the master that we're done
    if (!message.isEmpty) {
      val tempConf = ConfigFactory
        .parseString(s"""akka.remote.netty.tcp.hostname="$ip"""")
        .withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.port=0"))
        .withFallback(ConfigFactory.load("application"))

      val masterip = conf.mip()
      val user = conf.user()
      val temp = ActorSystem("clasp-temp", tempConf)
      debug(s"Sending message to akka.tcp://clasp@$masterip:2552/user/nodemanager")
      val manager = temp.actorFor(s"akka.tcp://clasp@$masterip:2552/user/nodemanager")

      manager ! NodeStartError(ip, message.get)

      debug("Waiting 10 seconds for the message to be delivered")
      Thread.sleep(10000)

      temp.shutdown
      temp.awaitTermination(10 second)
    }

    debug("Shutting down")
    System.exit(exitcode)

  }
}

/*
 * Interface to the clasp system.
 * Creating this will start a clasp worker on all clients 
 */
class ClaspMaster(val conf: ClaspConf) extends SimpleRoutingApp{
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{ error, debug, info, trace }

  val ip = conf.ip()
  info(s"Using IP $ip")
  info("I am the master!")
  val serverConf = ConfigFactory
    .parseString(s"""akka.remote.netty.tcp.hostname="$ip" """)
    .withFallback(ConfigFactory.load("master"))

  implicit var system: ActorSystem = null
  try {
    debug(s"About to create Master ActorSystem clasp");
    system = ActorSystem("clasp", serverConf)
    
    debug("Master's ActorSystem created")
  } catch {
    case inuse: org.jboss.netty.channel.ChannelException => {
      error(s"Another person is using $ip as a master node!")
      error("Refusing to start here")
      error("Here is some debug info to help detect what is running:")
      val ps_list: String = "ps -o user,cmd -C java".!!.stripLineEnd
      error(s"Process List (including you!): \n$ps_list")

      error("User List (including you!):")
      val user_list: String = "ps --no-headers -o user -C java".!!
      import scala.collection.immutable.StringOps
      (new StringOps(user_list)).lines.foreach(line => {
        val user: String = (s"getent passwd $line").!!
        error(user.stripLineEnd)
      })
      System.exit(0)
    }
  }
  
    startServer(interface = "localhost", port = 8080) {
    path("hello") {
      get {
        complete {
          <h1>Say hello to spray</h1>
        }
      }
    }
  }


  // Make SD card directory
  val logname = getLogDirectoryForMe
  val sdDir: File = new File(s"/tmp/sdcards/$logname")
  if (!sdDir.exists()) {
    sdDir.mkdir()
  }

  val emanager = system.actorOf(Props[EmulatorManager], name = "emulatormanager")
  info("Created EmulatorManager")

  var manager = system.actorOf(Props(new NodeManager(conf)), name = "nodemanager")
  info("Created NodeManager")

  // Cleanup then exit JVM
  system.registerOnTermination {
    info("Termination of clasp ActorSystem detected")
    info("Running cleanup tasks")
    // TODO: Give users an option 
    if (sdDir.listFiles() != null)
      sdDir.listFiles().map(sd => sd.delete())

    info("Exiting JVM")
    System.exit(0)
  }

  // If the JVM indicates it's shutting down (via Ctrl-C or a SIGTERM), 
  // then try to cleanup our ActorSystem
  sys addShutdownHook {
    if (!system.isTerminated) {
      debug("JVM is going to terminate, trigger ActorSystem cleanup")
      system.shutdown
      Thread.sleep(1000)
    }
  }

  // When a new emulator is ready to be used, the provided function will 
  // be transported to the node that emulator runs on and then executed
  //
  // Any values set on the returned Map will be delivered back to the
  // originating caller by way of the future
  def register_on_new_emulator(func: Emulator => Map[String, Any]): Future[Map[String, Any]] = {
    import ExecutionContext.Implicits.global
    val result = promise[Map[String, Any]]
    emanager ! QueueEmulatorTask(func, result)
    result.future
  }

  def kill {
    info("Killing Clasp and emulators.")
    system.shutdown
  }

  def getLogDirectoryForMe: String = {
    if ("logname".! == 0)
      return "logname".!!.stripLineEnd
    else if ("whoami".! == 0)
      return "whoami".!!.stripLineEnd
    else
      "default"
  }
}

/*
 * An object-oriented way to interface with an Actor.
 * This caches multiple items from the emulator process underneath the actor,
 * and therefore may become invalid if the emulator dies.
 * We may want to avoid caching in the future and just have this know how
 * to ask the emulator for the right things
 */
class Emulator(val serialID: String, var rebootWhenFinished: Boolean = false)
  extends Serializable {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{ error, debug, info, trace }
}
