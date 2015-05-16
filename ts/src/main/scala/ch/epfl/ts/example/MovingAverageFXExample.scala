package ch.epfl.ts.example

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import ch.epfl.ts.data.BooleanParameter
import ch.epfl.ts.data.CoefficientParameter
import ch.epfl.ts.data.Currency
import ch.epfl.ts.data.CurrencyPairParameter
import ch.epfl.ts.data.EndOfFetching
import ch.epfl.ts.data.MarketAskOrder
import ch.epfl.ts.data.MarketBidOrder
import ch.epfl.ts.data.NaturalNumberParameter
import ch.epfl.ts.data.Quote
import ch.epfl.ts.data.RealNumberParameter
import ch.epfl.ts.data.Register
import ch.epfl.ts.data.StrategyParameters
import ch.epfl.ts.data.TimeParameter
import ch.epfl.ts.data.Transaction
import ch.epfl.ts.data.WalletParameter
import ch.epfl.ts.engine.ExecutedAskOrder
import ch.epfl.ts.engine.ExecutedBidOrder
import ch.epfl.ts.engine.FundWallet
import ch.epfl.ts.engine.GetWalletFunds
import ch.epfl.ts.engine.Wallet
import ch.epfl.ts.evaluation.EvaluationReport
import ch.epfl.ts.optimization.ForexLiveStrategyFactory
import ch.epfl.ts.optimization.ForexReplayStrategyFactory
import ch.epfl.ts.optimization.SystemDeployment
import ch.epfl.ts.traders.MovingAverageTrader
import ch.epfl.ts.traders.TraderCompanion

object MovingAverageFXExample extends AbstractForexExample with TraderEvaluation {
	
  val useLiveData = false
	val symbol = (Currency.EUR, Currency.CHF)
  val evaluationPeriod = (10 seconds)
  val referenceCurrency = symbol._2
  
  // If using historical data, use this historical period & speed
  val speed = 4000.0
  val startDate = "201304"
  val endDate = "201304"
  
  val factory = {
    if(useLiveData) new ForexLiveStrategyFactory(evaluationPeriod, referenceCurrency)
    else new ForexReplayStrategyFactory(evaluationPeriod, referenceCurrency, symbol, speed, startDate, endDate)
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
  
  def main(args: Array[String]): Unit = {
    
    // Trader: cross moving average
	  val strategy: TraderCompanion = MovingAverageTrader
    val traderId = 123L
    val periods = List(2, 6)
    val initialFunds: Wallet.Type = Map(Currency.CHF -> 5000.0)
    val parameters = new StrategyParameters(
      MovingAverageTrader.INITIAL_FUNDS -> WalletParameter(initialFunds),
      MovingAverageTrader.SYMBOL -> CurrencyPairParameter(symbol),

      MovingAverageTrader.OHLC_PERIOD -> new TimeParameter(1 day),
      MovingAverageTrader.SHORT_PERIODS -> NaturalNumberParameter(periods(0)),
      MovingAverageTrader.LONG_PERIODS -> NaturalNumberParameter(periods(1)),
      MovingAverageTrader.TOLERANCE -> RealNumberParameter(0.0002),
      MovingAverageTrader.WITH_SHORT -> BooleanParameter(true),
      MovingAverageTrader.SHORT_PERCENT -> CoefficientParameter(0.2))
    
		// ----- Creating actors
    val d = factory.createDeployment(localHost, strategy, Set(parameters))

    // ----- Connecting actors
    makeConnections(d)

    // ----- Start
    builder.start
  }
}
