package ch.epfl.ts.indicators

import ch.epfl.ts.component.Component
import ch.epfl.ts.data.OHLC
import scala.collection.mutable.MutableList
import akka.actor.Actor
import akka.actor.ActorLogging

case class RangeIndic(val support : Double, val resistance : Double, val period : Int ) 

/**
 * This indicator will define a range that contains most of the prices in the given period.
 * A range is defined by two value a resistance that can be seen as the ceiling and a support which can be seen as a floor.
 * 
 * Note : tolerance should be set to 1 to have the classical range trading strategy. However if you want x
 * to have a more aggressive strategy you can increase the tolerance. 
 *
 *  @param tolerance the number of extremum value we discarded set to 1 to have the "classical" range.
 */
class RangeIndicator(timePeriod : Int, tolerance : Int) extends Actor with ActorLogging{
  
  // resistance will be the period^th highest prices  
  var support : Double = 0.0
  
   // support will be the period^th lowest prices
  var resistance : Double = 0.0
  
  var price : Double = 0.0 
 
  var pricesInPeriod : MutableList[Double] = MutableList[Double]()
  
  //boolean variable use to detect when the initialization phase is done, when we receive enough info to compute first period
  var initializationDone : Boolean = false
  
  /**
   * We need to make sure that we receive enough ohlc before computing the ranges.
   */
  var countOHLC : Int = 0
  
  override def receive = {

    case o: OHLC => {
      price = o.close
      if(!initializationDone){
        countOHLC += 1
        pricesInPeriod = pricesInPeriod :+ price
        if(countOHLC == timePeriod) {
          initializationDone = true
        }
      }
      
      else {
        pricesInPeriod = pricesInPeriod.tail :+ price
        resistance = getResistance(pricesInPeriod)
        support = getSupport(pricesInPeriod)
        var rangeMessage = RangeIndic(support, resistance, timePeriod)
        sender() ! rangeMessage
      }
    }
    
    case somethingElse => log.debug("RangeIndicator : received unknown :  "+somethingElse)
  }
  
  /** 
   *  @param list the list of prices of size timePeriod
   *  @return the support for list of prices and the tolerance
   */
  def getSupport(list : MutableList[Double]) : Double = {
 
   var sortedPrices : MutableList[Double] = list.sorted
   sortedPrices.get(tolerance) match {
     case Some(value) => value
     case None => -1.0
   }
  } 
  /**
   * @param list the list of prices of size timePeriod
   * @return the support for list of prices and the tolerance
   */
  def getResistance(list : MutableList[Double]) : Double = {
    
    var sortedPrices : MutableList[Double] = list.sorted
    sortedPrices.get(sortedPrices.size - tolerance) match {
     case Some(value) => value
     case None => -1.0
   }
  }
}