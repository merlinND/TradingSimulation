package ch.epfl.ts.evaluation

import ch.epfl.ts.component.fetch.{ HistDataCSVFetcher, MarketNames }
import ch.epfl.ts.component.{ ComponentBuilder, ComponentRef }
import ch.epfl.ts.data.Currency._
import ch.epfl.ts.data._
import ch.epfl.ts.engine.{ MarketFXSimulator, ForexMarketRules }
import ch.epfl.ts.indicators.SMA
import akka.actor.Props
import ch.epfl.ts.traders.MovingAverageTrader
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import ch.epfl.ts.component.utils.Printer
import ch.epfl.ts.indicators.EmaIndicator
import ch.epfl.ts.indicators.OhlcIndicator
import ch.epfl.ts.indicators.EMA
import ch.epfl.ts.engine.Wallet

/**
 * Evaluates the performance of trading strategies
 */
//TODO Make Evaluator consistent with a Trader connected to a Broker which provide wallet-awareness  
object EvaluationRunner {
  implicit val builder = new ComponentBuilder("evaluation")

  def test(trader: ComponentRef, traderId: Long, symbol: (Currency, Currency)) = {
    val marketForexId = MarketNames.FOREX_ID

    // Fetcher
    // variables for the fetcher
    val dateFormat = new java.text.SimpleDateFormat("yyyyMM")
    val startDate = dateFormat.parse("201304");
    val endDate = dateFormat.parse("201305");
    val workingDir = "./data";
    val currencyPair = symbol._1.toString() + symbol._2.toString();
    val fetcher = builder.createRef(Props(classOf[HistDataCSVFetcher], workingDir, currencyPair, startDate, endDate, 4200.0), "HistFetcher")

    // Market
    val rules = new ForexMarketRules()
    val forexMarket = builder.createRef(Props(classOf[MarketFXSimulator], marketForexId, rules), MarketNames.FOREX_NAME)

    // Evaluator
    // TODO: evaluator should get the initial funds from the Trader directly
    val period = 10 * 1000 milliseconds
    val initial = 5000.0
    val currency = symbol._2
    val evaluator = builder.createRef(Props(classOf[Evaluator], trader, traderId, initial, currency, period), "evaluator")

    // Printer 
    val printer = builder.createRef(Props(classOf[Printer], "my-printer"), "printer")

    // ----- Connecting actors
    fetcher -> (Seq(forexMarket, evaluator), classOf[Quote])
    evaluator -> (forexMarket, classOf[MarketAskOrder], classOf[MarketBidOrder])
    evaluator -> (printer, classOf[EvaluationReport])
    forexMarket -> (evaluator, classOf[Transaction])

    builder.start
  }

  def movingAverageTrader(traderId: Long, symbol: (Currency, Currency)) = {
    // Trader
    val marketIds = List(MarketNames.FOREX_ID)
    val periods = List(2, 6)
    val initialFunds: Wallet.Type = Map(Currency.CHF -> 5000.0)
    val parameters = new StrategyParameters(
      MovingAverageTrader.INITIAL_FUNDS -> WalletParameter(initialFunds),
      MovingAverageTrader.SYMBOL -> CurrencyPairParameter(symbol),
      MovingAverageTrader.OHLC_PERIOD -> new TimeParameter(1 minute),
      MovingAverageTrader.SHORT_PERIODS -> NaturalNumberParameter(periods(0)),
      MovingAverageTrader.LONG_PERIODS -> NaturalNumberParameter(periods(1)),
      MovingAverageTrader.TOLERANCE -> RealNumberParameter(0.0002))
    
    MovingAverageTrader.getInstance(traderId, marketIds, parameters, "SimpleTrader")
  }

  def main(args: Array[String]): Unit = {
    val traderId = 42L
    val symbol =  (Currency.EUR, Currency.CHF)
    test(movingAverageTrader(traderId, symbol), traderId, symbol)
  }
}
