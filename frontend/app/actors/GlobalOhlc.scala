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
import ch.epfl.ts.data.Quote
import ch.epfl.ts.indicators.OhlcIndicator
import ch.epfl.ts.data.Currency
import scala.collection.mutable.Set
import ch.epfl.ts.data.TimeParameter
import ch.epfl.ts.data.OHLC
import scala.collection.mutable.HashMap

class GlobalOhlc(out: ActorRef) extends Actor {
  implicit val formats = DefaultFormats
  
  type symbol = (Currency.Currency, Currency.Currency)
  var indicators = HashMap[symbol, ActorRef]()
  val ohlcPeriod = 1 minute
  
  val config = ConfigFactory.load()
  val name = config.getString("akka.backend.systemName")
  val hostname = config.getString("akka.backend.hostname")
  val port = config.getString("akka.backend.port")
  val actors = context.actorSelection("akka.tcp://" + name + "@" + hostname + ":" + port + "/user/*")
  actors ! ComponentRegistration(self, classOf[Quote], "frontendQuote")
  
  def receive() = {
    case q: Quote =>
      val symbol: symbol = (q.whatC, q.withC)
      if (!indicators.contains(symbol)) {
        val ohlcIndicator = context.actorOf(Props(classOf[OhlcIndicator], q.marketId, symbol, ohlcPeriod))
        indicators += (symbol -> ohlcIndicator)
      }
      indicators.values.map( _ ! q)
    
    case ohlc: OHLC =>
      println(">>> OHLC: " + ohlc)
      out ! write(ohlc)
      
    case _ =>
  }

}

