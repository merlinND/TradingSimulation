package ch.epfl.ts.traders

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps

import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import ch.epfl.ts.data.CoefficientParameter
import ch.epfl.ts.data.ConfirmRegistration
import ch.epfl.ts.data.Currency
import ch.epfl.ts.data.Currency.CHF
import ch.epfl.ts.data.Currency.USD
import ch.epfl.ts.data.CurrencyPairParameter
import ch.epfl.ts.data.LimitAskOrder
import ch.epfl.ts.data.LimitBidOrder
import ch.epfl.ts.data.MarketBidOrder
import ch.epfl.ts.data.MarketOrder
import ch.epfl.ts.data.NaturalNumberParameter
import ch.epfl.ts.data.Order
import ch.epfl.ts.data.ParameterTrait
import ch.epfl.ts.data.StrategyParameters
import ch.epfl.ts.data.TimeParameter
import ch.epfl.ts.engine.AcceptedOrder
import ch.epfl.ts.engine.ExecutedAskOrder
import ch.epfl.ts.engine.ExecutedBidOrder
import ch.epfl.ts.engine.FundWallet
import ch.epfl.ts.engine.GetWalletFunds
import ch.epfl.ts.engine.MarketAsksEmpty
import ch.epfl.ts.engine.MarketBidsEmpty
import ch.epfl.ts.engine.MarketEmpty
import ch.epfl.ts.engine.RejectedOrder
import ch.epfl.ts.engine.Wallet
import ch.epfl.ts.engine.WalletConfirm
import ch.epfl.ts.engine.WalletFunds

/**
 * MarketMakerTrader companion object
 * acts as a trader who's strategy is:
 * if market demands cannot be fulfilled by other traders
 *    if I have the capacity to fulfill them
 *      -> make a limit order with my spread (charge) added
 *    else
 *      CRY very loud!
 */
object MarketMakerTrader extends TraderCompanion {
  type ConcreteTrader = MarketMakerTrader
  override protected val concreteTraderTag = scala.reflect.classTag[MarketMakerTrader]
  
  /** Interval between two random trades (in ms) */
  val INTERVAL = "interval"

  /** Initial delay before the first random trade (in ms) */
  val INITIAL_DELAY = "initial_delay"

  /** Volume of currency to trade (in currency unit) */
  val ORDER_VOLUME = "order_volume"
  /** Random variations on the volume (in percentage of the order volume, both above and below `ORDER_VOLUME`) */
  val ORDER_VOLUME_VARIATION = "order_volume_variation"

  /** Which currencies to trade */
  val CURRENCY_PAIR = "currency_pair"
  
  /** Market maker specific spread */
  val SPREAD = "spread"

  override def strategyRequiredParameters: Map[Key, ParameterTrait] = Map(
      INTERVAL -> TimeParameter,
      ORDER_VOLUME -> NaturalNumberParameter,
      CURRENCY_PAIR -> CurrencyPairParameter
    )
  override def optionalParameters: Map[Key, ParameterTrait] = Map(
      INITIAL_DELAY -> TimeParameter,
      ORDER_VOLUME_VARIATION -> CoefficientParameter,
      SPREAD -> CoefficientParameter //TODO make usable with values > 1
    )
}

/**
 * broker-aware trader.
 */
class MarketMakerTrader(uid: Long, marketIds: List[Long], parameters: StrategyParameters)
    extends Trader(uid, marketIds, parameters)
    with ActorLogging {
  
  // Allows the usage of ask pattern in an Actor
  import context.dispatcher

  override def companion = MarketMakerTrader

  var broker: ActorRef = null
  var registered = false
  var oid = 1L
  
  val initialDelay = parameters.getOrDefault[FiniteDuration](MarketMakerTrader.INITIAL_DELAY, TimeParameter)
  val interval = parameters.get[FiniteDuration](MarketMakerTrader.INTERVAL)
  val volume = parameters.get[Int](MarketMakerTrader.ORDER_VOLUME)
  val volumeVariation = parameters.getOrElse[Double](MarketMakerTrader.ORDER_VOLUME_VARIATION, 0.1)
  val currencies = parameters.get[(Currency, Currency)](MarketMakerTrader.CURRENCY_PAIR)
  val spread = parameters.getOrElse[Double](MarketMakerTrader.SPREAD, 0.1)


  override def receiver = {
    case msg: MarketEmpty => {
      log.debug("OUCH nothing is going on")
    }
    case topBid: MarketAsksEmpty => {
      log.debug("MarketMaker is asked to sell " + topBid.volume + " " + topBid.whatC.toString() + " for a price of " + topBid.price + " " + topBid.withC.toString() )
      val order = LimitAskOrder(oid, uid, topBid.timestamp, topBid.whatC, topBid.withC, topBid.volume, topBid.price * (1 + spread))
      send[Order](order) // TODO make wallet aware ie use broker
      oid = oid + 1
    }
    case topAsk: MarketBidsEmpty => {
      log.debug("MarketMaker is asked to buy " + topAsk.volume + " " + topAsk.whatC.toString() + " for a price of " + topAsk.price + " " + topAsk.withC.toString() )
      val order = LimitBidOrder(oid, uid, topAsk.timestamp, topAsk.whatC, topAsk.withC, topAsk.volume, topAsk.price * (1 - spread))
      send[Order](order) // TODO make wallet aware ie use broker
      oid = oid + 1
    }
//    case q: Quote => {
//      log.debug("MarketMaker receided a quote: " + q)
//    }
    case ConfirmRegistration => {
      broker = sender()
      registered = true
      log.debug("MarketMaker: Broker confirmed")
    }
    case WalletFunds(uid, funds: Wallet.Type) => {
      log.debug("MarketMaker: money I have: ")
      for(i <- funds.keys) yield {log.debug(i + " -> " + funds.get(i))}
    }

    case WalletConfirm(tid) => {
      if (uid != tid)
        log.error("MarketMaker: Broker replied to the wrong trader")
      log.debug("MarketMaker: Got a wallet confirmation")
    }

    case _: ExecutedAskOrder => {
      log.debug("MarketMaker: Got an executed order")
    }
    case _: ExecutedBidOrder => {
      log.debug("MarketMaker: Got an executed order")
    }

    case 'sendTooBigOrder => {
      val order = MarketBidOrder(oid, uid, currentTimeMillis, CHF, USD, 1000.0, 100000.0)
      placeOrder(order)
      oid = oid + 1
    }
    case 'sendMarketOrder => {
      val order = MarketBidOrder(oid, uid, currentTimeMillis, CHF, USD, 3.0, 14.0)
      placeOrder(order)
      oid = oid + 1
    }
    case 'addFunds => {
      log.debug("MarketMaker: trying to add 100 bucks")
      send(FundWallet(uid, USD, 100))
    }
    case 'knowYourWallet => {
      send(GetWalletFunds(uid,this.self))
    }
    case p => {
      println("MarketMaker: received unknown: " + p)
    }
  }

  def placeOrder(order: MarketOrder) = {
    implicit val timeout = new Timeout(500 milliseconds)
    val future = (broker ? order).mapTo[Order]
    future onSuccess {
      case _: AcceptedOrder => log.debug("MarketMaker: order placement succeeded")
      case _: RejectedOrder => log.debug("MarketMaker: order failed")
      case _ => log.debug("MarketMaker: unknown order response")
    }
    future onFailure {
      case p => log.debug("Wallet command failed: " + p)
    }
  }

  override def init = {
    log.debug("MarketMaker received startSignal")
  }
}
