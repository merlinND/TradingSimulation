package ch.epfl.ts.example

import ch.epfl.ts.component.ComponentBuilder
import akka.actor.Props
import ch.epfl.ts.component.fetch.HistDataCSVFetcher
import ch.epfl.ts.component.utils.Printer
import ch.epfl.ts.data.Quote
import ch.epfl.ts.indicators.OhlcIndicator
import ch.epfl.ts.data.Transaction
import ch.epfl.ts.traders.RangeTrader
import ch.epfl.ts.indicators.RangeIndicator
import ch.epfl.ts.component.utils.BackLoop
import ch.epfl.ts.component.persist.DummyPersistor
import ch.epfl.ts.engine.RevenueComputeFX
import ch.epfl.ts.data.Currency
import ch.epfl.ts.data.MarketAskOrder
import ch.epfl.ts.indicators.RI2
import ch.epfl.ts.data.MarketBidOrder
import ch.epfl.ts.component.fetch.MarketNames
import ch.epfl.ts.engine.ForexMarketRules
import ch.epfl.ts.engine.MarketFXSimulator
import ch.epfl.ts.data.OHLC

object RangeExampleHistData {
  def main(args: Array[String]) {
    implicit val builder = new ComponentBuilder("HistFetcherExample")

    // variables for the fetcher
    val dateFormat = new java.text.SimpleDateFormat("yyyyMM")
    val startDate = dateFormat.parse("201411")
    val endDate   = dateFormat.parse("201411")
    val workingDir = "/Users/arnaud/Documents/data"
    val currencyPair = "USDCHF"
    
    // Create Components
    // build fetcher
    val fetcher = builder.createRef(Props(classOf[HistDataCSVFetcher], workingDir, currencyPair, startDate, endDate, 1000.0),"HistFetcher")    
    // build printer
    val printer = builder.createRef(Props(classOf[Printer], "Printer"), "Printer")

    // Market
    val marketForexId = MarketNames.FOREX_ID
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
    val period : Long = 1000*60
    val ohlcIndicator = builder.createRef(Props(classOf[OhlcIndicator], marketForexId, (Currency.USD, Currency.CHF), period), "ohlcIndicator")

    //time period over which the indicator is computed
    val timePeriod : Int = 10
    val tolerance = 2
    val rangeIndicator = builder.createRef(Props(classOf[RangeIndicator], timePeriod, tolerance), "smaShort")
    
    // Display
    val traderNames = Map(traderId -> "MovingAverageFXTrader")
    val display = builder.createRef(Props(classOf[RevenueComputeFX], traderNames), "display")

    // ----- Connecting actors
    fetcher->(Seq(forexMarket, ohlcIndicator, printer), classOf[Quote])
    
    trader->(forexMarket, classOf[MarketAskOrder])
    trader->(forexMarket, classOf[MarketBidOrder])

    forexMarket->(backloop, classOf[Transaction])
    forexMarket->(display, classOf[Transaction])
    
    rangeIndicator->(trader, classOf[RI2])
    ohlcIndicator->(Seq(rangeIndicator, trader), classOf[OHLC])
    
    backloop->(trader, classOf[Transaction])

    builder.start

  }
}