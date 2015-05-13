package ch.epfl.ts.engine

import ch.epfl.ts.data.Currency._
import ch.epfl.ts.data._
import ch.epfl.ts.component.fetch.MarketNames
import ch.epfl.ts.data.MarketBidOrder
import ch.epfl.ts.data.Transaction
import ch.epfl.ts.data.DelOrder
import ch.epfl.ts.data.LimitBidOrder
import ch.epfl.ts.data.MarketAskOrder
import ch.epfl.ts.data.LimitAskOrder

/**
 * Represents the cost of placing a bid and market order
 */
case class Commission(limitOrderFee: Double, marketOrderFee: Double)

/**
 * Market Simulator Configuration class. Defines the orders books priority implementation, the matching function and the commission costs of limit and market orders.
 * Extend this class and override its method(s) to customize Market rules for specific markets.
 *
 */
class MarketRules extends Serializable {
  val commission = Commission(0, 0)
  
  var lastBidPrice = 1.0
  var lastAskPrice = 1.0
  var withC = Currency.DEF
  var whatC = Currency.DEF
  

  var lastBidPrice = 1.0
  var lastAskPrice = 1.0
  var withC = Currency.DEF
  var whatC = Currency.DEF

  // when used on TreeSet, head() and iterator() provide increasing order
  def asksOrdering = new Ordering[Order] {
    def compare(first: Order, second: Order): Int =
      if (first.price > second.price) 1
      else if (first.price < second.price) -1
      else {
        if (first.timestamp < second.timestamp) 1 else if (first.timestamp > second.timestamp) -1 else 0
      }
  }

  // when used on TreeSet, head() and iterator() provide decreasing order
  def bidsOrdering = new Ordering[Order] {
    def compare(first: Order, second: Order): Int =
      if (first.price > second.price) -1
      else if (first.price < second.price) 1
      else {
        if (first.timestamp < second.timestamp) 1 else if (first.timestamp > second.timestamp) -1 else 0
      }
  }

  def alwaysTrue(a: Double, b: Double) = true

  /**
   * It tries to match the new order with the orders in the PartialOrderBooks and generates new Quotes for the market.
   * @param marketId
   * @param newOrder the order you want to process right now
   * @param newOrdersBook the partial order book of the orders of the same type (bid or ask) as the newOrder
   * @param bestMatchesBook the partial order book of the orders of the opposite type (ask or bid)
   * @param send a belonging to the Actor function that is going to be executed with executed Order (if there is one)
   *             and other Messages that are going to be sent to the other Actors
   * @param matchExists the actual matching function (checks if the order should be executed right now) between the newOrder
   *                    and the top entry in bestMatchesBook
   * @param oldTradingPrice previous market price of the same type (bid/ask) as the newOrder
   * @param enqueueOrElse the function that basically does: "newOrdersBook insert newOrder" - it enqueues the newOrder.
   *                      It gets executed only when there's no match for the order.
   * @return the price of last executed order (possibly this one) of newOrder type (bid/ask) (no change if there was no match)
   */
  def matchingFunction(marketId: Long,
                       newOrder: Order,
                       newOrdersBook: PartialOrderBook,
                       bestMatchesBook: PartialOrderBook,
                       send: Streamable => Unit,
                       matchExists: (Double, Double) => Boolean = alwaysTrue,
                       oldTradingPrice: Double,
                       enqueueOrElse: (Order, PartialOrderBook) => Unit): Double = {
    var result = -1.0
    if (withC == Currency.DEF) withC = newOrder.withC // make sure these are set
    if (whatC == Currency.DEF) whatC = newOrder.whatC

    if (bestMatchesBook.isEmpty) {
      println("MS: matching orders book empty")
      enqueueOrElse(newOrder, newOrdersBook)
      result = oldTradingPrice
    } else {

      val bestMatch = bestMatchesBook.head
      // check if a matching order exists when used with a limit order, if market order: matchExists = true
      if (matchExists(bestMatch.price, newOrder.price)) {

        bestMatchesBook delete bestMatch
        send(DelOrder(bestMatch.oid, bestMatch.uid, newOrder.timestamp, DEF, DEF, 0.0, 0.0))

        // perfect match
        if (bestMatch.volume == newOrder.volume) {
          println("MS: volume match with " + bestMatch)
          println("MS: removing order: " + bestMatch + " from bestMatch orders book.")

          bestMatch match {
            case lbo: LimitBidOrder =>
              send(Transaction(marketId, bestMatch.price, bestMatch.volume, newOrder.timestamp, bestMatch.whatC, bestMatch.withC, bestMatch.uid, bestMatch.oid, newOrder.uid, newOrder.oid))
            case lao: LimitAskOrder =>
              send(Transaction(marketId, bestMatch.price, bestMatch.volume, newOrder.timestamp, bestMatch.whatC, bestMatch.withC, newOrder.uid, newOrder.oid, bestMatch.uid, bestMatch.oid))
            case _ => println("MarketRules: casting error")
          }
        } else if (bestMatch.volume > newOrder.volume) {
          println("MS: matched with " + bestMatch + ", new order volume inferior - cutting matched order.")
          println("MS: removing order: " + bestMatch + " from match orders book. enqueuing same order with " + (bestMatch.volume - newOrder.volume) + " volume.")
          bestMatch match {
            case lbo: LimitBidOrder =>
              bestMatchesBook insert LimitBidOrder(bestMatch.oid, bestMatch.uid, bestMatch.timestamp, bestMatch.whatC, bestMatch.withC, bestMatch.volume - newOrder.volume, bestMatch.price)
              send(Transaction(marketId, bestMatch.price, newOrder.volume, newOrder.timestamp, bestMatch.whatC, bestMatch.withC, bestMatch.uid, bestMatch.oid, newOrder.uid, newOrder.oid))
              send(LimitBidOrder(bestMatch.oid, bestMatch.uid, bestMatch.timestamp, bestMatch.whatC, bestMatch.withC, bestMatch.volume - newOrder.volume, bestMatch.price))
            case lao: LimitAskOrder =>
              bestMatchesBook insert LimitAskOrder(bestMatch.oid, bestMatch.uid, bestMatch.timestamp, bestMatch.whatC, bestMatch.withC, bestMatch.volume - newOrder.volume, bestMatch.price)
              send(Transaction(marketId, bestMatch.price, newOrder.volume, newOrder.timestamp, bestMatch.whatC, bestMatch.withC, newOrder.uid, newOrder.oid, bestMatch.uid, bestMatch.oid))
              send(LimitAskOrder(bestMatch.oid, bestMatch.uid, bestMatch.timestamp, bestMatch.whatC, bestMatch.withC, bestMatch.volume - newOrder.volume, bestMatch.price))
            case _ => println("MarketRules: casting error")
          }
        } else {
          println("MS: matched with " + bestMatch + ", new order volume superior - reiterate")
          println("MS: removing order: " + bestMatch + " from match orders book.")

          bestMatch match {
            case lbo: LimitBidOrder =>
              send(Transaction(marketId, bestMatch.price, bestMatch.volume, newOrder.timestamp, bestMatch.whatC, bestMatch.withC, newOrder.uid, newOrder.oid, bestMatch.uid, bestMatch.oid))
              matchingFunction(marketId, LimitBidOrder(newOrder.oid, newOrder.uid, newOrder.timestamp, newOrder.whatC, newOrder.withC, newOrder.volume - bestMatch.volume, bestMatch.price), newOrdersBook, bestMatchesBook, send, matchExists, oldTradingPrice, enqueueOrElse)
            case lao: LimitAskOrder =>
              send(Transaction(marketId, bestMatch.price, bestMatch.volume, newOrder.timestamp, bestMatch.whatC, bestMatch.withC, bestMatch.uid, bestMatch.oid, newOrder.uid, newOrder.oid))
              matchingFunction(marketId, LimitAskOrder(newOrder.oid, newOrder.uid, newOrder.timestamp, newOrder.whatC, newOrder.withC, newOrder.volume - bestMatch.volume, bestMatch.price), newOrdersBook, bestMatchesBook, send, matchExists, oldTradingPrice, enqueueOrElse)
            case mbo: MarketBidOrder =>
              send(Transaction(marketId, bestMatch.price, bestMatch.volume, newOrder.timestamp, bestMatch.whatC, bestMatch.withC, newOrder.uid, newOrder.oid, bestMatch.uid, bestMatch.oid))
              matchingFunction(marketId, MarketBidOrder(newOrder.oid, newOrder.uid, newOrder.timestamp, newOrder.whatC, newOrder.withC, newOrder.volume - bestMatch.volume, newOrder.price), newOrdersBook, bestMatchesBook, send, matchExists, oldTradingPrice, enqueueOrElse)
            case mao: MarketAskOrder =>
              send(Transaction(marketId, bestMatch.price, bestMatch.volume, newOrder.timestamp, bestMatch.whatC, bestMatch.withC, bestMatch.uid, bestMatch.oid, newOrder.uid, newOrder.oid))
              matchingFunction(marketId, MarketAskOrder(newOrder.oid, newOrder.uid, newOrder.timestamp, newOrder.whatC, newOrder.withC, newOrder.volume - bestMatch.volume, newOrder.price), newOrdersBook, bestMatchesBook, send, matchExists, oldTradingPrice, enqueueOrElse)
            case _ => println("MarketRules: casting error")
          }
        }

        // Update price
        result = bestMatch.price

        // no match found
      } else {
        println("MS: no match found - enqueuing")
        enqueueOrElse(newOrder, newOrdersBook)
        result = oldTradingPrice
      }
    }

    newOrder match {
      case _ @ (_:MarketBidOrder | _:LimitBidOrder) =>
        lastBidPrice = result
      case _ @ (_:MarketAskOrder | _:LimitAskOrder) =>
        lastAskPrice = result
    }

    generateQuote(marketId, newOrdersBook, bestMatchesBook, newOrder.timestamp, send)
    result
  }

  //ob1 and ob2 contains the two partial orderbooks: asks and bids, but we don't know which is which.
  def generateQuote(marketId: Long, ob1: PartialOrderBook, ob2: PartialOrderBook, timestamp: Long, send: Streamable => Unit) = {
    if (!ob1.isEmpty && !ob2.isEmpty){
      var topAsk = ob1.head
      var topBid = ob2.head
      if (topAsk.price < topBid.price){
        val tmp = topAsk
        topAsk = topBid
        topBid = tmp
      }
      lastBidPrice = topBid.price
      lastAskPrice = topAsk.price
      whatC = topAsk.whatC
      withC = topAsk.withC
      
      val q = Quote(marketId, timestamp, topAsk.whatC, topAsk.withC, topBid.price, topAsk.price)
      println("MR: generating quote " + q)
      send(q)
    } else {
      if (whatC != Currency.DEF && withC != Currency.DEF) {
        val q = Quote(marketId, timestamp, whatC, withC, lastBidPrice, lastAskPrice)
        println("MR: can't generate new quote but using old one: " + q)
        send(q)
      }
    }
  }
}
