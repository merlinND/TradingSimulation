package actors

import akka.actor.Props
import akka.actor.Actor
import akka.actor.ActorRef
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.actor.ActorPath
import play.libs.Akka
import ch.epfl.ts.data.OHLC
import scala.concurrent.ExecutionContext.Implicits.global
import ch.epfl.ts.component.ComponentRegistration
import scala.reflect.ClassTag
import net.liftweb.json._
import net.liftweb.json.Serialization.write
import com.typesafe.config.ConfigFactory
import ch.epfl.ts.traders.Trader
import ch.epfl.ts.engine.TraderMessage
import ch.epfl.ts.engine.GetTraderParameters
import ch.epfl.ts.engine.TraderIdentity

class TraderParameters(out: ActorRef) extends Actor {
  implicit val formats = DefaultFormats

  val config = ConfigFactory.load()
  val name = config.getString("akka.backend.systemName")
  val hostname = config.getString("akka.backend.hostname")
  val port = config.getString("akka.backend.port")
  val actors = context.actorSelection("akka.tcp://" + name + "@" + hostname + ":" + port + "/user/*")

  actors ! ComponentRegistration(self, classOf[Trader], "frontend" + classOf[Trader])
  actors ! ComponentRegistration(self, classOf[TraderIdentity], "frontend" + classOf[TraderIdentity])

  def receive() = {
    case "getAllTraderParameters" =>
      actors ! GetTraderParameters
    
    case t: TraderIdentity =>
      out ! write(new SimpleTraderIdentity(t))
    
    case _ =>
  }

}

case class SimpleTraderIdentity(name: String, id: Long, strategy: String, parameters: List[String]) {
  def this(t: TraderIdentity) = {
    this(t.name, t.uid, t.strategy.toString(), t.parameters.parameters.map{ case (k,v) => k + ": " + v }.toList)
  }
}