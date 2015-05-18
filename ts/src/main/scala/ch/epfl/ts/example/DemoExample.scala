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
import ch.epfl.ts.traders.MadTrader

/**
 * Class used for a live demo of the project
 */
object DemoExample extends AbstractOptimizationExample {
  
  override val maximumRunDuration: Option[FiniteDuration] = None
  
  val symbol = (Currency.EUR, Currency.CHF)
  
  // Historical data
  val useLiveData = false
  val replaySpeed = 10000.0
  val startDate = "201304"
  val endDate = "201304"
  
  // Evaluation
  override val evaluationPeriod = (10 seconds)
  
  /** Names for our trader instances */
  override lazy val traderNames = {
    val urlToNames = getClass.getResource("/names-shuffled.txt")
    val names = Source.fromFile(urlToNames.toURI()).getLines()
    names.toSet
  }
  
  // Trading strategy
  val maxInstances = traderNames.size
  val strategy = MadTrader
  val parametersToOptimize = Set(
    MadTrader.ORDER_VOLUME,
    MadTrader.ORDER_VOLUME_VARIATION
  )
  val otherParameterValues = {
    val initialWallet: Wallet.Type = Map(symbol._1 -> 5000.0, symbol._2 -> 5000.0)
    Map(MadTrader.INITIAL_FUNDS -> WalletParameter(initialWallet),
        MadTrader.CURRENCY_PAIR -> CurrencyPairParameter(symbol),
        MadTrader.INTERVAL -> new TimeParameter(5 second))
  }
  
  
  override def main(args: Array[String]): Unit = {
    println("Going to create " + parameterizations.size + " traders on localhost")
    
    // ----- Create instances
    val d = factory.createDeployment(localHost, strategy, parameterizations, traderNames)

    // ----- Connecting actors
    makeConnections(d)

    // ----- Start
    // Give an early start to important components
    supervisorActor.get.ar ! StartSignal
    d.broker.ar ! StartSignal
    d.market.ar ! StartSignal
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
