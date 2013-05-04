package clasp.core

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
import scala.sys.process.Process
import scala.sys.process.ProcessLogger
import scala.sys.process.stringToProcess
import scala.util.matching.Regex

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

object AsynchronousCommand {

  /** Creates an AsynchronousCommand from a given string. Optionally adds a timeout on the
    * command, as well as other watchers. Returns the newly built command.
    *  str - Command string
    *  timeout - The time the command has to complete. <= 0 millis means no timeout
    *  others - Other watchers to attach.
    * Guaranteed to return non-null.
    */
  def fromString(str: String, timeout: FiniteDuration = 0 millis, others: Traversable[CommandWatcher] = null): 
      AsynchronousCommand[(Int, String)] = {
    val cmd = new AsynchronousCommand(() => 
      // This is done because calling command !! will throw a 
      // RuntimeException if the command returns a non-zero exit code.
      // Thus, we catch the error (and try to match it to the 
      // standardized Nonzero exit value: ...) here.
      // If we want, we can just undo this and let the exception live.
      // That'll autofail the AsynchronousCommand. This way just lets
      // you look at the exit code.
      try { 
        val out = new StringBuilder
        val logger = ProcessLogger(
          (o: String) => out.append(o),
          (e: String) => out.append(e))
        str ! logger
        (0, out.toString) 
      } catch {
        case e: RuntimeException => 
          val match_rgx = """Nonzero exit value: (-?[0-9]+)""".r
          match_rgx findFirstIn e.getMessage match {
            case Some(match_rgx(exit_str)) => 
              val exit_code = Integer.parseInt(exit_str)
              (exit_code, "")
            case None => throw e
          }
      })

    // If the user wants a timeout, attach a TimedCommandWatcher
    if(timeout > (0 millis)) {
      val watch = new TimedCommandWatcher(cmd, timeout)
      cmd.add_watcher(watch)
    }

    if (others != null) {
      for (v <- others if v != null) {
        cmd.add_watcher(v)
      }
    }

    cmd
  }

  // TODO: Remove duplicated code.
  def fromSeq(seq: Seq[String], timeout: FiniteDuration = 0 millis,
      others: Traversable[CommandWatcher] = null): 
      AsynchronousCommand[(Int, String)] = {
    val cmd = new AsynchronousCommand(() => 
      // This is done because calling command !! will throw a 
      // RuntimeException if the command returns a non-zero exit code.
      // Thus, we catch the error (and try to match it to the 
      // standardized Nonzero exit value: ...) here.
      // If we want, we can just undo this and let the exception live.
      // That'll autofail the AsynchronousCommand. This way just lets
      // you look at the exit code.
      try { 
        val out = new StringBuilder
        val logger = ProcessLogger(
          (o: String) => out.append(o),
          (e: String) => out.append(e))
        Process(seq) ! logger
        (0, out.toString) 
      } catch {
        case e: RuntimeException => 
          val match_rgx = """Nonzero exit value: (-?[0-9]+)""".r
          match_rgx findFirstIn e.getMessage match {
            case Some(match_rgx(exit_str)) => 
              val exit_code = Integer.parseInt(exit_str)
              (exit_code, "")
            case None => throw e
          }
      })

    // If the user wants a timeout, attach a TimedCommandWatcher
    if(timeout > (0 millis)) {
      val watch = new TimedCommandWatcher(cmd, timeout)
      cmd.add_watcher(watch)
    }

    if (others != null) {
      for (v <- others if v != null) {
        cmd.add_watcher(v)
      }
    }

    cmd
  }

  /** Gets the result of running the command created with 
    * AsynchronousCommand.fromString(str, timeout, others). 
    */
  def resultOf(str: String, timeout: FiniteDuration = 0 millis,
      others: Traversable[CommandWatcher] = null): Option[String] = {
    val cmd = fromString(str, timeout, others)
    /* TODO: Is this used?
    val convert_fn = (tup: (Int, String)) => {
      if (tup._1 == 0) Some(tup._2) else None
    };
    */

    val result = cmd.await().flatMap((tup) => 
      if (tup._1 == 0)
        Some(tup._2) 
      else
        None
    )
    println(s"Result of '$str' is '${result.get}'")
    return result
  }

  // TODO: Remove duplicated cude. 
  def resultOfSeq(seq: Seq[String], timeout: FiniteDuration = 0 millis,
      others: Traversable[CommandWatcher] = null): Option[String] = {
    val cmd = fromSeq(seq, timeout, others)
    cmd.await().flatMap((tup) => 
      if (tup._1 == 0)
        Some(tup._2) 
      else
        None
    )
  }

  /** Similar effect to resultOf, except this will run a regex through the command's
    * output upon completion.
    */
  def resultsOf(str: String, regex: Regex, timeout: FiniteDuration = 0 millis,
      others: Traversable[CommandWatcher] = null): Option[Vector[String]] = {
    val res = resultOf(str, timeout, others)
    res.map((s: String) => { 
      val r = for (regex(name) <- regex.findAllIn(s)) yield name
      r.toVector
    })
  }
}
