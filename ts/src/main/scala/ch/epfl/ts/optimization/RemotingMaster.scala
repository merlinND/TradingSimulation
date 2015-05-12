package ch.epfl.ts.optimization

import scala.collection.mutable.MutableList
import scala.reflect.ClassTag
import com.typesafe.config.ConfigFactory
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Address
import akka.actor.Deploy
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.remote.RemoteScope
import ch.epfl.ts.engine.MarketRules
import ch.epfl.ts.engine.MarketFXSimulator
import ch.epfl.ts.engine.ForexMarketRules
import ch.epfl.ts.component.fetch.TrueFxFetcher
import ch.epfl.ts.component.fetch.PullFetchComponent
import ch.epfl.ts.component.fetch.MarketNames
import ch.epfl.ts.data.Quote
import ch.epfl.ts.component.utils.Printer
import ch.epfl.ts.component.ComponentBuilder
import ch.epfl.ts.component.ComponentRef
import ch.epfl.ts.data.Transaction
import ch.epfl.ts.data.MarketAskOrder
import ch.epfl.ts.data.MarketBidOrder

case object WorkerIsLive

/**
 * Responsible for overseeing actors instantiated at worker nodes. That means it listens
 * to them (i.e. it doesn't send any commands or similar, so far, except for being able to ping them).
 */
class MasterActor extends Actor {

  var workers: MutableList[ActorRef] = MutableList()
  var nAlive = 0

  override def receive = {
    case w: ActorRef => {
      workers += w
    }
    case WorkerIsLive => {
      nAlive += 1
      println("Master sees " + nAlive + " workers available.")
    }
    case s: String => {
      println("MasterActor received string: " + s)
    }
    case m => println("MasterActor received weird: " + m)
  }


  def pingAllWorkers = workers.foreach {
    w => w ! 'Ping
  }
}

/**
 * Runs a main() method that creates a MasterActor and remote WorkerActors
 * by calling createRemoteActors() for every availableWorker. It assumes
 * that there is a RemotingWorker class running and listening on port 3333
 * on every availableWorker.
 */
object RemotingHost {

  val availableWorkers = List(
    "ts-1-021qv44y.cloudapp.net",
    "ts-2.cloudapp.net"
    //"ts-3.cloudapp.net",
    //"ts-4.cloudapp.net",
    //"ts-5.cloudapp.net",
    //"ts-6.cloudapp.net",
    //"ts-7.cloudapp.net",
    //"ts-8.cloudapp.net"
  )
  val workerPort = 3333
  // TODO: lookup in configuration
  val workerSystemName = "remote"

  def main(args: Array[String]): Unit = {

    // `akka.remote.netty.tcp.hostname` is specified on a per-machine basis in the `application.conf` file
    val remotingConfig = ConfigFactory.parseString(
"""
akka.actor.provider = "akka.remote.RemoteActorRefProvider"
akka.remote.enabled-transports = ["akka.remote.netty.tcp"]
akka.remote.netty.tcp.port = 3333
akka.actor.serialize-creators = on
""").withFallback(ConfigFactory.load());

    // Build the supervisor actor
    implicit val builder = new ComponentBuilder("host", remotingConfig)
    val master = builder.createRef(Props(classOf[MasterActor]), "MasterActor")

    // TODO: generate parameterizations
    // TODO: create actors on each host

    builder.start
    // TODO: handle evaluator reports on stop
    // TODO: select the best strategy
  }
}
