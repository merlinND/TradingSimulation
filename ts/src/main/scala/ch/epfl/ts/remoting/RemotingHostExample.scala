package ch.epfl.ts.remoting

import scala.collection.mutable.MutableList
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
import scala.reflect.ClassTag
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
 * Responsible for overseeing actors instantiated at worker nodes
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
 * Dummy actor to test Akka remoting
 */
class WorkerActor(hostActor: ActorRef, dummyParam: Int) extends Actor {
  
  println("Worker actor with param " + dummyParam + " now running.")
  hostActor ! WorkerIsLive
  
  override def receive = {
    case s: String => {
      println("WorkerActor received: " + s)
      hostActor ! "Hi there!"
    }
    case m => println("WorkerActor received weird: " + m)
  }
}

object RemotingHostExample {
  
  val availableWorkers = List(
    "ts-1-021qv44y.cloudapp.net",
    //"ts-2.cloudapp.net",
    "ts-3.cloudapp.net",
    //"ts-4.cloudapp.net",
    //"ts-5.cloudapp.net",
    //"ts-6.cloudapp.net",
    "ts-7.cloudapp.net",
    "ts-8.cloudapp.net"
    // TODO: other hosts
  )
  val workerPort = 3333
  // TODO: lookup in configuration
  val workerSystemName = "remote"
  
  /**
   * Get a list of props that need to be created identically on
   * all actor systems: 
   */
  val commonProps = {
    // Fetcher
    val fetcherActor = new TrueFxFetcher
    val fetcher = Props(classOf[PullFetchComponent[Quote]], fetcherActor, implicitly[ClassTag[Quote]])
    
    // Market
    val rules = new ForexMarketRules()
    val market = Props(classOf[MarketFXSimulator], MarketNames.FOREX_ID, rules)
    
    // Printer
    val printer = Props(classOf[Printer], "")
    
    Map(
      'fetcher -> fetcher,
      'market  -> market,
      'printer -> printer
    )
  }
  
  /**
   * For the given remote worker, create the common components
   * and an instance of the trading strategy for each parameter value given.
   * 
   * @param master Supervisor actor to register the worker to
   * @param host Hostname of the remote actor system
   * @param prefix Prefix to use in each of this system's actor names
   * @param parameterValues
   */
  // TODO: replace by actual parameters
  // TODO: do not hardcode strategy
  // TODO: connect actors
  def createRemoteActors(master: ComponentRef, host: String, prefix: String,
                         parameterValues: Iterable[Int])
                        (implicit builder: ComponentBuilder): Unit = {
    val address = Address("akka.tcp", workerSystemName, host, workerPort)
    val deploy = Deploy(scope = RemoteScope(address))
    
    // Common props
    val common = commonProps.map({ case (name, props) =>
      println("Created common prop " + name + " at host " + host)
      (name -> builder.createRef(props.withDeploy(deploy), prefix + name))
    })
    
    // One trader for each parameterization
    for {
      dummyParam <- parameterValues
    } yield {
      val name = prefix +  "-Worker-" + dummyParam
      
      // TODO: evaluator as well
      val trader = builder.createRef(Props(classOf[WorkerActor], master.ar, dummyParam).withDeploy(deploy), name)

      // Register this new trader to the master
      master.ar ! trader
      // Connect this new trader to the required components
      // TODO: support all order types
      trader -> (common('market), classOf[MarketAskOrder], classOf[MarketBidOrder])

      println("Created trader " + name + " at host " + host)
    }
    
    // TODO: connections
//    fxQuoteFetcher -> (Seq(forexMarket, ohlcIndicator), classOf[Quote])
//    trader -> (forexMarket, classOf[MarketAskOrder], classOf[MarketBidOrder])
//    forexMarket -> (display, classOf[Transaction])
//    maCross -> (trader, classOf[SMA])
//    ohlcIndicator -> (maCross, classOf[OHLC])
    common('fetcher) -> (common('printer), classOf[Quote])
    common('market) -> (common('printer), classOf[Transaction])
  }
  
  def main(args: Array[String]): Unit = {
    
    // `akka.remote.netty.tcp.hostname` is specified on a per-machine basis in the `application.conf` file
    val remotingConfig = ConfigFactory.parseString(
"""
akka.actor.provider = "akka.remote.RemoteActorRefProvider"
akka.remote.enabled-transports = ["akka.remote.netty.tcp"]
akka.remote.netty.tcp.port = 3333
akka.actor.serialize-creators = on
""").withFallback(ConfigFactory.load());
    
    implicit val builder = new ComponentBuilder("simpleFX", remotingConfig)
    val master = builder.createRef(Props(classOf[MasterActor]), "MasterActor")
    
    val allParameterValues = (1 to 10)
    val slicedParameters = {
      val n = (allParameterValues.length / availableWorkers.length).ceil.toInt 
      (0 until availableWorkers.length).map(i => allParameterValues.slice(n * i, (n * i) + n))
    }
    
    // Programmatic deployment: master asks workers to instantiate actors
    val remoteActors = for {
      (workerHost, idx) <- availableWorkers.zipWithIndex
    } yield createRemoteActors(master, workerHost, idx.toString(), slicedParameters(idx))
    
    // TODO: handle start and stop
  }
}