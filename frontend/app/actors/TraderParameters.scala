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
import utils.TradingSimulationActorSelection

class TraderParameters(out: ActorRef) extends Actor {
  implicit val formats = DefaultFormats

  val traders = new TradingSimulationActorSelection(context,
    ConfigFactory.load().getString("akka.backend.tradersActorSelection")).get

  traders ! ComponentRegistration(self, classOf[Trader], "frontend" + classOf[Trader])
  traders ! ComponentRegistration(self, classOf[TraderIdentity], "frontend" + classOf[TraderIdentity])

  def receive() = {
    case "getAllTraderParameters" =>
      traders ! GetTraderParameters

    case t: TraderIdentity =>
      out ! write(new SimpleTraderIdentity(t))

    case _ =>
  }

}

case class SimpleTraderIdentity(name: String, id: Long, strategy: String, parameters: List[String]) {
  def this(t: TraderIdentity) = {
    this(t.name, t.uid, t.strategy.toString(), t.parameters.parameters.map { case (k, v) => k + ": " + v.value() }.toList)
  }
}