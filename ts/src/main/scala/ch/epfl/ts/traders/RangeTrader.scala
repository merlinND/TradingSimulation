package ch.epfl.ts.traders

import ch.epfl.ts.component.Component
import ch.epfl.ts.indicators.RangeIndicator
import ch.epfl.ts.data.MarketAskOrder
import ch.epfl.ts.data.Currency._
import ch.epfl.ts.data.MarketBidOrder
import ch.epfl.ts.data.Quote
import ch.epfl.ts.indicators.RI2
import ch.epfl.ts.indicators.RI
import ch.epfl.ts.data.OHLC
import akka.actor.ActorLogging 


/** 
 * The strategy used by this trader is a classical mean reversion strategy. 
 * @param gapSupport the gap needed between the support line and the price to send an order
 * @param gapSupport the gap needed between the resistance line and the price to send an order
 * @param volume the volume that we want to buy
 */
class RangeTrader(id : Long, gapSupport : Double, gapResistance : Double, volume : Double, val symbol : (Currency, Currency)) extends Component 
with ActorLogging{

  var currentPrice: Double = 0.0
  
  var oid: Long = 0;
  val uid = id;
  val (whatC, withC) = symbol
  var recomputeRange : Boolean = true 
  var resistance : Double = Double.MaxValue
  var support : Double = Double.MinValue
  
  /**
   * To make sure that we sell when we actually have something to sell
   * and buy only when we haven't buy yet
   */
  var holdings : Double = 0.0
  var rangeReady : Boolean = false
  
  override def receiver = {
    
    case ohlc : OHLC => {
      currentPrice = ohlc.close
      println("RangeTrader : received an OHLC")
      
      if(rangeReady) {
        
        if(currentPrice < support * (1-gapSupport) && holdings > 0.0){
          send(MarketAskOrder(oid, uid, System.currentTimeMillis(), whatC, withC, volume, -1))
          oid += 1
          holdings = 0.0
          recomputeRange = true
          log.debug("sell")
        }
        
        else if(currentPrice > resistance * (1+gapResistance) && holdings == 0.0) {
          send(MarketBidOrder(oid, uid, System.currentTimeMillis(), whatC, withC, volume, -1))
          oid += 1
          holdings = volume
          recomputeRange = true 
          log.debug("buy")
        }
        else {
          log.debug("nothing is done")
        }
      }
    }
    
    case range : RI2 => {
      log.debug("received range with support = "+range.support+" and resistance = "+range.resistance)
      
      if(recomputeRange) {
        support = range.support
        resistance = range.resistance
        recomputeRange = false
        log.debug("range is updated")
      }
      rangeReady = true
    }
    
    case _ => println("RangeTrader received unknown ... ")
  }
}