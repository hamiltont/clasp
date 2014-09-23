package clasp.utils


import scala.concurrent.{ExecutionContext, CanAwait, Awaitable, Future, Promise}
import scala.concurrent.duration.Duration
import scala.util.Try
import org.jboss.netty.util.{TimerTask, HashedWheelTimer}
import java.util.concurrent.{TimeoutException, TimeUnit}
import org.jboss.netty.util.Timeout
import java.util.concurrent.atomic.{AtomicReference, AtomicBoolean}

  // Let's lobby for this to be added to scala.Predef ;)
  // Lets you write things like:
  //      { val someVal = func(); log.info(someVal); someVal }
  // as:  { func() tap log.info }
//  implicit class KestrelCombinator[A](val a: A) extends AnyVal {
//    def withSideEffect(fun: A => Unit): A = { fun(a); a }
//  def tap(fun: A => Unit): A = withSideEffect(fun)
//}


// From http://eng.kifi.com/future-safefuture-timeout-cancelable/ and 
// https://gist.github.com/andrewconner/6362491
object FancyFuture {
/*  We've run into a few common pitfalls when dealing with Futures in Scala, so I wrote these three helpful
 *  classes to give some baked-in functionality.
 *
 *  I'd love to hear about other helpers you're using like these, or if you have improvement suggestions.
 *  github@andrewconner.org / @connerdelights
 */
 

/*  SafeFuture is a Future that simply binds an onFailure to any future passed in to deal with hard failures.
 *  Future failures can and should be handled, but for fire-and-forget and map-chaining, it's often easy to
 *  not handle failures explicitly. The result is a exception that goes nowhere including failure to your 
 *  go to your logs. Mysterious failure is not good, so SafeFuture lets you design an application level failure
 *  monitoring strategy. This won't save your result, but at least you'll be aware it failed.
 * 
 *  As an added bonus, there's a second apply in SafeFuture that lets you name the future for logging purposes.
 *  If you like this, our Akka hook: http://eng.42go.com/handling-akka-actor-exceptions-with-prerestart/
 */

class SafeFuture[+T](future: Future[T], name: Option[String] = None)(implicit executor: ExecutionContext) extends Future[T] {

  future match {
    case _: SafeFuture[_] =>
    case dangerousFuture =>
      dangerousFuture.onFailure {
        case cause: Throwable =>
          cause.printStackTrace() // should always work, to stderr
          try {
            // Should work if the Logger is up:
            // Logger(getClass).error("[SafeFuture] Failure of future" + name.map(": " + _).getOrElse(""), cause)
            
            // ... and add your custom monitoring. Ideas: emails, healthcheck API calls, etc.
          } catch {
            case _: Throwable => // tried our best.
          }
      }
  }

  // Just a wrapper around Future, so we can match on SafeFuture explicitly.
  def onComplete[U](func: (Try[T]) => U)(implicit executor: ExecutionContext): Unit = future.onComplete(func)
  def isCompleted: Boolean = future.isCompleted
  def value: Option[Try[T]] = future.value
  def ready(atMost: Duration)(implicit permit: CanAwait): this.type = { future.ready(atMost); this }
  def result(atMost: Duration)(implicit permit: CanAwait): T = future.result(atMost)

}
object SafeFuture {
  // Replicate Future {} helper 
  def apply[T](func: => T)(implicit executor: ExecutionContext) = new SafeFuture(Future { func })
  def apply[T](name: String)(func: => T)(implicit executor: ExecutionContext) = new SafeFuture(Future { func }, Some(name))
}

  /*  TimeoutFuture lets you establish a SLA timeout for a Future. Simply, if that time passes and the future 
 *  has not resolved, it resolves as a failure with a TimeoutException. Typically, this should be explicitly
 *  handled by your application supplying a default value, returning an error, etc.
 *
 *  For efficiency, we use netty's HashWheelTimer. This does not give explicit guarantees about exactly when
 *  it runs, and instead provides a best-effort timer. So: It's much lighter than a scheduler, but less accurate.
 *  
 *  Example execution:
 *    implicit val after = Duration(1, "second")
 *    TimeoutFuture(Future { println("Started!"); Thread.sleep(5000); println("Ended"); }, println("Cancelled!"))
 */

/* CancelableFuture creates a future that can be cancelled, from a blocking code block. This is not
 * usually for SLA timeout guarentees like above. Rather, it's for when you have a complex long-running
 * blocking bit of code that you want to be able to kill. So, this returns a method that, once called,
 * will harshly interupt the thread and stop the code. It's nasty, be careful with it.
 *
 * Example non-deterministic usage:
 *   val (fut, cancel) = CancellableFuture(Thread.sleep((Math.random*2000).toInt tap println))
 *   Thread.sleep(1000)
 *   val wasCancelled = cancel()
 *   println("wasCancelled: " + wasCancelled)
 *   fut.onFailure { case ex: Throwable => println("failed: " + ex.getClass) }
 *   fut.onSuccess { case i => println("success!" + i) }
 */
object CancelableFuture {
  def apply[T](fun: => T)(implicit ex: ExecutionContext): (Future[T], () => Boolean) = {
    val promise = Promise[T]()
    val future = promise.future
    val threadRef = new AtomicReference[Thread](null)
    promise tryCompleteWith SafeFuture { // If you want, swap with normal `Future`
      val t = Thread.currentThread
      threadRef.synchronized { threadRef.set(t) }
      try fun finally { threadRef.synchronized(threadRef.set(null)) }
    }

    (future, () => {
      threadRef.synchronized { Option(threadRef getAndSet null) foreach { _.interrupt() } }
      promise.tryFailure(new java.util.concurrent.CancellationException)
    })
  }
}

}