package ch.epfl.ts.example

import scala.language.postfixOps
import scala.io.Source
import scala.concurrent.duration.DurationInt
import ch.epfl.ts.component.ComponentBuilder
import ch.epfl.ts.data.Currency
import ch.epfl.ts.traders.RangeTrader
import ch.epfl.ts.data.StrategyParameters
import scala.concurrent.duration.FiniteDuration
import ch.epfl.ts.traders.MovingAverageTrader
import ch.epfl.ts.engine.Wallet
import ch.epfl.ts.data.CurrencyPairParameter
import ch.epfl.ts.data.RealNumberParameter
import ch.epfl.ts.data.WalletParameter
import ch.epfl.ts.data.TimeParameter
import ch.epfl.ts.component.StartSignal

/**
 * Class used for a live demo of the project
 */
object DemoExample extends AbstractOptimizationExample {
  
  override val maximumRunDuration: Option[FiniteDuration] = None
  
  val symbol = (Currency.EUR, Currency.CHF)
  
  // Historical data
  val useLiveData = false
  val replaySpeed = 4000.0
  val startDate = "201304"
  val endDate = "201304"
  
  // Evaluation
  override val evaluationPeriod = (10 seconds)
  
  /** Names for our trader instances */
  override lazy val traderNames = {
    val urlToNames = getClass.getResource("/names.txt")
    val names = Source.fromFile(urlToNames.toURI()).getLines()
    names.toSet
  }
  
  // Trading strategy
  val maxInstances = traderNames.size
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
    println("Going to create " + parameterizations.size + " traders on localhost")
    
    // ----- Create instances
    val d = factory.createDeployment(localHost, strategy, parameterizations, traderNames)

    // ----- Connecting actors
    makeConnections(d)

    // ----- Start
    // Make sure brokers are started before the traders
    supervisorActor.get.ar ! StartSignal
    d.broker.ar ! StartSignal
    builder.start

    // ----- Registration to the supervisor
    // Register each new trader to the master
    for(e <- d.evaluators) {
      supervisorActor.get.ar ! e.ar
    }

    // ----- Controlled duration (optional)
    maximumRunDuration match {
      case Some(duration) => terminateOptimizationAfter(duration, supervisorActor.get.ar)
      case None =>
    }
  }
}
