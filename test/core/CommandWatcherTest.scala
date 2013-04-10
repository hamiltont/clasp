package core

import org.scalatest.junit.AssertionsForJUnit._
import org.junit.Assert._
import org.junit.Test
import org.junit.Before
import org.junit.After
import java.io.File
import org.apache.commons.io.FileUtils
import akka.testkit.TestKit
import core.sdktools.CommandWatcher

class AndroidCommandTest extends AssertionsForJUnit  {

  /** Very basic test. Ensures absolute minimum functionality
    */
  @Test def testBasics {
    val cmd = AndroidCommand[Integer] { 2 + 2 }
    
    assert(cmd.command_result == None)
    cmd.run_command()
    assert(cmd.command_result === 2)
  }

  /** This tests that the command is not executed at creation time.
    * Rather, that it is executed at run_command-time.
    */
  @Test def testTimeOfExecution {
    val time1 = System.currentTimeMillis()
    val cmd = AndroidCommand[Unit] { Thread.sleep(1000) }
    val time2 = System.currentTimeMillis()
    assert(time2 - time1 < 1000)
    cmd.run_command()
    val time3 = System.currentTimeMillis()
    assert(time3 - time2 >= 1000)
    assert(command_result != None)
  }
}

class AndroidCommandAsyncTest extends Actor with AssertionsForJUnit {
  /** Once again, basic functionality
    */
}

class CommandWatcherTest extends AssertionsForJUnit {
  // TODO --> 
}
