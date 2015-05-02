package ch.epfl.ts.evaluation

import ch.epfl.ts.component.fetch.{HistDataCSVFetcher, MarketNames}
import ch.epfl.ts.component.{ComponentBuilder, ComponentRef}
import ch.epfl.ts.data.Currency._
import ch.epfl.ts.data._
import ch.epfl.ts.engine.{MarketFXSimulator, ForexMarketRules}
import ch.epfl.ts.indicators.SMA
import akka.actor.Props
import ch.epfl.ts.traders.SimpleFXTrader
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import ch.epfl.ts.traders.RangeTrader
import ch.epfl.ts.indicators.OhlcIndicator
import ch.epfl.ts.indicators.RangeIndicator
import ch.epfl.ts.indicators.RI2
import ch.epfl.ts.component.utils.Printer

/**
 * Evaluates the performance of trading strategies
 */
object EvaluationRunnerRangeTrader {
  val builder = new ComponentBuilder("evaluation-range-trader")

  def test(trader: ComponentRef, traderId: Long) = {
    val marketForexId = MarketNames.FOREX_ID

    // Fetcher
    // variables for the fetcher
    val dateFormat = new java.text.SimpleDateFormat("yyyyMM")
    val startDate = dateFormat.parse("201411");
    val endDate   = dateFormat.parse("201411");
    val workingDir = "/Users/arnaud/Documents/data";
    val currencyPair = "USDCHF";
    val fetcher = builder.createRef(Props(classOf[HistDataCSVFetcher], workingDir, currencyPair, startDate, endDate, 1000.0),"HistFetcher")

    // Market
    val rules = new ForexMarketRules()
    val forexMarket = builder.createRef(Props(classOf[MarketFXSimulator], marketForexId, rules), MarketNames.FOREX_NAME)
    
    //Indicator
    val symbol = (Currency.USD, Currency.CHF)
    val periodOhlc : Long = 1000*60
    val ohlcIndicator = builder.createRef(Props(classOf[OhlcIndicator], marketForexId, symbol, periodOhlc), "ohlcIndicator")

    val rangeIndicator = builder.createRef(Props(classOf[RangeIndicator], 10, 3), "rangeIndicator")
    
    // Evaluator
    val period = 2000 milliseconds
    val initial = 1000000.0
    val currency = CHF
    val evaluator = builder.createRef(Props(classOf[Evaluator], trader, traderId, initial, currency, period), "evaluator")
    
    //printer
    val printer = builder.createRef(Props(classOf[Printer], "my-printer"), "printer") 

    // ----- Connecting actors
    fetcher -> (Seq(forexMarket, evaluator, ohlcIndicator), classOf[Quote])
    
    forexMarket -> (evaluator, classOf[Transaction])

    evaluator -> (forexMarket, classOf[MarketAskOrder], classOf[MarketBidOrder])
    evaluator -> (printer, classOf[EvaluationReport])
    
    ohlcIndicator -> (Seq(rangeIndicator,trader), classOf[OHLC])
    rangeIndicator -> (trader, classOf[RI2])

    builder.start
  }

  def RangeTrader(traderId: Long) = {
    // Trader
    val symbol = (Currency.USD, Currency.CHF)
    val volume = 1000.0
    val orderWindow : Double = 0.20
    val periods=List(3,10)
    
    builder.createRef(Props(classOf[RangeTrader], traderId, orderWindow, volume, symbol), "simpleTrader")
  }

  def main(args: Array[String]): Unit = {
    test(RangeTrader(123L), 123L)
  }
}
