package ch.epfl.ts.example

import akka.actor.Props
import ch.epfl.ts.component.ComponentBuilder
import ch.epfl.ts.component.fetch.HistDataCSVFetcher
import ch.epfl.ts.component.fetch.MarketNames
import ch.epfl.ts.component.persist.DummyPersistor
import ch.epfl.ts.component.utils.BackLoop
import ch.epfl.ts.data.CoefficientParameter
import ch.epfl.ts.data.Currency
import ch.epfl.ts.data.CurrencyPairParameter
import ch.epfl.ts.data.MarketAskOrder
import ch.epfl.ts.data.MarketBidOrder
import ch.epfl.ts.data.OHLC
import ch.epfl.ts.data.Quote
import ch.epfl.ts.data.RealNumberParameter
import ch.epfl.ts.data.StrategyParameters
import ch.epfl.ts.data.Transaction
import ch.epfl.ts.data.WalletParameter
import ch.epfl.ts.engine.ForexMarketRules
import ch.epfl.ts.engine.MarketFXSimulator
import ch.epfl.ts.engine.Wallet
import ch.epfl.ts.evaluation.Evaluator
import ch.epfl.ts.indicators.OhlcIndicator
import ch.epfl.ts.indicators.RI2
import ch.epfl.ts.indicators.RangeIndicator
import ch.epfl.ts.traders.RangeTrader
import ch.epfl.ts.evaluation.Evaluator
import ch.epfl.ts.evaluation.EvaluationReport
import ch.epfl.ts.component.utils.Printer
import scala.language.postfixOps
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.DurationInt


object RangeExample {
  
  def main(args: Array[String]): Unit = {
    implicit val builder = new ComponentBuilder("simpleFX")
    val marketForexId = MarketNames.FOREX_ID

    // ----- Creating actors
    // Fetcher
    //val fetcherFx: TrueFxFetcher = new TrueFxFetcher
    //val fetcher = builder.createRef(Props(classOf[PullFetchComponent[Quote]], fetcherFx, implicitly[ClassTag[Quote]]), "trueFxFetcher")
    
     val dateFormat = new java.text.SimpleDateFormat("yyyyMM")
     val startDate = dateFormat.parse("201411");
     val endDate   = dateFormat.parse("201411");
     val workingDir = "/Users/arnaud/Documents/data";
     val currencyPair = "USDCHF";
     val fetcher = builder.createRef(Props(classOf[HistDataCSVFetcher], workingDir, currencyPair, startDate, endDate, 100.0),"HistFetcher")

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
  
    
    // Evaluator
    val periodEvaluator : FiniteDuration  = 2000 milliseconds
    val initial = 1000000.0
    val currency = Currency.CHF
    val evaluator = builder.createRef(Props(classOf[Evaluator], trader, traderId, initial, currency, periodEvaluator), "evaluator")
    
    
    //printer
    val printer = builder.createRef(Props(classOf[Printer], "my-printer"), "printer")
   
    
    // ----- Connecting actors
    
   // fetcher -> (Seq(forexMarket, evaluator, ohlcIndicator), classOf[Quote])
    fetcher -> (Seq(forexMarket, evaluator, trader), classOf[Quote])   
    evaluator -> (forexMarket, classOf[MarketAskOrder], classOf[MarketBidOrder])
    evaluator -> (printer, classOf[EvaluationReport])
    
    //fetcher->(Seq(forexMarket, ohlcIndicator), classOf[Quote])
    
    trader->(forexMarket, classOf[MarketAskOrder])
    trader->(forexMarket, classOf[MarketBidOrder])

    forexMarket->(backloop, classOf[Transaction])
       
    backloop->(trader, classOf[Transaction])

    builder.start
  }
}