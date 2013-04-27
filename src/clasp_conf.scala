package clasp

// Used for command line parsing
import org.rogach.scallop._

// Used for auto-detection of hostname
import scala.sys.process._
 
// TODO add --local option that forces pool to be set to --pool "$ip"
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
  
  val ip     = opt[String] (descr = "Informs Clasp of the IP address it should bind to." +
    "This should be reachable by the master and by all other clients in the system." +
    "If no explicit IP is provided, then 10.0.2.{hostname} will be used", 
    default=Some("10.0.2." + "hostname".!!.stripLineEnd))
  
  // TODO figure out how to make scallop enfore this requirement for us
  val mip    = opt[String] (descr = "The server ip address. Does nothing without the " + 
    "flag indicating that this is a client. Required for clients")

  val workers= opt[Int]    (descr = "The number of worker clients Clasp should start " +
    "by default. This number can grow or shrink dynamically as the system runs. All " + 
    "clients are picked from a pool of IP addresses inside server.conf", 
    default=Some(3))

  val pool   = opt[String] (descr = "Override the worker pool provided in configuration " + 
    "file by providing a comma-separated list of IP addresses e.g. [--pool " + 
    "\"10.0.2.1,10.0.2.2\"]. Workers will be launched in the order given. No " + 
    "spaces are allowed after commas")
}
