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

abstract class AbstractOptimizationExample extends AbstractTraderShowcaseExample with RemotingDeployment {
  
  /** Set this if you want to end the run after a fixed duration */
  def maximumRunDuration: Option[FiniteDuration] = None
  
  // Supervisor
  override lazy val supervisorActor: Option[ComponentRef] = Some({
    builder.createRef(Props(classOf[OptimizationSupervisor]), "MasterSupervisor")
  })
  
  // Traders
  def parametersToOptimize: Set[String]
  def otherParameterValues: Map[String, Parameter]
  override lazy val parameterizations = {
    StrategyOptimizer.generateParameterizations(strategy, parametersToOptimize,
                                                otherParameterValues, maxInstances).toSet
  }
  def distributed = factory.distributeOverHosts(availableHosts, parameterizations)
  lazy val deployments = distributed.map({ case (host, parameters) =>
    println("Creating " + parameters.size + " instances of " + strategy.getClass.getSimpleName + " on host " + host)
    factory.createDeployment(host, strategy, parameterizations, traderNames)
  }) 
  
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
  
  
  override def main(args: Array[String]): Unit = {
    println("Going to distribute " + parameterizations.size + " traders over " + availableHosts.size + " worker machines.")

    // ----- Connections
    deployments.foreach(makeConnections(_))

    // Make sure brokers are started before the traders
    supervisorActor.get.ar ! StartSignal
    for(d <- deployments) d.broker.ar ! StartSignal
    // Start!
    builder.start

    // ----- Registration to the supervisor
    // Register each new trader to the master
    for(d <- deployments; e <- d.evaluators) {
      supervisorActor.get.ar ! e.ar
    }

    // ----- Controlled duration (optional)
    maximumRunDuration match {
      case Some(duration) => terminateOptimizationAfter(duration, supervisorActor.get.ar)
      case None =>
    }
  }
  
}