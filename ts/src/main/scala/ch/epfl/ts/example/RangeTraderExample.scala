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

object RangeTraderExample extends AbstractTraderShowcaseExample {

  val useLiveData = false
  val replaySpeed = 10000.0
  val startDate = "201304"
  val endDate = "201304"
  
  val symbol = (Currency.EUR, Currency.CHF)

  val strategy: TraderCompanion = RangeTrader
  val parameterizations = Set({
    val initialFunds: Wallet.Type = Map(symbol._1 -> 5000.0, symbol._2 -> 10000.0)
    new StrategyParameters(
      RangeTrader.INITIAL_FUNDS -> WalletParameter(initialFunds),
      RangeTrader.SYMBOL -> CurrencyPairParameter(symbol),
      RangeTrader.ORDER_WINDOW -> CoefficientParameter(0.20)
    )
  })

}
