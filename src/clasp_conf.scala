package clasp

// Used for command line parsing
import org.rogach.scallop._

class ClaspConf(arguments: Seq[String]) extends ScallopConf(arguments) {
  version("Clasp 0.0.0")
  banner("""Usage: clasp [-c|--client]
    |By default clasp runs as though it was a server with only 
    |the local node. This makes it easier for people running in
    |a non-distributed manner. If you use sbt, then to run a 
    |client use sbt "run --client". To run a whole system you
    |need a server running on the main node and then clients on
    |all other nodes
    |""".stripMargin)

  val client = opt[Boolean](descr = "Should this run as a client instance")
  val ip     = opt[String] (descr = "Informs Clasp of the IP address it should bind to." + 
    "If no explicit IP is provided, then 10.0.2.{hostname} will be used")
}
