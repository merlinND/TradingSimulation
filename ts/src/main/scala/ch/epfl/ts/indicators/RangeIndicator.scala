package ch.epfl.ts.indicators

import ch.epfl.ts.component.Component
import ch.epfl.ts.data.OHLC
import scala.collection.mutable.MutableList

class RI(val support : Double, val resistance : Double, val period : Int)
case class RI2( override val support : Double, override val resistance : Double, override val period : Int ) extends RI(support,resistance,period)
/**
 * This indicator is meant to draw support and resistance range over a given time period and with some given tolerance 
 * Note : not always possible to retrieve an accurate range over large period if there is lot of same prices that appears !
 * @param tolerance represent the number of points that are allowed to be above resp. below the resistance resp. support line 
 */
class RangeIndicator(timePeriod : Int, tolerance : Int) extends Component {
  
  // resistance will be the period^th highest prices  
  var support : Double = 0.0
  
   // support will be the period^th lowest prices
  var resistance : Double = 0.0
  
  var price : Double = 0.0 
 
  var pricePeriod : MutableList[Double] = MutableList[Double]()
  
  //boolean variable use to detect when the initialization phase is done, when we receive enough info to compute first period
  var initializationDone : Boolean = false
  var count : Int = 0
  
  override def receiver = {

    case o: OHLC => {
      price = o.close
      if(!initializationDone){
        count += 1
        pricePeriod = pricePeriod :+ price
        if(count == timePeriod) {
          initializationDone = true
        }
      }
      
      else {
        pricePeriod = pricePeriod.tail :+ price
        resistance = getResistance(pricePeriod)
        support = getSupport(pricePeriod)
        var ri = RI2(support, resistance, timePeriod)
        send(ri)
        println("RangeIndicator : send a RI2")
      }
    }
    
    case _ => println("RangeIndicator : received unknown ! ")
  }
  
  /** helper function that returns the k^th highest or lowest value contain into the list
   *  @param list the list of number over which we are applying our function
   *  @param f the function min or max 
   */
  def getSupport(list : MutableList[Double]) : Double = {
 
   var sortedPrices : MutableList[Double] = list.sorted
   sortedPrices.get(tolerance) match {
     case Some(value) => value
     case None => -1.0
   }
  } 
  
  def getResistance(list : MutableList[Double]) : Double = {
    
    var sortedPrices : MutableList[Double] = list.sorted
    sortedPrices.get(sortedPrices.size - tolerance) match {
     case Some(value) => value
     case None => -1.0
   }
  }
}