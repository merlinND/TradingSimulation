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

  // ----- Data
  def useLiveData: Boolean
  /** If using historical data, use this replay speed */
  def replaySpeed: Double
  /** If using historical data, fetch between these dates (format: YYYYMM) */
  def startDate: String
  def endDate: String

  // ----- Evaluation
  def evaluationPeriod = (10 seconds)
  def referenceCurrency = symbol._2

  lazy val factory = {
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
    })

    // ----- Print things for debug
    d.evaluators.foreach(_ -> (d.printer.get, classOf[EvaluationReport]))
    d.fetcher -> (d.printer.get, classOf[EndOfFetching])
    d.market -> (d.printer.get, classOf[Transaction])
  }

  /** The single strategy to showcase */
  def strategy: TraderCompanion

  /** Parameterizations to instantiate for this strategy (may be a singleton) */
  def parameterizations: Set[StrategyParameters]

  /** Optional: give names to your traders */
  def traderNames: Set[String] = Set()
  
  def main(args: Array[String]): Unit = {
    // ----- Creating actors
    val d = factory.createDeployment(localHost, strategy, parameterizations)

    // ----- Connecting actors
    makeConnections(d)

    // ----- Start
    builder.start
  }

}
