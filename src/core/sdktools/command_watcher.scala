package core.sdktools

import scala.actors._
import scala.concurrent.ops._
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.Future 
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Try, Success, Failure}
import scala.language.postfixOps

class AndroidCommand[T](val function: (Unit => T)) {
  require(function != null)

  private var _command_result: Option[T] = None
  private var _command_success: Option[Boolean] = None
  private var _attached_watchers: List[CommandWatcher] = List()

  def command_result = _command_success.flatMap {
    if (_)
      _command_result
    else 
      None 
  }

  def command_success = _command_success

  protected def command_check_succeeded(res: T): Boolean = true

  def add_watcher(cw: CommandWatcher) {
    _attached_watchers = cw :: _attached_watchers 
  }

  def rm_watcher(cw: CommandWatcher) {
    // Filter instead of remove because there could be multiple
    // instances of that Watcher in the list.
    command_result.filter { _ != cw }
  }

  def run_command() {
    val res = function()
    _command_result = Some(res)
    _command_success = Some(command_check_succeeded(res))
  }

  def force_fail(even_if_success: Boolean = false) {
    val set_to = _command_success getOrElse false
    if (!set_to || even_if_success) {
      _command_success = Some(set_to)
    }
  }
}

class AsynchronousCommand[T](function: (Unit => T), val max_wait: Duration = Duration.Inf, val notify_targ: Option[Actor] = None)
    extends AndroidCommand[T](function) with Actor {
  require(duration != null && notify_targ != null)

  private var _cmd_future: Option[Future[Option[T]]] = None

  def cmd_future = _cmd_future

  override def run_command() {
    if (command_running) {
      val fut = Future {
        super.run_command()
        val res = command_result
        notify_targ.map(_ ! (this, res))
      } onComplete {
        stop_self()
      }
      _cmd_future = Some(fut)
    }
  }

  def act() {
    react {
      case _ => 
        signal_done()
        exit()
    }
  }

  def stop_self() { this ! this }

  def command_running = _cmd_future != None

  def await(): Option[T] = {
    command_success match {
      // If success has not been reported, wait on the future.
      case None => _cmd_future match {
        case Some(x) => Await.result(x, max_wait)
        case _ => None
      }
      case _ => command_result
    }
  }

  def force_fail(autokill: Boolean, even_if_success: Boolean) {
    // TODO:
    super.force_fail(even_if_success)
  }

  override def force_fail(even_if_success: Boolean = false) {
    force_fail(even_if_success, true)
  }
}

trait Verifiable[T] {
  var verification_function: Option[(T => Boolean)] = None

  def command_result: T

  def command_success: Boolean = verification_function match {
    case Some(ver) => ver(command_result)
    case None => command_result != null
  }

  def command_failure = !command_success
}

/** 
 * CommandWatchers are entities that "watch" different parts of a command. They can
 * watch the execution time of a command, CPU graphs, whatever. What's important is
 * that, at any point, the command may be failed by the watcher.
 */
abstract class CommandWatcher(val watching: AndroidCommand[_]) {
  require(watching != null)

  def command_monitor()

  def fail_command(force: Boolean = false) { 
    watching.force_fail(force)
  }
}

class TimedCommandWatcher(watching: AsynchronousCommand[_], val time_limit: Duration) extends CommandWatcher(watching)
{
  require(time_limit != null)

  def command_monitor() {
    val fut = Futures.alarm(time_limit.toMillis).onComplete { fail_command(force = false) }
  }
}
