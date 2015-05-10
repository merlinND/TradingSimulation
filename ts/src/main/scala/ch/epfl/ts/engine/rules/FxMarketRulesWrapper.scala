package ch.epfl.ts.engine.rules

import ch.epfl.ts.engine.{OrderBook, ForexMarketRules}
import ch.epfl.ts.data._
import ch.epfl.ts.data.Currency.Currency
import scala.collection.mutable
import scala.Some
import ch.epfl.ts.data.MarketBidOrder
import ch.epfl.ts.data.LimitBidOrder
import scala.Some
import ch.epfl.ts.data.MarketAskOrder
import ch.epfl.ts.data.LimitAskOrder

/**
 * Wrapper around ForexMarketRules, to have ALL the information needed to proceed the order in one method.
 */
class FxMarketRulesWrapper extends MarketRulesWrapper(new ForexMarketRules()) {
  val rules = new ForexMarketRules()
  override def processOrder(o: Order, marketId: Long,
                            book: OrderBook, tradingPrices: mutable.HashMap[(Currency, Currency), (Double, Double)],
                            send: Streamable => Unit): Unit =
  o match {
    case limitBid: LimitBidOrder =>
      book.bids.insert(limitBid)
      checkPendingOrders(marketId, book, tradingPrices, send)

    case limitAsk: LimitAskOrder =>
      book.asks.insert(limitAsk)
      checkPendingOrders(marketId, book, tradingPrices, send)

    case marketBid: MarketBidOrder =>
      println("FMRW : received a bidOrder")
      tradingPrices.get((marketBid.withC, marketBid.whatC)) match {
        // We buy at current ask price
        case Some(t) => rules.matchingFunction(marketId, marketBid, book.bids, book.asks, send, t._2)
        case None    =>
      }
    case marketAsk: MarketAskOrder =>
      println("FMRW : received an askOrder")
      tradingPrices.get((marketAsk.withC, marketAsk.whatC)) match {
        // We sell at current bid price
        case Some(t) => rules.matchingFunction(marketId, marketAsk, book.bids, book.asks, send, t._1)
        //TODO(sygi): does it actually work at all, when there are multiple currencies?
        case None    =>
      }
    case _ =>
      println("FMRW: got unknown order")
  }

  //TODO(sygi): refactor common code
  def checkPendingOrders(marketId: Long,
                         book: OrderBook, tradingPrices: mutable.HashMap[(Currency, Currency), (Double, Double)],
                         send: (Streamable) => Unit) {
    println("FMRW: checking pending orders")
    if (!book.bids.isEmpty) {
      val topBid = book.bids.head
      println("FMRW: topbid: " + topBid)
      tradingPrices.get((topBid.withC, topBid.whatC)) match {
        case Some(t) => {
          val askPrice = t._2
          if (askPrice <= topBid.price) {
            //execute order right now (but on which price, actually?)
            val order = MarketBidOrder(topBid.oid, topBid.uid, topBid.timestamp, topBid.whatC, topBid.withC, topBid.volume, topBid.price)
            //TODO(sygi): Order type casting
            processOrder(order, marketId, book, tradingPrices, send)
          } else
            println("FMRW: can't yet execute order " + topBid)
        }
        case None => println("FMRW: dont have info about topBid " + topBid.withC + " " + topBid.whatC + " pair price")
      }
    }
    if (!book.asks.isEmpty) {
      val topAsk = book.asks.head
      println("FMRW: topask: " + topAsk)
      tradingPrices.get((topAsk.withC, topAsk.whatC)) match {
        case Some(t) => {
          val bidPrice = t._2
          if (bidPrice >= topAsk.price) {
            //execute order right now (but on which price, actually?)
            val order = MarketAskOrder(topAsk.oid, topAsk.uid, topAsk.timestamp, topAsk.whatC, topAsk.withC, topAsk.volume, topAsk.price)
            processOrder(order, marketId, book, tradingPrices, send)
          } else
            println("FMRW: can't yet execute order " + topAsk)
        }
        case None => println("FMRW: dont have info about " + topAsk.withC + " " + topAsk.whatC + " pair price")
      }
    }
  }
}
