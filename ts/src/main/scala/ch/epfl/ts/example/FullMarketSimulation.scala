package ch.epfl.ts.example

import ch.epfl.ts.component.{ComponentRef, StopSignal, ComponentBuilder}
import com.typesafe.config.ConfigFactory
import akka.actor.Props
import ch.epfl.ts.brokers.StandardBroker
import scala.language.postfixOps
import scala.concurrent.duration._
import ch.epfl.ts.data._
import ch.epfl.ts.traders.{MadTrader, MovingAverageTrader, SimpleTraderWithBroker}
import ch.epfl.ts.data.Register
import ch.epfl.ts.engine._
import ch.epfl.ts.data.MarketBidOrder
import ch.epfl.ts.data.Register
import ch.epfl.ts.engine.FundWallet
import ch.epfl.ts.data.MarketAskOrder
import ch.epfl.ts.component.fetch.{HistDataCSVFetcher, PullFetchComponent, TrueFxFetcher, MarketNames}
import scala.reflect.ClassTag
import ch.epfl.ts.data.Currency._
import ch.epfl.ts.data.MarketBidOrder
import ch.epfl.ts.data.Register
import ch.epfl.ts.engine.FundWallet
import ch.epfl.ts.data.Quote
import ch.epfl.ts.data.Currency
import ch.epfl.ts.data.MarketAskOrder
import ch.epfl.ts.component.utils.BackLoop
import ch.epfl.ts.component.persist.DummyPersistor
import java.util.Timer
import ch.epfl.ts.engine.rules.{SimulationMarketRulesWrapper, FxMarketRulesWrapper}

/**
 * Market simulation with first reading historical data and then running simulation on its own.
 */
object FullMarketSimulation {
  var producers = Map[Class[_], List[ComponentRef]]()
  var consuments = Map[Class[_], List[ComponentRef]]()
  def main(args: Array[String]): Unit = {
    //TODO(sygi): create functions to build multiple components to slim main down
    implicit val builder = new ComponentBuilder("TestingBroker", ConfigFactory.parseString("akka.loglevel = \"DEBUG\""))
    initProducersAndConsuments()

    //trader
    val parameters = new StrategyParameters(
      MadTrader.INITIAL_FUNDS -> WalletParameter(Map(Currency.CHF -> 1000.0)),
      MadTrader.INTERVAL -> new TimeParameter(1 seconds),
      MadTrader.ORDER_VOLUME -> NaturalNumberParameter(10),
      MadTrader.CURRENCY_PAIR -> new CurrencyPairParameter(Currency.USD, Currency.CHF))

    val tId = 15L

    val trader = MadTrader.getInstance(tId, parameters, "OneMadTrader")
    addConsument(classOf[Quote], trader)
    val broker = builder.createRef(Props(classOf[StandardBroker]), "Broker")
    addConsument(classOf[Quote], broker)

    trader->(broker, classOf[Register])
    trader->(broker, classOf[FundWallet])
    connectAllOrders(trader, broker)

    //fetcher
    val useLiveData = false
    val symbol = (Currency.USD, Currency.CHF)
    val fxQuoteFetcher = createFetcher(useLiveData, builder, symbol)
    addProducer(classOf[Quote], fxQuoteFetcher)

    //hybrid market
    val fetcherRules = new FxMarketRulesWrapper()
    val simulationRules = new SimulationMarketRulesWrapper()
    val marketForexId = MarketNames.FOREX_ID
    val forexMarket = builder.createRef(Props(classOf[HybridMarketSimulator], marketForexId, fetcherRules, simulationRules), MarketNames.FOREX_NAME)

    addConsument(classOf[Quote], forexMarket)
    addProducer(classOf[Quote], forexMarket)
    connectAllOrders(broker, forexMarket)
    forexMarket->(broker, classOf[ExecutedBidOrder], classOf[ExecutedAskOrder])

    connectProducersWithConsuments()

    builder.start
    val delay = 10 * 1000 //in ms
    scheduleChange(fxQuoteFetcher, forexMarket, delay)
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
      val dateFormat = new java.text.SimpleDateFormat("yyyyMM")
      val startDate = dateFormat.parse("201304")
      val endDate = dateFormat.parse("201305")
      val workingDir = "./data"
      val currencyPair = symbol._1.toString() + symbol._2.toString();

      builder.createRef(Props(classOf[HistDataCSVFetcher], workingDir, currencyPair, startDate, endDate, 1e-2), "HistDataFetcher")
    }
  }
}
