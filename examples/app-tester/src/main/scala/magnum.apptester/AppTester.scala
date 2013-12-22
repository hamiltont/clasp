// TODO: Use the same logging mechanism as clasp.
package magnum.apptester

import java.io.{File,FileWriter}
import java.util.concurrent.atomic.AtomicInteger

import scala.actors._ // For Thread.sleep. TODO remove!
import scala.language.postfixOps
import scala.io.Source

import clasp._
import clasp.core._
import clasp.core.sdktools._

import scala.sys.process._
import scala.concurrent._
import ExecutionContext.Implicits.global

object Driver {
  lazy val infoRegex = """.*name='([^']*)'.*""".r
  var clasp: ClaspMaster = null

  var totalToTest = 0
  var numTested: AtomicInteger = new AtomicInteger(0)

  def run(clasp: ClaspMaster, appsTag: String, outputTag: String) {
    this.clasp = clasp

    val monkeyOpts = "--pct-syskeys 0 " +
      "--pct-appswitch 0 " +
      "--pct-anyevent 0 " +
      "-s 0 " +
      "--throttle 50 " +
      "1000"
//
    // Clean and make the `outputTag` directories.
    s"rm -rf output/$outputTag" !!;
    s"mkdir -p output/$outputTag" !!;
    val infoFile = new FileWriter(s"output/$outputTag/info.txt", false)
    infoFile.write("Output information.\n")
    infoFile.write(s"adb monkey options: '$monkeyOpts'.\n")
    infoFile.write(s"appsTag = $appsTag\n")
    infoFile.close()

    // Queue all of the applications asynchronously.
    collectEmu(appsTag, outputTag, monkeyOpts)

    // Wait for the applications to finish profiling and timeout
    // after N minutes if there is no activity.
    // TODO: This might not be working because a session hung.
    //       Even ctrl+C was blocked. I'm not sure what happened.
    var previousNumTested = 0; var count = 0;
    var minutesToTimeout = 10;
    println("Waiting for applications to finish testing.")
    println("If nothing changes in " + minutesToTimeout + " minute(s), " +
      "assume we timed out and exit.")
    while (numTested.get < totalToTest) {
      Thread.sleep(1000);
      count = (count + 1) % (60*minutesToTimeout)
      if (count == 0) {
        if (previousNumTested == numTested.get) {
          println("No applications tested in " + minutesToTimeout
            + " minute(s). Exiting.")
          clasp.kill
          return
        }
        previousNumTested = numTested.get
      }
    }
    println("Tested " + totalToTest + " applications.")
    clasp.kill
  }

  def collectEmu(appsTag: String, outputTag: String, monkeyOpts: String) {
    var appsPath = s"apps/$appsTag"
    var file: File = new File(appsPath)
    if (file.listFiles == null) {
      println(s"Warning: $appsPath contains no files.")
      return;
    }

    println(s"Adding applications.")
    val startTime = System.currentTimeMillis()
    var i = 1
    for (apkFile <- file.listFiles) {
      val apkName = apkFile.getName()
      totalToTest += 1
      val f = clasp.register_on_new_emulator(
          (emu: Emulator) =>
            testApkCallback(emu, apkName, appsTag, outputTag, monkeyOpts))
      f onSuccess {
        case data => println(
            s"""Emulator Task completed successfully on Node""" +
            s"""${data("node")},emulator ${data("serialID")}""")
        val total = numTested.incrementAndGet
        val time = System.currentTimeMillis() - startTime;
        println(s"Tested $total in $time ms.")
      }
      f onFailure {
        // TODO: Add to queue again.
        case t => println(s"Future failed due to '${t.getMessage}'")
        numTested.incrementAndGet
      }
      i += 1
    }
  }

  // Don't use global variables in a callback!
  // This is why we pass `outputTag`.
  def testApkCallback(emu: Emulator, apkName: String, appsTag:String,
      outputTag: String, monkeyOpts: String): Map[String, Any] = {
    val startTime = System.currentTimeMillis();
    var result = scala.collection.mutable.Map[String, Any]()
    println(s"\n===========Testing application: $apkName===========")
    result("serialID") = emu.serialID
    result("node") = "hostname".!!.stripLineEnd

    val apkPath = s"apps/$appsTag/$apkName"
    sdk.install_package(emu.serialID, apkPath, true)

    // Get the package name.
    val apkInfo = sdk.aapt_dump("badging", apkPath)
    val infoRegex(packageName) = apkInfo.split("\n")(0)

    println("Monkey testing application '" + packageName + "'.")
    sdk.remote_shell(emu.serialID, s"monkey -p $packageName " + monkeyOpts)

    val endTime = System.currentTimeMillis();
    println(s"Time to test '$apkName': ${endTime-startTime}")
    result.toMap
  }
}
