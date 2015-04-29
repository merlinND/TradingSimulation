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
import ch.epfl.ts.traders.SimpleFXTrader
import com.typesafe.config.ConfigFactory
import ch.epfl.ts.indicators.SMA
import akka.testkit.EventFilter
import ch.epfl.ts.traders.RangeTrader
import ch.epfl.ts.indicators.RI2
import ch.epfl.ts.data.OHLC

class RangeTraderTest extends TestKit(ActorSystem("testSystem", ConfigFactory.parseString(
  """
  akka.loglevel = "DEBUG"
  akka.loggers = ["akka.testkit.TestEventListener"]
  """))) with WordSpecLike {

  /**
   * First part we test a range trader with no gapResitance and gapSupport 
   */
  val traderId: Long = 123L
  val symbol = (Currency.USD, Currency.CHF)
  val volume = 1000.0
  var gapSupport : Double = 0.0
  var gapResistance : Double = 0.0


  val trader = TestActorRef(Props(classOf[RangeTrader], traderId, gapSupport, gapResistance, volume, symbol))
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
    "buy price exceed range" in {
      within(1 second) {
        EventFilter.debug(message = "buy", occurrences = 1) intercept {
          trader ! OHLC(1L, 7, 7, 7, 7, 1000, 0L, 2)
        }
      }
    }
  }
  
  "A range trader " should {
    "not make two consecutives buys" in {
      within(1 second) {
        EventFilter.debug(message = "nothing is done", occurrences = 1) intercept {
          trader ! OHLC(1L, 10, 10, 10, 10, 1000, 0L, 2)
        }
      }
    }
  }
  
  "A range trader " should {
    "update its range only after an order " in {
      within(1 second) {
        EventFilter.debug(message = "range is updated", occurrences = 1) intercept {
          trader ! RI2(5, 10, 100)
          trader ! RI2(0.954, 0.964, 100)
        }
      }
    }
  } 
  
  "A range trader " should {
    "sell if support is breaken + he has something to sell " in {
      within(1 second) {
        EventFilter.debug(message = "sell", occurrences = 1) intercept {
          trader ! OHLC(1L, 2, 2, 2, 2, 1000, 0L, 2)
        }
      }
    }
  }
  
  
  /**
   * Second part we test a range trader with gapResistance of 3% and gapSupport of 1%
   */
  
  gapSupport = 0.01
  gapResistance = 0.03
  val traderWithGaps = TestActorRef(Props(classOf[RangeTrader], traderId, gapSupport, gapResistance, volume, symbol))
  
  
  traderWithGaps ! StartSignal

  "A range trader " should {
    "make a buy only if the prices exceed resistance + the gap " in {
      within(1 second) {
        EventFilter.debug(message = "buy", occurrences = 1) intercept {
          traderWithGaps ! RI2(5,10,100)
          traderWithGaps ! OHLC(1L,11, 11, 11, 11, 1000, 0L, 2)
          traderWithGaps ! RI2(5,10,100)
        }
      }
    }
  }
  
  "A range trader " should {
    "make a sell only if the prices is below support - the gap " in {
      within(1 second) {
        EventFilter.debug(message = "sell", occurrences = 1) intercept {
          traderWithGaps ! OHLC(1L,4.8, 4.8, 4.8, 4.8, 1000, 0L, 2)
        }
      }
    }
  }
}