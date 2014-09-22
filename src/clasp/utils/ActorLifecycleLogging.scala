package clasp.utils

import org.slf4j.LoggerFactory
import akka.actor.Actor

trait ActorLifecycleLogging extends Actor {
  val lifecycle_log = LoggerFactory.getLogger("clasp.utils.ActorLifecycleLogging")
  import lifecycle_log.{ error, debug, info, trace }

  override def preStart() {
      debug(s"preStart called for ${self}")
      super.preStart
  }  
  
  override def preRestart(reason: Throwable, message: Option[Any]) {
    debug(s"preRestart called for ${self}")
    super.preRestart(reason, message)
  }
  
  override def postRestart(reason: Throwable) {
    debug(s"postRestart called for ${self}")
    super.postRestart(reason)
  }

  override def postStop() {
    debug(s"postStop called for ${self}")
    super.postStop
  }
  
}