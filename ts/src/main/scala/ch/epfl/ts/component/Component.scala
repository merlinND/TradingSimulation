package ch.epfl.ts.component

import scala.collection.mutable.{HashMap => MHashMap}
import scala.language.existentials
import scala.language.postfixOps
import scala.reflect.ClassTag
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.actorRef2Scala
import akka.actor.ActorLogging

trait Receiver extends Actor with ActorLogging {
  def receive: PartialFunction[Any, Unit]

  def send[T: ClassTag](t: T): Unit
  def send[T: ClassTag](t: List[T]): Unit
}

abstract class Component extends Receiver {
  var dest = MHashMap[Class[_], List[ActorRef]]()
  var stopped = true

  final def componentReceive: PartialFunction[Any, Unit] = {
    case ComponentRegistration(ar, ct, name) =>
      connect(ar, ct, name)
      log.debug("Received destination " + this.getClass.getSimpleName + ": from " + ar + " to " + ct.getSimpleName)
    case StartSignal => stopped = false
      start
      log.debug("Received Start " + this.getClass.getSimpleName)
    case StopSignal => context.stop(self)
      stop
      log.debug("Received Stop " + this.getClass.getSimpleName)
      stopped = true
    case y if stopped => log.debug("Received data when stopped " + this.getClass.getSimpleName + " of type " + y.getClass )
  }

  /**
   * Connects two compoenents
   *
   * Normally subclass don't need to override this method.
   * */
  def connect(ar: ActorRef, ct: Class[_], name: String): Unit = {
    dest += (ct -> (ar :: dest.getOrElse(ct, List())))
  }

  /**
   * Starts the component
   *
   * Subclass can override do initialization here
   * */
  def start: Unit = {}

  /**
   * Stops the component
   *
   * Subclass can override do release resources here
   * */
  def stop: Unit = {}

  def receiver: PartialFunction[Any, Unit]

  /* TODO: Dirty hack, componentReceive giving back unmatched to rematch in receiver using a andThen */
  override def receive = componentReceive orElse receiver

  def send[T: ClassTag](t: T) = dest.get(t.getClass).map(_.map (_ ! t)) //TODO(sygi): support superclasses
  def send[T: ClassTag](t: List[T]) = t.map( elem => dest.get(elem.getClass).map(_.map(_ ! elem)))
}

/** Encapsulates [[akka.actor.ActorRef]] to facilitate connection of components
 * TODO(sygi): support sending messages to ComponentRefs through !
 */
class ComponentRef(val ar: ActorRef, val clazz: Class[_], val name: String, cb: ComponentBuilder) extends Serializable {
  /** Connects current component to the destination component
    *
    * @param destination the destination component
    * @param types the types of messages that the destination expects to receive
    */
  def ->(destination: ComponentRef, types: Class[_]*) = {
    types.map(cb.add(this, destination, _))
  }

  /** Connects current component to the specified components
    *
    * @param refs the destination components
    * @param types the types of messages that the destination components expect to receive
    */
  def ->(refs: Seq[ComponentRef], types: Class[_]*) = {
    for (ref <- refs; typ <- types) cb.add(this, ref, typ)
  }
}
