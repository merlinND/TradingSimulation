package ch.epfl.ts.engine

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import akka.actor.ActorLogging
import akka.actor.Props
import ch.epfl.ts.component.utils.Timekeeper
import ch.epfl.ts.data.Currency
import ch.epfl.ts.data.Order
import ch.epfl.ts.data.Quote
import ch.epfl.ts.data.Streamable
import ch.epfl.ts.data.TheTimeIs
import ch.epfl.ts.engine.rules.FxMarketRulesWrapper
import ch.epfl.ts.engine.rules.MarketRulesWrapper

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
  private var lastHistoricalTime = 0L
  /** Time period at which to emit  */
  private val timekeeperPeriod = (500 milliseconds)
  /** keep track of last quote before simulating */
  private var lastQuote = Quote(marketId, -1, Currency.DEF, Currency.DEF, -1, -1)

  /** When playing the role of a market maker, apply this spread */
  // TODO: tweak value
  val spread = 0.1


  override def receiver: PartialFunction[Any, Unit] = {
    case o: Order => {
      getCurrentRules.processOrder(o, marketId, book, tradingPrices, this.send[Streamable])
      if (isSimulating)
        playMarketMaker()
    }

    case 'ChangeMarketRules => {
      log.info("Hybrid market: changing rules")
      changeRules

      // We now enter full simulation mode, and we need an actor
      // to take care of the keeping of the time
      context.actorOf(Props(classOf[Timekeeper], self, lastHistoricalTime, timekeeperPeriod), "SimulationTimekeeper")
    }

    case t: TheTimeIs =>
      send(t)

    case q: Quote if (!isSimulating) => {
    	log.debug("HybridMarket: got quote: " + q)

      lastHistoricalTime = q.timestamp
      rules1.checkPendingOrders(marketId, book, tradingPrices, this.send[Streamable])
      tradingPrices((q.withC, q.whatC)) = (q.bid, q.ask)
      lastQuote = q
      send(q)
    }

    case q: Quote if (isSimulating) =>
      log.warning("HybridMarket received a quote when in simulation mode")
  }

  /**
   * Quick & dirty way of simulating a market maker.
   * This helps keeping the simulation running, mostly by adding
   * liquidity to the system.
   */
  // TODO JAKOB this has been changed back for some unknown reason...
  def playMarketMaker() = {
    val asksEmpty = if (book.asks.isEmpty) 1 else 0
    val bidsEmpty = if (book.bids.isEmpty) 1 else 0
    if (asksEmpty + bidsEmpty >= 1){
      val msg = if (asksEmpty == 1){
        val topBid = book.bids.head
        MarketAsksEmpty(topBid.timestamp, topBid.whatC, topBid.withC, topBid.volume, topBid.price)
      } else if (bidsEmpty == 1) {
        val topAsk = book.asks.head
        MarketBidsEmpty(topAsk.timestamp, topAsk.whatC, topAsk.withC, topAsk.volume, topAsk.price)
      } else {
        MarketEmpty()
      }
      send(msg)
    }
  }

  def getCurrentRules = {
    if (isSimulating)
      rules2
    else
      rules1
  }
  def changeRules = {
    if (!isSimulating) rules2.initQuotes(lastQuote) // allows for smooth transition to simulation
    isSimulating = !isSimulating
  }
}
