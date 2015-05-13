package ch.epfl.ts.traders
import ch.epfl.ts.component.Component
import ch.epfl.ts.indicators.MovingAverage
import ch.epfl.ts.data.Currency._
import ch.epfl.ts.data.{ MarketAskOrder, MarketBidOrder, Quote }
import ch.epfl.ts.data.Currency
import akka.actor.ActorLogging
import ch.epfl.ts.engine.MarketFXSimulator
import akka.actor.ActorRef
import ch.epfl.ts.data.ConfirmRegistration
import ch.epfl.ts.data.Register
import scala.concurrent.duration.FiniteDuration
import ch.epfl.ts.data.CurrencyPairParameter
import ch.epfl.ts.data.NaturalNumberParameter
import ch.epfl.ts.data.StrategyParameters
import ch.epfl.ts.data.TimeParameter
import ch.epfl.ts.data.RealNumberParameter
import ch.epfl.ts.data.BooleanParameter
import scala.slick.direct.order
import akka.pattern.ask
import ch.epfl.ts.data._
import akka.util.Timeout
import scala.collection.mutable.{ HashMap => MHashMap }
import scala.math.abs
import scala.math.floor
import ch.epfl.ts.engine.Wallet
import akka.actor.Props
import ch.epfl.ts.indicators.OhlcIndicator
import ch.epfl.ts.indicators.SmaIndicator
import ch.epfl.ts.indicators.EmaIndicator
import ch.epfl.ts.engine.RejectedOrder
import ch.epfl.ts.engine.ExecutedBidOrder
import ch.epfl.ts.engine.WalletFunds
import ch.epfl.ts.engine.AcceptedOrder
import ch.epfl.ts.engine.ExecutedAskOrder
import ch.epfl.ts.engine.GetWalletFunds
import ch.epfl.ts.engine.RejectedOrder
import ch.epfl.ts.engine.ExecutedBidOrder
import ch.epfl.ts.engine.WalletFunds
import ch.epfl.ts.engine.AcceptedOrder
import ch.epfl.ts.engine.ExecutedAskOrder
import ch.epfl.ts.engine.GetWalletFunds
import ch.epfl.ts.engine.MarketBidsEmpty
import ch.epfl.ts.engine.MarketAsksEmpty
import ch.epfl.ts.engine.MarketEmpty
import ch.epfl.ts.engine.MarketMakerNotification

/**
 * MMwithWallet companion object
 */
object MMwithWallet extends TraderCompanion {
  type ConcreteTrader = MMwithWallet
  override protected val concreteTraderTag = scala.reflect.classTag[MMwithWallet]

  /** Currency pair to trade */
  val SYMBOL = "Symbol"
  /** Market maker specific spread */
  val SPREAD = "spread"

  override def strategyRequiredParameters = Map(
    SYMBOL -> CurrencyPairParameter)

  override def optionalParameters = Map(
    SPREAD -> CoefficientParameter) //TODO make usable with values > 1
}

/**
 * "Simply add your spread" strategy.
 */
class MMwithWallet(uid: Long, marketIds: List[Long], parameters: StrategyParameters)
  extends Trader(uid, marketIds, parameters) with ActorLogging {

  import context.dispatcher

  override def companion = MMwithWallet

  val symbol = parameters.get[(Currency, Currency)](MMwithWallet.SYMBOL)
  val (whatC, withC) = symbol
  val spread = parameters.getOrElse[Double](MMwithWallet.SPREAD, 0.2)
  /**
   * Indicators needed by the Moving Average Trader
   */
  val marketId = marketIds(0)

  /**
   * Broker information
   */
  var broker: ActorRef = null
  var registered = false

  var oid = 0

  // TODO: replace by being wallet-aware
  var holdings: Double = 0.0
  var shortings: Double = 0.0

  var tradingPrices = MHashMap[(Currency, Currency), (Double, Double)]()
  
  val NEED_TO_BUY = 0
  val NEED_TO_SELL = 1

  override def receiver = {

    /**
     * When receive a quote update bid and ask
     * and forward to ohlcIndicator
     */
    case q: Quote => {
      currentTimeMillis = q.timestamp
      tradingPrices((q.whatC, q.withC)) = (q.bid, q.ask)
    }
    
    case t: TheTimeIs => {
      currentTimeMillis = t.now
    }

    case ConfirmRegistration => {
      broker = sender()
      registered = true
      log.debug("MMwithWallet: Broker confirmed")
    }

    case msg: MarketEmpty => if (registered){
      log.debug("OUCH nothing is going on")
    }
    case topBid: MarketAsksEmpty => if (registered) {
//      currentTimeMillis = topBid.timestamp
      log.debug("MarketMaker is asked to sell " + topBid.volume + " " + topBid.whatC.toString() + " for a price of " + topBid.price + " " + topBid.withC.toString() )
//      val order = LimitAskOrder(oid, uid, topBid.timestamp, topBid.whatC, topBid.withC, topBid.volume, topBid.price * (1 + spread))
      decideOrder(NEED_TO_SELL, topBid.whatC, topBid.withC, topBid.volume, topBid.price)
    }
    case topAsk: MarketBidsEmpty => if (registered) {
//      currentTimeMillis = topAsk.timestamp
      log.debug("MarketMaker is asked to buy " + topAsk.volume + " " + topAsk.whatC.toString() + " for a price of " + topAsk.price + " " + topAsk.withC.toString() )
//      val order = LimitBidOrder(oid, uid, topAsk.timestamp, topAsk.whatC, topAsk.withC, topAsk.volume, topAsk.price * (1 - spread))
      decideOrder(NEED_TO_BUY, topAsk.whatC, topAsk.withC, topAsk.volume, topAsk.price)
    }

    // Order has been executed on the market = CLOSE Positions
    case eb: ExecutedBidOrder    => if (eb.uid == uid) log.debug("executed bid volume: " + eb.volume) else println("eb WRONG uid")
    case ea: ExecutedAskOrder    => if (ea.uid == uid) log.debug("executed ask volume: " + ea.volume) else println("ea WRONG uid")

    case whatever if !registered => println("MMwithWallet: received while not registered [check that you have a Broker]: " + whatever)
    case whatever                => println("MMwithWallet: received unknown : " + whatever)
  }
  def decideOrder(action: Int, whatC: Currency, withC: Currency, volume: Double, price: Double) = {
    implicit val timeout = new Timeout(askTimeout)

    val future = (broker ? GetWalletFunds(uid, this.self)).mapTo[WalletFunds]
    future onSuccess {
      case WalletFunds(id, funds: Map[Currency, Double]) => {
        var holdings = 0.0
        // Arnaud: cashWith is the amount of the WithC currency that we have and holdings are the amount of whatC that we have
        val cashWith = funds.getOrElse(withC, 0.0)
        holdings = funds.getOrElse(whatC, 0.0)
        log.debug("cash " + cashWith + " holdings" + holdings)
        
//        val (bidPrice, askPrice) = tradingPrices(whatC, withC)
        
        if(action == NEED_TO_BUY) {
          if (cashWith >= (volume * price)) {
            placeOrder(LimitBidOrder(oid, uid, currentTimeMillis, whatC, withC, volume, price * (1 - spread)))
          } else {
            println("MMwithWallet: NOT ENOUGH CASH TO BUY!!!")
          }
        } else if (action == NEED_TO_SELL) {
          if (holdings >= volume) {
            placeOrder(LimitAskOrder(oid, uid, currentTimeMillis, whatC, withC, volume, price * (1 + spread)))
          } else {
            println("MMwithWallet: NOT ENOUGH HOLDINGS TO SELL!!!")
          }
        }
      }
    }
    future onFailure {
      case p => {
        log.debug("MMwithWallet: Getting funds failed : " + p)
        stop
      }
    }

  }

  def placeOrder(order: LimitOrder) = {
    log.debug("An order is placed")
    implicit val timeout = new Timeout(askTimeout)
    oid += 1
    val future = (broker ? order).mapTo[Order]
    future onSuccess {
      // Transaction has been accepted by the broker (but may not be executed : e.g. limit orders) = OPEN Positions
      case ao: AcceptedOrder => log.debug("Accepted order costCurrency: " + order.costCurrency() + " volume: " + ao.volume)
      case _: RejectedOrder => {
        log.debug("MMwithWallet: order failed")
      }
      case _ => {
        log.debug("MMwithWallet: unknown order response")
      }
    }
    future onFailure {
      case p => {
        log.debug("Placing the order failed: " + p)
      }
    }
  }

  override def init = {
    log.debug("MMwithWallet received startSignal")
  }

}