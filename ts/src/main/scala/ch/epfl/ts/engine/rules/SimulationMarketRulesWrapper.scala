package ch.epfl.ts.engine.rules

import ch.epfl.ts.engine.{OrderBook, MarketRules}
import ch.epfl.ts.data._
import ch.epfl.ts.data.Currency._
import ch.epfl.ts.data.MarketBidOrder
import ch.epfl.ts.data.LimitBidOrder
import ch.epfl.ts.data.MarketAskOrder
import ch.epfl.ts.data.LimitAskOrder
import scala.collection.mutable.HashMap

class SimulationMarketRulesWrapper(val rules: MarketRules = new MarketRules()) extends MarketRulesWrapper(rules){
  override def processOrder(o: Order, marketId: Long,
                   book: OrderBook, tradingPrices: HashMap[(Currency, Currency), (Double, Double)],
                   send: Streamable => Unit) = {
    o match {
      case limitBid: LimitBidOrder =>
        val currentPrice = tradingPrices((limitBid.withC, limitBid.whatC))
        val newBidPrice = rules.matchingFunction(
          marketId, limitBid, book.bids, book.asks,
          send,
          (a, b) => a <= b, currentPrice._1,
          (limitBid, bidOrdersBook) => {
            bidOrdersBook insert limitBid
          })
        tradingPrices((limitBid.withC, limitBid.whatC)) = (newBidPrice, currentPrice._2)

      case limitAsk: LimitAskOrder =>
        val currentPrice = tradingPrices((limitAsk.withC, limitAsk.whatC))
        val newAskPrice = rules.matchingFunction(
          marketId, limitAsk, book.asks, book.bids,
          send,
          (a, b) => a >= b, currentPrice._2,
          (limitAsk, askOrdersBook) => {
            askOrdersBook insert limitAsk
          })
        tradingPrices((limitAsk.withC, limitAsk.whatC)) = (currentPrice._1, newAskPrice)

      case marketBid: MarketBidOrder =>
        val currentPrice = tradingPrices((marketBid.withC, marketBid.whatC))
        val newBidPrice = rules.matchingFunction(
          marketId, marketBid, book.bids, book.asks,
          send,
          (a, b) => true,
          currentPrice._1,
          (marketBid, bidOrdersBook) => println("SMRW: market order discarded - there is no matching order"))
        tradingPrices((marketBid.withC, marketBid.whatC)) = (newBidPrice, currentPrice._2)

      case marketAsk: MarketAskOrder =>
        val currentPrice = tradingPrices((marketAsk.withC, marketAsk.whatC))
        val newAskPrice = rules.matchingFunction(
          marketId, marketAsk, book.asks, book.bids,
          send,
          (a, b) => true,
          currentPrice._2,
          (marketAsk, askOrdersBook) => println("SMRW: market order discarded - there is no matching order"))
        tradingPrices((marketAsk.withC, marketAsk.whatC)) = (currentPrice._1, newAskPrice)
    }
  }
}
