package ch.epfl.ts.indicators

import akka.actor.ActorLogging
import akka.actor.Actor
import scala.collection.mutable.MutableList
import ch.epfl.ts.data._

case class RSI(value : Double)

class RsiIndicator(period : Int) extends Actor with ActorLogging {
 
  var previousPrice : Double = 0.0
  var U : MutableList[Double] = MutableList[Double]()
  var D : MutableList[Double] = MutableList[Double]()  
  var countPeriod = period
  var currentPrice : Double = 0.0
  
  override def receive = {
    case ohlc : OHLC => { 
      log.debug("receive OHLC")
      currentPrice = ohlc.close 
      if(previousPrice != 0.0) {
        if(countPeriod == 0){
          U = U.tail
          D = D.tail
          if(currentPrice - previousPrice >= 0) {
            U += (currentPrice - previousPrice) 
            D += 0.0
          }
          else{
            D += -(currentPrice - previousPrice)
            U += 0.0
          } 
        log.debug("send RSI")
        sender() ! RSI(computeRsi)  
        }
          else{
            log.debug("building datas")
            if(currentPrice - previousPrice >= 0) {
              U += currentPrice -previousPrice
              D += 0.0
            }
            else {
              D += -(currentPrice-previousPrice) 
              U += 0.0
            }
            countPeriod = countPeriod - 1
         } 
      }
      previousPrice = currentPrice
    }
    case somethingElse => log.debug("RSI indicator receive the following unknow message "+somethingElse)
  }
  
  def computeRsi: Double  = {
    100 - 100/(1 + (computeSma(U) / computeSma(D) ))
  }
  
  def computeSma(data : MutableList[Double]) = {
      var sma: Double = 0.0
      data.map { o => sma = sma + o }
      sma / period  
  }
}