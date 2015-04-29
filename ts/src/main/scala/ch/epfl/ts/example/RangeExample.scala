package ch.epfl.ts.example

import ch.epfl.ts.component.ComponentBuilder
import ch.epfl.ts.component.fetch.PullFetchComponent
import ch.epfl.ts.data.{ Quote, OHLC }
import ch.epfl.ts.engine.ForexMarketRules
import ch.epfl.ts.component.fetch.TrueFxFetcher
import ch.epfl.ts.component.persist.DummyPersistor
import ch.epfl.ts.component.fetch.MarketNames
import ch.epfl.ts.engine.MarketFXSimulator
import ch.epfl.ts.engine.RevenueComputeFX
import akka.actor.Props
import ch.epfl.ts.component.utils.BackLoop
import ch.epfl.ts.traders.RangeTrader
import ch.epfl.ts.indicators.OhlcIndicator
import ch.epfl.ts.data.Transaction
import ch.epfl.ts.data.MarketBidOrder
import scala.reflect.ClassTag
import ch.epfl.ts.data.MarketAskOrder
import ch.epfl.ts.indicators.RangeIndicator
import ch.epfl.ts.indicators.RI2
import ch.epfl.ts.indicators.RI
import ch.epfl.ts.data.Currency
import ch.epfl.ts.indicators.RangeIndicatorPeek

object RangeExample {
  
  def main(args: Array[String]): Unit = {
    implicit val builder = new ComponentBuilder("simpleFX")
    val marketForexId = MarketNames.FOREX_ID

    // ----- Creating actors
    // Fetcher
    val fetcherFx: TrueFxFetcher = new TrueFxFetcher
    val fxQuoteFetcher = builder.createRef(Props(classOf[PullFetchComponent[Quote]], fetcherFx, implicitly[ClassTag[Quote]]), "trueFxFetcher")
    
    // Market
    val rules = new ForexMarketRules()
    val forexMarket = builder.createRef(Props(classOf[MarketFXSimulator], marketForexId, rules), MarketNames.FOREX_NAME)
    
    // Persistor
    val dummyPersistor = new DummyPersistor()
    
    // Backloop
    val backloop = builder.createRef(Props(classOf[BackLoop], marketForexId, dummyPersistor), "backloop")
    
    // Trader: range trader. 
    val traderId : Long = 123L
    val volume : Double = 10.0
    val gapSupport : Double = 0.0
    val gapResistance : Double = 0.0
    val trader = builder.createRef(Props(classOf[RangeTrader], traderId, gapSupport, gapResistance, volume, (Currency.USD, Currency.CHF)), "rangeTrader")
   
    // Indicator
    // specify period over which we build the OHLC (from quotes)
    val period : Long = 5
    
    //time period over which the indicator is computed
    val timePeriod : Int = 10
    val tolerance = 10
    
    val rangeIndicator = builder.createRef(Props(classOf[RangeIndicator], timePeriod, tolerance), "smaShort")
    val ohlcIndicator = builder.createRef(Props(classOf[OhlcIndicator], fetcherFx.marketId, (Currency.USD, Currency.CHF), period), "ohlcIndicator")
    
    // Display
    val traderNames = Map(traderId -> "MovingAverageFXTrader")
    val display = builder.createRef(Props(classOf[RevenueComputeFX], traderNames), "display")

    // ----- Connecting actors
    fxQuoteFetcher.addDestination(forexMarket, classOf[Quote])
    fxQuoteFetcher.addDestination(ohlcIndicator, classOf[Quote])
    
    trader.addDestination(forexMarket, classOf[MarketAskOrder])
    trader.addDestination(forexMarket, classOf[MarketBidOrder])

    forexMarket.addDestination(backloop, classOf[Transaction])
    forexMarket.addDestination(display, classOf[Transaction])
    
    rangeIndicator.addDestination(trader, classOf[RI2])
    ohlcIndicator.addDestination(rangeIndicator, classOf[OHLC])
    ohlcIndicator.addDestination(trader, classOf[OHLC])
    
    backloop.addDestination(trader, classOf[Transaction])

    builder.start
  }
}