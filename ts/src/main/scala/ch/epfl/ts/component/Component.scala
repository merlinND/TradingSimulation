package ch.epfl.ts.component

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.{HashMap => MHashMap}
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.language.existentials
import scala.language.postfixOps
import scala.reflect.ClassTag
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import akka.actor._
import akka.pattern.gracefulStop
import akka.pattern.ask
import akka.util.Timeout
import ch.epfl.ts.component.utils.Reaper
import ch.epfl.ts.component.utils.StartKilling

case object StartSignal
case object StopSignal
case class ComponentRegistration(ar: ActorRef, ct: Class[_], name: String)

final class ComponentBuilder(val system: ActorSystem) {
  /** Alternative construcors */
  def this() {
    this(ActorSystem(ConfigFactory.load().getString("akka.systemName"), ConfigFactory.load()))
  }

  def this(name: String) {
    this(ActorSystem(name, ConfigFactory.load()))
  }

  def this(myName: String, config: Config) {
    this(ActorSystem(myName, config))
  }


  type ComponentProps = akka.actor.Props
  var graph = Map[ComponentRef, List[(ComponentRef, Class[_])]]()
  var instances = List[ComponentRef]()

  private val reaper = system.actorOf(Props(classOf[Reaper]), "Reaper")


  def add(src: ComponentRef, dest: ComponentRef, data: Class[_]) {
    log.debug("Connecting " + src.ar + " to " + dest.ar + " for type " + data.getSimpleName)
    graph = graph + (src -> ((dest, data) :: graph.getOrElse(src, List[(ComponentRef, Class[_])]())))
    src.ar ! ComponentRegistration(dest.ar, data, dest.name)
  }

  def add(src: ComponentRef, dest: ComponentRef) = (src, dest, classOf[Any])

  def start = instances.map(cr => {
    cr.ar ! StartSignal
    log.debug("Sending start Signal to " + cr.ar)
  })

  /**
   * Send a `StopSignal` to all managed components, giving them the opportunity
   * to run some cleanup.
   */
  def stop = instances.map(cr => {
    cr.ar ! StopSignal
    log.debug("Sending stop Signal to " + cr.ar)
  })

  def createRef(props: ComponentProps, name: String) = {
    instances = new ComponentRef(system.actorOf(props, name), props.clazz, name, this) :: instances
    instances.head
  }

  /**
   * Gracefully stop all managed components.
   * When all stops are successful, we clear the `instances` list.
   *
   * @note This differs from the `stop` in that here, actors get killed for good,
   *       and cannot get restarted.
   *
   * @return A future which completes when *all* managed actors have shut down.
   */
  def shutdownManagedActors(timeout: FiniteDuration = 10 seconds): Future[Unit] = {
    import scala.concurrent.ExecutionContext.Implicits.global

    // This allows the user of this function to be notified when shutdown is complete
    val externalPromise = Promise[Unit]()

    implicit val tt = new Timeout(timeout)
    val p: Future[Any] = (reaper ? StartKilling(instances.map(_.ar)))

    p.onSuccess({ case _ =>
      instances = List[ComponentRef]()
      externalPromise.success(Unit)
    })

    externalPromise.future
  }
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
    case y if stopped => log.warning("Received data when stopped " + this.getClass.getSimpleName + " of type " + y.getClass )
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
