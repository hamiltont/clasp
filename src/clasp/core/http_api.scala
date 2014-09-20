package clasp.core

import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.ActorRef
import akka.pattern.ask
import spray.http.HttpMethods._
import spray.http.StatusCodes.InternalServerError
import spray.routing._
import scala.concurrent.duration._
import spray.httpx.marshalling._
import spray.http._
import scala.util.Success
import scala.util.Failure
import spray.httpx.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import NodeManager._
import Node._
import clasp.Person
import spray.json.DefaultJsonProtocol
import spray.json.JsonFormat
import spray.json.JsValue
import spray.json.JsArray
import spray.json.JsObject
import spray.json.JsNumber
import spray.json.JsString
import spray.json._
import java.awt.Color
import clasp.core.EmulatorManager.ListEmulators

object MyJsonProtocol extends DefaultJsonProtocol {

  // Teach Spray how we want to marshal an ActorRef
  implicit object actorFormat extends JsonFormat[ActorRef] {
    def write(d: ActorRef) = {
      if (d == null)
        JsNull
      else
        JsString(d.toString)
    }

    // Not worried about recreating ActorRefs currently, but I may 
    // eventually use ActorSelection and then resolve to ActorRef in a future
    def read(value: JsValue) =
      value.asJsObject.getFields("path") match {
        case Seq(JsString(path)) =>
          null
        case _ => deserializationError("ActorRef expected")
      }
  }

  implicit val nodeFormat = jsonFormat(NodeDescription, "ip", "name", "emulators", "asOf")
}

class HttpApi(val nodeManager: ActorRef,
  val emulatorManger: ActorRef) extends HttpServiceActor {

  import MyJsonProtocol._

  def receive = runRoute {
    path("nodes") {
      get {
        onComplete(nodeManager.ask(NodeList())(3.seconds).mapTo[List[NodeDescription]]) {
          case Success(value) => {
            complete(value)
          }
          case Failure(ex) => {
            complete(InternalServerError, s"An error occurred: ${ex.getMessage}")
          }
        }
      }
    } ~
    path("emulators") {
      get {
        onComplete(emulatorManger.ask(ListEmulators())(3.seconds).mapTo[List[ActorRef]]) {
          case Success(value) => {
            complete(value)
          }
          case Failure(ex) => {
            complete(InternalServerError, s"An error occurred: ${ex.getMessage}")
          }
        }
      }
    }
  }

}