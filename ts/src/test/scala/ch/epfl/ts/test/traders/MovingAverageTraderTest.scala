package ch.epfl.ts.test.traders

import scala.language.postfixOps
import scala.concurrent.duration.DurationInt
import org.scalatest.WordSpecLike
import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.actor.Props
import akka.testkit.EventFilter
import akka.testkit.TestActorRef
import akka.testkit.TestKit
import ch.epfl.ts.component.StartSignal
import ch.epfl.ts.data.Currency._
import ch.epfl.ts.indicators.SMA
import ch.epfl.ts.traders.MovingAverageTrader
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import ch.epfl.ts.engine.MarketFXSimulator
import ch.epfl.ts.brokers.StandardBroker
import ch.epfl.ts.engine.ForexMarketRules
import scala.reflect.ClassTag
import ch.epfl.ts.data.Currency
import akka.actor.ActorRef
import akka.util.Timeout
import ch.epfl.ts.component.fetch.MarketNames
import ch.epfl.ts.data.Quote

@RunWith(classOf[JUnitRunner])
class MovingAverageTraderTest extends TestKit(ActorSystem("testSystem", ConfigFactory.parseString(
  """
  akka.loglevel = "DEBUG"
  akka.loggers = ["akka.testkit.TestEventListener"]
  """))) with WordSpecLike {

  val tId: Long = 123L
  val volume = 1000.0
  val shortPeriod = 5
  val longPeriod = 30
  val periods = List(5, 30)
  val tolerance = 0.0002
  val symbol = (Currency.USD, Currency.CHF)
  val initialFund = 5000.0;
  val initialCurrency = Currency.CHF

  val marketID = 1L
  val market = system.actorOf(Props(classOf[FxMarketWrapped], marketID, new ForexMarketRules()), MarketNames.FOREX_NAME)
  val broker: ActorRef = system.actorOf(Props(classOf[SimpleBrokerWrapped], market), "Broker")
  val trader = system.actorOf(Props(classOf[MATraderWrapped], tId, symbol, initialFund, initialCurrency, shortPeriod, longPeriod, tolerance,false, broker), "Trader")

  market ! StartSignal
  broker ! StartSignal
  trader ! StartSignal

  market ! Quote(marketID, System.currentTimeMillis(), USD, CHF, 10.2, 13.2)
  //TODO broker and trader gets quote from market
  broker ! Quote(marketID, System.currentTimeMillis(), USD, CHF, 10.2, 13.2)
  trader ! Quote(marketID, System.currentTimeMillis(), USD, CHF, 10.2, 13.2)

  /**
   * @warning The following tests are dependent and should be executed in the specified order.
   */
  "A trader " should {
    "buy (20,3)" in {
      within(1 second) {
        EventFilter.debug(message = "buying " + volume, occurrences = 1) intercept {
          trader ! SMA(Map(5 -> 20.0, 30 -> 3.0))
        }
      }
    }

    "sell(3,20)" in {
      within(1 second) {
        EventFilter.debug(message = "selling " + volume, occurrences = 1) intercept {
          trader ! SMA(Map(5 -> 3.0, 30 -> 20.0))
        }
      }
    }

    "not buy(10.001,10)" in {
      within(1 second) {
        EventFilter.debug(message = "buying " + volume, occurrences = 0) intercept {
          trader ! SMA(Map(5 -> 10.001, 30 -> 10.0))
        }
      }
    }

    // For small numbers > is eq to >=  (10*(1+0.0002) = 10.00199999)
    "buy(10.002,10)" in {
      within(1 second) {
        EventFilter.debug(message = "buying " + volume, occurrences = 1) intercept {
          trader ! SMA(Map(5 -> 10.002, 30 -> 10))
        }
      }
    }

    "not buy(10.003,10) (already hold a position)" in {
      within(1 second) {
        EventFilter.debug(message = "buying " + volume, occurrences = 0) intercept {
          trader ! SMA(Map(5 -> 10.003, 30 -> 10))
        }
      }
    }

    "sell(9.9999,10)" in {
      within(1 second) {
        EventFilter.debug(message = "selling " + volume, occurrences = 1) intercept {
          trader ! SMA(Map(5 -> 9.9999, 30 -> 10))
        }
      }
    }

    "not sell(9.9999,10) (no holding)" in {
      within(1 second) {
        EventFilter.debug(message = "selling " + volume, occurrences = 0) intercept {
          trader ! SMA(Map(5 -> 9.9999, 30 -> 10))
        }
      }
    }
  }
}

class MATraderWrapped(uid: Long, symbol: (Currency, Currency), initialFund: Double, initialCurrency: Currency,
                      shortPeriod: Int, longPeriod: Int, tolerance: Double, withShort: Boolean, broker: ActorRef) extends MovingAverageTrader(uid, symbol, initialFund, initialCurrency, shortPeriod, longPeriod, tolerance, withShort) {
  override def send[T: ClassTag](t: T) {
    broker ! t
  }

  override def send[T: ClassTag](t: List[T]) = t.map(broker ! _)
}

/**
 * Analogical class for the broker.
 */
class SimpleBrokerWrapped(market: ActorRef) extends StandardBroker {
  override def send[T: ClassTag](t: T) {
    market ! t
  }

  override def send[T: ClassTag](t: List[T]) = t.map(market ! _)
}

class FxMarketWrapped(uid: Long, rules: ForexMarketRules) extends MarketFXSimulator(uid, rules) {
  import context.dispatcher
  override def send[T: ClassTag](t: T) {
    val broker = context.actorSelection("../Broker")
    implicit val timeout = new Timeout(100 milliseconds)
    for (res <- broker.resolveOne()) {
      res ! t
    }
  }
}