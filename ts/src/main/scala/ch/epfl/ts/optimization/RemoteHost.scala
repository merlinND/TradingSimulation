package ch.epfl.ts.optimization

import akka.actor.Deploy
import akka.actor.Props
import ch.epfl.ts.component.ComponentBuilder
import ch.epfl.ts.component.ComponentRef
import akka.actor.Address
import akka.remote.RemoteScope

/**
 * @param namingPrefix Prefix that will precede the name of each actor on this remote
 * @param systemName Name of the actor system being run on this remote
 */
class RemoteHost(val hostname: String, val port: Int, val namingPrefix: String, val systemName: String = "remote") {
  val address = Address("akka.tcp", systemName, hostname, port)
  val deploy = Deploy(scope = RemoteScope(address))
  
  def createRemotely(props: Props, name: String)(implicit builder: ComponentBuilder): ComponentRef = {
    // TODO: use `log.debug`
    val actualName = namingPrefix + '-' + name
    println("Creating remotely component " + actualName + " at host " + hostname)
    builder.createRef(props.withDeploy(deploy), actualName)
  }
  
  override def toString(): String = systemName + "@" + hostname + ":" + port
}