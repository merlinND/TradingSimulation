package ch.epfl.ts.optimization

import scala.collection.mutable.{Map => MutableMap}
import scala.collection.mutable.MutableList
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Promise
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Address
import akka.actor.Deploy
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.remote.RemoteScope
import ch.epfl.ts.component.Component
import ch.epfl.ts.component.ComponentBuilder
import ch.epfl.ts.component.ComponentRef
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
import ch.epfl.ts.engine.GetTraderParameters
import ch.epfl.ts.engine.GetWalletFunds
import ch.epfl.ts.engine.TraderIdentity
import ch.epfl.ts.engine.Wallet
import ch.epfl.ts.evaluation.EvaluationReport
import ch.epfl.ts.traders.MadTrader
import ch.epfl.ts.data.EndOfFetching

/**
 * @param namingPrefix Prefix that will precede the name of each actor on this remote
 * @param systemName Name of the actor system being run on this remote
 */
class RemoteHost(val hostname: String, val port: Int, val namingPrefix: String, val systemName: String = "remote") {
  val address = Address("akka.tcp", systemName, hostname, port)
  val deploy = Deploy(scope = RemoteScope(address))
  
  def createRemotely(props: Props, name: String)(implicit builder: ComponentBuilder): ComponentRef = {
    // TODO: use `log.debug`
    val actualName = namingPrefix + '-' + name
    println("Creating remotely component " + actualName + " at host " + hostname)
    builder.createRef(props.withDeploy(deploy), actualName)
  }
  
  override def toString(): String = systemName + "@" + hostname + ":" + port
}


/**
 * Responsible for overseeing actors instantiated at worker nodes. That means it listens
 * to them (i.e. it doesn't send any commands or similar, so far, except for being able to ping them).
 * 
 * @param onEnd Callback to call when optimization has completed and the best trader was picked
 */
class OptimizationSupervisor(onEnd: (TraderIdentity, EvaluationReport) => Unit) extends Component with ActorLogging {

  /**
   * Collection of evaluations for registered traders being tested
   * The set of registered actors can be deduces from this map's keys.
   */
  // TODO: only keep the most N recent to avoid unnecessary build-up
  val evaluations: MutableMap[ActorRef, MutableList[EvaluationReport]] = MutableMap.empty

  def lastEvaluations = evaluations.map({ case (t, l) => t -> l.head })
  def bestTraderPerformance = lastEvaluations.toList.sortBy(p => p._2).head
  
  var bestEvaluation: Option[EvaluationReport] = None
  def askBestTraderIdentity = {
    bestEvaluation = Some(bestTraderPerformance._2)
    bestTraderPerformance._1 ! GetTraderParameters
  }
  
  override def receiver = {
    case w: ActorRef => {
      evaluations += (w -> MutableList.empty)
    }

    case e: EvaluationReport if evaluations.contains(sender) => {
      // Add the report to the collection
      evaluations.get(sender).foreach { l => l += e }
    }
    
    case e: EvaluationReport => log.error("Received report from unknown sender " + sender + ": " + e)

    // When all data has been replayed, we can determine the best trader
    case _: EndOfFetching if bestEvaluation.isEmpty => {
      log.info("Supervisor has received an EndOfFetching signal. Will now try to determine the best fetcher's identity.")
      askBestTraderIdentity
    }
    case _: EndOfFetching =>
      log.warning("Supervisor has received more than one EndOfFetching signals")
    
    case t: TraderIdentity if bestEvaluation.isDefined => {
      onEnd(t, bestEvaluation.get)
    }
    case t: TraderIdentity => {
      log.warning("Received a TraderIdentity before knowing the best performance, which is weird:" + t)
    }

    
    case m => log.warning("MasterActor received weird: " + m)
  }
  
}

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
    
    // ----- Generate candidate parameterizations
    val strategyToOptimize = MadTrader
    val parametersToOptimize = Set(
      MadTrader.INTERVAL,
      MadTrader.ORDER_VOLUME
    )
    val initialWallet: Wallet.Type = Map(Currency.EUR -> 1000.0, Currency.CHF -> 1000.0)
    val otherParameterValues = Map(
      MadTrader.INITIAL_FUNDS -> WalletParameter(initialWallet),
      MadTrader.CURRENCY_PAIR -> CurrencyPairParameter(Currency.EUR, Currency.CHF)
    )
    
    val maxInstances = (5 * availableHosts.size)
    val parameterizations = StrategyOptimizer.generateParameterizations(strategyToOptimize, parametersToOptimize,
                                                                        otherParameterValues, maxInstances).toSet

    // TODO: use log.info
    println("Going to distribute " + parameterizations.size + " traders over " + availableHosts.size + " worker machines.")
                                                             
    // ----- Instantiate the all components on each available worker
    val distributed = ForexStrategyFactory.distributeOverHosts(availableHosts, parameterizations)
    val deployments = distributed.map({ case (host, parameters) =>
      println("Creating " + parameters.size + " instances of " + strategyToOptimize.getClass.getSimpleName + " on host " + host)
      ForexStrategyFactory.createRemoteActors(master, host, strategyToOptimize, parameterizations)
    })
    
    
    // ----- Connections
    deployments.foreach(d => {
      d.fetcher -> (d.market, classOf[Quote])
      d.fetcher -> (master, classOf[EndOfFetching])
      d.market -> (d.broker, classOf[Quote], classOf[ExecutedBidOrder], classOf[ExecutedAskOrder])
      // TODO: make sure to support all order types
      d.broker -> (d.market, classOf[MarketAskOrder], classOf[MarketBidOrder])
      
      for(e <- d.evaluators) {
        e -> (d.broker, classOf[Register], classOf[FundWallet], classOf[GetWalletFunds], classOf[MarketAskOrder], classOf[MarketBidOrder])
        d.market -> (e, classOf[Transaction])
        
        e -> (master, classOf[EvaluationReport])
        for(printer <- d.printer) e -> (printer, classOf[EvaluationReport])
      }
      
      for(printer <- d.printer) {
        d.market -> (printer, classOf[Transaction])
      }
    })                                                              
    
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
  }
}
