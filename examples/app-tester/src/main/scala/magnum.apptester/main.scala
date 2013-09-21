package magnum.apptester

import scala.sys.process._
import scala.language.reflectiveCalls

import org.rogach.scallop.ScallopConf;

import java.io.File

import clasp._
import clasp.core.sdktools.EmulatorOptions

object Main extends App {
  val emuOpts = new EmulatorOptions
  emuOpts.noBootAnim = true; emuOpts.noWindow = true;
  emuOpts.noSnapShotLoad = true; emuOpts.noAudio = true;
  emuOpts.noSkin = true; emuOpts.wipeData = true;
  emuOpts.gpuMode = "off";

  val opts = new AppTesterConf(args)

  if(opts.client()) {
    new ClaspClient(opts, emuOpts)
  } else {
    if (!opts.appsTag.isSupplied || !opts.outputTag.isSupplied) {
      println("Error: `appsTag` and `outputTag` are both required.")
      System.exit(42)
    }
    Driver.run(new ClaspMaster(opts),opts.appsTag.apply,opts.outputTag.apply)
  }
}

class AppTesterConf(args: Seq[String]) extends ClaspConf(args) {
  version("AppTester :driver.")
  banner("""AppTester dynamically tests a set of applications using
     | Android's monkey and reports failures.
     | + The input application set is `apps/<appsTag>`.
     | + The output files will be in `output/<outputTag>`.
     |
     |Where <appsTag> and <outputTag> are arguments described below.
     |
     |A timeout after queueing the tasks is defined in AppTester.scala.
     !Information is logged in `output/<outputTag>/info.txt`.
  """.stripMargin)
  val appsTag = opt[String] (
    "appsTag", descr="Specific tag within the `apps` directory to use.")
  val outputTag = opt[String] (
    "outputTag", descr="Specific tag within the `output` directory to use.")
  val help = opt[Boolean]("help",
    noshort = true, descr = "Show this message.")
  // TODO: Option for the number of monkey events to send.
  // TODO: Option for the types of devices to profile.
}
