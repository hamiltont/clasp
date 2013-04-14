package core

import org.scalatest.junit.AssertionsForJUnit._
import org.scalatest.junit.AssertionsForJUnit
import org.junit.Assert._
import org.junit.Test
import org.junit.Before
import org.junit.After
import java.io.File
import org.apache.commons.io.FileUtils
import akka.testkit.TestKit
import scala.language.postfixOps
import scala.concurrent.duration._

class AndroidCommandTest extends AssertionsForJUnit  {

  /** Very basic test. Ensures absolute minimum functionality
    */
  @Test def testBasics {
    val cmd = new AndroidCommand[Integer](() => 2 + 2 )
    
    assert(cmd.result == None)
    cmd.run()
    assert(cmd.result.getOrElse(-90909090) === 4)
  }

  /** This tests that the command is not executed at creation time.
    * Rather, that it is executed at run_command-time.
    */
  @Test def testTimeOfExecution {
    val time1 = System.currentTimeMillis()
    val cmd = new AndroidCommand[Unit](() => Thread.sleep(1000))
    val time2 = System.currentTimeMillis()
    assert(time2 - time1 < 1000)
    cmd.run()
    val time3 = System.currentTimeMillis()
    assert(time3 - time2 >= 1000)
    assert(cmd.result != None)
  }
}

class AndroidCommandAsyncTest extends AssertionsForJUnit {

  /** Ensures absolute minimum functionality
    */
  @Test def testBasics {
    val cmd = new AsynchronousCommand[Integer](() => 2 + 2 )
    
    assert(cmd.result == None)
    cmd.run()
    assert(cmd.await() == Some(4))
    assert(cmd.result == Some(4))
  }

  /** Guarantee that all commands are run async.
    */
  @Test def testAsyncrony {
    val time1 = System.currentTimeMillis()
    val cmd = new AsynchronousCommand[Unit](() => Thread.sleep(1000))
    val time2 = System.currentTimeMillis()
    assert(time2 - time1 < 1000)
    assert(!cmd.command_running)
    cmd.run()
    assert(cmd.command_running)

    val time3 = System.currentTimeMillis()
    assert(time3 - time2 < 1000)
    assert(cmd.result == None)

    val res = cmd.await()
    assert(!cmd.command_running)

    val time4 = System.currentTimeMillis()
    assert(time4 - time2 >= 1000)
    assert(res != None)
    assert(cmd.result != None)
  }

  /** Guarantee that multiple consecutive await()s will
    * not fail.
    */
  @Test def multiAwait {
    val cmd = new AsynchronousCommand[Unit](() => 2 + 2)
    cmd.run()
    val fst = cmd.await()
    assert(fst != None)
    assert(fst == cmd.result)
    for(i <- 1 to 10) {
      val a = cmd.await()
      assert(a == fst)
    }
  }

  /** Guarantee that force_fail wakes us up in a timely manner.
    */
  @Test def wakeUp {
    val cmd = new AsynchronousCommand[Unit](() => Thread.sleep(1000))
    val time1 = System.currentTimeMillis()
    cmd.run()
    cmd.force_fail(even_if_success = true)
    // Guarantee that force_fail doesn't do the waiting for us...
    val time2 = System.currentTimeMillis()
    assert(time2 - time1 < 1000)
    cmd.await()
    val time3 = System.currentTimeMillis()
    // Less than 1000ms have passed since start? Cool!
    assert(time3 - time1 < 1000)
    assert(cmd.success == Some(false))
    assert(cmd.result == None)
  }

  @Test def forceFail {
    val cmd = new AsynchronousCommand[Integer](() => 2 + 2)
    assert(cmd.await() == Some(4))
    assert(cmd.success == Some(true))
    assert(cmd.result == Some(4))

    cmd.force_fail(even_if_success = false)
    assert(cmd.await() == Some(4))
    assert(cmd.success == Some(true))
    assert(cmd.result == Some(4))

    cmd.force_fail(even_if_success = true)
    assert(cmd.await() == None)
    assert(cmd.success == Some(false))
    assert(cmd.result == None)
  }

  class CommandWatcher_watchers(cmd: AndroidCommand[_]) extends CommandWatcher(cmd) {
    require(cmd != null)
    thread_monitor_for_me = false

    override def do_monitor() {}
  }

  @Test def watchers {
    val cmd = new AsynchronousCommand(() => 2 + 2)
    val watcher = new CommandWatcher_watchers(cmd)
    cmd.add_watcher(watcher)
    assert(!watcher.started)
    assert(!watcher.triggered)
    cmd.run()
    assert(cmd.success != None)
    assert(watcher.started)
    assert(!watcher.triggered)
  }
}

class TimedCommandWatcherTest extends AssertionsForJUnit {

  /** Test fail (i.e. CommandWatcher must "fail" the result.)
    */
  @Test def testFailResult {
    val cmd = new AsynchronousCommand(() => Thread.sleep(1000))
    val watch = new TimedCommandWatcher(cmd, 100 millis)

    cmd.add_watcher(watch)
    assert(!watch.started)
    assert(!watch.triggered)

    val time1 = System.currentTimeMillis()
    cmd.run()
    assert(watch.started)
    assert(!watch.triggered)

    cmd.await()
    val time2 = System.currentTimeMillis()
    assert(time2 - time1 < 1000)
    assert(watch.started)
    assert(watch.triggered)
    assert(cmd.result == None)
    assert(cmd.success == Some(false))
  }

  // Test pass (i.e. CommandWatcher must not "fail" the result.)
  @Test def testPassResult {
    val cmd = new AsynchronousCommand(() => Thread.sleep(100))
    val watch = new TimedCommandWatcher(cmd, 1000 millis)

    cmd.add_watcher(watch)
    assert(!watch.started)
    assert(!watch.triggered)

    val time1 = System.currentTimeMillis()
    cmd.run()
    assert(watch.started)
    assert(!watch.triggered)

    cmd.await()
    val time2 = System.currentTimeMillis()
    assert(time2 - time1 < 1000)
    assert(watch.started)
    assert(!watch.triggered)
    assert(cmd.success == Some(true))    
    assert(cmd.result != None)
  }

  @Test def testPassResultFull {
    val cmd = new AsynchronousCommand(() => Thread.sleep(100))
    val watch = new TimedCommandWatcher(cmd, 500 millis)

    cmd.add_watcher(watch)
    assert(!watch.started)
    assert(!watch.triggered)

    val time1 = System.currentTimeMillis()
    cmd.run()
    assert(watch.started)
    assert(!watch.triggered)

    cmd.await()
    val time2 = System.currentTimeMillis()
    assert(time2 - time1 >= 100 && time2 - time1 < 500)
    assert(!watch.triggered)
    assert(cmd.success == Some(true))
    val oldres = cmd.result
    assert(cmd.result != None)

    // Give the watcher 750ms to finish, because why not?
    Thread.sleep(750)
    assert(watch.started)
    assert(watch.triggered)

    // Cool. Now that it's been finished and triggered, make sure
    // that nothing was reset by it.
    assert(cmd.success == Some(true))
    assert(cmd.result != None)
    assert(cmd.result == oldres)
  }
}
