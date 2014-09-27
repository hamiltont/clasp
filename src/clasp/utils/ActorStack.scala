package clasp.utils

import org.slf4j.LoggerFactory
import akka.actor.Actor
import akka.japi.Procedure

/**
 * Allows an approach similar to aspects with Actors - invisibly rope in new
 * behavior to all your actors by mixing in these traits and then using
 * <code>wrappedReceive</code> instead of <code>receive</code>. Also supports
 * post receive calls for enabling behavior there
 *
 * <code>
 * class MyActor extends Actor with Slf4jLogging {
 *   def wrappedReceive = {
 *     case x => {}
 *   }
 *   override def postReceive = {
 *     case x => {}
 *   }
 * }
 * </code>
 * See https://groups.google.com/d/topic/akka-user/J4QTzSj5usQ/discussion
 */
trait ActorStack { this: Actor =>

  /** Actor classes should implement this partialFunction for standard actor message handling */
  def wrappedReceive: Receive

  /**
   * (Optional) Actor classes can override this. postReceive
   * handlers are passed the original message so you can match if needed
   */
  def postReceive: Receive = {
    case x => {}
  }

  /** Stackable traits should override and call super.receiveWrapper() for stacking functionality */
  @inline
  def receiveWrapper(x: Any, receive: Receive) = receive(x)

  /** Stackable traits should override this and call super for stacking this */
  @inline
  def postReceiveWrapper(x: Any, postreceive: Receive) = postreceive(x)

  /** For logging MatchError exceptions */
  private[this] val stackLog = LoggerFactory.getLogger(getClass)
  private[this] val myPath = self.path.toString

  def wrapReceive(receive: Receive = wrappedReceive, postreceive: Receive = postReceive): Receive = {
    case x: Any => try {
      val result = receiveWrapper(x, receive)
      postReceiveWrapper(x, postreceive)
      result
    } catch {
      case nomatch: MatchError => {
        // Because each actor receive invocation could happen in a different thread, and MDC is thread-based,
        // we kind of have to set the MDC anew for each receive invocation.  :(
        org.slf4j.MDC.put("akkaSource", myPath)
        stackLog.error(s"Received unhandled message $x")
        postReceiveWrapper(x, postreceive)
      }
    }
  }

  /** Setup default behavior */
  def receive: Receive = wrapReceive()
}

trait Slf4jLoggingStack extends Actor with ActorStack {
  private[this] val logger = LoggerFactory.getLogger(getClass)
  private[this] val myPath = self.path.toString
  logger.info(s"Starting actor ${getClass.getName} at $myPath")

  override def receiveWrapper(x: Any, original: Receive) {
    // Because each actor receive invocation could happen in a different thread, and MDC is thread-based,
    // we kind of have to set the MDC anew for each receive invocation.  :(
    org.slf4j.MDC.put("akkaSource", myPath)

    logger.info(s"Receiving $x")
    super.receiveWrapper(x, original)
  }
}

trait ActorMetrics extends Actor with ActorStack {
  // val metricReceiveTimer = Metrics.newTimer(getClass, "message-handler",
  //                                          TimeUnit.MILLISECONDS, TimeUnit.SECONDS)

  override def receiveWrapper(x: Any, original: Receive) {
    // val context = metricReceiveTimer.time()
    try {
      super.receiveWrapper(x, original)
    } finally {
      // context.stop()
    }
  }
}
