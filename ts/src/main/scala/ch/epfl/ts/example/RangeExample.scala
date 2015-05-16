package ch.epfl.ts.example

import scala.language.postfixOps

import ch.epfl.ts.data.CoefficientParameter
import ch.epfl.ts.data.Currency
import ch.epfl.ts.data.CurrencyPairParameter
import ch.epfl.ts.data.RealNumberParameter
import ch.epfl.ts.data.StrategyParameters
import ch.epfl.ts.data.WalletParameter
import ch.epfl.ts.engine.Wallet
import ch.epfl.ts.traders.RangeTrader
import ch.epfl.ts.traders.TraderCompanion

object RangeExample extends AbstractTraderShowcaseExample {

  val useLiveData = false
  val replaySpeed = 4000.0
  val startDate = "201304"
  val endDate = "201304"
  
  val symbol = (Currency.EUR, Currency.CHF)

  val strategy: TraderCompanion = RangeTrader
  val parameterization = {
    val initialFunds: Wallet.Type = Map(Currency.CHF -> 10000000.0)
    new StrategyParameters(
      RangeTrader.INITIAL_FUNDS -> WalletParameter(initialFunds),
      RangeTrader.SYMBOL -> CurrencyPairParameter(Currency.USD, Currency.CHF),
      RangeTrader.VOLUME -> RealNumberParameter(10.0),
      RangeTrader.ORDER_WINDOW -> CoefficientParameter(0.20)
    )
  }

}
