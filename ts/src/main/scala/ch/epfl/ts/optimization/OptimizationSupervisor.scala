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

  def hasEvaluations = evaluations.forall(p => !p._2.isEmpty)
  def lastEvaluations: Option[Map[ActorRef, EvaluationReport]] =
    if (hasEvaluations) Some(evaluations.map({ case (t, l) => t -> l.head }).toMap)
    else None
  def bestTraderPerformance = lastEvaluations.map(_.toList.sortBy(p => p._2).head)
  
  var bestEvaluation: Option[EvaluationReport] = None
  def askBestTraderIdentity = bestTraderPerformance match {
    case Some((trader, evaluation)) => {
      bestEvaluation = Some(evaluation)
      trader ! GetTraderParameters
    }
    case None => throw new Exception("Tried to get best trader's identity while no EvaluationReport had been seen yet")
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
    case EndOfFetching if lastEvaluations.isEmpty =>
      log.warning("Supervisor has received an EndOfFetching signal but hasn't seen any EvaluationReport yet.")
    case EndOfFetching if bestEvaluation.isEmpty => {
      log.info("Supervisor has received an EndOfFetching signal. Will now try to determine the best fetcher's identity.")
      askBestTraderIdentity
    }
    case EndOfFetching =>
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