package ch.epfl.ts.indicators

import akka.actor.ActorLogging
import akka.actor.Actor
import scala.collection.mutable.MutableList
import ch.epfl.ts.data._

class RsiIndicator(period : Long) extends Actor with ActorLogging {
 
  var U : MutableList[Double] = MutableList[Double]()
  var D : MutableList[Double] = MutableList[Double]()
  
  override def receiver = {
    case ohlc : OHLC => {
      
      
    }
  }
  
  def computeRsi: Double  = {
    100 - 100/(1 + (computeSma(U) / computeSma(D) ))
  }
  
  def computeSma(data : MutableList[Double]) = {
    def auxCompute(period: Long): Double = {
      var sma: Double = 0.0
      values.takeRight(period.toInt).map { o => sma = sma + o.close }
      sma / period
    }
    
  //  SMA(periods.map(p => (p -> auxCompute(p))).toMap)
  }

}