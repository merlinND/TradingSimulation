package ch.epfl.ts.example

import scala.reflect.ClassTag
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import akka.actor.Props
import ch.epfl.ts.component.ComponentBuilder
import ch.epfl.ts.engine.ForexMarketRules
import ch.epfl.ts.component.fetch.TrueFxFetcher
import ch.epfl.ts.component.persist.DummyPersistor
import ch.epfl.ts.component.fetch.MarketNames
import ch.epfl.ts.engine.MarketFXSimulator
import ch.epfl.ts.traders.MovingAverageTrader
import ch.epfl.ts.component.utils.BackLoop
import ch.epfl.ts.indicators.SmaIndicator
import ch.epfl.ts.component.fetch.PullFetchComponent
import ch.epfl.ts.data.{ Quote, OHLC }
import ch.epfl.ts.data.Transaction
import ch.epfl.ts.data.MarketAskOrder
import ch.epfl.ts.data.MarketBidOrder
import ch.epfl.ts.data.Currency
import ch.epfl.ts.component.fetch.HistDataCSVFetcher
import ch.epfl.ts.evaluation.Evaluator
import ch.epfl.ts.indicators.EmaIndicator
import ch.epfl.ts.brokers.StandardBroker
import ch.epfl.ts.data.Register
import ch.epfl.ts.engine.FundWallet
import com.typesafe.config.ConfigFactory
import ch.epfl.ts.indicators.EMA
import ch.epfl.ts.engine.GetWalletFunds
import ch.epfl.ts.engine.ExecutedAskOrder
import ch.epfl.ts.engine.ExecutedBidOrder
import ch.epfl.ts.data.Order
import ch.epfl.ts.data.NaturalNumberParameter
import ch.epfl.ts.data.TimeParameter
import ch.epfl.ts.data.CurrencyPairParameter
import ch.epfl.ts.data.StrategyParameters
import ch.epfl.ts.engine.Wallet
import ch.epfl.ts.data.WalletParameter
import ch.epfl.ts.data.RealNumberParameter
import ch.epfl.ts.component.utils.Printer
import com.sun.javafx.scene.control.behavior.OptionalBoolean
import ch.epfl.ts.data.BooleanParameter
import ch.epfl.ts.evaluation.EvaluationReport

object MovingAverageFXExample {
  def main(args: Array[String]): Unit = {
    implicit val builder = new ComponentBuilder()
    val marketForexId = MarketNames.FOREX_ID

    val useLiveData = false
    val symbol = (Currency.EUR, Currency.CHF)

    // ----- Creating actors
    // Fetcher
    val fxQuoteFetcher = {
      if (useLiveData) {
        val fetcherFx: TrueFxFetcher = new TrueFxFetcher
        builder.createRef(Props(classOf[PullFetchComponent[Quote]], fetcherFx, implicitly[ClassTag[Quote]]), "TrueFxFetcher")
      } else {
        val replaySpeed = 40000.0

        val dateFormat = new java.text.SimpleDateFormat("yyyyMM")
        val startDate = dateFormat.parse("201304");
        val endDate = dateFormat.parse("201305");
        val workingDir = "./data";
        val currencyPair = symbol._1.toString() + symbol._2.toString();

        val fetcherProps = Props(classOf[HistDataCSVFetcher], workingDir, currencyPair, startDate, endDate, replaySpeed)
        builder.createRef(fetcherProps, "HistDataFetcher")
      }
    }
    // Market
    val rules = new ForexMarketRules()
    val forexMarket = builder.createRef(Props(classOf[MarketFXSimulator], marketForexId, rules), MarketNames.FOREX_NAME)

    // Trader: cross moving average
    val traderId = 123L
    val periods = List(2, 6)
    val initialFunds: Wallet.Type = Map(Currency.CHF -> 5000.0)
    val parameters = new StrategyParameters(
      MovingAverageTrader.INITIAL_FUNDS -> WalletParameter(initialFunds),
      MovingAverageTrader.SYMBOL -> CurrencyPairParameter(symbol),

      MovingAverageTrader.OHLC_PERIOD -> new TimeParameter(1 minute),
      MovingAverageTrader.SHORT_PERIODS -> NaturalNumberParameter(periods(0)),
      MovingAverageTrader.LONG_PERIODS -> NaturalNumberParameter(periods(1)),
      MovingAverageTrader.TOLERANCE -> RealNumberParameter(0.0002),
      MovingAverageTrader.WITH_SHORT -> BooleanParameter(true),
      MovingAverageTrader.SHORT_PERCENT -> RealNumberParameter(0.2))

    val trader = MovingAverageTrader.getInstance(traderId, List(marketForexId), parameters, "MovingAverageTrader")

    // Evaluation
    val evaluationPeriod = 2 seconds
    val referenceCurrency = symbol._2
    val evaluator = builder.createRef(Props(classOf[Evaluator], trader, traderId, referenceCurrency, evaluationPeriod), "Evaluator")

    // Broker
    val broker = builder.createRef(Props(classOf[StandardBroker]), "Broker")

    // Add printer if needed to debug / display

    val printer = builder.createRef(Props(classOf[Printer], "MyPrinter"), "Printer")

    // ----- Connecting actors

    // TODO : connect fetcher only to the market (other components will get quotes from it)
    fxQuoteFetcher -> (Seq(forexMarket, broker, evaluator), classOf[Quote])

    evaluator -> (printer, classOf[EvaluationReport])
    evaluator -> (broker, classOf[Register], classOf[FundWallet], classOf[GetWalletFunds], classOf[MarketAskOrder], classOf[MarketBidOrder])
    broker -> (forexMarket, classOf[MarketAskOrder], classOf[MarketBidOrder])
    forexMarket -> (broker, classOf[ExecutedBidOrder], classOf[ExecutedAskOrder])
    forexMarket -> (Seq(evaluator, printer), classOf[Transaction])

    // ----- Start
    builder.start
  }
}
