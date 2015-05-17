package ch.epfl.ts.component.utils

import akka.actor.Actor
import akka.actor.Props
import akka.actor.ActorRef
import ch.epfl.ts.data.Streamable

object ParentActor {
  abstract class ParentActorMessage extends Streamable
  
  case class Create(props: Props, name: String) extends ParentActorMessage
  case class Done(ref: ActorRef) extends ParentActorMessage
}

/**
 * Empty actor who's only goal is to be parents to other actors.
 * It only serves as a sub-root in the actor hierarchy.
 */
class ParentActor extends Actor {
	import context.dispatcher
  import ParentActor._
  
  def receive = {
    case Create(props, name) => {
      val ref = context.actorOf(props, name)
      sender ! Done(ref)
    }
  }
}