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
import ch.epfl.ts.data.RealNumberParameter
import ch.epfl.ts.data.StrategyParameters
import ch.epfl.ts.data.TimeParameter
import ch.epfl.ts.engine.WalletFunds
import ch.epfl.ts.indicators.OhlcIndicator
import ch.epfl.ts.indicators.RsiIndicator
import ch.epfl.ts.indicators.RsiIndicator
import ch.epfl.ts.engine.GetWalletFunds
import akka.pattern.ask

object RsiTrader extends TraderCompanion {
  type ConcreteTrader = MovingAverageTrader
  override protected val concreteTraderTag = scala.reflect.classTag[MovingAverageTrader]

  /** Currency pair to trade */
  val SYMBOL = "Symbol"
  /** OHLC period (duration) */
  val OHLC_PERIOD = "OhlcPeriod"
  /** Period for RSI*/
  val RSI_PERIOD = "RsiPeriod"
  /** HIGH_RSI : Market is overbought , time to sell  */
  val HIGH_RSI = "highRsi"
  /**LOW_RSI : Market is oversell, time to buy"*/
  val LOW_RSI = "lowRsi"

  override def strategyRequiredParameters = Map(
    SYMBOL -> CurrencyPairParameter,
    OHLC_PERIOD -> TimeParameter,
    RSI_PERIOD -> NaturalNumberParameter,
    HIGH_RSI -> RealNumberParameter,
    LOW_RSI -> RealNumberParameter)
}
class RsiTrader(uid: Long, marketIds: List[Long], parameters: StrategyParameters) extends Trader(uid, marketIds, parameters) with ActorLogging {
  import context.dispatcher
  override def companion = RsiTrader
  val marketId = marketIds(0)

  val symbol = parameters.get[(Currency, Currency)](RsiTrader.SYMBOL)
  val (whatC, withC) = symbol
  val ohlcPeriod = parameters.get[FiniteDuration](RsiTrader.OHLC_PERIOD)
  val rsiPeriod = parameters.get[Long](RsiTrader.RSI_PERIOD)
  val highRsi = parameters.get[Double](RsiTrader.HIGH_RSI)
  val lowRsi = parameters.get[Double](RsiTrader.HIGH_RSI)

  /**Indicators needed for RSI strategy*/
  val ohlcIndicator = context.actorOf(Props(classOf[OhlcIndicator], marketId, symbol, ohlcPeriod))
  val rsiIndicator = context.actorOf(Props(classOf[RsiIndicator], rsiPeriod))

  /**
   * Broker information
   */
  var broker: ActorRef = null
  var registered = false

  /**
   * To store prices
   */
  var tradingPrices = MHashMap[(Currency, Currency), (Double, Double)]()

  override def receiver = {

    case q: Quote => {
      currentTimeMillis = q.timestamp
      tradingPrices((q.whatC, q.withC)) = (q.bid, q.ask)
      ohlcIndicator ! q
    }

    case ohlc: OHLC => {
      rsiIndicator ! ohlc
    }

    case ConfirmRegistration => {
      broker = sender()
      registered = true
      log.debug("RsiIndicator: Broker confirmed")
    }

    case rsi: RsiIndicator if registered => {
      decideOrder
    }
    case whatever if !registered => println("RsiTrader: received while not registered [check that you have a Broker]: " + whatever)
    case whatever                => println("RsiTrader: received unknown : " + whatever)
  }
  def decideOrder = {
    implicit val timeout = new Timeout(askTimeout)
    val future = (broker ? GetWalletFunds(uid, this.self)).mapTo[WalletFunds]
    future onSuccess {
      case WalletFunds(id, funds: Map[Currency, Double]) => {
        var holdings = 0.0
        val cashWith = funds.getOrElse(withC, 0.0)
        holdings = funds.getOrElse(whatC, 0.0)
       

      }
    }
    future onFailure {
      case p => {
        log.debug("MA Trader : Wallet command failed : " + p)
        stop
      }
    }
  }

}