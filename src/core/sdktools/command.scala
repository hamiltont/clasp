package clasp.core.sdktools

import scala.language.postfixOps
import scala.concurrent.duration._
import scala.sys.process._
import scala.util.matching.Regex

import org.slf4j.LoggerFactory

object Command {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{error, debug, info, trace}

  def run(command: String, timeout: FiniteDuration = 0 seconds,
      killSignal: Int = 9): Option[String] = {
    info(s"Executing '$command'")
    val out = new StringBuilder
    val logger = ProcessLogger(
      (o: String) => out.append(o),
      (e: String) => out.append(e))
    val ret = if (timeout.toSeconds != 0) {
        s"timeout -s $killSignal ${timeout.toSeconds} $command" ! logger
      } else {
        command ! logger
      }
    info(s"Finished executing'$command'")
    if (ret != 0) {
      return None
    } else {
      return Some(out.toString)
    }
  }

  def runSeq(command: Seq[String], timeout: FiniteDuration = 0 seconds,
      killSignal: Int = 9): Option[String] = {
    run(command.mkString(" "), timeout, killSignal)
  }

  def runAndParse(command: String, regex: Regex,
      timeout: FiniteDuration = 0 seconds, killSignal: Int = 9):
      Option[Vector[String]] = {
    val res = run(command, timeout, killSignal)
    res.map((s: String) => { 
      val r = for (regex(name) <- regex.findAllIn(s)) yield name
      r.toVector
    })
  }

  def runSeqAndParse(command: Seq[String], regex: Regex,
      timeout: FiniteDuration = 0 seconds, killSignal: Int = 9):
      Option[Vector[String]] = {
    runAndParse(command.mkString(" "), regex, timeout, killSignal)
  }
}
