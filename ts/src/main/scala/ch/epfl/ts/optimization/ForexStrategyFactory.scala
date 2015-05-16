package ch.epfl.ts.optimization

import akka.actor.Props
import ch.epfl.ts.brokers.StandardBroker
import ch.epfl.ts.component.ComponentBuilder
import ch.epfl.ts.component.ComponentRef
import ch.epfl.ts.component.fetch.MarketNames
import ch.epfl.ts.component.fetch.PullFetchComponent
import ch.epfl.ts.component.utils.Printer
import ch.epfl.ts.data.StrategyParameters
import ch.epfl.ts.engine.MarketFXSimulator
import ch.epfl.ts.engine.rules.FxMarketRulesWrapper
import ch.epfl.ts.traders.TraderCompanion
import scala.concurrent.duration.FiniteDuration
import ch.epfl.ts.data.Currency

/**
 * StrategyFactory allowing to use Forex traders.
 */
abstract class ForexStrategyFactory(override val evaluationPeriod: FiniteDuration,
                                    override val referenceCurrency: Currency)
    extends StrategyFactory {

  protected abstract class ForexCommonProps extends CommonProps {
    def marketIds = List(MarketNames.FOREX_ID)

    // Market
    def market = {
      val rules = new FxMarketRulesWrapper()
      Props(classOf[MarketFXSimulator], marketIds(0), rules)
    }

    def broker = {
      Props(classOf[StandardBroker])
    }

    // Printer
    override def printer = Some(Props(classOf[Printer], "MyPrinter"))
  }
}

class ForexLiveStrategyFactory(evaluationPeriod: FiniteDuration, referenceCurrency: Currency)
    extends ForexStrategyFactory(evaluationPeriod, referenceCurrency) {
  override def commonProps = new ForexCommonProps with LiveFetcher
}

class ForexReplayStrategyFactory(evaluationPeriod: FiniteDuration, referenceCurrency: Currency,
                                 val symbol: (Currency, Currency),
                                 val speed: Double, val start: String, val end: String,
                                 val workingDirectory: String = "./data")
    extends ForexStrategyFactory(evaluationPeriod, referenceCurrency) { self =>

	override def commonProps = new ForexCommonProps with HistoricalFetcher {
    val symbol = self.symbol
    val speed = self.speed
    val start = self.start
    val end = self.end
    override val workingDirectory = self.workingDirectory
  }
}
