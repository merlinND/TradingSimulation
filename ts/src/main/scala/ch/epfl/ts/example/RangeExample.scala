package ch.epfl.ts.example

import ch.epfl.ts.component.ComponentBuilder
import ch.epfl.ts.component.fetch.PullFetchComponent
import ch.epfl.ts.data.{ Quote, OHLC }
import ch.epfl.ts.engine.ForexMarketRules
import ch.epfl.ts.component.fetch.TrueFxFetcher
import ch.epfl.ts.component.persist.DummyPersistor
import ch.epfl.ts.component.fetch.MarketNames
import ch.epfl.ts.engine.MarketFXSimulator
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
import ch.epfl.ts.engine.Wallet
import ch.epfl.ts.data.StrategyParameters
import ch.epfl.ts.data.RealNumberParameter
import ch.epfl.ts.data.WalletParameter
import ch.epfl.ts.data.CurrencyPairParameter
import ch.epfl.ts.data.CoefficientParameter

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
    val traderId: Long = 123L
    val initialFunds: Wallet.Type = Map(Currency.USD -> 1000.0)
    val parameters = new StrategyParameters(
      RangeTrader.INITIAL_FUNDS -> WalletParameter(initialFunds),
      RangeTrader.SYMBOL -> CurrencyPairParameter(Currency.USD, Currency.CHF),
      RangeTrader.VOLUME -> RealNumberParameter(10.0),
      RangeTrader.ORDER_WINDOW -> CoefficientParameter(0.15)
    )
    val trader = builder.createRef(Props(classOf[RangeTrader], traderId, parameters), "RangeTrader")
   
    // Indicator
    // specify period over which we build the OHLC (from quotes)
    val period : Long = 5
    
    // Time period over which the indicator is computed (in OHLC)
    val timePeriod : Int = 10
    val tolerance : Int = 1
    val rangeIndicator = builder.createRef(Props(classOf[RangeIndicator], timePeriod, tolerance), "smaShort")
    val ohlcIndicator = builder.createRef(Props(classOf[OhlcIndicator], fetcherFx.marketId, (Currency.USD, Currency.CHF), period), "ohlcIndicator")

    // TODO: use evaluator if needed
    
    // ----- Connecting actors
    fxQuoteFetcher->(Seq(forexMarket, ohlcIndicator), classOf[Quote])
    
    trader->(forexMarket, classOf[MarketAskOrder])
    trader->(forexMarket, classOf[MarketBidOrder])

    forexMarket->(backloop, classOf[Transaction])
    
    rangeIndicator->(trader, classOf[RI2])
    ohlcIndicator->(Seq(rangeIndicator, trader), classOf[OHLC])
    
    backloop->(trader, classOf[Transaction])

    builder.start
  }
}