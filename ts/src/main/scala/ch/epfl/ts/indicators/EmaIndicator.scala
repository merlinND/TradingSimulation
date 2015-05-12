package ch.epfl.ts.indicators

import scala.collection.mutable.MutableList
case class EMA(override val value: Map[Long, Double]) extends MovingAverage(value)

class EmaIndicator(periods: List[Long]) extends MaIndicator(periods: List[Long]) {
  val multipliers = periods.map(p => p -> 2.0 / (p + 1)).toMap
  var previousMas = periods.map(p => (p -> 0.0)).toMap
  var hasStarted = periods.map(p => p -> false).toMap

  def computeMa: EMA = {

    def auxCompute(period: Long): Double = {
      //EMA = SMA at initialization
      if (!hasStarted(period)) {
        var sma: Double = 0.0
        values.takeRight(period.toInt).map { o => sma = sma + o.close }
        sma = sma / period
        previousMas += period -> sma
        hasStarted += period -> true
        sma
      } else {
        val lastPrice = values.last.close
        val previousMa = previousMas(period)
        val ema = multipliers.get(period).get * (lastPrice - previousMa) + previousMa
        previousMas+=period->ema
        ema
      }
    }
    EMA(periods.map(p => (p -> auxCompute(p))).toMap)
  }
}