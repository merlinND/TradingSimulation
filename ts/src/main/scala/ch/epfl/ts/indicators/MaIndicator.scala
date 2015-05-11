package ch.epfl.ts.indicators

import ch.epfl.ts.component.Component
import ch.epfl.ts.data.OHLC
import ch.epfl.ts.data.Quote
import scala.collection.mutable.MutableList
import akka.actor.Actor
import akka.event.Logging
import akka.actor.ActorLogging

/**
 * Moving Average value data
 */
abstract class MovingAverage(val value: Map[Long, Double])

/**
 * Moving average superclass. To implement a moving average indicator,
 * extend this class and implement the computeMa() method.
 * @param periods List of periods (expressed in "Number of OHLC" unit)
 */
abstract class MaIndicator(periods: List[Long]) extends Actor with ActorLogging {

  var values: MutableList[OHLC] = MutableList[OHLC]()
  val sortedPeriods = periods.sorted
  val maxPeriod = sortedPeriods.last

  def receive = {

    case o: OHLC => {
      log.debug("Moving Average Indicator received an OHLC: " + o)
      values += o
      if(values.size == maxPeriod) {
        val ma = computeMa
        sender ! ma
        values = values.tail
      }
    }
    case m => log.debug("MaIndicator: received unknown: " + m)
  }

  /**
   * Compute moving average
   * Needs to be implemented by concrete subclasses
   */
  def computeMa: MovingAverage

}
