package ch.epfl.ts.component

import scala.language.existentials
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.actorRef2Scala
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

  /** Responsible for sending `PoisonPill`s when we want to terminate the system gracefully */
  private val reaper = system.actorOf(Props(classOf[Reaper]), "Reaper")

  /**
   * Connect `src` to `dest` on the given type of messages
   */
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