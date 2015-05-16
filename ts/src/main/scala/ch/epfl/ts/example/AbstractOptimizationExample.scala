package ch.epfl.ts.example

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration

import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import ch.epfl.ts.component.ComponentRef
import ch.epfl.ts.component.StartSignal
import ch.epfl.ts.data._
import ch.epfl.ts.engine.ExecutedAskOrder
import ch.epfl.ts.engine.ExecutedBidOrder
import ch.epfl.ts.engine.FundWallet
import ch.epfl.ts.engine.GetWalletFunds
import ch.epfl.ts.evaluation.EvaluationReport
import ch.epfl.ts.optimization.OptimizationSupervisor
import ch.epfl.ts.optimization.StrategyOptimizer
import ch.epfl.ts.optimization.SystemDeployment

abstract class AbstractOptimizationExample extends AbstractTraderShowcaseExample {
  
  /** Set this if you want to end the run after a fixed duration */
  def maximumRunDuration: Option[FiniteDuration] = None
  
  // Supervisor
  override lazy val supervisorActor: Option[ComponentRef] = Some({
    builder.createRef(Props(classOf[OptimizationSupervisor]), "MasterSupervisor")
  })
  
  // Traders
  /**
   * Target number of Trader instances to deploy (may not be respected exactly)
   */
  def maxInstances: Int
  /** Which of this strategy's parameter do we want to optimize */
  def parametersToOptimize: Set[String]
  /** Provide values for the required parameters that we do not optimize for */
  def otherParameterValues: Map[String, Parameter]
  /** Generate parameterizations using the previous fields */
  override lazy val parameterizations = {
    StrategyOptimizer.generateParameterizations(strategy, parametersToOptimize,
                                                otherParameterValues, maxInstances).toSet
  }
  
  override def makeConnections(d: SystemDeployment): Unit = {
    val master = supervisorActor.get

    d.fetcher -> (d.market, classOf[Quote])
    d.fetcher -> (Seq(d.market, master), classOf[EndOfFetching])
    d.market -> (d.broker, classOf[Quote], classOf[ExecutedBidOrder], classOf[ExecutedAskOrder])
    // TODO: make sure to support all order types
    d.broker -> (d.market, classOf[LimitBidOrder], classOf[LimitAskOrder], classOf[MarketBidOrder], classOf[MarketAskOrder])

    for(e <- d.evaluators) {
      d.fetcher -> (e, classOf[EndOfFetching])
      e -> (d.broker, classOf[Register], classOf[FundWallet], classOf[GetWalletFunds])
      e -> (d.broker, classOf[LimitBidOrder], classOf[LimitAskOrder], classOf[MarketBidOrder], classOf[MarketAskOrder])
      d.market -> (e, classOf[Quote], classOf[ExecutedBidOrder], classOf[ExecutedAskOrder])
      d.market -> (e, classOf[Transaction])

      e -> (master, classOf[EvaluationReport])
    }

    for(printer <- d.printer) {
      d.market -> (printer, classOf[Transaction])
      d.fetcher -> (printer, classOf[EndOfFetching])

      for(e <- d.evaluators) e -> (printer, classOf[EvaluationReport])
    }
  }
  
  
  /**
   * Use this if we need to terminate early regardless of the data being fetched
   */
  def terminateOptimizationAfter(delay: FiniteDuration, supervisor: ActorRef) = {
		import scala.concurrent.ExecutionContext.Implicits.global
    builder.system.scheduler.scheduleOnce(delay) {
      println("---------- Terminating optimization after a fixed duration of " + delay)
      supervisor ! EndOfFetching(System.currentTimeMillis())
    }
  }
}