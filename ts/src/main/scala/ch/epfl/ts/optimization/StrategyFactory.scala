package ch.epfl.ts.optimization

import scala.language.postfixOps
import scala.concurrent.duration.DurationInt
import scala.reflect.ClassTag
import akka.actor.Props
import akka.actor.Deploy
import akka.actor.Address
import akka.actor.ActorRef
import akka.remote.RemoteScope
import ch.epfl.ts.engine.MarketFXSimulator
import ch.epfl.ts.engine.ForexMarketRules
import ch.epfl.ts.component.ComponentBuilder
import ch.epfl.ts.component.fetch.TrueFxFetcher
import ch.epfl.ts.component.ComponentRef
import ch.epfl.ts.component.fetch.MarketNames
import ch.epfl.ts.component.fetch.PullFetchComponent
import ch.epfl.ts.component.utils.Printer
import ch.epfl.ts.data.Quote
import ch.epfl.ts.data.StrategyParameters
import ch.epfl.ts.traders.TraderCompanion
import ch.epfl.ts.brokers.StandardBroker
import ch.epfl.ts.data.Currency
import ch.epfl.ts.evaluation.Evaluator
import ch.epfl.ts.engine.rules.FxMarketRulesWrapper
import ch.epfl.ts.component.fetch.HistDataCSVFetcher
import scala.concurrent.duration.FiniteDuration

/**
 * Need this concrete class to help serialization.
 * @note It should not be an inner class, otherwise it would implicitly try
 *       to send the whole container as a closure along with the QuoteTag.
 */
class QuoteTag extends ClassTag[Quote] with Serializable {
  override def runtimeClass = classOf[Quote]
}

/**
 * Contains concrete references to instances created remotely (or locally just as well, actually).
 * An instance of this class contains references to all components
 * that were instantiated on a host.
 *
 * @param evaluators Can be used just as a reference to a Trader (messages will be forwarded)
 */
class SystemDeployment(val fetcher: ComponentRef, val market: ComponentRef, val broker: ComponentRef,
                       val evaluators: Set[ComponentRef],
                       val printer: Option[ComponentRef] = None)

/**
 * This trait defines abstract methods and fields with everything that is needed
 * for a trading system (market, broker, data fetcher, traders, ...).
 * We implement here what can be done generically, but otherwise this trait
 * needs to be extended into a concrete Factory in order to provide values
 * for all the abstract fields.
 *
 * @see {@link ch.epfl.ts.optimization.ForexStrategyFactory} for an example concrete class
 */
trait StrategyFactory {

  /**
   * Contains props that are instantiated once per trading system,
   * i.e. everything that can be shared by several traders.
   *
   * Compose this class with the `LiveFetcher` or `HistoricalFetcher`
   * for an easy way to specify your data source.
   */
  abstract class CommonProps {
    // Required components for a system to work
    def fetcher: Props
    def market: Props
    def broker: Props

    def marketIds: List[Long]

    // Optional components
    def printer: Option[Props] = None
  }

  trait LiveFetcher {
    def getFetcherInstance
    def fetcher: Props = Props(classOf[PullFetchComponent[Quote]], getFetcherInstance, new QuoteTag)
  }

  /**
   * Extend this trait to provide concrete values for `symbol`, `speed`, etc.
   */
  trait HistoricalFetcher {
	  val symbol: (Currency, Currency)
    val speed: Double
    val workingDirectory = "./data"
    /** @example "201304" */
    val start: String
    val end: String

    def fetcher: Props = {
      val dateFormat = new java.text.SimpleDateFormat("yyyyMM")
      val startDate = dateFormat.parse(start)
      val endDate = dateFormat.parse(end)
      val currencyPair = symbol._1.toString() + symbol._2.toString();

      Props(classOf[HistDataCSVFetcher], workingDirectory, currencyPair, startDate, endDate, speed)
    }
  }


  /**
   * Define a set of props (used to create components) that will be created identically on
   * all actor systems (i.e. every worker will run these actors).
   * It should respect the `CommonProps` interface, but can be extended as you wish.
   */
  protected def commonProps: CommonProps

  /**
   * TimePeriod between two `EvaluationReports`
   */
  val evaluationPeriod: FiniteDuration
  /**
   * Reference currency used to compute the value of the traders' wallets
   * at any given point in time.
   */
  val referenceCurrency: Currency


  /**
   * For the given remote worker (host machine), create the common components
   * and an instance of the trading strategy for each parameterization given.
   *
   * @param host              System on which to deploy the new actors (may be remote)
   * @param parameterizations Many parameter values (one trader + evaluator
                              will be instantiated for each)
   * @param names             Names to assign to the strategies. Number of elements
   *                          should ideally be  the same as `parameterizations.size`.
   *                          Otherwise, we will auto-generate names.
   *                          Names may be transformed in order to make valid actor names.
   *
   * @return A `SystemDeployment` containing references to all instantiated components
   */
  def createDeployment(host: AnyHost,
                       strategyToOptimize: TraderCompanion,
                       parameterizations: Set[StrategyParameters], names: Set[String] = Set.empty)
                      (implicit builder: ComponentBuilder): SystemDeployment = {

    // ----- Common props (need one instance per host, used by all the traders)
	  val broker = host.actorFor(commonProps.broker, "Broker")
	  val market = host.actorFor(commonProps.market, "Market")
    val fetcher = host.actorFor(commonProps.fetcher, "Fetcher")
    val printer = commonProps.printer.map(p => host.actorFor(p, "Printer"))

    // ----- Traders (possibly many) to be run in parallel on this host
    val sanitized = names.map(n => sanitizeActorName(n)).toList
    val evaluators = createTraders(host, strategyToOptimize, parameterizations, sanitized)

    new SystemDeployment(fetcher, market, broker, evaluators, printer)
  }


  /**
   * @return A list of the Evaluator components references (one for each parameterization passed)
   *         These must receive Transactions from the market in order to evaluate.
   *         Also, it should be used just as a reference to the Trader itself since it will forward
   *         the messages.
   */
  protected def createTraders(host: AnyHost, strategyToOptimize: TraderCompanion,
                              parameterizations: Set[StrategyParameters], names: List[String] = List.empty)
                             (implicit builder: ComponentBuilder): Set[ComponentRef] = {
    // One trader for each parameterization
    for((parameterization, i) <- parameterizations.zipWithIndex) yield {

      // Assert that parameterization is valid for this strategy (will throw an exception if it is not the case)
      strategyToOptimize.verifyParameters(parameterization)

      val name = {
        if(i < names.size) names(i)
        else "Trader-" + i
      }

      // TODO: create all traders / evaluators under a 'root' actor
      // Trader
      val traderId = i.toLong
      val traderProps = strategyToOptimize.getProps(traderId, commonProps.marketIds, parameterization)
      val trader = host.actorFor(traderProps, name)

      // Evaluator monitoring the performance of this trader
      val evaluatorProps = Props(classOf[Evaluator], trader.ar, traderId, trader.name, referenceCurrency, evaluationPeriod)
      val evaluator = host.actorFor(evaluatorProps, name + "-Evaluator")

      evaluator
    }
  }

  /**
   * Try assigning evenly the parameterizations to hosts
   *
   * @TODO Less naive repartition
   * @TODO This could actually be a very generic function
   */
  def distributeOverHosts(hosts: Seq[RemoteHost], parameterizations: Set[StrategyParameters]): Map[RemoteHost, Set[StrategyParameters]] = {
    // Prepare parameters for worker actors (there will be one actor per parameter value)
    val nPerHost = (parameterizations.size / hosts.length.toDouble).ceil.toInt

    hosts.zipWithIndex.map({ case (host, i) =>
      val offset = nPerHost * i
      host -> parameterizations.slice(offset, offset + nPerHost)
    }).toMap
  }

  /**
   * Sanitize a string into a valid actor name
   *
   * @TODO Make more complete (most cases are not covered here)
   */
  def sanitizeActorName(s: String): String = {
    val replacements = Map(
      "Ö" -> "O",
      "é" -> "e",
      "è" -> "e",
      "ü" -> "u",
      "ç" -> "c",
      "ä" -> "a",
      "à" -> "a",
      " " -> "-"
    )
    replacements.foldLeft(s)({
      case (string, (from, to)) => string.replaceAll(from, to)
    })
  }
}
