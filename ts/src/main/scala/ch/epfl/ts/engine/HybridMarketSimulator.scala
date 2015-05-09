package ch.epfl.ts.engine

import ch.epfl.ts.data.{Quote, Order, Streamable}
import ch.epfl.ts.engine.rules.{FxMarketRulesWrapper, MarketRulesWrapper}

/**
 * Market simulator, where first the data is being received from fetcher and market behaves in a way that traders' orders
 * don't influence the prices (quotes) and after some time it changes to the regular market simulation,
 * where traders buy/sell only among them.
 * Rules should have same bids/asks ordering.
 * Works only for one currency pair.
 * Created by sygi on 09.05.15.
 */
class HybridMarketSimulator(marketId: Long, rules1: FxMarketRulesWrapper, rules2: MarketRulesWrapper) extends MarketSimulator(marketId, rules1.getRules) {
  var rulesChanged = false
  override def receiver: PartialFunction[Any, Unit] = {
    case o: Order =>
      getCurrentRules.processOrder(o, marketId, book, tradingPrices, this.send[Streamable])
    case 'ChangeMarketRules =>
      println("Hybrid market: changing rules")
      changeRules
    case q: Quote =>
      println("Hybrid market: got quote: " + q)
      tradingPrices((q.withC, q.whatC)) = (q.bid, q.ask)
      if (!rulesChanged)
        rules1.checkPendingOrders(marketId, book, tradingPrices, this.send[Streamable])
      send(q)
  }

  def getCurrentRules = {
    if (rulesChanged)
      rules2
    else
      rules1
  }
  def changeRules =
    rulesChanged = !rulesChanged
}
