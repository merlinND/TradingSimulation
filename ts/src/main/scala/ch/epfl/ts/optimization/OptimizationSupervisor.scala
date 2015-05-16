package ch.epfl.ts.optimization

import scala.language.postfixOps
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.collection.mutable.{ Map => MutableMap, MutableList }
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.{ActorRef, Cancellable}
import akka.pattern.{ ask, pipe }
import akka.util.Timeout
import akka.actor.Status.Failure
import akka.pattern.AskTimeoutException
import ch.epfl.ts.engine.TraderIdentity
import ch.epfl.ts.data.EndOfFetching
import ch.epfl.ts.evaluation.EvaluationReport
import ch.epfl.ts.component.Component
import ch.epfl.ts.engine.GetTraderParameters

/**
 * Responsible for overseeing actors instantiated at worker nodes. That means it listens
 * to them (i.e. it doesn't send any commands or similar, so far, except for being able to ping them).
 * 
 * @param onEnd Callback to call when optimization has completed and the best trader was picked
 */
class OptimizationSupervisor extends Component with ActorLogging {
  
  def onEnd(t: TraderIdentity, e: EvaluationReport) = (t, e) match {
    case (TraderIdentity(name, uid, companion, parameters), evaluation) => {
      println("---------- Optimization completed ----------")
      println(s"The best trader was: $name (id $uid) using strategy $companion.")
      println(s"Its parameters were:\n$parameters")
      println(s"Its final evaluation was:\n$evaluation")
      println("--------------------------------------------")
      
      context.system.terminate()
    }
  }
  
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
  def askBestTraderIdentity() = bestTraderPerformance match {
    case Some((trader, evaluation)) => {
      bestEvaluation = Some(evaluation)
      log.debug("Best trader is " + trader + " with evaluation " + evaluation + ", now asking its identity")

      implicit val timeout = Timeout(3 seconds)
      implicit val executionContext = context.system.dispatcher
      (trader ? GetTraderParameters).mapTo[TraderIdentity] pipeTo(self)
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
    case _: EndOfFetching if lastEvaluations.isEmpty =>
      log.warning("Supervisor has received an EndOfFetching signal but hasn't seen any EvaluationReport yet.")
    case _: EndOfFetching if bestEvaluation.isEmpty => {
      log.debug("Supervisor has received an EndOfFetching signal. Will now try to determine the best fetcher's identity.")
      askBestTraderIdentity()
    }
    case _: EndOfFetching =>
      log.warning("Supervisor has received more than one EndOfFetching signals")
    
      
    case t: TraderIdentity if bestEvaluation.isDefined =>
      onEnd(t, bestEvaluation.get)
      
    case t: TraderIdentity => {
      log.warning("Received a TraderIdentity before knowing the best performance, which is weird:" + t)
    }
    
    case Failure(cause) => cause match {
      case e: AskTimeoutException => log.error(e, "Failed to receive best trader's identity")
      case e => log.error(e, "MasterActor received failure")
    }

    
    case m => log.warning("MasterActor received weird: " + m)
  }
  
}