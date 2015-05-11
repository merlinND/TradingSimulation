package ch.epfl.ts.engine

import ch.epfl.ts.data._
import akka.actor.ActorLogging
import ch.epfl.ts.data.MarketBidOrder
import ch.epfl.ts.data.Quote
import ch.epfl.ts.data.LimitBidOrder
import scala.Some
import ch.epfl.ts.data.MarketAskOrder
import ch.epfl.ts.data.LimitAskOrder
import ch.epfl.ts.engine.rules.FxMarketRulesWrapper

class MarketFXSimulator(marketId: Long, val rulesWrapper: FxMarketRulesWrapper = new FxMarketRulesWrapper())
    extends MarketSimulator(marketId, rulesWrapper.rules) with ActorLogging {
  override def receiver = {
    case o: Order =>
      rulesWrapper.processOrder(o, marketId, book, tradingPrices, this.send[Streamable])
    case q: Quote =>
      log.debug("FxMS: got quote: " + q)
      tradingPrices((q.withC, q.whatC)) = (q.bid, q.ask)
      rulesWrapper.checkPendingOrders(marketId, book, tradingPrices, this.send[Streamable])
      send(q)
    case _ =>
      println("FxMS: got unknown")
  }
}
