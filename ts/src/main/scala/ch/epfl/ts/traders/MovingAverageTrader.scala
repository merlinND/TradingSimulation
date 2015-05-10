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
import ch.epfl.ts.engine.FundWallet
import ch.epfl.ts.engine.GetWalletFunds
import ch.epfl.ts.engine.AcceptedOrder
import ch.epfl.ts.engine.RejectedOrder
import ch.epfl.ts.engine.ExecutedAskOrder
import ch.epfl.ts.engine.ExecutedBidOrder
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
import ch.epfl.ts.engine.WalletFunds
import scala.math.abs
import scala.math.floor
import ch.epfl.ts.engine.Wallet
import ch.epfl.ts.engine.GetWalletFunds
import akka.actor.Props
import ch.epfl.ts.indicators.OhlcIndicator
import ch.epfl.ts.indicators.SmaIndicator
import ch.epfl.ts.indicators.EmaIndicator

/**
 * MovingAverageTrader companion object
 */
object MovingAverageTrader extends TraderCompanion {
  type ConcreteTrader = MovingAverageTrader
  override protected val concreteTraderTag = scala.reflect.classTag[MovingAverageTrader]

  /** Currency pair to trade */
  val SYMBOL = "Symbol"
  /** OHLC period (duration) */
  val OHLC_PERIOD = "OhlcPeriod"
  /** Number of OHLC periods to use for the shorter moving average **/
  val SHORT_PERIODS = "ShortPeriods"
  /** Number of OHLC periods to use for the longer moving average **/
  val LONG_PERIODS = "LongPeriods"
  /** Tolerance: a kind of sensitivity threshold to avoid "fake" buy signals */
  val TOLERANCE = "Tolerance"
  /** Allow the use of Short orders in the strategy */
  val WITH_SHORT = "WithShort"

  override def strategyRequiredParameters = Map(
    SYMBOL -> CurrencyPairParameter,
    OHLC_PERIOD -> TimeParameter,
    SHORT_PERIODS -> NaturalNumberParameter,
    LONG_PERIODS -> NaturalNumberParameter,
    TOLERANCE -> RealNumberParameter)

  override def optionalParameters = Map(
    WITH_SHORT -> BooleanParameter)
}

/**
 * Simple momentum strategy.
 */
class MovingAverageTrader(uid: Long, marketIds : List[Long], parameters: StrategyParameters)
  extends Trader(uid, marketIds, parameters) with ActorLogging {

  import context.dispatcher

  override def companion = MovingAverageTrader

  val symbol = parameters.get[(Currency, Currency)](MovingAverageTrader.SYMBOL)
  val (whatC, withC) = symbol
  
  val ohlcPeriod = parameters.get[FiniteDuration](MovingAverageTrader.OHLC_PERIOD)
  val shortPeriods: Long = parameters.get[Int](MovingAverageTrader.SHORT_PERIODS).toLong
  val longPeriods: Long = parameters.get[Int](MovingAverageTrader.LONG_PERIODS).toLong
  val tolerance = parameters.get[Double](MovingAverageTrader.TOLERANCE)
  val withShort = parameters.getOrElse[Boolean](MovingAverageTrader.WITH_SHORT, false)
  
  /**
   * Indicators needed by the Moving Average Trader 
   */
  val marketId = marketIds(0)
  val ohlcIndicator = context.actorOf(Props(classOf[OhlcIndicator], marketId, symbol, ohlcPeriod))
  val movingAverageIndicator = context.actorOf(Props(classOf[EmaIndicator], List(shortPeriods, longPeriods)))

  
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
  var holdings: Double = 0.0
  var shortings: Double = 0.0

  var tradingPrices = MHashMap[(Currency, Currency), (Double, Double)]()
  
  /**
   * TODO : actually trader update price based on quotes and MA is computed based on ohlc...
   */
  override def receiver = {

    /**
     * When receive a quote update bid and ask
     * and forward to ohlcIndicator
     */
    case q: Quote => {
      tradingPrices((q.whatC, q.withC)) = (q.bid, q.ask)
      ohlcIndicator ! q
    }
    
    case ohlc : OHLC => {
      movingAverageIndicator ! ohlc
    }

    case ConfirmRegistration => {
      broker = sender()
      registered = true
      log.debug("MATrader: Broker confirmed")
    }

    case ma: MovingAverage if registered => {
      println("Trader receive MAs")
      ma.value.get(shortPeriods) match {
        case Some(x) => currentShort = x
        case None    => println("Error: Missing indicator with period " + shortPeriods)
      }
      ma.value.get(longPeriods) match {
        case Some(x) => currentLong = x
        case None    => println("Error: Missing indicator with period " + longPeriods)
      }
        decideOrder
    }

    // Order has been executed on the market = CLOSE Positions
    case _: ExecutedBidOrder => // TODO SimplePrint / Log /.../Frontend log ??
    case _: ExecutedAskOrder => // TODO SimplePrint/Log/.../Frontend log ??

    case whatever            => println("SimpleTrader: received unknown : " + whatever)
  }
  def decideOrder = {
    var volume = 0.0
    var holdings = 0.0
    var shortings = 0.0

    implicit val timeout = new Timeout(askTimeout)
    val future = (broker ? GetWalletFunds(uid, this.self)).mapTo[WalletFunds]
    future onSuccess {
      case WalletFunds(id, funds: Map[Currency, Double]) => {
        val cashWith = funds.getOrElse(withC, 0.0)
        holdings = funds.getOrElse(whatC, 0.0)
        if (holdings < 0.0) {
          shortings = abs(holdings)
          holdings = 0.0
        }
        val askPrice = tradingPrices(whatC, withC)._2
        //Prevent slippage leading to insufisent funds
        volume = floor(cashWith / askPrice)
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
    // BUY signal
    if (currentShort > currentLong * (1 + tolerance) && holdings == 0.0) {
      placeOrder(MarketBidOrder(oid, uid, System.currentTimeMillis(), whatC, withC, volume, -1))
      oid += 1
    } // SELL signal
    else if (currentShort < currentLong && holdings > 0.0) {
      placeOrder(MarketAskOrder(oid, uid, System.currentTimeMillis(), whatC, withC, holdings, -1))
      oid += 1
    }
  }

  def decideOrderWithShort(volume: Double, holdings: Double, shortings: Double) = {
    // BUY signal
    if (currentShort > currentLong) {
      if (shortings > 0.0) {
        placeOrder(MarketBidOrder(oid, uid, System.currentTimeMillis(), whatC, withC, shortings, -1))
        oid += 1;
      }
      if (currentShort > currentLong * (1 + tolerance) && holdings == 0.0) {
        placeOrder(MarketBidOrder(oid, uid, System.currentTimeMillis(), whatC, withC, volume, -1))
        oid += 1
      }
    }
    // SELL signal
    else if (currentShort < currentLong) {
      if (holdings > 0.0) {
        placeOrder(MarketAskOrder(oid, uid, System.currentTimeMillis(), whatC, withC, holdings, -1))
        oid += 1
      }
      if (currentShort * (1 + tolerance) < currentLong && shortings == 0.0) {
        placeOrder(MarketAskOrder(oid, uid, System.currentTimeMillis(), whatC, withC, volume, -1))
        oid += 1;
      }
    }
  }

  def placeOrder(order: MarketOrder) = {
    implicit val timeout = new Timeout(askTimeout)
    val future = (broker ? order).mapTo[Order]
    future onSuccess {
      // Transaction has been accepted by the broker (but may not be executed : e.g. limit orders) = OPEN Positions
      case ao: AcceptedOrder => log.debug("Accepted order costCurrency: " + order.costCurrency() + " volume: " + ao.volume)
      case _: RejectedOrder => {
        log.debug("MATrader: order failed")
      }
      case _ => {
        log.debug("MATrader: unknown order response")
      }
    }
    future onFailure {
      case p => {
        log.debug("Wallet command failed: " + p)
      }
    }
  }

  override def init = {
    log.debug("MovingAverageTrader received startSignal")
  }

}
