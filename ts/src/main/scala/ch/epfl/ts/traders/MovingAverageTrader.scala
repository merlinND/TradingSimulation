package ch.epfl.ts.traders

import ch.epfl.ts.component.Component
import ch.epfl.ts.indicators.MovingAverage
import ch.epfl.ts.data._
import akka.actor.ActorLogging
import ch.epfl.ts.engine.MarketFXSimulator
import akka.actor.ActorRef
import ch.epfl.ts.data.ConfirmRegistration
import ch.epfl.ts.data.Register
import ch.epfl.ts.engine.FundWallet
import ch.epfl.ts.engine.GetWalletFunds
import ch.epfl.ts.engine.AcceptedOrder
import ch.epfl.ts.engine.RejectedOrder
import ch.epfl.ts.engine.ExecutedAskOrder
import ch.epfl.ts.engine.ExecutedBidOrder
import scala.collection.mutable.{ HashMap => MHashMap }
import scala.slick.direct.order
import akka.util.Timeout
import ch.epfl.ts.data.Currency._
import scala.concurrent.duration._
import scala.language.postfixOps
import akka.pattern.ask
import ch.epfl.ts.engine.GetWalletFunds
import ch.epfl.ts.engine.WalletFunds
import scala.math.abs

/**
 * Simple momentum strategy.
 * @param symbol the pair of currency we are trading with
 * @param shortPeriod the size of the rolling window of the short moving average
 * @param longPeriod the size of the rolling window of the long moving average
 * @param tolerance is required to avoid fake buy signal
 * @param withShort version with/without short orders
 */

//TODO Kbs for MND's PR => Remove InitialFund , Initial Currency 
class MovingAverageTrader(val uid: Long, symbol: (Currency, Currency), val initialFund: Double, val initialCurrency: Currency,
                          val shortPeriod: Int, val longPeriod: Int, val tolerance: Double, val withShort: Boolean) extends Component with ActorLogging {

  import context.dispatcher
  /**
   * Broker information
   */
  var broker: ActorRef = null
  var registered = false

  /**
   * Moving average of the current period
   */
  var currentShort: Double = 0.0
  var currentLong: Double = 0.0

  var oid = 0

  // TODO: replace by being wallet-aware
  //  var holdings: Double = 0.0
  //  var shortings: Double = 0.0

  val (whatC, withC) = symbol
  var tradingPrices = MHashMap[(Currency, Currency), (Double, Double)]()
  //  val volume = 1000.0

  override def receiver = {

    case q: Quote => {
      tradingPrices((q.whatC, q.withC)) = (q.bid, q.ask)
    }

    case ConfirmRegistration => {
      broker = sender()
      registered = true
      log.debug("TraderWithB: Broker confirmed")
    }

    case ma: MovingAverage => {
      println("Trader receive MAs")
      ma.value.get(shortPeriod) match {
        case Some(x) => currentShort = x
        case None    => println("Error: Missing indicator with period " + shortPeriod)
      }
      ma.value.get(longPeriod) match {
        case Some(x) => currentLong = x
        case None    => println("Error: Missing indicator with period " + longPeriod)
      }

      decideOrder
    }

    //Order has been executed on the market = CLOSE Positions
    case _: ExecutedBidOrder => //TODO SimplePrint / Log /.../Frontend log ??
    case _: ExecutedAskOrder => //TODO SimplePrint/Log/.../Frontend log ??

    case whatever            => println("SimpleTrader: received unknown : " + whatever)
  }

  def decideOrder = {
    var volume = 0.0
    var holdings = 0.0
    var shortings = 0.0

    implicit val timeout = new Timeout(500 milliseconds)
    val future = (broker ? GetWalletFunds(uid)).mapTo[WalletFunds]
    future onSuccess {
      case WalletFunds(id, funds: Map[Currency, Double]) => {
        val cashWith = funds.getOrElse(withC, 0.0)
        log.debug("cashWith"+cashWith)
        holdings = funds.getOrElse(whatC, 0.0)
        if (holdings < 0.0) {
          shortings = abs(holdings)
          holdings = 0.0
        }
        val askPrice = tradingPrices(whatC, withC)._2
        volume = cashWith / askPrice
        if (withShort) {
          decideOrderWithShort(volume, holdings, shortings)
        } else {
          decideOrderWithoutShort(volume, holdings)
        }
      }
    }
    future onFailure {
      case p => {
        log.debug("MA Trader : Wallet command failed : " + p)
        stop
      }
    }

  }

  def decideOrderWithoutShort(volume: Double, holdings: Double) = {
    if (currentShort > currentLong * (1 + tolerance) && holdings == 0.0) {
      log.debug("buying " + volume)
      placeOrder(MarketBidOrder(oid, uid, System.currentTimeMillis(), whatC, withC, volume, -1))
      oid += 1
    } //SELL signal
    else if (currentShort < currentLong && holdings > 0.0) {
      log.debug("selling " + volume)
      placeOrder(MarketAskOrder(oid, uid, System.currentTimeMillis(), whatC, withC, holdings, -1))
      oid += 1
    }
  }

  def decideOrderWithShort(volume: Double, holdings: Double, shortings: Double) = {
    //BUY signal
    if (currentShort > currentLong) {
      if (shortings > 0.0) {
        log.debug("closing short " + volume)
        placeOrder(MarketBidOrder(oid, uid, System.currentTimeMillis(), whatC, withC, shortings, -1))
        oid += 1;
      }
      if (currentShort > currentLong * (1 + tolerance) && holdings == 0.0) {
        log.debug("buying " + volume)
        placeOrder(MarketBidOrder(oid, uid, System.currentTimeMillis(), whatC, withC, volume, -1))
        oid += 1
      }
    } //SELL signal
    else if (currentShort < currentLong) {
      if (holdings > 0.0) {
        log.debug("selling " + volume)
        placeOrder(MarketAskOrder(oid, uid, System.currentTimeMillis(), whatC, withC, holdings, -1))
        oid += 1
      }
      if (currentShort * (1 + tolerance) < currentLong && shortings == 0.0) {
        log.debug("short " + volume)
        placeOrder(MarketAskOrder(oid, uid, System.currentTimeMillis(), whatC, withC, volume, -1))
        oid += 1;
      }
    }
  }

  def placeOrder(order: MarketOrder) = {
    implicit val timeout = new Timeout(500 milliseconds)
    val future = (broker ? order).mapTo[Order]
    future onSuccess {
      //Transaction has been accepted by the broker (but may not be executed : e.g. limit orders) = OPEN Positions
      case _: AcceptedOrder => log.debug("MATrader: order placement succeeded")
      case _: RejectedOrder => {
        log.debug("MATrader: order failed")
        stop
      }
      case _ => {
        log.debug("MATrader: unknown order response")
        stop
      }
    }
    future onFailure {
      case p => {
        log.debug("Wallet command failed: " + p)
        stop
      }
    }
  }

  override def start = {
    log.debug("MovingAverageTrader received startSignal")
    send(Register(uid))
    send(FundWallet(uid, initialCurrency, initialFund))
    send(GetWalletFunds(uid))
  }

}