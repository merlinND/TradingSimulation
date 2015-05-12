package ch.epfl.ts.engine

import scala.language.postfixOps
import scala.concurrent.duration.DurationInt
import ch.epfl.ts.data.{Quote, Order, Streamable}
import ch.epfl.ts.engine.rules.{FxMarketRulesWrapper, MarketRulesWrapper}
import akka.actor.ActorLogging
import ch.epfl.ts.component.utils.Timekeeper
import akka.actor.Props
import ch.epfl.ts.data.TheTimeIs

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
  /** Information whether the market is now in simulation mode (traders trade only among themselves) or in replay mode
   * (with high liquidity assumption = all market orders are executed immediately at the market price)
   */
  private var isSimulating = false
  
  /** Most recent time read from historical data (in milliseconds) */
  private var lastHistoricalTime = 0L;
  /** Time period at which to emit  */
  private val timekeeperPeriod = (500 milliseconds)
  
  override def receiver: PartialFunction[Any, Unit] = {
    case o: Order => {
      getCurrentRules.processOrder(o, marketId, book, tradingPrices, this.send[Streamable])
    }
    
    case 'ChangeMarketRules => {
      log.info("Hybrid market: changing rules")
      changeRules
      
      // We now enter full simulation mode, and we need an actor
      // to take care of the keeping of the time
      val timekeeper = context.actorOf(Props(classOf[Timekeeper], self, lastHistoricalTime, timekeeperPeriod), "SimulationTimekeeper")
    }
    
    case t: TheTimeIs =>
      send(t)
    
    case q: Quote if (!isSimulating) => {
    	log.debug("HybridMarket: got quote: " + q)

      lastHistoricalTime = q.timestamp
      rules1.checkPendingOrders(marketId, book, tradingPrices, this.send[Streamable])
      tradingPrices((q.withC, q.whatC)) = (q.bid, q.ask)
      send(q)
    }
     
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
