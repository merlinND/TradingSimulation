package ch.epfl.ts.component

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.{HashMap => MHashMap}
import scala.concurrent.ExecutionContext.Implicits.global
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
  
  
  /**
   * Supervising actor that waits for Actors termination
   * @see http://letitcrash.com/post/30165507578/shutdown-patterns-in-akka-2
   */
  private class Reaper extends Actor {
    var watched = ArrayBuffer.empty[ActorRef]
    var cb: () => Any = () => Unit
    
    override def receive = {
      case StartKilling(l, onAllDead) => {
        cb = onAllDead
        instances.foreach(c => {
          context.watch(c.ar)
          c.ar ! PoisonPill
          
          watched += c.ar
        })
        
        if(instances.isEmpty) cb()
      }
      
      case Terminated(ref) => {
        watched -= ref
        if(watched.isEmpty) cb()
      }
    }
  }
  /**
   * @param references List of components that need to be watched
   * @param onAllDead Callback function to call when all watched actors have been terminated
   */
  private case class StartKilling(references: List[ComponentRef], onAllDead: () => Any)
  private val reaper = system.actorOf(Props(classOf[Reaper], this), "Reaper")
    
  def add(src: ComponentRef, dest: ComponentRef, data: Class[_]) {
    println("Connecting " + src.ar + " to " + dest.ar + " for type " + data.getSimpleName)
    graph = graph + (src -> ((dest, data) :: graph.getOrElse(src, List[(ComponentRef, Class[_])]())))
    src.ar ! ComponentRegistration(dest.ar, data, dest.name)
  }

  def add(src: ComponentRef, dest: ComponentRef) = (src, dest, classOf[Any])

  def start = instances.map(cr => {
    cr.ar ! StartSignal
    println("Sending start Signal to " + cr.ar)
  })

  /**
   * Send a `StopSignal` to all managed components, giving them the opportunity
   * to run some cleanup.
   */
  def stop = instances.map(cr => {
    cr.ar ! StopSignal
    println("Sending stop Signal to " + cr.ar)
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
  def shutdownManagedActors(timeout: FiniteDuration = 3 seconds): Future[Unit] = {
    val p = Promise[Unit]()
    
    val cb: () => Any = () => {
      instances = List[ComponentRef]()
      p.success(Unit)
    }
    reaper ! StartKilling(instances, cb)
    
    p.future
  }
}

/** Encapsulates [[akka.actor.ActorRef]] to facilitate connection of components
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

trait Receiver extends Actor {
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
      println("Received destination " + this.getClass.getSimpleName + ": from " + ar + " to " + ct.getSimpleName)
    case StartSignal => stopped = false
      start
      println("Received Start " + this.getClass.getSimpleName)
    case StopSignal => context.stop(self)
      stop
      println("Received Stop " + this.getClass.getSimpleName)
    
    case y if stopped => println("Received data when stopped " + this.getClass.getSimpleName + " of type " + y.getClass )
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
