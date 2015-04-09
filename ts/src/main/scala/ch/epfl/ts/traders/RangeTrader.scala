package ch.epfl.ts.traders

import ch.epfl.ts.component.Component
import ch.epfl.ts.indicators.RangeIndicator


/**
 * This trader will receive support and resistance level form RangeIndicator and send long order if the price break
 * the resistance level and short if the price break the support level above the treshold given in parmameter. 
 */
class RangeTrader(treshold : Int) extends Component {
  
  var currentPrice: Double = 0.0
  
  override def receiver = {
    
    case range : RangeIndicator => {
      if(currentPrice < range.support){
        //time to go long
      }
        
      if(currentPrice > range.resistance){
        //time to go short
      }
    }
    
    case _ => println("RangeTrader received unknown ... ")
    
  }

}