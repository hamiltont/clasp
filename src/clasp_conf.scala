package clasp

import java.net.Inet4Address
import java.net.Inet6Address

import scala.sys.process.stringToProcess

import org.rogach.scallop.ScallopConf

// TODO add --local option that forces pool to be set to --pool "$ip". Note that this will 
//      require that the master can SSH into the local computer, so running with the 
//      --local flag will require that the local computer is running an SSH Server. However, 
//      this also removes the need for guessing the IP address. Both the client and the 
//      server can use 127.0.0.1 because they run on different ports. Local should also 
//      indicate that we are *not* going to use the clasp username, because the user will 
//      be using their own username. Also needs to ensure that EmulatorOptions.noWindow is 
//      false
// TODO add --cleanup option to walk through all nodes in the worker pool and ensure that 
//      there are no instances of clasp running. Can only kill processes for this user, 
//      so should report processids / nodes where clasp processes are still running
class ClaspConf(arguments: Seq[String]) extends ScallopConf(arguments) {
  version("Clasp 0.0.0")
  banner("""Usage: clasp [-c|--client] [-i|--ip <external ip>] [-m|--mip <master ip>] [-w|--workers <number>]
    |
    |By default clasp runs as though it was a server with only 
    |the local node. This makes it easier for people running in
    |a non-distributed manner. If you use sbt, then to run a 
    |client use sbt "run --client". To run a whole system you
    |need a server running on the main node and then clients on
    |all other nodes
    |""".stripMargin)

  val client = opt[Boolean](descr = "Should this run as a client instance")

  var ip = opt[String](descr = "Informs Clasp of the IP address it should bind to. " +
    "This should be reachable by the master and by all other clients in the system.",
    default = Some(getBestIP))

  // TODO figure out how to make scallop enforce this requirement for us
  val mip = opt[String](descr = "The master ip address. Only used with --client, " +
    "and required for clients")

  var user = opt[String](descr = "The username that clasp should use when SSHing into " +
    "worker systems", default = Some("clasp"))

  val workers = opt[Int](descr = "The number of worker clients Clasp should start " +
    "by default. This number can grow or shrink dynamically as the system runs. All " +
    "clients are picked from the pool of IP addresses inside client.conf",
    default = Some(3))

  var pool = opt[String](descr = "Override the worker pool provided in client.conf " +
    "file by providing a comma-separated list of IP addresses e.g. [--pool " +
    "\"10.0.2.1,10.0.2.2\"]. Workers will be launched in the order given. No " +
    "spaces are allowed after commas")

  val numEmulators = opt[Int](default = Option(1),
    descr = "The number of emulators to start on each node.")

  val local = opt[Boolean](descr = "Indicates that you are running Clasp on only one " +
    "computer, instead of the (more typical) distributed system. If ip, pool, or user " +
    "were not explicitely provided, this wil update them. --ip will become " +
    "127.0.0.1, user will be the current user, pool to be 127.0.0.1. Currently forces " + 
    "emulators to run in non-headless mode")

  def getBestIP: String = {
    val interfaces = java.net.NetworkInterface.getNetworkInterfaces();

    var noBetterOption = "127.0.0.1"
    while (interfaces.hasMoreElements()) {
      val interface = interfaces.nextElement();
      if (!(interface.isVirtual() || interface.isLoopback() ||
        interface.isPointToPoint() || !interface.isUp())) {
        val addresses = interface.getInetAddresses();
        while (addresses.hasMoreElements()) {
          val address = addresses.nextElement();
          // More filtering could probably happen here
          address match {
            case a: Inet4Address => return a.getHostAddress();
            case b: Inet6Address => noBetterOption = b.getHostAddress();
          }
        }
      }
    }

    noBetterOption
  }

  def doModificationAfterParse {
    if (local()) {

      // Both master and client run on localhost
      if (!ip.isSupplied) ip = opt[String](default = Some("127.0.0.1"));
      if (!pool.isSupplied) pool = opt[String](default = Some("127.0.0.1"));

      // User name defaults to me
      if (!user.isSupplied) user = opt[String](default = Some("whoami".!!))
    }
  }
}

