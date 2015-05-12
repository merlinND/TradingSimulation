package ch.epfl.ts.optimization

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import akka.actor.Deploy
import ch.epfl.ts.engine.MarketFXSimulator
import akka.actor.Props
import ch.epfl.ts.component.ComponentBuilder
import ch.epfl.ts.engine.ForexMarketRules
import ch.epfl.ts.component.fetch.TrueFxFetcher
import ch.epfl.ts.component.ComponentRef
import ch.epfl.ts.component.fetch.MarketNames
import ch.epfl.ts.component.fetch.PullFetchComponent
import akka.actor.Address
import ch.epfl.ts.component.utils.Printer
import ch.epfl.ts.data.Quote
import akka.remote.RemoteScope
import scala.reflect.ClassTag
import ch.epfl.ts.data.StrategyParameters
import ch.epfl.ts.traders.TraderCompanion
import ch.epfl.ts.brokers.StandardBroker
import ch.epfl.ts.data.Currency
import ch.epfl.ts.evaluation.Evaluator
import ch.epfl.ts.engine.rules.FxMarketRulesWrapper

/**
 * Need this concrete class to help serialization.
 * @note It should not be an inner class, otherwise it would implicitly try
 *       to send the whole container as a closure along with the QuoteTag.
 */
class QuoteTag extends ClassTag[Quote] with Serializable {
  override def runtimeClass = classOf[Quote]
}

trait StrategyFactory {
 
  abstract class CommonProps {
    // Required components for a system to work
    def fetcher: Props
    def market: Props
    def broker: Props
    
    def marketIds: List[Long]
    
    // Optional components
    def printer: Option[Props] = None
  }
  
  /**
   * Concrete references to instances created remotely.
   * An instance of this class contains references to all components
   * that were instantiated on a host.
   * 
   * @param evaluators Can be used just as a reference to a Trader (messages will be forwarded)
   */
  class SystemDeployment(val fetcher: ComponentRef,
                         val market: ComponentRef, broker: ComponentRef,
                         val evaluators: Set[ComponentRef],
                         val printer: Option[ComponentRef] = None)
  
  
  /**
   * Define a set of props (used to create components) that will be created identically on
   * all actor systems (i.e. every worker will run these actors):
   */
  protected def commonProps: CommonProps
  
  
  /**
   * For the given remote worker, create the common components
   * and an instance of the trading strategy for each parameterization given.
   *
   * @param master            Supervisor actor to register the remote actors to
   * @param host              Remote machine on which to deploy the new actors
   * @param parameterizations
   * 
   * @return A list of references to all the instances of the strategy being optimized
   */
  // TODO: connect components smartly
  def createRemoteActors(master: ComponentRef, host: RemoteHost,
                         strategyToOptimize: TraderCompanion, parameterizations: Set[StrategyParameters])
                        (implicit builder: ComponentBuilder): SystemDeployment
  

  /**
   * @return A list of the Evaluator components references (one for each parameterization passed)
   *         These must receive Transactions from the market in order to evaluate.
   *         Also, it should be used just as a reference to the Trader itself since it will forward
   *         the messages.
   */
  protected def createRemoteTraders(host: RemoteHost, strategyToOptimize: TraderCompanion,
                                  parameterizations: Set[StrategyParameters])
                                 (implicit builder: ComponentBuilder): Set[ComponentRef] = {
    // One trader for each parameterization
    for((parameterization, i) <- parameterizations.zipWithIndex) yield {
      
      // Assert that parameterization is valid for this strategy (will throw if it is not the case)
      strategyToOptimize.verifyParameters(parameterization)
      
      // TODO: user of this class could give a list of names
      val name = "Trader-" + i

      // Trader
      val traderId = i.toLong
      val traderProps = strategyToOptimize.getProps(traderId, commonProps.marketIds, parameterization)
      val trader = host.createRemotely(traderProps, name)
      // Evaluator monitoring the performance of this trader
      // TODO: factor these out
      val period = 10 seconds
      val referenceCurrency = Currency.CHF
      val evaluatorProps = Props(classOf[Evaluator], trader.ar, traderId, trader.name, referenceCurrency, period)
      val evaluator = host.createRemotely(evaluatorProps, name + "-Evaluator")
      
      evaluator
    }
  }
  
  /**
   * Try assigning evenly the strategies to hosts
   */
  def distributeOverHosts(hosts: List[RemoteHost], parameterizations: Set[StrategyParameters]): Map[RemoteHost, Set[StrategyParameters]] = {
    // Prepare parameters for worker actors (there will be one actor per parameter value)
    val nPerHost = (parameterizations.size / hosts.length.toDouble).ceil.toInt
    
    hosts.zipWithIndex.map({ case (host, i) =>
      val offset = nPerHost * i
      host -> parameterizations.slice(offset, offset + nPerHost)
    }).toMap
  }
}


/**
 * Concrete StrategyFactory allowing to test Forex traders.
 */
object ForexStrategyFactory extends StrategyFactory {

  
  def commonProps = new CommonProps {
    // Fetcher
    def fetcher = {
      val fetcher = new TrueFxFetcher
      Props(classOf[PullFetchComponent[Quote]], fetcher, new QuoteTag)
    }
    
    def marketIds = List(MarketNames.FOREX_ID)

    // Market
    def market = {
      val rules = new FxMarketRulesWrapper()
      Props(classOf[MarketFXSimulator], marketIds(0), rules)
    }
    
    def broker = {
      Props(classOf[StandardBroker])
    }
    
    // Printer
    override def printer = Some(Props(classOf[Printer], "MyPrinter"))
  }
  
  /**
   * Create one actor for each of the `commonProps`
   * and one Trader instance for each of the `parameterizations`.
   * @warning You are responsible for making the connections between the created components
   */
  def createRemoteActors(master: ComponentRef, host: RemoteHost,
                         strategyToOptimize: TraderCompanion, parameterizations: Set[StrategyParameters])
                        (implicit builder: ComponentBuilder): SystemDeployment = {

    // ----- Common props (need one instance per host, used by all the traders)
    val fetcher = host.createRemotely(commonProps.fetcher, "Fetcher")
		val market = host.createRemotely(commonProps.market, "Market")
		val broker = host.createRemotely(commonProps.broker, "Broker")
		val printer = commonProps.printer.map(p => host.createRemotely(p, "Printer"))

    // ----- Traders (possibly many) to be run in parallel on this host
    val evaluators = Set[ComponentRef]()// createRemoteTraders(host, strategyToOptimize, parameterizations)
    
    new SystemDeployment(fetcher, market, broker, evaluators, printer)
  }
}