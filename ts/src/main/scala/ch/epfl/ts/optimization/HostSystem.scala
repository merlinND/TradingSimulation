package ch.epfl.ts.optimization

import akka.actor.Deploy
import akka.actor.Props
import ch.epfl.ts.component.ComponentBuilder
import ch.epfl.ts.component.ComponentRef
import akka.actor.Address
import akka.remote.RemoteScope

/**
 * Abstraction for "something that lets you create components".
 * This is mostly useful in its overriden form:
 * @see {@link RemoteHost}
 * 
 * @param namingPrefix Prefix that will precede the name of each actor on this remote
 *                     Can be left empty if you are sure that there will be no naming conflict
 */
abstract class AnyHost(val namingPrefix: String = "") {
  def actualName(name: String) = namingPrefix match {
    case "" => name
    case _  => namingPrefix + '-' + name
  }
  
  def actorOf(props: Props, name: String)(implicit builder: ComponentBuilder): ComponentRef
}

/**
 * Typical local actor system (not using remote deployment)
 */
class HostActorSystem(namingPrefix: String = "") extends AnyHost(namingPrefix) {
  override def actorOf(props: Props, name: String)(implicit builder: ComponentBuilder): ComponentRef =
    builder.createRef(props, actualName(name))
}

/**
 * @param systemName Name of the actor system being run on this remote
 */
class RemoteHost(val hostname: String, val port: Int,
                 namingPrefix: String = "", val systemName: String = "remote")
                 extends AnyHost(namingPrefix) {
  
  val address = Address("akka.tcp", systemName, hostname, port)
  val deploy = Deploy(scope = RemoteScope(address))
  
  override def actorOf(props: Props, name: String)(implicit builder: ComponentBuilder): ComponentRef = {
    // TODO: use `log.debug`
    println("Creating remotely component " + actualName(name) + " at host " + hostname)
    builder.createRef(props.withDeploy(deploy), actualName(name))
  }
  
  override def toString(): String = systemName + "@" + hostname + ":" + port
}