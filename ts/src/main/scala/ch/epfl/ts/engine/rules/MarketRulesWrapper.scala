package ch.epfl.ts.engine.rules

import ch.epfl.ts.data.{Streamable, Order}
import ch.epfl.ts.engine.{OrderBook, PartialOrderBook, MarketRules}
import scala.collection.mutable.HashMap
import ch.epfl.ts.data.Currency._
import ch.epfl.ts.data.Quote

/**
 * A wrapper around the MarketRules, so that all the information needed to proceed with the order is in one place.
 * Needed to be able to change between strategies without changing the MarketSimulator.
 */
abstract class MarketRulesWrapper(rules: MarketRules){
  def processOrder(o: Order, marketId: Long,
                   book: OrderBook, tradingPrices: HashMap[(Currency, Currency), (Double, Double)], //TODO(sygi): get type from original class
                   send: Streamable => Unit)
  def getRules = rules

  /** give the first quote to be able to generate them periodically right from the start */
  def initQuotes(q: Quote): Unit
}
