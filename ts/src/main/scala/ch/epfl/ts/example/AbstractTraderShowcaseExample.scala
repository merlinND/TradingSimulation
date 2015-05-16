package ch.epfl.ts.example

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import ch.epfl.ts.data.Currency
import ch.epfl.ts.data.EndOfFetching
import ch.epfl.ts.data.MarketAskOrder
import ch.epfl.ts.data.MarketBidOrder
import ch.epfl.ts.data.Quote
import ch.epfl.ts.data.Register
import ch.epfl.ts.data.StrategyParameters
import ch.epfl.ts.data.Transaction
import ch.epfl.ts.engine.ExecutedAskOrder
import ch.epfl.ts.engine.ExecutedBidOrder
import ch.epfl.ts.engine.FundWallet
import ch.epfl.ts.engine.GetWalletFunds
import ch.epfl.ts.evaluation.EvaluationReport
import ch.epfl.ts.optimization.ForexLiveStrategyFactory
import ch.epfl.ts.optimization.ForexReplayStrategyFactory
import ch.epfl.ts.optimization.SystemDeployment
import ch.epfl.ts.traders.TraderCompanion

/**
 * Typical runnable class that showcases a trading strategy
 * on either live or historical data.
 */
abstract class AbstractTraderShowcaseExample extends AbstractForexExample with TraderEvaluation {

  val useLiveData: Boolean
  /** If using historical data, use this replay speed */
  val replaySpeed: Double
  /** If using historical data, fetch between these dates (format: YYYYMM) */
  val startDate: String
  val endDate: String

  val evaluationPeriod = (10 seconds)
  val referenceCurrency = symbol._2

  val factory = {
    if(useLiveData) new ForexLiveStrategyFactory(evaluationPeriod, referenceCurrency)
    else new ForexReplayStrategyFactory(evaluationPeriod, referenceCurrency, symbol, replaySpeed, startDate, endDate)
  }

  def makeConnections(d: SystemDeployment) = {
    d.fetcher -> (d.market, classOf[Quote])
    d.broker -> (d.market, classOf[MarketAskOrder], classOf[MarketBidOrder])
    d.market -> (d.broker, classOf[Quote], classOf[ExecutedBidOrder], classOf[ExecutedAskOrder])

    d.evaluators.foreach(e => {
      d.fetcher -> (e, classOf[EndOfFetching])
      d.market -> (e, classOf[Quote], classOf[Transaction])
      e -> (d.broker, classOf[Register], classOf[FundWallet], classOf[GetWalletFunds], classOf[MarketAskOrder], classOf[MarketBidOrder])

      e -> (d.printer.get, classOf[EvaluationReport])
    })

    // ----- Print things for debug
    d.evaluators.foreach(_ -> (d.printer.get, classOf[EvaluationReport]))
    d.fetcher -> (d.printer.get, classOf[EndOfFetching])
    d.market -> (d.printer.get, classOf[Transaction])
  }

  /** The single strategy to showcase */
  val strategy: TraderCompanion

  /** A single parameterization for this strategy */
  val parameterization: StrategyParameters

  def main(args: Array[String]): Unit = {
    // ----- Creating actors
    val d = factory.createDeployment(localHost, strategy, Set(parameterization))

    // ----- Connecting actors
    makeConnections(d)

    // ----- Start
    builder.start
  }

}
