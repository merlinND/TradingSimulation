package ch.epfl.ts.component.utils

import scala.collection.mutable.ArrayBuffer
import akka.actor.Terminated
import akka.actor.ActorLogging
import akka.actor.Actor
import akka.actor.ActorRef
import scala.concurrent.Promise
import akka.actor.PoisonPill

/**
 * @param references List of components that need to be killed & watched
 */
case class StartKilling(references: List[ActorRef])

/**
 * Supervising actor that waits for Actors' termination
 * @see http://letitcrash.com/post/30165507578/shutdown-patterns-in-akka-2
 */
class Reaper extends Actor with ActorLogging {
  import scala.concurrent.ExecutionContext.Implicits.global
  
	var watched = ArrayBuffer.empty[ActorRef]
  var promise: Option[Promise[Unit]] = None

  def onDone = promise match {
    case None => log.warning("Reaper tried to complete a non-existing promise")
    case Some(p) => p.success(Unit)
  }

  override def receive = {
    case StartKilling(bodies) => {
      bodies.foreach(c => {
        context.watch(c)
        c ! PoisonPill

        watched += c
      })

      // This promise will be completed when all watched have died
      val p = Promise[Unit]
      val respondTo = sender
      p.future.onSuccess({ case _ =>
        respondTo ! Unit
      })
      promise = Some(p)

      if(bodies.isEmpty) onDone
    }
    
    case Terminated(ref) => {
      watched -= ref
      if(watched.isEmpty) onDone
    }
  }
}