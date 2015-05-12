package ch.epfl.ts.optimization

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.collection.mutable.MutableList
import scala.reflect.ClassTag
import com.typesafe.config.ConfigFactory
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Address
import akka.actor.Deploy
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.remote.RemoteScope
import ch.epfl.ts.engine.MarketRules
import ch.epfl.ts.engine.MarketFXSimulator
import ch.epfl.ts.engine.ForexMarketRules
import ch.epfl.ts.component.fetch.TrueFxFetcher
import ch.epfl.ts.component.fetch.PullFetchComponent
import ch.epfl.ts.component.fetch.MarketNames
import ch.epfl.ts.data.Quote
import ch.epfl.ts.component.utils.Printer
import ch.epfl.ts.component.ComponentBuilder
import ch.epfl.ts.component.ComponentRef
import ch.epfl.ts.data.Transaction
import ch.epfl.ts.data.MarketAskOrder
import ch.epfl.ts.data.MarketBidOrder
import ch.epfl.ts.traders.MadTrader
import ch.epfl.ts.data.CurrencyPairParameter
import ch.epfl.ts.data.Currency
import ch.epfl.ts.data.WalletParameter
import ch.epfl.ts.engine.Wallet

case object WorkerIsLive

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
 */
class MasterActor extends Actor {

  var workers: MutableList[ActorRef] = MutableList()
  var nAlive = 0

  override def receive = {
    case w: ActorRef => {
      workers += w
    }
    case WorkerIsLive => {
      nAlive += 1
      println("Master sees " + nAlive + " workers available.")
    }
    case s: String => {
      println("MasterActor received string: " + s)
    }
    case m => println("MasterActor received weird: " + m)
  }


  def pingAllWorkers = workers.foreach {
    w => w ! 'Ping
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

  def main(args: Array[String]): Unit = {

    // ----- Build the supervisor actor
    implicit val builder = new ComponentBuilder()
    val master = builder.createRef(Props(classOf[MasterActor]), "MasterActor")
    
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
      // TODO
      d.market -> (d.printer, classOf[Quote])
      
    	// ----- Registration to the supervisor
    	// Register this new trader to the master
    	for(e <- d.evaluators) master.ar ! e.ar
    })
                                                                        
    
    //builder.start
    // TODO: handle evaluator reports on stop
    // TODO: select the best strategy
    
    builder.shutdownManagedActors(3 seconds)
  }
}
