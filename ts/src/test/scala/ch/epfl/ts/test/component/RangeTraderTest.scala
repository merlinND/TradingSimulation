package ch.epfl.ts.test.component

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import org.scalatest.WordSpecLike
import akka.actor.ActorSystem
import akka.actor.Props
import akka.testkit.TestActorRef
import akka.testkit.TestKit
import ch.epfl.ts.component.StartSignal
import ch.epfl.ts.data.Currency
import com.typesafe.config.ConfigFactory
import ch.epfl.ts.indicators.SMA
import akka.testkit.EventFilter
import ch.epfl.ts.traders.RangeTrader
import ch.epfl.ts.indicators.RI2
import ch.epfl.ts.data.OHLC
import ch.epfl.ts.test.TestHelpers
import ch.epfl.ts.engine.Wallet
import ch.epfl.ts.data.CurrencyPairParameter
import ch.epfl.ts.data.StrategyParameters
import ch.epfl.ts.data.RealNumberParameter
import ch.epfl.ts.data.WalletParameter
import ch.epfl.ts.data.CoefficientParameter
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class RangeTraderTest
  extends TestKit(TestHelpers.makeTestActorSystem("RangeTraderTestActorSystem"))
  with WordSpecLike {

  /**
   * First part we test a range trader with no gapResitance and gapSupport 
   */
  val traderId: Long = 123L
  val initialFunds: Wallet.Type = Map(Currency.USD -> 1000.0)
  val parameters = new StrategyParameters(
    RangeTrader.INITIAL_FUNDS -> WalletParameter(initialFunds),
    RangeTrader.SYMBOL -> CurrencyPairParameter(Currency.USD, Currency.CHF),
    RangeTrader.VOLUME -> RealNumberParameter(1000.0),
    RangeTrader.ORDER_WINDOW -> CoefficientParameter(0.15)
  )
  
  val trader = TestActorRef(Props(classOf[RangeTrader], traderId, parameters))
  trader ! StartSignal

  "A range trader " should {
    "receive a range and collect support and resistance" in {
      within(1 second) {
        EventFilter.debug(message = "received range with support = 4.0 and resistance = 6.0", occurrences = 1) intercept {
          trader ! RI2(4, 6, 100)
        }
      }
    }
  }
  
  "A range trader " should {
    "recompute its range if he breaks the resistance " in {
      within(1 second) {
        EventFilter.debug(message = "resitance broken allow range recomputation", occurrences = 1) intercept {
          trader ! OHLC(1L, 43, 43, 43, 43, 1000, 0L, 2)
          trader ! RI2(4, 6, 100)
        }
      }
    }
  }
  
  //buy window is 4 + (6-4)*0.15 = 4.3
    "A range trader " should {
    "buy if prices are in the selling window " in {
      within(1 second) {
        EventFilter.debug(message = "buy", occurrences = 1) intercept {
          trader ! OHLC(1L, 4.3, 4.3, 4.3, 4.3, 1000, 0L, 2)
        }
      }
    }
  }  
 
  //range size is 2 so sell window start at 6 - (6-4)*0.15 = 5.7
  "A range trader " should {
    "sell if price is in the sell window " in {
      within(1 second) {
        EventFilter.debug(message = "sell", occurrences = 1) intercept {
          trader ! OHLC(1L, 5.7, 5.7, 5.7, 5.7, 1000, 0L, 2)
        }
      }
    }
  }
  
  "A range trader " should {
    "not make to consecutives buys " in {
      within(1 second) {
        EventFilter.debug(message = "buy", occurrences = 1) intercept {
          trader ! OHLC(1L, 4.3, 4.3, 4.3, 4.3, 1000, 0L, 2)
        }
        EventFilter.debug(message="nothing is done", occurrences = 1) intercept {
          trader ! OHLC(1L, 4.3, 4.3, 4.3, 4.3, 1000, 0L, 2)
        }
      }
    }
  }
  
  
  "A range trader " should {
    "update its range only after a sell order " in {
      within(1 second) {
        EventFilter.debug(message = "range is updated", occurrences = 1) intercept {
          //panic sell order
          trader ! OHLC(1L, 1, 1, 1, 1, 1000, 0L, 2)
          trader ! RI2(5, 10, 100)
          trader ! RI2(0.954, 0.964, 100)
        }
      }
    }
  } 
}