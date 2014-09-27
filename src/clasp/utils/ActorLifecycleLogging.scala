package clasp.utils

import org.slf4j.LoggerFactory
import akka.actor.Actor

trait ActorLifecycleLogging extends Actor {
  val lifecycle_log = LoggerFactory.getLogger(getClass)
  import lifecycle_log.{ error, debug, info, trace }

  override def preStart() {
      debug(s"Calling preStart")
      super.preStart
  }  
  
  override def preRestart(reason: Throwable, message: Option[Any]) {
    debug(s"Calling preRestart")
    super.preRestart(reason, message)
  }
  
  override def postRestart(reason: Throwable) {
    debug(s"Calling postRestart")
    super.postRestart(reason)
  }

  override def postStop() {
    debug(s"Calling postStop")
    super.postStop
  }
  
}