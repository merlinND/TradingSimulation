package actors

import scala.language.postfixOps
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
import ch.epfl.ts.data.Currency.Currency
import scala.collection.mutable.Set
import ch.epfl.ts.data.TimeParameter
import ch.epfl.ts.data.OHLC
import scala.collection.mutable.HashMap
import utils.TradingSimulationActorSelection

/**
 * Computes OHLC for each currency based on the quote data fetched in the TradingSimulation backend
 *
 * Since each trader in the backend instantiates its own indicators, we simply compute a global
 * OHLC to display on a graph in the frontend
 * Starts a child actor for each symbol and converts the result to JSON which included the symbol
 */
class GlobalOhlc(out: ActorRef) extends Actor {
  implicit val formats = DefaultFormats

  type Symbol = (Currency, Currency)
  var workers = HashMap[Symbol, ActorRef]()
  val ohlcPeriod = 60 minutes

  val fetchers = new TradingSimulationActorSelection(context,
    ConfigFactory.load().getString("akka.backend.fetcherActorSelection")).get
    
  fetchers ! ComponentRegistration(self, classOf[Quote], "frontendQuote")

  def receive() = {
    case q: Quote =>
      val symbol: Symbol = (q.whatC, q.withC)
      val worker = workers.getOrElseUpdate(symbol,
        context.actorOf(Props(classOf[OhlcIndicator], q.marketId, symbol, ohlcPeriod)))
      worker ! q

    case ohlc: OHLC =>
      workers.find(_._2 == sender) match {
        case Some((symbol: Symbol, _)) =>
          out ! write(SymbolOhlc(symbol._1, symbol._2, ohlc))
        case _ =>
      }

    case _ =>
  }

}

case class SymbolOhlc(whatC: Currency, withC: Currency, ohlc: OHLC)
