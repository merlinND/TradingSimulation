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
import ch.epfl.ts.indicators.SMA
import ch.epfl.ts.test.ActorTestSuite
import ch.epfl.ts.test.FxMarketWrapped
import ch.epfl.ts.test.SimpleBrokerWrapped
import ch.epfl.ts.traders.MovingAverageTrader
import ch.epfl.ts.data.OHLC
import ch.epfl.ts.data.NaturalNumberParameter

/**
 * @warning Some of the following tests are dependent and should be executed in the specified order.
 */
@RunWith(classOf[JUnitRunner])
class MovingAverageTraderTest
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
    MovingAverageTrader.WITH_SHORT -> BooleanParameter(false))

  val marketID = 1L
  val market = builder.createRef(Props(classOf[FxMarketWrapped], marketID, new ForexMarketRules()), MarketNames.FOREX_NAME)
  val broker = builder.createRef(Props(classOf[SimpleBrokerWrapped], market.ar), "Broker")
  val trader = builder.createRef(Props(classOf[MovingAverageTraderWrapped], traderId,List(marketID),parameters, broker.ar), "Trader")

  market.ar ! StartSignal
  broker.ar ! StartSignal

  val (bidPrice, askPrice) = (0.90, 0.95)
  val testQuote = Quote(marketID, System.currentTimeMillis(), symbol._1, symbol._2, bidPrice, askPrice)
  market.ar ! testQuote
  broker.ar ! testQuote

  val initWallet = initialFunds;
  var cash = initialFunds(Currency.CHF)
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
    
    "notify its ohlcIndicator when he receives a quote" in {
      within(1 second) {
        EventFilter.debug(message = "olhc just received a quote", occurrences = 1) intercept {
          trader.ar ! testQuote
        }
      }
    }
    
    "notify its movingAverageIndicator when he receives an OHLC" in {
      within(1 second) {
        val ohlc = OHLC(1L, 0.0, 0.0,0.0,0.0,0.0,0L,0L)
        EventFilter.debug(message = "Moving Average Indicator received an OHLC: " + ohlc, occurrences = 1) intercept {
          trader.ar ! ohlc
        }
      }
    }

    "buy (20,3)" in {
      within(1 second) {
        EventFilter.debug(message = "Accepted order costCurrency: " + symbol._2 + " volume: " + volume, occurrences = 1) intercept {

          trader.ar ! SMA(Map(5L -> 20.0, 30L -> 3.0))
        }
      }
      cash -= volume * askPrice
    }

    "sell(3,20)" in {
      within(1 second) {
        EventFilter.debug(message = "Accepted order costCurrency: " + symbol._1 + " volume: " + volume, occurrences = 1) intercept {

          trader.ar ! SMA(Map(5L -> 3.0, 30L -> 20.0))
        }
      }
      cash += volume * bidPrice
      volume = floor(cash / askPrice)
    }

    "not buy(10.001,10)" in {
      within(1 second) {
        EventFilter.debug(message = "Accepted order costCurrency: " + symbol._2 + " volume: " + volume, occurrences = 0) intercept {

          trader.ar ! SMA(Map(5L -> 10.001, 30L -> 10.0))
        }
      }
    }

    // For small numbers > is eq to >=  (10*(1+0.0002) = 10.00199999)
    "buy(10.002,10)" in {
      within(1 second) {
        EventFilter.debug(message = "Accepted order costCurrency: " + symbol._2 + " volume: " + volume, occurrences = 1) intercept {
          trader.ar ! SMA(Map(5L -> 10.002, 30L -> 10))

        }
      }
      cash -= volume * askPrice
    }

    "not buy(10.003,10) (already hold a position)" in {
      within(1 second) {
        EventFilter.debug(message = "Accepted order costCurrency: " + symbol._2 + " volume: " + volume, occurrences = 0) intercept {

          trader.ar ! SMA(Map(5L -> 10.003, 30L -> 10))
        }
      }
    }

    "sell(9.9999,10)" in {
      within(1 second) {
        EventFilter.debug(message = "Accepted order costCurrency: " + symbol._1 + " volume: " + volume, occurrences = 1) intercept {

          trader.ar ! SMA(Map(5L -> 9.9999, 30L -> 10))
        }
      }
      cash += volume * bidPrice
      volume = floor(cash / askPrice)
    }

    "not sell(9.9999,10) (no holding)" in {
      within(1 second) {
        EventFilter.debug(message = "Accepted order costCurrency: " + symbol._1 + " volume: " + volume, occurrences = 0) intercept {

          trader.ar ! SMA(Map(5L -> 9.9999, 30L -> 10))
        }
      }
    }
  }
}


