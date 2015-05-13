package ch.epfl.ts.optimization

import scala.collection.mutable.{ Map => MutableMap, MutableList }
import akka.actor.ActorLogging
import ch.epfl.ts.engine.TraderIdentity
import ch.epfl.ts.data.EndOfFetching
import ch.epfl.ts.evaluation.EvaluationReport
import ch.epfl.ts.component.Component
import akka.actor.ActorRef
import ch.epfl.ts.engine.GetTraderParameters

/**
 * Responsible for overseeing actors instantiated at worker nodes. That means it listens
 * to them (i.e. it doesn't send any commands or similar, so far, except for being able to ping them).
 * 
 * @param onEnd Callback to call when optimization has completed and the best trader was picked
 */
class OptimizationSupervisor(onEnd: (TraderIdentity, EvaluationReport) => Unit) extends Component with ActorLogging {

  /**
   * Collection of evaluations for registered traders being tested
   * The set of registered actors can be deduces from this map's keys.
   */
  // TODO: only keep the most N recent to avoid unnecessary build-up
  val evaluations: MutableMap[ActorRef, MutableList[EvaluationReport]] = MutableMap.empty

  def lastEvaluations = evaluations.map({ case (t, l) => t -> l.head })
  def bestTraderPerformance = lastEvaluations.toList.sortBy(p => p._2).head
  
  var bestEvaluation: Option[EvaluationReport] = None
  def askBestTraderIdentity = {
    bestEvaluation = Some(bestTraderPerformance._2)
    bestTraderPerformance._1 ! GetTraderParameters
  }
  
  override def receiver = {
    case w: ActorRef => {
      evaluations += (w -> MutableList.empty)
    }

    case e: EvaluationReport if evaluations.contains(sender) => {
      // Add the report to the collection
      evaluations.get(sender).foreach { l => l += e }
    }
    
    case e: EvaluationReport => log.error("Received report from unknown sender " + sender + ": " + e)

    // When all data has been replayed, we can determine the best trader
    case _: EndOfFetching if bestEvaluation.isEmpty => {
      log.info("Supervisor has received an EndOfFetching signal. Will now try to determine the best fetcher's identity.")
      askBestTraderIdentity
    }
    case _: EndOfFetching =>
      log.warning("Supervisor has received more than one EndOfFetching signals")
    
    case t: TraderIdentity if bestEvaluation.isDefined => {
      onEnd(t, bestEvaluation.get)
    }
    case t: TraderIdentity => {
      log.warning("Received a TraderIdentity before knowing the best performance, which is weird:" + t)
    }

    
    case m => log.warning("MasterActor received weird: " + m)
  }
  
}