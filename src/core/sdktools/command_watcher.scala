package core

import akka.actor._
import akka.actor.ActorDSL._
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import scala.actors.Futures.alarm
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ops._
import scala.language.postfixOps

class AndroidCommand[T](val function: (() => T)) {
  require(function != null)

  private var _command_result: Option[T] = None
  private var _command_success: Option[Boolean] = None
  private var _attached_watchers: List[CommandWatcher] = List()

  def result =
    if (_command_success.getOrElse(false))
      _command_result
    else
      None

  def success = _command_success

  protected def command_check_succeeded(res: T): Boolean = true

  def run() { 
    // If we've not tried to run the command already...
    if (_command_success == None) {
      val res = function()
      _command_result = Some(res)
      _command_success = Some(command_check_succeeded(res))
    }
  }

  def force_fail(even_if_success: Boolean = false) {
    val succeeded = _command_success getOrElse false
    if (!succeeded || even_if_success) {
      _command_success = Some(false)
    }
  }
}

class AsynchronousCommand[T](function: (() => T), val max_wait: Duration = Duration.Inf, val notify_targ: Option[ActorRef] = None)
  extends AndroidCommand[T](function) {
  require(max_wait != null && notify_targ != null)

  private var _watchers = List[CommandWatcher]()
  private val _started = new AtomicBoolean(false)
  private val _awoken = new AtomicBoolean(false)
  // Locks/condition variables are used so external events can fail
  // us out. If we await the future and the future hangs, there's no
  // reason to have the future in the first place. Only the future can
  // "signal" itself. OTOH, anything can signal a condition. :)
  private val _lock = new ReentrantLock()
  private val _cond = _lock.newCondition()

  override def run() = synchronized {
    var watchers_snapshot = _watchers
    var was_running = false

    // Wow, this looks awkward. We need to reset watchers_snapshot
    // and was_running based on what they are when we have the lock
    // on watchers. This is because add_watcher does its interesting
    // stuff when it has the same lock. Thus, if this is not done, 
    // it's a race condition! And due to scoping, I can't just assign
    // them inside of the synchronized block. If I'm just derping really
    // hard and there's a better way to do this, *please* feel free to fix it.
    synchronized {
      watchers_snapshot = _watchers
      was_running = _started.getAndSet(true)
    }

    if (!was_running) {
      val fut = Future {
        super.run()
        result
        // Don't call tell_targ here. If run() fails, then
        // we still have to call tell_targ.
      }
      fut.onFailure {
        case _ =>
          force_fail(even_if_success = false)
          tell_targ(None)
          wake_up()
      }
      fut.onSuccess {
        case res =>
          tell_targ(res)
          wake_up()
      }

      // Fire off all monitors
      for (w <- watchers_snapshot) {
        w.start_monitoring()
      }
    }
  }

  def add_watcher(cmd_watcher: CommandWatcher, retroactive: Boolean = false) {
    var started_after_add = false

    synchronized {
      started_after_add = _started.get()

      if (!retroactive && started_after_add) {
        throw new RuntimeException("Tried to add CommandWatcher when command already running.")
      }

      _watchers = cmd_watcher :: _watchers
    }

    // If we've already started the command, fire up the watcher...
    // Done outside of synchronized block because this is (potentially) a 
    // fairly lengthy operation.
    if (started_after_add) {
      cmd_watcher.start_monitoring()
    }
  }

  private def wake_up() {
    val was_awoken = _awoken.getAndSet(true)
    if (!was_awoken) {
      _lock.lock()
      _cond.signalAll()
      _lock.unlock()
    }
  }

  private def tell_targ(res: Option[T]) {
    notify_targ map {
      // Can't do _ ! (this, res) because it's interpreted as
      // _.!(this, res), which is an invalid call to _.!.
      val tup = (this, res)
      _ ! tup
    }
  }

  def command_running = _started.get() && !_awoken.get()

  def await(): Option[T] = {
    // Attempt to run command on await(). Worst case, 
    // we waste a few cycles. Commands are guaranteed not to be
    // runnable multiple times per instance.
    run()
    try {
      _lock.lock()
      while (!_awoken.get()) {
        _cond.await()
      }
    } finally {
      _lock.unlock()
    }
    result
  }

  def force_fail(even_if_success: Boolean, autokill: Boolean) {
    super.force_fail(even_if_success)
    wake_up()
    // TODO: NOTE: autokill is useless at the time of writing.
    // There is no way to kill a future. Thus, tasks that never return just
    // take up precious threads. We'll want to migrate to a thread model instead
    // of a future model soon. But for now, Futures seem to work just fine.
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
      spawn {
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

class TimedCommandWatcher(watching: AsynchronousCommand[_], val time_limit: FiniteDuration) extends CommandWatcher(watching) {
  require(time_limit != null)
  thread_monitor_for_me = false

  val system = ActorSystem("system")

  override def do_monitor() {
    system.scheduler.scheduleOnce(time_limit) {
      fail_command(force = false)
    }
  }
}
