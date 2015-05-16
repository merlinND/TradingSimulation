package ch.epfl.ts.benchmark.marketSimulator

import ch.epfl.ts.component.Component
import ch.epfl.ts.data.Currency
import ch.epfl.ts.data.Transaction

/**
 * Trader used to compute a trader's reaction in the
 * TraderReactionBenchmark.
 */
class BenchmarkTrader extends Component {

  def receiver = {
    case t:Transaction => send(LastOrder(0L, 0L, 0L, Currency.BTC, Currency.USD, 0.0, 0.0))
    case _ =>
  }
}
