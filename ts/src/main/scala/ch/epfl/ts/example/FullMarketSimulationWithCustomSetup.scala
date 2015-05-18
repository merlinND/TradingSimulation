package ch.epfl.ts.example

import ch.epfl.ts.component.{ ComponentRef, StopSignal, ComponentBuilder }
import com.typesafe.config.ConfigFactory
import akka.actor.Props
import ch.epfl.ts.brokers.StandardBroker
import scala.language.postfixOps
import scala.concurrent.duration._
import ch.epfl.ts.data._
import ch.epfl.ts.traders.MadTrader
import ch.epfl.ts.engine._
import ch.epfl.ts.component.fetch.{ HistDataCSVFetcher, PullFetchComponent, TrueFxFetcher, MarketNames }
import scala.reflect.ClassTag
import ch.epfl.ts.data.Currency._
import ch.epfl.ts.data.MarketBidOrder
import ch.epfl.ts.data.Register
import ch.epfl.ts.engine.FundWallet
import ch.epfl.ts.data.Quote
import ch.epfl.ts.data.Currency
import ch.epfl.ts.data.MarketAskOrder
import ch.epfl.ts.engine.MarketAsksEmpty
import ch.epfl.ts.engine.MarketBidsEmpty
import ch.epfl.ts.engine.MarketEmpty
import ch.epfl.ts.engine.MarketMakerNotification
import java.util.Timer
import ch.epfl.ts.engine.rules.{ SimulationMarketRulesWrapper, FxMarketRulesWrapper }
import ch.epfl.ts.traders.MarketMakerTrader
import ch.epfl.ts.component.utils.Printer
import ch.epfl.ts.traders.MMwithWallet
import ch.epfl.ts.traders.TraderCompanion
import ch.epfl.ts.config._
import ch.epfl.ts.traders.TraderCompanion
import ch.epfl.ts.traders.MovingAverageTrader
import ch.epfl.ts.traders.RangeTrader
import ch.epfl.ts.traders.RsiTrader
import ch.epfl.ts.traders.TraderCompanion

/**
 * Market simulation with first reading historical data and then running simulation on its own.
 */
object FullMarketSimulationWithCustomSetup {
  var producers = Map[Class[_], List[ComponentRef]]()
  var consuments = Map[Class[_], List[ComponentRef]]()

  /** market configuration */
  val funds = new FundsGermany
  val symbol = (Currency.EUR, Currency.CHF)
  /**
   * ._1: fund to symbol._1, ._2: fund to  symbol._2
   * Must be hardcoded since the distribution might be given in a different currency and
   * hence this is only a scaling factor.
   */
  val exchangeRateFunding = (1.0, 1.0)

  /** Trader type -> number of instances */
  var traderDistribution = Map(
    MadTrader -> 1,
    MovingAverageTrader -> 1,
    RsiTrader -> 1,
    RangeTrader -> 1)
  var traderList = List[ComponentRef]() // list of trader instances, to be filled during setup

  var market: ComponentRef = null
  var fetcher: ComponentRef = null
  var marketMaker: ComponentRef = null // market maker (who is technically a trader
  var marketMakerFunds = (0.0, 0.0)
  val marketIds = List(MarketNames.FOREX_ID)

  def setup = {
    implicit val builder = new ComponentBuilder
    initProducersAndConsuments()

    // market data
    val useLiveData = false

    // one broker for all
    val broker = builder.createRef(Props(classOf[StandardBroker]), "Broker")
    addConsument(classOf[Quote], broker)

    // traders
    var uid = 1L
    traderList = instantiateTraders(builder)

    // connect traders and broker
    traderList.foreach(trader => {
      addConsument(classOf[Quote], trader)
//      addConsument(classOf[TheTimeIs], trader)
      trader -> (broker, classOf[Register])
      trader -> (broker, classOf[FundWallet])
      connectAllOrders(trader, broker)
    })

    // market maker
    val uid_marketMaker = 0L
    marketMaker = MMwithWallet.getInstance(uid_marketMaker, marketIds, getStrategyParameters(MMwithWallet, marketMakerFunds), "WalletMarketMakerTrader")
    addConsument(classOf[Quote], marketMaker)
    addConsument(classOf[TheTimeIs], marketMaker)

    marketMaker -> (broker, classOf[Register])
    marketMaker -> (broker, classOf[FundWallet])
    connectAllOrders(marketMaker, broker)

    // Fetcher
    fetcher = createFetcher(useLiveData, builder, symbol)

    // Hybrid market
    val fetcherRules = new FxMarketRulesWrapper
    val simulationRules = new SimulationMarketRulesWrapper
    market = builder.createRef(Props(classOf[HybridMarketSimulator], marketIds(0), fetcherRules, simulationRules), MarketNames.FOREX_NAME)

    market -> (marketMaker, classOf[MarketBidsEmpty], classOf[MarketAsksEmpty], classOf[MarketEmpty], classOf[ExecutedBidOrder], classOf[ExecutedBidOrder])
    market -> (broker, classOf[ExecutedBidOrder], classOf[ExecutedAskOrder])
    fetcher -> (market, classOf[Quote])

    addProducer(classOf[Quote], market)
    addProducer(classOf[TheTimeIs], market)
    connectAllOrders(broker, market)

    connectProducersWithConsuments()

    builder // return the builder (this is not beautiful but solved the problem of type ambiguity inside the function
  }

  /**
   * @return References to the traders that were instantiated
   */
  def instantiateTraders(implicit builder: ComponentBuilder): List[ComponentRef] = {
    // TODO Jakob

    // TODO: is this needed?
    // val numberOfTraders = traderDistribution.values.sum

    var uid = 1L
    var bothFunds1 = (0.0, 0.0)
    var bothFunds2 = (0.0, 0.0)
    // Instantiate traders of each type
    traderDistribution.flatMap({
      case (companion, n) => { // TODO Jakob differentiate strategies
        val traders1 = List.range(uid, uid + n).map(i => {
          bothFunds1 = (funds.get * exchangeRateFunding._1, 0.0)
          marketMakerFunds = (marketMakerFunds._1 + bothFunds1._1, marketMakerFunds._2 + bothFunds1._2)
          companion.getInstance(i, marketIds, getStrategyParameters(companion, bothFunds1), "trader-MadTrader-" + i)
        })
        uid += n
        val traders2 = List.range(uid, uid + n).map(i => {
          bothFunds2 = (0.0, funds.get * exchangeRateFunding._2)
          marketMakerFunds = (marketMakerFunds._1 + bothFunds2._1, marketMakerFunds._2 + bothFunds2._2)
          companion.getInstance(i, marketIds, getStrategyParameters(companion, bothFunds2), "trader-MadTrader-" + i)
        })
        uid += n
        traders1 ++ traders2
      }
    }).toList

  }

  def main(args: Array[String]): Unit = {
    implicit val builder = setup
    builder.start
    val delay = 1 * 1000 //time in ms when to switch to full simulation mode
    scheduleChange(fetcher, market, delay)
  }

  def scheduleChange(quoteFetcher: ComponentRef, market: ComponentRef, delay: Long) = {
    val timer = new Timer()
    class StopFetcher extends java.util.TimerTask {
      def run() {
        quoteFetcher.ar ! StopSignal
        market.ar ! 'ChangeMarketRules
      }
    }
    timer.schedule(new StopFetcher, delay)
  }

  def initProducersAndConsuments() = {
    val messageClasses = List(classOf[Quote], classOf[Transaction])
    for (msg <- messageClasses) {
      producers = producers + (msg -> List())
      consuments = consuments + (msg -> List())
    }
  }

  def addProducer(msg: Class[_], component: ComponentRef) =
    producers = addEntity(msg, component, producers)

  def addConsument(msg: Class[_], component: ComponentRef) =
    consuments = addEntity(msg, component, consuments)

  def addEntity(msg: Class[_], component: ComponentRef, mapping: Map[Class[_], List[ComponentRef]]) =
    mapping + (msg -> (component :: mapping.getOrElse(msg, List())))

  def connectProducersWithConsuments() = {
    for (msgClass <- producers.keys) {
      for (myProducers <- producers.get(msgClass); myConsuments <- consuments.get(msgClass)) {
        for (producer <- myProducers; consument <- myConsuments) {
          if (producer != consument) {
            producer -> (consument, msgClass)
          }
        }
      }
    }
  }

  def connectAllOrders(source: ComponentRef, destination: ComponentRef) =
    source -> (destination, classOf[LimitBidOrder], classOf[LimitAskOrder], classOf[MarketBidOrder], classOf[MarketAskOrder])

  def createFetcher(useLiveData: Boolean, builder: ComponentBuilder, symbol: (Currency, Currency)) = {
    if (useLiveData) {
      val fetcherFx: TrueFxFetcher = new TrueFxFetcher
      builder.createRef(Props(classOf[PullFetchComponent[Quote]], fetcherFx, implicitly[ClassTag[Quote]]), "TrueFxFetcher")
    } else {
      val speed = 100.0
      val dateFormat = new java.text.SimpleDateFormat("yyyyMM")
      val startDate = dateFormat.parse("201304")
      val endDate = dateFormat.parse("201305")
      val workingDir = "./data"
      val currencyPair = symbol._1.toString() + symbol._2.toString();

      builder.createRef(Props(classOf[HistDataCSVFetcher], workingDir, currencyPair, startDate, endDate, speed), "HistDataFetcher")
    }
  }
  /**
   * @return strategy parameters for a particular strategy. All values are hardcoded except the initial funds
   */
  def getStrategyParameters(ttype: TraderCompanion, funds: (Double, Double)) = {
    ttype match {
      case MadTrader => new StrategyParameters(
        MadTrader.INITIAL_FUNDS -> WalletParameter(Map(symbol._1 -> funds._1, symbol._2 -> funds._2)),
        MadTrader.INTERVAL -> new TimeParameter(1 seconds),
        MadTrader.ORDER_VOLUME -> NaturalNumberParameter(10),
        MadTrader.CURRENCY_PAIR -> new CurrencyPairParameter(symbol._1, symbol._2))

      // taken from MovingAverageTraderExample.scala
      case MovingAverageTrader => new StrategyParameters(
        MovingAverageTrader.INITIAL_FUNDS -> WalletParameter(Map(symbol._1 -> funds._1, symbol._2 -> funds._2)),
        MovingAverageTrader.SYMBOL -> CurrencyPairParameter(symbol),

        MovingAverageTrader.OHLC_PERIOD -> new TimeParameter(1 day),
        MovingAverageTrader.SHORT_PERIODS -> NaturalNumberParameter(2),
        MovingAverageTrader.LONG_PERIODS -> NaturalNumberParameter(6),
        MovingAverageTrader.TOLERANCE -> RealNumberParameter(0.0002),
        MovingAverageTrader.WITH_SHORT -> BooleanParameter(true),
        MovingAverageTrader.SHORT_PERCENT -> CoefficientParameter(0.2))

      // taken from RangeTraderExample.scala
      case RangeTrader => new StrategyParameters(
        RangeTrader.INITIAL_FUNDS -> WalletParameter(Map(symbol._1 -> funds._1, symbol._2 -> funds._2)),
        RangeTrader.SYMBOL -> CurrencyPairParameter(Currency.USD, Currency.CHF),
        RangeTrader.VOLUME -> RealNumberParameter(10.0),
        RangeTrader.ORDER_WINDOW -> CoefficientParameter(0.20))

      // taken from RsiTraderExample
      case RsiTrader => new StrategyParameters(
        RsiTrader.INITIAL_FUNDS -> WalletParameter(Map(symbol._1 -> funds._1, symbol._2 -> funds._2)),
        RsiTrader.SYMBOL -> CurrencyPairParameter(symbol),
        RsiTrader.OHLC_PERIOD -> new TimeParameter(1 hour),
        RsiTrader.RSI_PERIOD -> new NaturalNumberParameter(12),
        RsiTrader.HIGH_RSI -> RealNumberParameter(80),
        RsiTrader.LOW_RSI -> RealNumberParameter(20),
        RsiTrader.WITH_SMA_CONFIRMATION -> BooleanParameter(true),
        RsiTrader.LONG_SMA_PERIOD -> new NaturalNumberParameter(20))

      case MMwithWallet => new StrategyParameters(
        MMwithWallet.INITIAL_FUNDS -> WalletParameter(Map(symbol._1 -> funds._1, symbol._2 -> funds._2)),
        MMwithWallet.SYMBOL -> new CurrencyPairParameter(symbol),
        MMwithWallet.SPREAD -> new RealNumberParameter(0.001))

      case _ => {
        println("getStrategyParameters failed")
        null
      }
    }
  }

}
