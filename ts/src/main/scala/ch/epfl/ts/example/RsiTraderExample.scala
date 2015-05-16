package ch.epfl.ts.example

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import ch.epfl.ts.data.BooleanParameter
import ch.epfl.ts.data.Currency
import ch.epfl.ts.data.CurrencyPairParameter
import ch.epfl.ts.data.NaturalNumberParameter
import ch.epfl.ts.data.RealNumberParameter
import ch.epfl.ts.data.StrategyParameters
import ch.epfl.ts.data.TimeParameter
import ch.epfl.ts.data.WalletParameter
import ch.epfl.ts.engine.Wallet
import ch.epfl.ts.traders.RsiTrader
import ch.epfl.ts.traders.TraderCompanion

object RsiTraderExample extends AbstractTraderShowcaseExample {

  val useLiveData = false
  val replaySpeed = 4000.0
  val startDate = "201304"
  val endDate = "201304"
  
  val symbol = (Currency.EUR, Currency.CHF)

  val strategy: TraderCompanion = RsiTrader
  val parameterization = {
    val initialFunds: Wallet.Type = Map(Currency.CHF -> 100000.0)
    new StrategyParameters(
      RsiTrader.INITIAL_FUNDS -> WalletParameter(initialFunds),
      RsiTrader.SYMBOL -> CurrencyPairParameter(symbol),
      RsiTrader.OHLC_PERIOD -> new TimeParameter(1 hour),
      RsiTrader.RSI_PERIOD->new NaturalNumberParameter(12),
      RsiTrader.HIGH_RSI -> RealNumberParameter(80),
      RsiTrader.LOW_RSI -> RealNumberParameter(20),
      RsiTrader.WITH_SMA_CONFIRMATION->BooleanParameter(true),
      RsiTrader.LONG_SMA_PERIOD->new NaturalNumberParameter(20)
    )
  }

}
