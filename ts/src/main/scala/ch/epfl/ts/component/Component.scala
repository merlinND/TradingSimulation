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
import akka.pattern.ask
import akka.util.Timeout

case object StartSignal
case object StopSignal
case class ComponentRegistration(ar: ActorRef, ct: Class[_], name: String)

/**
 * Supervising actor that waits for Actors termination
 * @see http://letitcrash.com/post/30165507578/shutdown-patterns-in-akka-2
 */
private class Reaper extends Actor with ActorLogging {
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
/**
 * @param references List of components that need to be watched
 * @param onAllDead Callback function to call when all watched actors have been terminated
 */
private case class StartKilling(references: List[ActorRef])

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
  def shutdownManagedActors(timeout: FiniteDuration = 10 seconds): Future[Unit] = {
    val externalPromise = Promise[Unit]()
    
    implicit val tt = new Timeout(timeout)
    //implicit val sender = system.actorSelection("/user").resolveOne()
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
      stopped = true
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
