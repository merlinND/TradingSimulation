package ch.epfl.ts.traders

import ch.epfl.ts.component.Component
import ch.epfl.ts.indicators.RangeIndicator
import ch.epfl.ts.data.MarketAskOrder
import ch.epfl.ts.data.Currency._
import ch.epfl.ts.data.MarketBidOrder



/**
 * This trader will receive support and resistance level form RangeIndicator and send long order if the price break
 * the resistance level and short if the price break the support level above the treshold given in parmameter.
 * TODO once we have access to wallet better to switch to percentage of wallet rather than volume  
 */
class RangeTrader(id : Long, treshold : Int, volume : Double) extends Component {
  
  var currentPrice: Double = 0.0
 
  //TODO : handle this oid...
  var oid: Long = 0;
  val uid = id;
  
  override def receiver = {
    
    case range : RangeIndicator => {
      if(currentPrice < range.support){
        //time to go long, since it is market order why do we need to specify price ? 
        send(MarketAskOrder(oid, uid, System.currentTimeMillis(), USD, CHF, volume, 0))
        oid += 1
      }
        
      if(currentPrice > range.resistance){
        //time to go short
        send(MarketBidOrder(oid, uid, System.currentTimeMillis(), USD, CHF, volume, 0))
        oid += 1
      }
    }
    
    case _ => println("RangeTrader received unknown ... ")
    
  }

}