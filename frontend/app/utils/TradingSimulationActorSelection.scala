package utils

import com.typesafe.config.ConfigFactory
import akka.actor.ActorContext

class TradingSimulationActorSelection(context: ActorContext, actorSelection: String = "/user/*") {
  val config = ConfigFactory.load()
  val name = config.getString("akka.backend.systemName")
  val hostname = config.getString("akka.backend.hostname")
  val port = config.getString("akka.backend.port")
  val actors = context.actorSelection("akka.tcp://" + name + "@" + hostname + ":" + port + actorSelection)
  
  def get = actors
}