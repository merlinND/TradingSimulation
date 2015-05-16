package ch.epfl.ts.traders

import scala.collection.mutable.{ HashMap => MHashMap }
import scala.concurrent.duration.FiniteDuration
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.util.Timeout
import ch.epfl.ts.data.ConfirmRegistration
import ch.epfl.ts.data.Currency
import ch.epfl.ts.data.Currency._
import ch.epfl.ts.data.CurrencyPairParameter
import ch.epfl.ts.data.NaturalNumberParameter
import ch.epfl.ts.data.OHLC
import ch.epfl.ts.data.Quote
import ch.epfl.ts.indicators.RSI
import ch.epfl.ts.data.RealNumberParameter
import ch.epfl.ts.data.StrategyParameters
import ch.epfl.ts.data.TimeParameter
import ch.epfl.ts.engine.WalletFunds
import ch.epfl.ts.indicators.OhlcIndicator
import ch.epfl.ts.indicators.RsiIndicator
import ch.epfl.ts.engine.GetWalletFunds
import akka.pattern.ask
import scala.math.floor
import ch.epfl.ts.engine.AcceptedOrder
import ch.epfl.ts.data.MarketOrder
import ch.epfl.ts.engine.RejectedOrder
import ch.epfl.ts.data.Order
import ch.epfl.ts.data.MarketBidOrder
import ch.epfl.ts.data.MarketAskOrder
import ch.epfl.ts.engine.ExecutedAskOrder
import ch.epfl.ts.engine.ExecutedBidOrder
import ch.epfl.ts.data.BooleanParameter
import ch.epfl.ts.indicators.SmaIndicator
import ch.epfl.ts.indicators.MovingAverage


object RsiTrader extends TraderCompanion {
  type ConcreteTrader = RsiTrader
  override protected val concreteTraderTag = scala.reflect.classTag[RsiTrader]

  /** Currency pair to trade */
  val SYMBOL = "Symbol"
  /** OHLC period (duration) */
  val OHLC_PERIOD = "OhlcPeriod"
  /** Period for RSI in number of OHLC*/
  val RSI_PERIOD = "RsiPeriod"
  /** HIGH_RSI : Market is overbought , time to sell  */
  val HIGH_RSI = "highRsi"
  /**LOW_RSI : Market is oversell, time to buy"*/
  val LOW_RSI = "lowRsi"

  /**WITH_SMA_CONFIRMATION : need a confirmation by SMA indicator*/
  val WITH_SMA_CONFIRMATION = "withSmaConfirmation"
  /**LONG_SMA_PERIOD : should be > to the period of RSI*/
  val LONG_SMA_PERIOD = "longSMAPeriod"

  override def strategyRequiredParameters = Map(
    SYMBOL -> CurrencyPairParameter,
    OHLC_PERIOD -> TimeParameter,
    RSI_PERIOD -> NaturalNumberParameter,
    HIGH_RSI -> RealNumberParameter,
    LOW_RSI -> RealNumberParameter)

  override def optionalParameters = Map(
    WITH_SMA_CONFIRMATION -> BooleanParameter,
    LONG_SMA_PERIOD -> NaturalNumberParameter)
}

class RsiTrader(uid: Long, marketIds: List[Long], parameters: StrategyParameters) extends Trader(uid, marketIds, parameters) {
  import context.dispatcher
  override def companion = RsiTrader
  val marketId = marketIds(0)

  val symbol = parameters.get[(Currency, Currency)](RsiTrader.SYMBOL)
  val (whatC, withC) = symbol
  val ohlcPeriod = parameters.get[FiniteDuration](RsiTrader.OHLC_PERIOD)
  val rsiPeriod = parameters.get[Int](RsiTrader.RSI_PERIOD)
  val highRsi = parameters.get[Double](RsiTrader.HIGH_RSI)
  val lowRsi = parameters.get[Double](RsiTrader.HIGH_RSI)

  val withSmaConfirmation = parameters.get[Boolean](RsiTrader.WITH_SMA_CONFIRMATION)
  val longSmaPeriod = parameters.get[Int](RsiTrader.LONG_SMA_PERIOD)
  val shortSmaPeriod = rsiPeriod

  /**Indicators needed for RSI strategy*/
  val ohlcIndicator = context.actorOf(Props(classOf[OhlcIndicator], marketId, symbol, ohlcPeriod))
  val rsiIndicator = context.actorOf(Props(classOf[RsiIndicator], rsiPeriod))

  //TODO do not create if not used
  val smaIndicator = context.actorOf(Props(classOf[SmaIndicator], List(shortSmaPeriod, longSmaPeriod)))
  var currentShort = 0.0
  var currentLong = 0.0

  /**
   * Broker information
   */
  var broker: ActorRef = null
  var registered = false

  /**
   * To store prices
   */
  var tradingPrices = MHashMap[(Currency, Currency), (Double, Double)]()

  var oid = 0

  override def receiver = {

    case q: Quote => {
      currentTimeMillis = q.timestamp
      tradingPrices((q.whatC, q.withC)) = (q.bid, q.ask)
      ohlcIndicator ! q
    }

    case ohlc: OHLC => {
      rsiIndicator ! ohlc
      if (withSmaConfirmation) {
        smaIndicator ! ohlc
      }
    }

    case ConfirmRegistration => {
      broker = sender()
      registered = true
      log.debug("RsiIndicator: Broker confirmed")
    }

    case rsi: RSI if registered => {
      decideOrder(rsi.value)
    }

    case ma: MovingAverage if registered => {
      ma.value.get(shortSmaPeriod) match {
        case Some(x) => currentShort = x
        case None    => println("Error: Missing indicator with period " + shortSmaPeriod)
      }
      ma.value.get(longSmaPeriod) match {
        case Some(x) => currentLong = x
        case None    => println("Error: Missing indicator with period " + longSmaPeriod)
      }
    }

    case eb: ExecutedBidOrder    => log.debug("executed bid volume: " + eb.volume)
    case ea: ExecutedAskOrder    => log.debug("executed ask volume: " + ea.volume)

    case whatever if !registered => println("RsiTrader: received while not registered [check that you have a Broker]: " + whatever)
    case whatever                => println("RsiTrader: received unknown : " + whatever)
  }
  def decideOrder(rsi: Double) = {
    implicit val timeout = new Timeout(askTimeout)
    val future = (broker ? GetWalletFunds(uid, this.self)).mapTo[WalletFunds]
    future onSuccess {
      case WalletFunds(id, funds: Map[Currency, Double]) => {
        var holdings = 0.0
        val cashWith = funds.getOrElse(withC, 0.0)
        holdings = funds.getOrElse(whatC, 0.0)
        if (!withSmaConfirmation) {
          //overbought : time to sell
          if (rsi >= highRsi && holdings > 0.0) {
            placeOrder(MarketAskOrder(oid, uid, currentTimeMillis, whatC, withC, holdings, -1))
            //oversell : time to buy
          } else if (rsi <= lowRsi && holdings == 0) {
            val askPrice = tradingPrices(whatC, withC)._2
            val volumeToBuy = floor(cashWith / askPrice)
            placeOrder(MarketBidOrder(oid, uid, currentTimeMillis, whatC, withC, volumeToBuy, -1))
          }
        } //withSmaConfirmation
        else {
          //overbought : time to sell
          if (rsi >= highRsi && holdings > 0.0 && currentShort <= currentLong) {
            placeOrder(MarketAskOrder(oid, uid, currentTimeMillis, whatC, withC, holdings, -1))
            //oversell : time to buy
          } else if (rsi <= lowRsi && holdings == 0 && currentShort >= currentLong) {
            val askPrice = tradingPrices(whatC, withC)._2
            val volumeToBuy = floor(cashWith / askPrice)
            placeOrder(MarketBidOrder(oid, uid, currentTimeMillis, whatC, withC, volumeToBuy, -1))
          }
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

  def placeOrder(order: MarketOrder) = {
    oid += 1
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
}
