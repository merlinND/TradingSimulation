package ch.epfl.ts.test.traders

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.reflect.ClassTag
import org.junit.runner.RunWith
import org.scalatest.WordSpecLike
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.testkit.EventFilter
import ch.epfl.ts.component.StartSignal
import ch.epfl.ts.component.fetch.MarketNames
import ch.epfl.ts.data.CoefficientParameter
import ch.epfl.ts.data.Currency
import ch.epfl.ts.data.CurrencyPairParameter
import ch.epfl.ts.data.MarketAskOrder
import ch.epfl.ts.data.MarketBidOrder
import ch.epfl.ts.data.OHLC
import ch.epfl.ts.data.Quote
import ch.epfl.ts.data.RealNumberParameter
import ch.epfl.ts.data.Register
import ch.epfl.ts.data.StrategyParameters
import ch.epfl.ts.data.WalletParameter
import ch.epfl.ts.engine.ExecutedAskOrder
import ch.epfl.ts.engine.ExecutedBidOrder
import ch.epfl.ts.engine.ForexMarketRules
import ch.epfl.ts.engine.FundWallet
import ch.epfl.ts.engine.GetWalletFunds
import ch.epfl.ts.engine.Wallet
import ch.epfl.ts.test.ActorTestSuite
import ch.epfl.ts.test.FxMarketWrapped
import ch.epfl.ts.test.SimpleBrokerWrapped
import ch.epfl.ts.traders.RangeTrader
import ch.epfl.ts.data.Quote
import org.scalatest.junit.JUnitRunner
import ch.epfl.ts.indicators.RangeIndic

@RunWith(classOf[JUnitRunner])
class RangeTraderTest
  extends ActorTestSuite("RangeTraderTestSuite") {

  /**
   * First part we test a range trader with no gapResitance and gapSupport 
   */
  val traderId = 123L
  val marketId = 1L
  
  val initialFunds: Wallet.Type = Map(Currency.CHF -> 1000.0)
  val parameters = new StrategyParameters(
    RangeTrader.INITIAL_FUNDS -> WalletParameter(initialFunds),
    RangeTrader.SYMBOL -> CurrencyPairParameter(Currency.USD, Currency.CHF),
    RangeTrader.ORDER_WINDOW -> CoefficientParameter(0.15)
  )
  
  val symbol = (Currency.USD, Currency.CHF)
  val (bidPrice, askPrice) = (4.3, 4.8)
  val testQuote = Quote(1L, System.currentTimeMillis(), symbol._1, symbol._2, bidPrice, askPrice)
  val market = builder.createRef(Props(classOf[FxMarketWrapped], marketId, new ForexMarketRules()), MarketNames.FOREX_NAME)
  val broker = builder.createRef(Props(classOf[SimpleBrokerWrapped], market.ar), "Broker")
  val trader = builder.createRef(Props(classOf[RangeTraderWrapped], traderId, List(marketId), parameters, broker.ar), "RangeTrader")
  
  
  market.ar ! StartSignal
  broker.ar ! StartSignal
  market.ar ! testQuote
  broker.ar ! testQuote

  "A range trader " should {
  
    "register" in {
      within(1 second) {
        EventFilter.debug(message = "RangeTrader: Broker confirmed", occurrences = 1) intercept {
          trader.ar ! StartSignal
          trader.ar ! testQuote
        }
      }
    }

    "receive a range and collect support and resistance" in {
      within(1 second) {
        EventFilter.debug(message = "received range with support = 4.0 and resistance = 6.0", occurrences = 1) intercept {
          trader.ar ! RangeIndic(4, 6, 100)
        }
      }
    }
  
    "allow range recomputation if its range breaks the resistance " in {
      within(1 second) {
        EventFilter.debug(message = "resitance broken allow range recomputation", occurrences = 1) intercept {
          trader.ar ! OHLC(1L, 43, 43, 43, 43, 1000, 0L, 2)
        }
      }
    }
    
    "replace the range when received a new one " in {
      within(1 second) {
        EventFilter.debug(message = "range is updated", occurrences = 1) intercept {
          trader.ar ! RangeIndic(4, 6, 100)
        }
      }
    } 
  
  //buy window is 4 + (6-4)*0.15 = 4.3
    "buy if prices are in the buying window " in {
      within(1 second) {
        EventFilter.debug(message = "RangeTrader: bid executed", occurrences = 1) intercept {
          trader.ar ! OHLC(1L, 4.3, 4.3, 4.3, 4.3, 1000, 0L, 2) 
        }
      }
    }
    
  //range size is 2 so sell window start at 6 - (6-4)*0.15 = 5.7
    "sell if price is in the sell window " in {
      within(1 second) {
        EventFilter.debug(message = "RangeTrader: ask executed", occurrences = 1) intercept {
          trader.ar ! OHLC(1L, 5.7, 5.7, 5.7, 5.7, 1000, 0L, 2)
        }
      }
    }
  
    "not make to consecutives buys " in {
      within(1 second) {
        EventFilter.debug(message = "RangeTrader: bid executed", occurrences = 1) intercept {
          trader.ar ! OHLC(1L, 4.3, 4.3, 4.3, 4.3, 1000, 0L, 2)
        }
     }   
     within(1 second) { 
        EventFilter.debug(message="nothing is done", occurrences = 1) intercept {
          trader.ar ! OHLC(1L, 4.3, 4.3, 4.3, 4.3, 1000, 0L, 2)
        }
     }
  }

   "update its range after a sell order " in {
      within(1 second) {
        EventFilter.debug(message = "RangeTrader: ask executed", occurrences = 1) intercept {
          //panic sell order
          trader.ar ! OHLC(1L, 1, 1, 1, 1, 1000, 0L, 2)
          }
        }
      within(1 second) {
        EventFilter.debug(message = "range is updated", occurrences = 1) intercept {
          trader.ar ! RangeIndic(5, 10, 100)
          trader.ar ! RangeIndic(0.954, 0.964, 100)
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
class RangeTraderWrapped(uid: Long, marketIds: List[Long], parameters: StrategyParameters, broker: ActorRef)
  extends RangeTrader(uid, marketIds, parameters) {
  override def send[T: ClassTag](t: T) {
    broker ! t
  }
  override def send[T: ClassTag](t: List[T]) = t.map(broker ! _)
}

