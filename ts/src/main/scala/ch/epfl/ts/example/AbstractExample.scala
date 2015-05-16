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


/**
 * Common behaviors observed in typical deployments of our systems.
 */
abstract class AbstractExample {

  implicit val builder: ComponentBuilder = new ComponentBuilder
  
  /** The default host is a simple local actor system (no remoting) */
  val localHost: HostActorSystem = new HostActorSystem()
  
  /** Factory to use in order to create a deployment */
  val factory: StrategyFactory
  
  /** List of IDs of the market being traded on */
  val marketIds: Seq[Long]
  
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
  val evaluationPeriod: FiniteDuration
  /** Assess the value of traders' wallet using this currency */
  val referenceCurrency: Currency
}

abstract class AbstractForexExample extends AbstractExample {
  
  val marketIds = Seq(MarketNames.FOREX_ID)
  
  /** Main symbol (currency pair) being traded */
  val symbol: (Currency, Currency)
}
