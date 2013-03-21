package core;


/**
 * Provides an interface to the
 * [[http://developer.android.com/tools/sdk/tools-notes.html Android SDK Tools]].
 */
package object sdktools {
  // http://stackoverflow.com/questions/6227759
  def runWithTimeout[T](timeoutMs: Long)(f: => T) : Option[T] = {
    import scala.actors.Futures.awaitAll
    import scala.actors.Futures.future
    
    //TODO Fix classpath errors?
    awaitAll(timeoutMs, future(f)).head.asInstanceOf[Option[T]]
  }

  def runWithTimeout[T](timeoutMs: Long, default: T)(f: => T) : T = {
    runWithTimeout(timeoutMs)(f).getOrElse(default)
  }
}
