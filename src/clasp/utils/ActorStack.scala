package clasp.utils

import org.slf4j.LoggerFactory
import akka.actor.Actor
import akka.japi.Procedure

/**
 * Allows an approach similar to aspects with Actors - invisibly rope in new
 * behavior to all your actors by mixing in these traits and then using
 * <code>wrappedReceive</code> instead of <code>receive</code>
 *
 * <code>
 * abstract class MyInstrumentedActor extends Actor with Slf4jLogging with ActorMetrics
 * </code>
 * See https://groups.google.com/d/topic/akka-user/J4QTzSj5usQ/discussion
 */
trait ActorStack { this: Actor =>

  /** Actor classes should implement this partialFunction for standard actor message handling */
  def wrappedReceive: Receive

  /** Actor classes can optionally override this for things such as printing their state after a message */
  def postReceive: Unit = {}

  /** Stackable traits should override and call super.receiveWrapper() for stacking functionality */
  @inline
  def receiveWrapper(x: Any) = { wrappedReceive(x) }

  /** Stackable traits should override this and call super for stacking this */
  @inline
  def postReceiveWrapper() = { postReceive }

  /** For logging MatchError exceptions */
  private[this] val stackLog = LoggerFactory.getLogger(getClass)
  private[this] val myPath = self.path.toString
  
  def receive: Receive = {
    case x: Any => try {
      val result = receiveWrapper(x)

      // This works because the partial function is actually being called
      // and we are storing the Unit() result 
      postReceiveWrapper
      result
    } catch {
      case nomatch: MatchError => {
        // Because each actor receive invocation could happen in a different thread, and MDC is thread-based,
        // we kind of have to set the MDC anew for each receive invocation.  :(
        org.slf4j.MDC.put("akkaSource", myPath)
        stackLog.info(s"Received unhandled message $x")
        postReceiveWrapper
      }
    }
  }
}

trait Slf4jLoggingStack extends Actor with ActorStack {
  private[this] val logger = LoggerFactory.getLogger(getClass)
  private[this] val myPath = self.path.toString
  logger.info("Starting actor " + getClass.getName)

  override def receiveWrapper(x: Any) {
    // Because each actor receive invocation could happen in a different thread, and MDC is thread-based,
    // we kind of have to set the MDC anew for each receive invocation.  :(
    org.slf4j.MDC.put("akkaSource", myPath)

    logger.info(s"Receiving $x")
    super.receiveWrapper(x)
  }
}

trait ActorMetrics extends Actor with ActorStack {
  // val metricReceiveTimer = Metrics.newTimer(getClass, "message-handler",
  //                                          TimeUnit.MILLISECONDS, TimeUnit.SECONDS)

  override def receiveWrapper(x: Any) {
    // val context = metricReceiveTimer.time()
    try {
      super.receiveWrapper(x)
    } finally {
      // context.stop()
    }
  }
}
