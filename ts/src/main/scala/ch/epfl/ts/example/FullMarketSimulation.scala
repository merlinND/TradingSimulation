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

/**
 * Market simulation with first reading historical data and then running simulation on its own.
 */
object FullMarketSimulation {
  var producers = Map[Class[_], List[ComponentRef]]()
  var consuments = Map[Class[_], List[ComponentRef]]()
  
  def main(args: Array[String]): Unit = {
    //TODO(sygi): create functions to build multiple components to slim main down
    implicit val builder = new ComponentBuilder
    initProducersAndConsuments()
    
    val useLiveData = false
    val symbol = (Currency.EUR, Currency.CHF)

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

    val tId2 = 100L

    val trader2 = MarketMakerTrader.getInstance(tId, List(marketId), parameters, "OneMarketMakerTrader")
//    addConsument(classOf[Quote], trader2)
    addConsument(classOf[MarketMakerNotification], trader2)
//    val broker2 = builder.createRef(Props(classOf[StandardBroker]), "Broker")
//    addConsument(classOf[Quote], broker2)

    trader2->(broker, classOf[Register])
    trader2->(broker, classOf[FundWallet])
    connectAllOrders(trader2, broker)
    

    // Fetcher
    val fetcher = createFetcher(useLiveData, builder, symbol)
    
    // Hybrid market
    val fetcherRules = new FxMarketRulesWrapper
    val simulationRules = new SimulationMarketRulesWrapper
    val market = builder.createRef(Props(classOf[HybridMarketSimulator], marketId, fetcherRules, simulationRules), MarketNames.FOREX_NAME)
    fetcher->(market, classOf[Quote])

    addProducer(classOf[Quote], market)
    addProducer(classOf[TheTimeIs], market)
    addConsument(classOf[TheTimeIs], trader)
    connectAllOrders(broker, market)
    market->(broker, classOf[ExecutedBidOrder], classOf[ExecutedAskOrder])
    market->(trader2, classOf[MarketMakerNotification]); //

    connectProducersWithConsuments()

    builder.start
    val delay = 2 * 1000 //in ms
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
}
