package ch.epfl.ts.example

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import ch.epfl.ts.component.ComponentBuilder
import ch.epfl.ts.optimization.SystemDeployment
import akka.actor.ActorRef
import ch.epfl.ts.component.ComponentRef
import ch.epfl.ts.optimization.StrategyFactory
import ch.epfl.ts.data.Currency
import ch.epfl.ts.component.fetch.MarketNames
import ch.epfl.ts.optimization.HostActorSystem
import ch.epfl.ts.optimization.RemoteHost


/**
 * Common behaviors observed in typical deployments of our systems.
 */
abstract class AbstractExample {

  implicit val builder: ComponentBuilder = new ComponentBuilder
  
  /** The default host is a simple local actor system (no remoting) */
  def localHost: HostActorSystem = new HostActorSystem()
  
  /** Factory to use in order to create a deployment */
  def factory: StrategyFactory
  
  /** List of IDs of the market being traded on */
  def marketIds: Seq[Long]
  
  /**
   * Connect the various components of a system deployment
   */
  def makeConnections(d: SystemDeployment): Unit
  
  /**
   * Optional supervisor actor
   * @example [[OptimizationSupervisor]]
   */
  lazy val supervisorActor: Option[ComponentRef] = None
  
  
  /**
   * Main runnable method
   */
  def main(args: Array[String]): Unit
  
  /**
   * Use this if we need to terminate early regardless of the data being fetched
   */
  import scala.concurrent.ExecutionContext.Implicits.global
  def terminateAfter(delay: FiniteDuration)(implicit builder: ComponentBuilder) = {   
    builder.system.scheduler.scheduleOnce(delay) {
      println("---------- Terminating system after a fixed duration of " + delay)
      builder.shutdownManagedActors(delay) onComplete {
        _ => builder.system.terminate()
      }
    }
  }
}

/**
 * Use this trait if your example uses evaluation
 */
trait TraderEvaluation {
  /** Emit evaluation reports every `evaluationPeriod` */
  def evaluationPeriod: FiniteDuration
  /** Assess the value of traders' wallet using this currency */
  def referenceCurrency: Currency
}

/**
 * Use this trait if your example uses remoting
 */
trait RemotingDeployment {
  /**
   * List of hosts on which to deploy the systems
   */
  def availableHosts: Seq[RemoteHost]
  
  /**
   * Target number of Trader instances to deploy (may not be respected exactly)
   */
  def maxInstances: Int
}

abstract class AbstractForexExample extends AbstractExample {
  
  lazy val marketIds = Seq(MarketNames.FOREX_ID)
  
  /** Main symbol (currency pair) being traded */
  def symbol: (Currency, Currency)
}
