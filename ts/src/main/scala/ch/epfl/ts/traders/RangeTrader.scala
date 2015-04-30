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
 * We define to range the resistance and the support.  
 * The resistance is considered as a ceiling and when prices are close to it we sell since we expect prices to go back to normal
 * The support is considered as a floor ans when pricess are close to it is a ggod time to buy. But note that if prices breaks the
 * support then we liquidate our position. We avoid the risk that prices will crash. 
 * @param orderWindow the size of the window during which we will send order, expressed in percent of the range total size.
 * @param volume the volume that we want to buy
 */
class RangeTrader(id : Long, orderWindow : Double, volume : Double, val symbol : (Currency, Currency)) extends Component 
with ActorLogging{

  var currentPrice: Double = 0.0
  
  var oid: Long = 0;
  val uid = id;
  val (whatC, withC) = symbol
  var recomputeRange : Boolean = true 
  var resistance : Double = Double.MaxValue
  var support : Double = Double.MinValue
  var rangeSize : Double = 0.0
  
  /**
   * To make sure that we sell when we actually have something to sell
   * and buy only when we haven't buy yet
   */
  var holdings : Double = 0.0
  var rangeReady : Boolean = false
  
  /**
   * When we receive an OHLC we check if the price is in the buying range or in the selling range.
   * If the price break the support we sell (assumption price will crashes)
   */
  override def receiver = {
    
    case ohlc : OHLC => {
      currentPrice = ohlc.close
      println("RangeTrader : received an OHLC")
      
      if(rangeReady) {
        println(currentPrice)
        println(support + (rangeSize * orderWindow))
        println(holdings)
        /**
         * We are in the sell window
         */
        if(currentPrice >= resistance - (rangeSize * orderWindow) && holdings > 0.0) {
          send(MarketAskOrder(oid, uid, System.currentTimeMillis(), whatC, withC, volume, -1))
          oid += 1
          holdings = 0.0
          recomputeRange = true 
          log.debug("sell")
        }
       
        /**
         * We are in the buy window
         */
        
        else if(currentPrice <= support + (rangeSize * orderWindow) && currentPrice > support && holdings == 0.0) {
          send(MarketBidOrder(oid, uid, System.currentTimeMillis(), whatC, withC, volume, -1))
          oid += 1
          holdings = volume
          recomputeRange = false
          log.debug("buy")
        }
        
        /**
         * panic sell, the prices breaks the support
         */
        else if(currentPrice < support && holdings > 0) {
          send(MarketAskOrder(oid, uid, System.currentTimeMillis(), whatC, withC, volume, -1))
          oid += 1
          holdings = 0.0
          recomputeRange = true
          log.debug("panic sell")
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
        rangeSize = resistance - support
        recomputeRange = false
        log.debug("range is updated")
      }
      rangeReady = true
    }
    
    case _ => println("RangeTrader received unknown ... ")
  }
}