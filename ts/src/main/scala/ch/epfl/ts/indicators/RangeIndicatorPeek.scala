package ch.epfl.ts.indicators

import ch.epfl.ts.component.Component
import ch.epfl.ts.data.OHLC
import scala.collection.mutable.MutableList

class RangeIndicatorPeek(val timePeriod : Int, tolerance : Int) extends Component{
  
  var price : Double = 0.0
  var initializationDone : Boolean = false
  var pricePeriod : MutableList[Double] = MutableList[Double]()
  var resistance : Double = 0.0
  var support : Double = 0.0
  var count : Int = 0
  
  override def receiver = {
    
    case o : OHLC => {
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
        collectPeeks(pricePeriod)
        resistance = getResistance
        support = getSupport
        var ri = RI2(support, resistance, timePeriod)
        send(ri)
        println("RangeIndicator : send a RI2")
      }
    }
    
    case _ => println("RangeIndicator : received unknown ! ")
  }
  var topPeeks : MutableList[Double] = MutableList[Double]()
  var downPeeks : MutableList[Double] = MutableList[Double]()
  
  def collectPeeks(priceList : MutableList[Double]) = {
    for(i <- 1 to priceList.size-1) {
      if( i < priceList.size-1 && priceList(i) > priceList(i-1) && priceList(i+1) < priceList(i)) {
        topPeeks = topPeeks :+ priceList(i)
      }
      else if(i < priceList.size-1 && priceList(i-1) > priceList(i) && priceList(i) > priceList(i+1)) {}
        downPeeks = downPeeks :+ priceList(i)
    }
  }
  
  def getSupport : Double = {
    downPeeks = downPeeks.sorted
    downPeeks.get(tolerance) match {
      case Some(value) => value
      case None => -1.0
    }
  }
  
  def getResistance : Double = {
    topPeeks = topPeeks.sorted
    topPeeks.get(topPeeks.size-tolerance) match {
      case Some(value) => value
      case None => -1.0
    }
  }
}