package ch.epfl.ts.evaluation

import ch.epfl.ts.component.fetch.{ HistDataCSVFetcher, MarketNames }
import ch.epfl.ts.component.{ ComponentBuilder, ComponentRef }
import ch.epfl.ts.data.Currency._
import ch.epfl.ts.data._
import ch.epfl.ts.engine.{Wallet, MarketFXSimulator, ForexMarketRules}
import ch.epfl.ts.indicators.SMA
import akka.actor.Props
import ch.epfl.ts.traders.MovingAverageTrader
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import ch.epfl.ts.component.utils.Printer
import ch.epfl.ts.indicators.EmaIndicator
import ch.epfl.ts.indicators.OhlcIndicator
import ch.epfl.ts.indicators.EMA

/**
 * Evaluates the performance of trading strategies
 */
//TODO Make Evaluator consistent with a Trader connected to a Broker which provide wallet-awareness  
object EvaluationRunner {
  implicit val builder = new ComponentBuilder("evaluation")

  def test(trader: ComponentRef, traderId: Long) = {
    val marketForexId = MarketNames.FOREX_ID

    // Fetcher
    // variables for the fetcher
    val dateFormat = new java.text.SimpleDateFormat("yyyyMM")
    val startDate = dateFormat.parse("201503");
    val endDate = dateFormat.parse("201503");
    val workingDir = "./data";
    val currencyPair = "EURCHF";
    val fetcher = builder.createRef(Props(classOf[HistDataCSVFetcher], workingDir, currencyPair, startDate, endDate, 20.0), "HistFetcher")

    // Market
    val rules = new ForexMarketRules()
    val forexMarket = builder.createRef(Props(classOf[MarketFXSimulator], marketForexId, rules), MarketNames.FOREX_NAME)

    // Evaluator
    val period = 10 * 1000 milliseconds
    val initial = 5000.0
    val currency = CHF
    val evaluator = builder.createRef(Props(classOf[Evaluator], trader, traderId, currency, period), "evaluator")

    //Printer 
    val printer = builder.createRef(Props(classOf[Printer], "my-printer"), "printer")

    //TODO Integrate indicators inside trader
    val symbol = Currency.EUR -> Currency.CHF
    val periods = List(2, 10)
    val periodOHLC: Long =  4 * 1000 //OHLC of 1 hour
    val ohlcIndicator = builder.createRef(Props(classOf[OhlcIndicator], 4L, symbol, periodOHLC), "ohlcIndicator")
    val maCross = builder.createRef(Props(classOf[EmaIndicator], periods), "maCross")

    // ----- Connecting actors
    fetcher -> (Seq(forexMarket, evaluator), classOf[Quote])
    evaluator -> (forexMarket, classOf[MarketAskOrder], classOf[MarketBidOrder])
    evaluator -> (printer, classOf[EvaluationReport])
    forexMarket -> (evaluator, classOf[Transaction])
    forexMarket -> (printer, classOf[Transaction])

    // -- TODO Integrate indicators inside trader
    fetcher -> (ohlcIndicator, classOf[Quote])
    ohlcIndicator -> (maCross, classOf[OHLC])
    maCross -> (trader, classOf[EMA])

    builder.start
  }

  def movingAverageTrader(traderId: Long) = {
    // Trader
    val traderId = 123L
    val periods = List(2, 10)
    val initialFunds: Wallet.Type = Map(Currency.CHF -> 5000.0)
    val parameters = new StrategyParameters(
      MovingAverageTrader.INITIAL_FUNDS -> WalletParameter(initialFunds),
      MovingAverageTrader.SYMBOL -> CurrencyPairParameter(Currency.EUR -> Currency.CHF),
      MovingAverageTrader.SHORT_PERIOD -> new TimeParameter(periods(0) seconds),
      MovingAverageTrader.LONG_PERIOD -> new TimeParameter(periods(1) seconds),
      MovingAverageTrader.TOLERANCE -> RealNumberParameter(0.0002))

    MovingAverageTrader.getInstance(traderId, parameters, "MovingAverageTrader")

  }

  def main(args: Array[String]): Unit = {
    test(movingAverageTrader(123L), 123L)
  }
}
