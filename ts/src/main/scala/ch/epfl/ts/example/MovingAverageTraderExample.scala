package ch.epfl.ts.example

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import ch.epfl.ts.data.BooleanParameter
import ch.epfl.ts.data.CoefficientParameter
import ch.epfl.ts.data.Currency
import ch.epfl.ts.data.CurrencyPairParameter
import ch.epfl.ts.data.NaturalNumberParameter
import ch.epfl.ts.data.RealNumberParameter
import ch.epfl.ts.data.StrategyParameters
import ch.epfl.ts.data.TimeParameter
import ch.epfl.ts.data.WalletParameter
import ch.epfl.ts.engine.Wallet
import ch.epfl.ts.traders.MovingAverageTrader
import ch.epfl.ts.traders.TraderCompanion

object MovingAverageTraderExample extends AbstractTraderShowcaseExample {

  val useLiveData = false
  val replaySpeed = 4000.0
  val startDate = "201304"
  val endDate = "201304"
  
  val symbol = (Currency.EUR, Currency.CHF)

  val strategy: TraderCompanion = MovingAverageTrader
  val parameterizations = Set({
    val periods = List(2, 6)
    val initialFunds: Wallet.Type = Map(Currency.CHF -> 5000.0)
    new StrategyParameters(
      MovingAverageTrader.INITIAL_FUNDS -> WalletParameter(initialFunds),
      MovingAverageTrader.SYMBOL -> CurrencyPairParameter(symbol),

      MovingAverageTrader.OHLC_PERIOD -> new TimeParameter(1 day),
      MovingAverageTrader.SHORT_PERIODS -> NaturalNumberParameter(periods(0)),
      MovingAverageTrader.LONG_PERIODS -> NaturalNumberParameter(periods(1)),
      MovingAverageTrader.TOLERANCE -> RealNumberParameter(0.0002),
      MovingAverageTrader.WITH_SHORT -> BooleanParameter(true),
      MovingAverageTrader.SHORT_PERCENT -> CoefficientParameter(0.2))
  })

}
