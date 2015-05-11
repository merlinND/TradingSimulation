package ch.epfl.ts.engine

import ch.epfl.ts.data.{Quote, Order, Streamable}
import ch.epfl.ts.engine.rules.{FxMarketRulesWrapper, MarketRulesWrapper}
import akka.actor.ActorLogging

/**
 * Market simulator, where first the data is being received from fetcher and market behaves in a way that traders' orders
 * don't influence the prices (replay mode) and after some time it changes to the regular market simulation (simulation mode),
 * where traders buy/sell only among them.
 * Rules should have same bids/asks ordering.
 * Works only for one currency pair.
 * Created by sygi on 09.05.15.
 */
class HybridMarketSimulator(marketId: Long, rules1: FxMarketRulesWrapper, rules2: MarketRulesWrapper)
    extends MarketSimulator(marketId, rules1.getRules) with ActorLogging {
  /** information whether the market is now in simulation mode (traders trade only among themselves) or in replay mode
   * (with high liquidity assumption = all market orders are executed immediately at the market price)
   */
  var isSimulating = false
  override def receiver: PartialFunction[Any, Unit] = {
    case o: Order =>
      getCurrentRules.processOrder(o, marketId, book, tradingPrices, this.send[Streamable])
    case 'ChangeMarketRules =>
      log.info("Hybrid market: changing rules")
      changeRules
    case q: Quote if (!isSimulating) =>
      rules1.checkPendingOrders(marketId, book, tradingPrices, this.send[Streamable])
      log.debug("HybridMarket: got quote: " + q)
      tradingPrices((q.withC, q.whatC)) = (q.bid, q.ask)
      send(q)
    case q: Quote if (isSimulating) =>
      log.warning("HybridMarket received a quote when in simulation mode")
  }

  def getCurrentRules = {
    if (isSimulating)
      rules2
    else
      rules1
  }
  def changeRules =
    isSimulating = !isSimulating
}
