package ch.epfl.ts.example

import ch.epfl.ts.component.{ComponentRef, StopSignal, ComponentBuilder}
import com.typesafe.config.ConfigFactory
import akka.actor.Props
import ch.epfl.ts.brokers.StandardBroker
import scala.language.postfixOps
import scala.concurrent.duration._
import ch.epfl.ts.data._
import ch.epfl.ts.traders.MadTrader
import ch.epfl.ts.engine._
import ch.epfl.ts.component.fetch.{HistDataCSVFetcher, PullFetchComponent, TrueFxFetcher, MarketNames}
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
import ch.epfl.ts.engine.rules.{SimulationMarketRulesWrapper, FxMarketRulesWrapper}
import ch.epfl.ts.traders.MarketMakerTrader
import ch.epfl.ts.component.utils.Printer
import ch.epfl.ts.traders.MMwithWallet
import ch.epfl.ts.traders.TraderCompanion
import ch.epfl.ts.config.FundsGermany

/**
 * Market simulation with first reading historical data and then running simulation on its own.
 */
object FullMarketSimulationWithCustomSetup {
  var producers = Map[Class[_], List[ComponentRef]]()
  var consuments = Map[Class[_], List[ComponentRef]]()
  
  /** market configuration */
  val symbol = (Currency.EUR, Currency.CHF)
  val fundDistribution = new FundsGermany      /* country */
  val exchangeRateFunding = (1.0, 1.0)         /* ._1: fund to symbol._1, ._2: fund to  symbol._2
                                                * must be hardcoded since the distribution 
                                                * might be given in a different currency and
                                                * hence this is only a scaling factor
                                                */ 
  var traderDistribution = Map[Class[_], Int]() /* trader type -> number of instances */
  var traderList = List[TraderCompanion]()      /* list of trader instances, to be filled during setup */
  var marketMaker: TraderCompanion = null       /* market maker (who is technically a trader */
  
  def setup = {
    // TODO Jakob
    /** add trader type and number of instances */
    traderDistribution += classOf[MadTrader] -> 5
    
    val numberOfTraders = traderDistribution.foldLeft(0)(_+_._2)
    /** create trader instances */
    traderList = traderDistribution.flatMap(x => x match {
      case (key, value) => key match {
        case madtrader if madtrader.isAssignableFrom(classOf[MadTrader]) => {
          // TODO create value instances of MadTrader
          List[TraderCompanion]()
        }
        case _ => {
          println("Unknown trader class")
          List[TraderCompanion]()
        }
      }
    }).toList
  }
  
  def main(args: Array[String]): Unit = {
    //TODO(sygi): create functions to build multiple components to slim main down
    implicit val builder = new ComponentBuilder
    initProducersAndConsuments()
    
    val useLiveData = false

    // Trader
    val parameters = new StrategyParameters(
      MadTrader.INITIAL_FUNDS -> WalletParameter(Map(Currency.CHF -> 10000.0, Currency.EUR -> 10000.0)),
      MadTrader.INTERVAL -> new TimeParameter(1 seconds),
      MadTrader.ORDER_VOLUME -> NaturalNumberParameter(10),
      MadTrader.CURRENCY_PAIR -> new CurrencyPairParameter(Currency.EUR, Currency.CHF))

    val tId = 15L
    val marketId = MarketNames.FOREX_ID

    val trader = MadTrader.getInstance(tId, List(marketId), parameters, "OneMadTrader")
    addConsument(classOf[Quote], trader)
    addConsument(classOf[TheTimeIs], trader)
    val broker = builder.createRef(Props(classOf[StandardBroker]), "Broker")
    addConsument(classOf[Quote], broker)

    trader->(broker, classOf[Register])
    trader->(broker, classOf[FundWallet])
    connectAllOrders(trader, broker)
    
    // MarketMaker
    val parameters2 = new StrategyParameters(
      MarketMakerTrader.INITIAL_FUNDS -> WalletParameter(Map(Currency.CHF -> 10000.0, Currency.EUR -> 10000.0)),
      MarketMakerTrader.INTERVAL -> new TimeParameter(1 seconds),
      MarketMakerTrader.ORDER_VOLUME -> NaturalNumberParameter(10),
      MarketMakerTrader.CURRENCY_PAIR -> new CurrencyPairParameter(Currency.EUR, Currency.CHF))
    
    val marketMakerParameters = new StrategyParameters(
        MMwithWallet.INITIAL_FUNDS -> WalletParameter(Map(Currency.CHF -> 10000.0, Currency.EUR -> 10000.0)),
        MMwithWallet.SYMBOL -> new CurrencyPairParameter(Currency.EUR, Currency.CHF),
        MMwithWallet.SPREAD -> new RealNumberParameter(0.001))

    val tId2 = 100L

//    val marketMaker = MarketMakerTrader.getInstance(tId2, List(marketId), parameters2, "OneMarketMakerTrader")
    val marketMaker = MMwithWallet.getInstance(tId2, List(marketId), marketMakerParameters, "WalletMarketMakerTrader")
    addConsument(classOf[Quote], marketMaker)
    addConsument(classOf[TheTimeIs], marketMaker)

    marketMaker->(broker, classOf[Register])
    marketMaker->(broker, classOf[FundWallet])
    connectAllOrders(marketMaker, broker)

    // Fetcher
    val fetcher = createFetcher(useLiveData, builder, symbol)
    
    // Hybrid market
    val fetcherRules = new FxMarketRulesWrapper
    val simulationRules = new SimulationMarketRulesWrapper
    val market = builder.createRef(Props(classOf[HybridMarketSimulator], marketId, fetcherRules, simulationRules), MarketNames.FOREX_NAME)
    fetcher->(market, classOf[Quote])

    addProducer(classOf[Quote], market)
    addProducer(classOf[TheTimeIs], market)
    connectAllOrders(broker, market)
    market->(broker, classOf[ExecutedBidOrder], classOf[ExecutedAskOrder])
    
    // for the market maker
//    connectAllOrders(marketMaker, market) // TODO use broker inbetween
    market->(marketMaker, classOf[MarketBidsEmpty], classOf[MarketAsksEmpty], classOf[MarketEmpty], classOf[ExecutedBidOrder], classOf[ExecutedBidOrder])

    connectProducersWithConsuments()
    
    builder.start
    val delay = 20 * 1000 //in ms
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
    for(msg <- messageClasses) {
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
    for(msgClass <- producers.keys){
        for(myProducers <- producers.get(msgClass); myConsuments <- consuments.get(msgClass)) {
          for (producer <- myProducers; consument <- myConsuments) {
            if (producer != consument) {
              producer ->(consument, msgClass)
            }
          }
        }
    }
  }

  def connectAllOrders(source: ComponentRef, destination: ComponentRef) =
    source->(destination, classOf[LimitBidOrder], classOf[LimitAskOrder], classOf[MarketBidOrder], classOf[MarketAskOrder])

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
  
  def getStrategyParameters(ttype: Class[_]) = {
    ttype match {
      case madtrader if madtrader.isAssignableFrom(classOf[MadTrader]) => new StrategyParameters(
        MadTrader.INITIAL_FUNDS -> WalletParameter(Map(Currency.CHF -> 10000.0, Currency.EUR -> 10000.0)), // TODO introduce random values
        MadTrader.INTERVAL -> new TimeParameter(1 seconds),
        MadTrader.ORDER_VOLUME -> NaturalNumberParameter(10),
        MadTrader.CURRENCY_PAIR -> new CurrencyPairParameter(Currency.EUR, Currency.CHF))
      case _ => {
        println("getStrategyParameters failed")
        null
      }
    }
  }  
}
