package clasp

import scala.collection.mutable.MutableList

import scala.collection.mutable.ListBuffer
import scala.sys.process._
import scala.language.postfixOps

import scala.concurrent._
import scala.concurrent.duration._

import org.slf4j.LoggerFactory

import com.typesafe.config.ConfigFactory

import akka.actor._
import akka.pattern.Patterns.ask
import akka.util.Timeout
import java.util.concurrent.TimeUnit

import clasp.core._
import clasp.core.sdktools.sdk
import clasp.core.sdktools.EmulatorOptions
import java.io.File

/*
 * Example of using Clasp. The App will be packaged and deployed to both the
 * server and the client machines. Note that you should never be the one
 * specifying --client on the command line. Starting the server will, with
 * proper configuration parameters, start all of the worker machines. The
 * corollary is that stopping the server is a graceful operation and all of the
 * worker machines will be properly shutdown as soon as they are done
 * processing their current message. If your messages are huge this might take
 * a while, but this is much better than logging into each machine and force
 * killing all of the processes associated with running clasp. If you manually
 * shutdown instead of using the graceful operation then you had better know
 * how to clean up the proper processes, because the system may refuse to
 * restart on any computers that have an incorrect start state!
 */
object ClaspRunner extends App {
  // If you would like logging on remote machines(all workers), then use these
  // logging methods in your codebase. The logs will (eventually) be pulled
  // back to the master, so that you don't need to manually aggregrate your
  // logs
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}

  // Used to parse the command line arguments
  val conf = new ClaspConf(args)

  // TODO: Add a way for emulator options to be nonuniform?
  // Setup some options for the emulator
  val opts = new EmulatorOptions
  opts.noWindow = true

  // Create a new instance of the framework. There should be at least one
  // instance of Clasp started per computer in your cluster, although you
  // should probably let the master handle starting all of the workers.
  if (conf.client()) 
    new ClaspClient(conf, opts)
  else {
    var clasp = new ClaspMaster(conf)
    printf("Testing callback abilities")
  
    import ExecutionContext.Implicits.global
    // Any logging done inside the callback will show up in the remote host
    // nohup file Any exceptions thrown will be delivered to onFailure handler.
    val f = clasp.register_on_new_emulator( (emu: Emulator) => {
        var result = scala.collection.mutable.Map[String, Any]()
        info("About to install")
        result("serialID") = emu.serialID
        result("node") = "hostname".!!.stripLineEnd
        
        sdk.install_package(emu.serialID,"examples/antimalware/Profiler.apk")
        info("Installed.")

        result.toMap
      }
    )
    f onSuccess {
      case data => info(s"""Emulator Task completed successfully on Node ${data("node")}, emulator ${data("serialID")}""")
    }
    f onFailure {
      case t => error(s"Future failed due to ${t.getMessage}")
    }
    Thread.sleep(25000)
    clasp.kill
  } // End of clasp master logic
}
 

/* TODO make it possible to support sending EmulatorOptions across the
 * network and starting emulators with different options
 */
class ClaspClient(val conf: ClaspConf, val emuOpts: EmulatorOptions) {
  val ip = conf.ip()
  val clientConf = ConfigFactory
    .parseString(s"""akka.remote.netty.hostname="$ip"""")
    .withFallback(ConfigFactory.load("client"))

  // Turn on the actor system, avoiding logging until we are sure we can run so we 
  // don't mix our logs in with someone already running on this node
  var system:ActorSystem = null
  try {
    system = ActorSystem("clasp", clientConf)
  } catch {
    case inuse: org.jboss.netty.channel.ChannelException => {
      val logbuffer = new StringBuilder(s"Another person is using $ip as a client node!\n")
      logbuffer ++= "Refusing to start a new client here\n"
      logbuffer ++= "Here is some debug info to help detect what is running:\n"
      // TODO update this to include any sdk commands
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

      // Notify failure
      val failConf = ConfigFactory
        .parseString(s"""akka.remote.netty.hostname="$ip"""")
        .withFallback(ConfigFactory.parseString("akka.remote.netty.port=0"))
        .withFallback(ConfigFactory.load("application"))
 
      val masterip = conf.mip()
      val temp = ActorSystem("clasp-failure", failConf)
      val manager = temp.actorFor(s"akka://clasp@$masterip:2552/user/nodemanager")
      manager ! NodeBusy(ip, logbuffer.toString)
      // TODO replace above with ask and terminate as soon as the Ack receive

      // 99% confidence the busy message was received
      Thread.sleep(2000)
      System.exit(1)
    }
  }
 
  // Try not to log above here. If we end up failing to start, there's no reason to 
  // clutter up the log of the clasp instance already running on this system
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}
  info("----------------------------  Starting New Client -----------------------------")
  info(s"Using IP $ip")
  info("I am a client!")  

  if (conf.mip.get == None) {
    error("Client started without an ip address for the master node, aborting")
    System.exit(1)
  }
  val masterip = conf.mip().stripLineEnd

  var n = system.actorOf(Props(new Node(ip, masterip, emuOpts,
    conf.numEmulators.apply)), name=s"node-$ip")
  info(s"Created Node for $ip")
}


/*
 * Interface to the clasp system. Creating this will start a clasp worker on all clients 
 * 
 */
class ClaspMaster(val conf: ClaspConf) {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}

  val ip = conf.ip()
  info(s"Using IP $ip")
  info("I am the master!")
  val serverConf = ConfigFactory
      .parseString(s"""akka.remote.netty.hostname="$ip" """)
      .withFallback(ConfigFactory.load("master"))

  var system:ActorSystem = null
  try {
    system = ActorSystem("clasp", serverConf)
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
  
  //Check for presence of and make a temporary SD card directory if it does not yet exist
  val logname: String = "logname".!!.stripLineEnd
  
  val sdDir: File = new File ("/tmp/sdcards" + logname)
  if (!sdDir.exists()){
    sdDir.mkdir()
  }

  val emanager = system.actorOf(Props[EmulatorManager], name="emulatormanager")
  info("Created EmulatorManager")
 
  var manager = system.actorOf(Props(
    new NodeManger(ip, conf.workers(), conf.pool.get,
      conf.numEmulators.apply)), name="nodemanager")
  info("Created NodeManger")
  
  sys addShutdownHook(shutdown_listener)

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
    shutdown_listener
  }

  private def shutdown_listener() {
    // TODO: Context.system.shutdown somewhere?

    if (manager != null && !manager.isTerminated) {
      // First shutdown everything from Node down the hierarchy
      manager ! Shutdown

      // Second shutdown all other high-level management activities
      emanager ! PoisonPill
    }

    // Manage SD cards and delete them all at/before the end of clasp's lifetime.
    // TODO: Options to allow users to state that they want to keep SD cards after running.
    val listSDs = sdDir.listFiles()
    for {sd <- listSDs}{
    //How does one delete all of the files in this list?
      sd.delete()
    }
    //Next question, do we delete the directory as well at the end? Or are we happy with the temp directory continuing to exist?

    var timeSlept = 0.0d; var timeout = 10.0d
    while (manager != null && !manager.isTerminated) {
      Thread.sleep(500)
      timeSlept += 0.5d
      if (timeSlept >= timeout) {
        val input = readLine(
          s"""|Waited for $timeSlept seconds, and all nodes have not responded.
              |Continue waiting (y)/n? """.stripMargin)
        if (input != null && input.size > 0 &&
            input.toLowerCase.charAt(0) == 'n') {
          println("Exiting.")
          println("Warning: Nodes may still have emulators running on them!")
          return;
        } else {
          println("Waiting for 10 more seconds.")
          timeout += 10.0d
        }
      }
    }
  }
}

/*
 * An object-oriented way to interface with an Actor.
 * This caches multiple items from the emulator process underneath the actor,
 * and therefore may become invalid if the emulator dies.
 * We may want to avoid caching in the future and just have this know how
 * to ask the emulator for the right things
 */
class Emulator(val serialID: String) extends Serializable {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}
}
