package ch.epfl.ts.evaluation

import akka.util.Timeout
import ch.epfl.ts.engine.{Wallet, TraderIdentity, GetTraderParameters}

import scala.concurrent.Future
import scala.language.postfixOps
import scala.collection.mutable.{Map => MMap, MutableList => MList}
import scala.concurrent.duration.{DurationInt, DurationLong}
import scala.concurrent.duration.FiniteDuration
import akka.actor.{ActorRef, Cancellable}
import akka.pattern.{ ask, pipe }
import ch.epfl.ts.component.{ComponentRegistration, Component, ComponentRef}
import ch.epfl.ts.data._

/**
 * Represents metrics of a strategy
 */
case class EvaluationReport(traderId: Long, traderName: String, wallet: Map[Currency, Double],
                            currency: Currency, initial: Double, current: Double, totalReturns: Double,
                            volatility: Double, drawdown: Double, sharpeRatio: Double)
                           extends Ordered[EvaluationReport] with Serializable {

  /** Compares two evaluation reports by total returns
    *
    * Returns x where:
    *   x < 0 when this < that
    *   x == 0 when this == that
    *   x > 0 when this > that
    *
    * Use the default comparable implementation to sort a list of reports:
    *   list.sorted[EvaluationReport]
    *
    * Use sortBy to sort a list of reports:
    *   list.sortBy(_.sharpeRatio)
    *
    * Use sortWith to sort a list of reports:
    *   list.sortWith(_.sharpeRatio > _.sharpeRatio)
    *
    */
  def compare(that: EvaluationReport) = {
    val delta = this.totalReturns - that.totalReturns
    if (delta > 0) 1 else if (delta < 0) -1 else 0
  }
}

/**
  * Evaluates the performance of traders by total returns, volatility, draw down and sharpe ratio
  *
  * To use this class, redirect all previous connections into and out of the trader to
  * instances of this class.
  *
  * Note that a component providing Quote data must be connected to this component in order for
  * it to maintain a price table and calculate profit/loss correctly.
  *
  * @param trader the reference to the trader component
  * @param traderId the id of the trader
  * @param traderName the name of the trader
  * @param currency the reference currency for reporting and calculation
  * @param period the time period to send evaluation report
  */
class Evaluator(trader: ActorRef, traderId: Long, traderName: String, currency: Currency, period: FiniteDuration) extends Component {
  // For usage of Scheduler
  import context._

  // Initial values
  private var initialValueReceived = false
  private var initialWallet: Map[Currency, Double] = Map.empty
  private val wallet = MMap[Currency, Double]()

  private var schedule: Cancellable = null
  private val returnsList = MList[Double]()
  private val priceTable = MMap[(Currency, Currency), Double]()

  private var lastValue: Option[Double] = None
  private var maxProfit = 0.0

  /** Max loss as a positive value
   */
  private var maxLoss = 0.0

  /**
   * Redirects out-going connections to the trader
   */
  override def connect(ar: ActorRef, ct: Class[_], name: String) = {
    if (ct.equals(classOf[EvaluationReport]))
      super.connect(ar, ct, name)
    else
      trader ! ComponentRegistration(ar, ct, name)
  }

  /**
   * Handles interested messages and forward all messages to the trader
   */
  override def receiver = {
    case t: Transaction if t.buyerId == traderId =>  // buy
      buy(t)
    case t: Transaction if t.sellerId == traderId =>  // sell
      sell(t)

    case t: Transaction => // Nothing to do
       // Let's not forward unrelated transactions to our poor Trader

    case q: Quote =>
      updatePrice(q)
      trader ! q
    case 'Report =>
      if (canReport) report
    case TraderIdentity(_, _, companion, parameters) =>
      initialWallet = parameters.get[Wallet.Type](companion.INITIAL_FUNDS)
      initialWallet.foreach { case (currency, amount) =>
        wallet += currency -> (wallet.getOrElse(currency, 0.0) + amount)
      }
      initialValueReceived = true
      if(canReport) {
        lastValue = Some(valueOfWallet(wallet.toMap))
      }
    case m => trader ! m
  }

  /**
   *  Returns the exchange ratio between two currency
   *
   *  1 unit of currency *from* can buy *ratio* units of currency *to*
   */
  private def ratio(from: Currency, to: Currency): Double = {
    if (from == to) 1.0 else priceTable(from -> to)
  }

  /**
   *  Returns the total money of the wallet converted to the given currency
   */
  private def valueOfWallet(wallet: Map[Currency, Double], in: Currency = currency): Double = {
    (wallet :\ 0.0) { case ((c, amount), acc) => acc + ratio(c, in) * amount }
  }

  /**
   *  Updates the wallet and statistics after a sell transaction
   */
  private def sell(t: Transaction): Unit = {
    wallet += t.whatC -> (wallet.getOrElse(t.whatC, 0.0) - t.volume)
    wallet += t.withC -> (wallet.getOrElse(t.withC, 0.0) + t.volume * t.price)
  }

  /**
   * Updates the wallet and statistics after a buy transaction
   */
  private def buy(t: Transaction): Unit = {
    wallet += t.whatC -> (wallet.getOrElse(t.whatC, 0.0) + t.volume)
    wallet += t.withC -> (wallet.getOrElse(t.withC, 0.0) - t.volume * t.price)
  }

  /**
   * Updates the price table
   */
  private def updatePrice(q: Quote) = {
    val Quote(_, _, whatC, withC, bid, ask) = q
    priceTable.put(whatC -> withC, bid)
    priceTable.put(withC -> whatC, 1/ask)

    if(lastValue.isEmpty) {
      lastValue = Some(valueOfWallet(wallet.toMap))
    }
  }

  /**
   * Computes volatility, which is the variance of returns
   */
  private def computeVolatility = {
    val mean = (returnsList :\ 0.0)(_ + _) / returnsList.length
    (returnsList :\ 0.0) { (r, acc) => (r - mean) * (r - mean) + acc } / returnsList.length
  }

  /**
   * Updates the statistics
   */
  private def report: Unit = {
    val initial = valueOfWallet(initialWallet)
    val curVal = valueOfWallet(wallet.toMap)

    val profit = curVal - initial
    if (profit > maxProfit) maxProfit = profit
    else if (profit < 0 && 0 - profit > maxLoss) maxLoss = 0 - profit

    returnsList += (curVal - lastValue.get) / lastValue.get

    lastValue = Some(curVal)

    // Generate report
    // TODO: Find appropriate value for risk free rate
    val riskFreeRate = 0.03
    val totalReturns = (curVal - initial) / initial
    val volatility = computeVolatility
    val drawdown = maxLoss / initial
    val sharpeRatio = (totalReturns - riskFreeRate) / volatility

    send(EvaluationReport(traderId, traderName, wallet.toMap, currency, initial, curVal, totalReturns, volatility, drawdown, sharpeRatio))
  }

  /**
   * Tells whether it's OK to report
   * @return True if each currency appearing in the wallet also appears in the price table
   *         and we have received the initial funds from the trader we are evaluating.
   * */
  private def canReport: Boolean = {
    if (priceTable.size == 0 || !initialValueReceived || lastValue.isEmpty) false
    else wallet.keys.forall(c => c == currency || priceTable.contains(c -> currency))
  }

  /**
   * Starts the scheduler and trader
   */
  override def start = {
    schedule = context.system.scheduler.schedule(2 seconds, period, self, 'Report)

    // Query trader for the initial wallet
    implicit val timeout = Timeout(2 seconds)
    (trader ? GetTraderParameters).mapTo[TraderIdentity] pipeTo self
 }

  /**
   * Stops the scheduler and trader
   */
  override def stop = {
    schedule.cancel()

    // Last report
    if (canReport) report
  }
}
