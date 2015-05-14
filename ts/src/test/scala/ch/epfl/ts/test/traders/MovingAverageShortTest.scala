package ch.epfl.ts.test.traders

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.math.floor
import scala.reflect.ClassTag
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.testkit.EventFilter
import ch.epfl.ts.component.StartSignal
import ch.epfl.ts.component.fetch.MarketNames
import ch.epfl.ts.data.BooleanParameter
import ch.epfl.ts.data.Currency
import ch.epfl.ts.data.CurrencyPairParameter
import ch.epfl.ts.data.Quote
import ch.epfl.ts.data.RealNumberParameter
import ch.epfl.ts.data.StrategyParameters
import ch.epfl.ts.data.TimeParameter
import ch.epfl.ts.data.WalletParameter
import ch.epfl.ts.engine.ForexMarketRules
import ch.epfl.ts.engine.Wallet
import ch.epfl.ts.test.ActorTestSuite
import ch.epfl.ts.test.FxMarketWrapped
import ch.epfl.ts.test.SimpleBrokerWrapped
import ch.epfl.ts.traders.MovingAverageTrader
import ch.epfl.ts.data.NaturalNumberParameter
import ch.epfl.ts.indicators.SMA
import ch.epfl.ts.data.CoefficientParameter

/**
 * @warning Some of the following tests are dependent and should be executed in the specified order.
 */
@RunWith(classOf[JUnitRunner])
class MovingAverageShortTest
  extends ActorTestSuite("MovingAverageTraderTestSystem") {

  val traderId: Long = 123L
  val symbol = (Currency.USD, Currency.CHF)
  val initialFunds: Wallet.Type = Map(symbol._2 -> 5000.0)
  val periods = Seq(5, 30)
  val tolerance = 0.0002

  val parameters = new StrategyParameters(
    MovingAverageTrader.INITIAL_FUNDS -> WalletParameter(initialFunds),
    MovingAverageTrader.SYMBOL -> CurrencyPairParameter(symbol),
     MovingAverageTrader.OHLC_PERIOD -> new TimeParameter(1 minute),
    MovingAverageTrader.SHORT_PERIODS -> NaturalNumberParameter(periods(0)),
    MovingAverageTrader.LONG_PERIODS -> NaturalNumberParameter(periods(1)),
    MovingAverageTrader.TOLERANCE -> RealNumberParameter(tolerance),
    MovingAverageTrader.WITH_SHORT -> BooleanParameter(true),
    MovingAverageTrader.SHORT_PERCENT -> CoefficientParameter(0.2))

  val marketID = 1L
  val market = builder.createRef(Props(classOf[FxMarketWrapped], marketID, new ForexMarketRules()), MarketNames.FOREX_NAME)
  val broker = builder.createRef(Props(classOf[SimpleBrokerWrapped], market.ar), "Broker")
  val trader = builder.createRef(Props(classOf[MovingAverageTraderWrapped], traderId, List(marketID), parameters, broker.ar), "Trader")

  market.ar ! StartSignal
  broker.ar ! StartSignal

  val (bidPrice, askPrice) = (0.90, 0.95)
  val testQuote = Quote(marketID, System.currentTimeMillis(), symbol._1, symbol._2, bidPrice, askPrice)
  market.ar ! testQuote
  broker.ar ! testQuote

  val initWallet = initialFunds;
  var cash = initialFunds(Currency.CHF)
  var shortings = 0.0
  var holdings = 0.0
  var volume = floor(cash / askPrice)
  "A trader " should {

    "register" in {
      within(1 second) {
        EventFilter.debug(message = "MATrader: Broker confirmed", occurrences = 1) intercept {
          trader.ar ! StartSignal
          trader.ar ! testQuote
        }
      }
    }

    "buy (20,3)" in {
      within(1 second) {
        EventFilter.debug(message = "executed bid volume: " + volume, occurrences = 1) intercept {
          trader.ar ! SMA(Map(5 -> 20.0, 30 -> 3.0))
        }
      }
      cash -= volume * askPrice
      holdings = volume
    }

    "sell(2.99999,3)" in {
      within(1 second) {
        EventFilter.debug(message = "executed ask volume: " + volume, occurrences = 1) intercept {
          trader.ar ! SMA(Map(5 -> 2.999999, 30 -> 3))
        }
      }
      cash += volume * bidPrice
      holdings = 0.0
      //we are going to short :
      volume = floor((cash * 0.2) / askPrice)
    }

    "short(2,3)" in {
      within(1 second) {
        EventFilter.debug(message = "executed ask volume: " + volume, occurrences = 1) intercept {
          trader.ar ! SMA(Map(5 -> 2, 30 -> 3))
        }
      }
      cash += volume * bidPrice
      shortings = volume
      //going to recover and buy
      volume = floor(cash / askPrice)
    }

    "recover and buy (20,3)" in {
      within(1 second) {
        EventFilter.debug(message = "executed bid volume: " + volume, occurrences = 1) intercept {
          trader.ar ! SMA(Map(5 -> 20.0, 30 -> 3.0))
        }
      }
      holdings = volume - shortings
      cash -= volume * askPrice
    }
    "nothing (20,3)" in {
      within(1 second) {
        EventFilter.debug(message = "An order is placed", occurrences = 0) intercept {
          trader.ar ! SMA(Map(5 -> 20.0, 30 -> 3.0))
        }
      }
      //going to sell and go short
      cash += holdings * bidPrice
      volume = holdings + floor((0.2 * cash) / askPrice)
    }
    "sell and go short (3,20)" in {
      within(1 second) {
        EventFilter.debug(message = "executed ask volume: " + volume, occurrences = 1) intercept {
          trader.ar ! SMA(Map(5 -> 3, 30 -> 20))
        }
      }
      shortings = volume - holdings
      volume = shortings
    }
    "recover short only (3,2.9999999999)" in {
      within(1 second) {
        EventFilter.debug(message = "executed bid volume: " + volume, occurrences = 1) intercept {
          trader.ar ! SMA(Map(5 -> 3, 30 -> 2.9999999999))
        }
      }
    }

  }

}

/**
 * A bit dirty hack to allow ComponentRef-like communication between components, while having them in Test ActorSystem
 * @param uid traderID
 * @param StrategyParameters parameters
 * @param broker ActorRef
 */
class MovingAverageTraderWrapped(uid: Long, marketIds: List[Long], parameters: StrategyParameters, broker: ActorRef)
  extends MovingAverageTrader(uid, marketIds, parameters) {
  override def send[T: ClassTag](t: T) {
    broker ! t
  }
  override def send[T: ClassTag](t: List[T]) = t.map(broker ! _)
}

