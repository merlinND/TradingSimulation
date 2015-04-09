package ch.epfl.ts.indicators

import ch.epfl.ts.component.Component
import ch.epfl.ts.data.OHLC
import scala.collection.mutable.MutableList
import com.sun.xml.internal.bind.v2.model.annotation.Init

class RI(val support : Double, val resistance : Double, val period : Int)
case class RI2( override val support : Double, override val resistance : Double, override val period : Int ) extends RI(support,resistance,period)
/**
 * This indicator is meant to draw support and resistance range over a given time period and with some given tolerance 
 * 
 */
class RangeIndicator(timePeriod : Int, tolerance : Int) extends Component {
  
  //resistance and support are the two bound on our graph that encapsulate most of our prices
  // resistance will be the period^th highest prices 
  // support will be the period^th lowest prices 
  var support : Double = 0.0
  var resistance : Double = 0.0
  
  var price : Double = 0.0 
  
  //helper variable
  var index : Int = 0
  
  var pricePeriod : MutableList[Double] = MutableList[Double]()
  
  //boolean variable use to detect when the initialization phase is done, when we receive enough info to compute first period
  var initializationDone : Boolean = false
  var count : Int = 0
  
  override def receiver = {
   
    
    
    case o: OHLC => {
      price = o.close
      if(!initializationDone){
        count += 1
        pricePeriod :+ price
        if(count >= timePeriod) {
          initializationDone = true
        }
      }
      else {
        pricePeriod = pricePeriod.tail :+ price
        resistance = kmax(timePeriod, pricePeriod).min
        support = kmin(timePeriod, pricePeriod).max
        var ri = RI2(support, resistance, timePeriod)
        send(ri)
      }
    }
    
    case _ => println("RangeIndicator : received unknown ! ")
  }
  
  
  // helper function that returns the k^th highest value contain into the list
  def kmax(k : Int, list : MutableList[Double]) : List[Double] = {
    
    var result = List[Double]()
    var list2 = list
    var i = 0
    var index = 0
   
    for(i <- 1 to k) {
      result = list2.max :: result 
      index = list2.indexOf(list2.max)
      list2 = list2.dropRight(list2.length - index) ++ list2.drop(index + 1)
    }
    
    return result
  }
  
  
  def kmin(k : Int, list : MutableList[Double]) : List[Double] = {
    var result = List[Double]()
    var list2 = list
    var i = 0
    var index = 0
   
    for(i <- 1 to k) {
      result = list2.min :: result 
      index = list2.indexOf(list2.min)
      list2 = list2.dropRight(list2.length - index) ++ list2.drop(index + 1)
    }
    return result
  }
}