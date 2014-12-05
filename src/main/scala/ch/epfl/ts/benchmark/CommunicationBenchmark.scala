package ch.epfl.ts.benchmark

import java.net.Socket
import java.net.ServerSocket
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.SocketAddress
import java.net.InetAddress
import java.io.BufferedOutputStream
import java.io.PrintStream
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import java.io.ObjectOutputStream
import java.io.ObjectInputStream
import java.io.BufferedInputStream

/**
 * Compares message transferring throughput between Java Sockets (serialized Tuples over Buffered socket) and akka Actors.
 * There is an implementation for Tuple2(Int, Int) and Tuple3(Int, Int, Int). The amount of tuples sent can be defined
 * using the msgQuantity variable. The throughput is computed as the time delta when the first message is sent and the last
 * tuple is received.
 *
 */
object CommunicationBenchmark {

  val msgQuantity = 1000000

  def main(args: Array[String]) {

    /**
     * Tuple2
     */

    /**
     * Java sockets
     */
    //    javaSockets2(msgQuantity)

    /**
     *  akka actors
     */
    //    actorsTuple2(msgQuantity)

    /**
     * triple actors
     */
    tripleActor2(msgQuantity)

    /**
     * Tuple3
     */

    /**
     * Java sockets
     */
    //    javaSockets3(msgQuantity)

    /**
     *  akka actors
     */
    //    actorsTuple3(msgQuantity)

    /**
     * triple actors
     */
    //    tripleActor3(msgQuantity)

  }

  /**
   *
   *
   * Tuples 2
   *
   *
   */

  def generateTuple2(quantity: Int): List[Tuple2[Int, Int]] = {
    println("#####----- Tuples of size 2 -----#####")
    var elemsList: List[Tuple2[Int, Int]] = List()
    for (i <- 1 to quantity) {
      elemsList = new Tuple2(i, i) :: elemsList
    }
    elemsList = (-1, -1) :: elemsList
    elemsList = elemsList.reverse
    println("generated list of " + elemsList.size + " tuples.")
    elemsList
  }

  /**
   * Java Sockets
   */

  def javaSockets2(quantity: Int) = {
    val elemsList = generateTuple2(quantity)
    println("###--- Java Sockets ---###")
    val server = new javaServer2(8765)
    val client = new Socket()
    server.start()
    client.connect(server.server.getLocalSocketAddress)
    val stream = new ObjectOutputStream(new BufferedOutputStream(client.getOutputStream))
    server.startTime = System.currentTimeMillis();
    elemsList.map(a => { stream.writeObject(a) })
    stream.close()
  }

  class javaServer2(port: Int) extends Thread {

    val server = new ServerSocket(port)
    var isRunning = true
    var startTime: Long = 0

    override def run() {

      val worker = server.accept()
      val ois = new ObjectInputStream(new BufferedInputStream(worker.getInputStream))
      var newObject: Any = null
      while ({ newObject = ois.readObject(); (newObject != (-1, -1)) }) {
        //      println("Java server: received: " + newObject)
      }
      println("javaTime: " + (System.currentTimeMillis() - startTime) + "ms")
      this.stop()
    }
  }

  /**
   * akka Actors
   */

  def actorsTuple2(quantity: Int) = {
    val elemsList = generateTuple2(quantity)
    println("###--- Akka actors ---###")

    val system = ActorSystem("CommBenchmark")
    val receiver = system.actorOf(Props(new ReceiverActor2), "receiver")
    val sender = system.actorOf(Props(new SenderActor2(receiver)), "sender")
    sender ! StartTuples2(elemsList)
  }

  case class StartTuples2(tuples: List[Tuple2[Int, Int]])

  class SenderActor2(receiver: ActorRef) extends Actor {
    var startTime: Long = 0
    def receive = {
      case StartTuples2(tuples) => {
        startTime = System.currentTimeMillis()
        tuples.map(x => receiver ! x);
        receiver ! "Stop"
      }

      case endTime: Long => {
        println("akka time: " + (endTime - startTime) + " ms.")
        context.system.shutdown()
      }
    }
  }

  class ReceiverActor2 extends Actor {
    def receive = {
      case Tuple2(a, b) => {
      }
      case "Stop" => sender ! System.currentTimeMillis()
    }
  }

  /**
   * Triple message passing
   */

  def tripleActor2(quantity: Int) = {
    val elemsList = generateTuple2(quantity)
    val system3 = ActorSystem("TripleActor2")
    val receiver3 = system3.actorOf(Props(new ReceiverActor2), "receiver3")
    val middle3 = system3.actorOf(Props(new middleActor2(receiver3)), "middle3")
    val sender3 = system3.actorOf(Props(new SenderActor2(middle3)), "sender3")
    middle3 ! sender3
    sender3 ! StartTuples2(elemsList)
  }

  class middleActor2(dest: ActorRef) extends Actor {
    var source: ActorRef = null
    def receive = {
      case t: Tuple2[Int, Int] => {
        dest ! t
      }
      case "Stop"        => dest ! "Stop"
      case endTime: Long => source ! endTime
      case a: ActorRef   => source = a
    }
  }

  /**
   *
   * Tuple3
   *
   */

  def generateTuple3(quantity: Int): List[Tuple3[Int, Int, Int]] = {
    println("#####----- Tuples of size 3 -----#####")
    var elemsList3: List[Tuple3[Int, Int, Int]] = List()
    for (i <- 1 to quantity) {
      elemsList3 = new Tuple3(i, i, i) :: elemsList3
    }
    elemsList3 = (-1, -1, -1) :: elemsList3
    elemsList3 = elemsList3.reverse
    println("generated list of " + elemsList3.size + " tuples.")
    elemsList3
  }

  /**
   * Java Sockets
   */

  def javaSockets3(quantity: Int) = {
    val elemsList3 = generateTuple3(quantity)
    println("###--- Java Sockets ---###")
    val server = new javaServer3(8765)
    val client = new Socket()
    server.start()
    client.connect(server.server.getLocalSocketAddress)
    val stream = new ObjectOutputStream(new BufferedOutputStream(client.getOutputStream))
    server.startTime = System.currentTimeMillis();
    elemsList3.map(a => { stream.writeObject(a) })
    stream.close()
  }

  class javaServer3(port: Int) extends Thread {

    val server = new ServerSocket(port)
    var isRunning = true
    var startTime: Long = 0

    override def run() {

      val worker = server.accept()
      val ois = new ObjectInputStream(new BufferedInputStream(worker.getInputStream))
      var newObject: Any = null
      while ({ newObject = ois.readObject(); (newObject != (-1, -1, -1)) }) {
        //      println("Java server: received: " + newObject)
      }
      println("javaTime: " + (System.currentTimeMillis() - startTime) + "ms")
      this.stop()
    }
  }

  /**
   * akka Actors
   */

  def actorsTuple3(quantity: Int) = {
    val elemsList = generateTuple3(quantity)
    println("###--- Akka actors ---###")

    val system3 = ActorSystem("CommBenchmark3")
    val receiver3 = system3.actorOf(Props(new ReceiverActor3), "receiver3")
    val sender3 = system3.actorOf(Props(new SenderActor3(receiver3)), "sender3")
    sender3 ! StartTuples3(elemsList)
  }

  case class StartTuples3(tuples: List[Tuple3[Int, Int, Int]])

  class SenderActor3(receiver: ActorRef) extends Actor {
    var startTime: Long = 0
    def receive = {
      case StartTuples3(tuples) => {
        startTime = System.currentTimeMillis()
        tuples.map(x => receiver ! x);
        receiver ! "Stop"
      }

      case endTime: Long => {
        println("akka time: " + (endTime - startTime) + " ms.")
        context.system.shutdown()
      }
    }
  }

  class ReceiverActor3 extends Actor {
    def receive = {
      case Tuple3(a, b, c) => {}
      case "Stop"          => sender ! System.currentTimeMillis()

    }
  }

  /**
   * Triple message passing
   */

  def tripleActor3(quantity: Int) = {
    val elemsList = generateTuple3(quantity)
    val system3 = ActorSystem("TripleActor3")
    val receiver3 = system3.actorOf(Props(new ReceiverActor3), "receiver3")
    val middle3 = system3.actorOf(Props(new middleActor3(receiver3)), "middle3")
    val sender3 = system3.actorOf(Props(new SenderActor3(middle3)), "sender3")
    middle3 ! sender3
    sender3 ! StartTuples3(elemsList)
  }

  class middleActor3(dest: ActorRef) extends Actor {
    var source: ActorRef = null
    def receive = {
      case t: Tuple3[Int, Int, Int] => {
        dest ! t
      }
      case "Stop"        => dest ! "Stop"
      case endTime: Long => source ! endTime
      case a: ActorRef   => source = a
    }
  }

}
