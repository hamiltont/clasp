package clasp.core

import akka.actor._
import akka.actor.ActorDSL._
import java.util.concurrent.atomic.AtomicBoolean
import scala.actors.Futures.alarm
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.Future._
import scala.concurrent.Future
import scala.language.postfixOps

/**
 * CommandWatchers are entities that "watch" different parts of a command. They can
 * watch the execution time of a command, CPU graphs, whatever. What's important is
 * that, at any point, the command may be failed by the watcher.
 */
abstract class CommandWatcher(val watching: AndroidCommand[_]) {
  require(watching != null)

  // Maybe we can name this better in the future ;)
  protected var thread_monitor_for_me = true
  private var _started = new AtomicBoolean(false)
  private var _triggered = new AtomicBoolean(false)

  // Override *this* method in order to give a monitoring implementation.
  protected def do_monitor()

  def started = _started.get()
  def triggered = _triggered.get()

  final def start_monitoring() {
    _started.set(true)
    if (thread_monitor_for_me) {
      Future {
        do_monitor()
      }
    } else {
      do_monitor()
    }
  }

  protected def fail_command(force: Boolean = false) {
    _triggered.set(true)
    watching.force_fail(force)
  }
}

class TimedCommandWatcher(watching: AsynchronousCommand[_],
    val time_limit: FiniteDuration)
    (implicit system: ActorSystem)
    extends CommandWatcher(watching) {
  require(time_limit != null)
  thread_monitor_for_me = false

  //val system = ActorSystem("system")

  override def do_monitor() {
    system.scheduler.scheduleOnce(time_limit) {
      fail_command(force = false)
    }
  }
}
