package ch.epfl.ts.optimization

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Promise
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import akka.actor.Props
import akka.actor.actorRef2Scala
import ch.epfl.ts.component.ComponentBuilder
import ch.epfl.ts.component.StartSignal
import ch.epfl.ts.data.Currency
import ch.epfl.ts.data.CurrencyPairParameter
import ch.epfl.ts.data.EndOfFetching
import ch.epfl.ts.data.MarketAskOrder
import ch.epfl.ts.data.MarketBidOrder
import ch.epfl.ts.data.Quote
import ch.epfl.ts.data.Register
import ch.epfl.ts.data.Transaction
import ch.epfl.ts.data.WalletParameter
import ch.epfl.ts.engine.ExecutedAskOrder
import ch.epfl.ts.engine.ExecutedBidOrder
import ch.epfl.ts.engine.FundWallet
import ch.epfl.ts.engine.GetWalletFunds
import ch.epfl.ts.engine.TraderIdentity
import ch.epfl.ts.engine.Wallet
import ch.epfl.ts.evaluation.EvaluationReport
import ch.epfl.ts.traders.MovingAverageTrader
import ch.epfl.ts.data.TimeParameter
import ch.epfl.ts.data.RealNumberParameter

/**
 * Runs a main() method that creates a MasterActor and remote WorkerActors
 * by calling createRemoteActors() for every availableWorker. It assumes
 * that there is a RemotingWorker class running and listening on port 3333
 * on every availableWorker.
 * 
 * @see {@link ch.epfl.ts.optimization.RemotingWorker}
 */
object RemotingHostRunner {

  val availableHosts = {
    val availableWorkers = List(
      "localhost"
      //"ts-1-021qv44y.cloudapp.net",
      //"ts-2.cloudapp.net"
      //"ts-3.cloudapp.net",
      //"ts-4.cloudapp.net",
      //"ts-5.cloudapp.net",
      //"ts-6.cloudapp.net",
      //"ts-7.cloudapp.net",
      //"ts-8.cloudapp.net"
    )
    val port = 3333
    // TODO: lookup in configuration
    val systemName = "remote"
    
    availableWorkers.map(hostname => {
      val prefix = hostname.substring(0, 4)
      new RemoteHost(hostname, port, prefix, systemName)
    })
  }
  
  val optimizationFinished = Promise[(TraderIdentity, EvaluationReport)]
  def onEnd(t: TraderIdentity, e: EvaluationReport) = {
    optimizationFinished.success((t, e))
    Unit
  }

  def main(args: Array[String]): Unit = {

    // ----- Build the supervisor actor
    implicit val builder = new ComponentBuilder()
    val master = builder.createRef(Props(classOf[OptimizationSupervisor], onEnd _), "MasterActor")
    
    // ----- Factory: class responsible for creating the components
    val speed = 200000.0
    val symbol = (Currency.EUR, Currency.CHF)
    val start = "201304"
    val end = "201304"
    val factory = new ForexReplayStrategyFactory(10 seconds, symbol._2, symbol, speed, start, end)
    
    
    // ----- Generate candidate parameterizations
    val strategyToOptimize = MovingAverageTrader
    val parametersToOptimize = Set(
      MovingAverageTrader.SHORT_PERIODS,
      MovingAverageTrader.LONG_PERIODS
    )
    val initialWallet: Wallet.Type = Map(Currency.EUR -> 1000.0, Currency.CHF -> 1000.0)
    val otherParameterValues = Map(
      MovingAverageTrader.INITIAL_FUNDS -> WalletParameter(initialWallet),
      MovingAverageTrader.SYMBOL -> CurrencyPairParameter(symbol),
      MovingAverageTrader.OHLC_PERIOD -> new TimeParameter(1 hour),
      MovingAverageTrader.TOLERANCE -> RealNumberParameter(0.0002)      
    )
    
    val maxInstances = (5 * availableHosts.size)
    val parameterizations = StrategyOptimizer.generateParameterizations(strategyToOptimize, parametersToOptimize,
                                                                        otherParameterValues, maxInstances).toSet

    // TODO: use log.info
    println("Going to distribute " + parameterizations.size + " traders over " + availableHosts.size + " worker machines.")
                                                             
    // ----- Instantiate the all components on each available worker
    val distributed = factory.distributeOverHosts(availableHosts, parameterizations)
    val deployments = distributed.map({ case (host, parameters) =>
      println("Creating " + parameters.size + " instances of " + strategyToOptimize.getClass.getSimpleName + " on host " + host)
      factory.createRemoteActors(master, host, strategyToOptimize, parameterizations)
    })
    
    
    // ----- Connections
    deployments.foreach(d => {
      d.fetcher -> (d.market, classOf[Quote])
      // TODO: is it being received correctly?
      d.fetcher -> (Seq(d.market, master), EndOfFetching)
      d.market -> (d.broker, classOf[Quote], classOf[ExecutedBidOrder], classOf[ExecutedAskOrder])
      // TODO: make sure to support all order types
      d.broker -> (d.market, classOf[MarketAskOrder], classOf[MarketBidOrder])
      
      for(e <- d.evaluators) {
        e -> (d.broker, classOf[Register], classOf[FundWallet], classOf[GetWalletFunds], classOf[MarketAskOrder], classOf[MarketBidOrder])
        d.market -> (e, classOf[Quote], classOf[ExecutedBidOrder], classOf[ExecutedAskOrder])
        d.market -> (e, classOf[Transaction])
        
        e -> (master, classOf[EvaluationReport])
      }
      
      for(printer <- d.printer) {
        d.market -> (printer, classOf[Transaction])
        for(e <- d.evaluators) e -> (printer, classOf[EvaluationReport])
      }
    })
    
    // Make sure brokers are started before the traders
    for(d <- deployments) d.broker.ar ! StartSignal
    builder.start
    
    // ----- Registration to the supervisor
    // Register each new trader to the master
    for(d <- deployments; e <- d.evaluators) {
      master.ar ! e.ar
    }
    
    optimizationFinished.future.onSuccess({
      case (TraderIdentity(name, uid, companion, parameters), evaluation: EvaluationReport) => {
        println("---------- Optimization completed ----------")
        println(s"The best trader was: $name (id $uid) using strategy $companion.")
        println(s"Its parameters were:\n$parameters")
        println(s"Its final evaluation was:\n$evaluation")
        println("--------------------------------------------")
        
        builder.shutdownManagedActors(3 seconds)
      }
    })
    
    // TODO: fix actor names including the full path to the root (even though it is remote)
  }
}
