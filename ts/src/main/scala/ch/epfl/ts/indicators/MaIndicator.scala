package ch.epfl.ts.indicators

import ch.epfl.ts.component.Component
import ch.epfl.ts.data.OHLC
import ch.epfl.ts.data.Quote
import scala.collection.mutable.MutableList
import akka.actor.Actor
import akka.event.Logging

/**
 * Moving Average value data
 */
abstract class MovingAverage(val value: Map[Long, Double])

/**
 * Moving average superclass. To implement a moving average indicator,
 * extend this class and implement the computeMa() method.
 */
abstract class MaIndicator(periods: List[Long]) extends Actor {

  var values: MutableList[OHLC] = MutableList[OHLC]()
  val sortedPeriod = periods.sorted
  val maxPeriod = periods.last
  val log = Logging(context.system, this)

  
  def receive = {
    
    case o: OHLC => {
      log.debug("Moving Average Indicator received an olhc")
      println("MaIndicator: received OHLC: " + o)
      values += o
      if (values.size == maxPeriod) {
        val ma = computeMa
        println("MaIndicator: sending " + ma)
        sender() ! ma
        values = values.tail
      }
    } 
    case _ => println("MaIndicator : received unknown")
  }
  
  /**
   * Compute moving average
   */
  def computeMa : MovingAverage

}