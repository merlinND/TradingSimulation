package ch.epfl.ts.optimization

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import ch.epfl.ts.data.Currency
import ch.epfl.ts.data.CurrencyPairParameter
import ch.epfl.ts.data.RealNumberParameter
import ch.epfl.ts.data.TimeParameter
import ch.epfl.ts.data.WalletParameter
import ch.epfl.ts.engine.Wallet
import ch.epfl.ts.example.AbstractOptimizationExample
import ch.epfl.ts.traders.MovingAverageTrader
import ch.epfl.ts.example.RemotingDeployment
import ch.epfl.ts.component.StartSignal
import scala.concurrent.duration.FiniteDuration

/**
 * Runs a main() method that creates all remote systems
 * as well as an `OptimizationSupervisor` to watch them all.
 * It assumes that there is a `RemotingWorker` class running and listening
 * on every availableWorker.
 *
 * @see {@link ch.epfl.ts.optimization.RemotingWorker}
 */
object RemotingMasterRunner extends AbstractOptimizationExample with RemotingDeployment {

  override val maximumRunDuration: Option[FiniteDuration] = None

  val availableHosts = {
    val availableWorkers = List(
      "ts-1-021qv44y.cloudapp.net",
      "ts-2.cloudapp.net",
      "ts-3.cloudapp.net",
      "ts-4.cloudapp.net",
      "ts-5.cloudapp.net",
      "ts-6.cloudapp.net",
      "ts-7.cloudapp.net",
      "ts-8.cloudapp.net"
    )
    val port = 3333
    // TODO: lookup in configuration
    val systemName = "remote"

    availableWorkers.map(hostname => {
      val prefix = hostname.substring(0, 4)
      new RemoteHost(hostname, port, prefix, systemName)
    })
  }

  val symbol = (Currency.USD, Currency.CHF)

  val useLiveData = false
  val replaySpeed = 4000.0
  val startDate = "201304"
  val endDate = "201304"

  // ----- Trading strategy
  val maxInstances = (10 * availableHosts.size)
  val strategy = MovingAverageTrader
  val parametersToOptimize = Set(
    MovingAverageTrader.SHORT_PERIODS,
    MovingAverageTrader.LONG_PERIODS
  )
  val otherParameterValues = {
    val initialWallet: Wallet.Type = Map(symbol._1 -> 0, symbol._2 -> 5000.0)
    Map(MovingAverageTrader.INITIAL_FUNDS -> WalletParameter(initialWallet),
        MovingAverageTrader.SYMBOL -> CurrencyPairParameter(symbol),
        MovingAverageTrader.OHLC_PERIOD -> new TimeParameter(1 day),
        MovingAverageTrader.TOLERANCE -> RealNumberParameter(0.0002))
  }

  override def main(args: Array[String]): Unit = {
    println("Going to distribute " + parameterizations.size + " traders over " + availableHosts.size + " worker machines.")

    // ----- Create instances over many hosts
    def distributed = factory.distributeOverHosts(availableHosts, parameterizations)
    lazy val deployments = distributed.map({ case (host, parameters) =>
      println("Creating " + parameters.size + " instances of " + strategy.getClass.getSimpleName + " on host " + host)
      factory.createDeployment(host, strategy, parameterizations, traderNames)
    })

    // ----- Connections
    deployments.foreach(makeConnections(_))

    // ----- Start
    // Make sure brokers are started before the traders
    supervisorActor.get.ar ! StartSignal
    for(d <- deployments) d.broker.ar ! StartSignal
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
