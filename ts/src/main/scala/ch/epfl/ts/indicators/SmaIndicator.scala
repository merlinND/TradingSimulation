package ch.epfl.ts.indicators

case class SMA(override val value: Map[Int, Double]) extends MovingAverage(value) with Serializable

/**
 * Simple moving average indicator
 */
class SmaIndicator(periods: List[Int]) extends MaIndicator(periods) {

  def computeMa: SMA = {
    def auxCompute(period: Long): Double = {
      var sma: Double = 0.0
      values.takeRight(period.toInt).map { o => sma = sma + o.close }
      sma / period
    }

    SMA(periods.map(p => (p -> auxCompute(p))).toMap)
  }
}
