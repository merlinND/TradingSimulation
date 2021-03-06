package ch.epfl.ts.data

/**
 * Definition of the System's internal messages.
 */
trait Streamable extends Serializable

/**
 * Message sent out by an actor which takes authority on the system's time.
 * This way, we may override both physical time and historical data time if needed.
 * @see Timekeeper
 */
case class TheTimeIs(now: Long) extends Streamable

/**
 * Data Transfer Object representing a Transaction
 * @param mid market id
 * @param price
 * @param volume
 * @param timestamp
 * @param whatC Which currency we are buying
 * @param withC The currency with which we are buying
 * @param buyerId buyer user id
 * @param buyOrderId buyer order id
 * @param sellerId seller user id
 * @param sellOrderId seller order id
 */
case class Transaction(mid: Long, price: Double, volume: Double, timestamp: Long, whatC: Currency, withC: Currency, buyerId: Long, buyOrderId: Long, sellerId: Long, sellOrderId: Long) extends Streamable

/**
 * Trait representing an object that causes placing a charge on the wallet.
 */
trait Chargeable {
  /**
   * The currency with which we pay (withC in a bidOrder , whatC in an Ask order)
   *
   */
  def costCurrency(): Currency
  def chargedTraderId(): Long
}

/**
 * Data Transfer Object representing a Order
 * @param oid
 * @param uid
 * @param timestamp
 * @param whatC Which currency we are buying
 * @param withC The currency with which we are buying
 * @param volume
 * @param price
 */
abstract class Order() extends Streamable with Chargeable {
  def oid: Long
  def uid: Long
  def timestamp: Long
  def whatC: Currency
  def withC: Currency
  def volume: Double
  def price: Double
  override def chargedTraderId() = uid
  override def costCurrency() = withC
}


/**
 * Type of orders: You send a bid order if you want to buy a security at a given price.
 *                 You send an ask order if you want to sell a security.
 *
 * Limit order:    Limit order are left open during a certain period of time and are matched if the currency
 *                  reach the given price.
 *
 * Market order:   Those orders are immediately executed at the current price
 *
 * Note that the "price" of a currency is the value of 1 unit of whatC currency expressed in withC currency
 *
 * @param whatC Which currency we are buying
 * @param withC The currency with which we are buying
 */
abstract class LimitOrder extends Order

/**
 * @param whatC Which currency we are buying
 * @param withC The currency with which we are buying
 *
 * @see LimitOrder
 */
case class LimitBidOrder(val oid: Long, val uid: Long, val timestamp: Long, val whatC: Currency, val withC: Currency, val volume: Double, val price: Double)
  extends LimitOrder {
}

/**
 * @param whatC Which currency we are buying
 * @param withC The currency with which we are buying
 *
 * @see LimitOrder
 */
case class LimitAskOrder(val oid: Long, val uid: Long, val timestamp: Long, val whatC: Currency, val withC: Currency, val volume: Double, val price: Double)
  extends LimitOrder {
  override def costCurrency() = whatC
}

/**
 * @param whatC Which currency we are buying
 * @param withC The currency with which we are buying
 * Allow shorting of whatC
 * @see LimitOrder
 */
case class LimitShortOrder(val oid: Long, val uid: Long, val timestamp: Long, val whatC: Currency, val withC: Currency, val volume: Double, val price: Double)
  extends LimitOrder {
  override def costCurrency() = whatC
}

//TODO: remove price from common subclass, as for MarketOrders it doesn't make sense
abstract class MarketOrder extends Order

/**
 * @param whatC Which currency we are buying
 * @param withC The currency with which we are buying
 *
 * @see LimitOrder
 */
case class MarketBidOrder(val oid: Long, val uid: Long, val timestamp: Long, val whatC: Currency, val withC: Currency, val volume: Double, val price: Double)
  extends MarketOrder {
}

/**
 * @param whatC Which currency we are buying
 * @param withC The currency with which we are buying
 *
 * @see LimitOrder
 */
case class MarketAskOrder(val oid: Long, val uid: Long, val timestamp: Long, val whatC: Currency, val withC: Currency, val volume: Double, val price: Double)
  extends MarketOrder {
  override def costCurrency() = whatC
}
/**
 * @param whatC Which currency we are buying
 * @param withC The currency with which we are buying
 * Allow shorting of whatC
 * @see LimitOrder
 */
case class MarketShortOrder(val oid: Long, val uid: Long, val timestamp: Long, val whatC: Currency, val withC: Currency, val volume: Double, val price: Double)
  extends MarketOrder {
  override def costCurrency() = whatC
}

/**
 * @param whatC Which currency we are buying
 * @param withC The currency with which we are buying
 *
 * @see LimitOrder
 */
case class DelOrder(val oid: Long, val uid: Long, val timestamp: Long, val whatC: Currency, val withC: Currency, val volume: Double, val price: Double)
  extends Order {
}

/**
 *
 * Represents an Open-High-Low-Close tick, with volume and timestamp (beginning of the tick)
 *
 * @param whatC Which currency we are buying
 * @param withC The currency with which we are buying
 *
 * @see LimitOrder
 */
case class OHLC(marketId: Long, open: Double, high: Double, low: Double, close: Double, volume: Double, timestamp: Long, duration: Long) extends Streamable

/**
 * Forex-style data
 *
 * @param whatC Which currency we are buying
 * @param withC The currency with which we are buying
 *
 * @see LimitOrder
 */
case class Quote(marketId: Long, timestamp: Long, whatC: Currency, withC: Currency, bid: Double, ask: Double) extends Streamable {
  override def toString() = "(" + whatC.toString().toUpperCase() + "/" + withC.toString().toUpperCase() + ") = (" + bid + ", " + ask + ")";
}

/**
 * Signals that the Fetcher component will not send any more messages
 * 
 * @param Last timestamp seen (either real or simulated), in milliseconds
 * @example In the case of historical data, this message is sent out when
 *          there are no more quotes to be sent sent.
 */
case class EndOfFetching(lastTimestamp: Long) extends Streamable

/**
 * Data Transfer Object representing a Tweet
 * @param timestamp
 * @param content
 * @param sentiment
 * @param imagesrc
 * @param author
 */
case class Tweet(timestamp: Long, content: String, sentiment: Int, imagesrc: String, author: String) extends Streamable

/**
 * Messages that are used for the communication between broker and its agents
 * TODO(sygi): put this in separate file
 */
case class Register(traderId: Long) extends Streamable

case class ConfirmRegistration() extends Streamable
