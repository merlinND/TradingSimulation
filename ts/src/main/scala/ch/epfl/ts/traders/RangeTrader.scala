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
import ch.epfl.ts.data.StrategyParameters
import ch.epfl.ts.data.CurrencyPairParameter
import ch.epfl.ts.data.RealNumberParameter
import ch.epfl.ts.data.CoefficientParameter
import akka.actor.Props
import ch.epfl.ts.component.fetch.MarketNames
import ch.epfl.ts.indicators.OhlcIndicator

/**
 * RangeTrader companion object
 */
object RangeTrader extends TraderCompanion {
  type ConcreteTrader = RangeTrader
  override protected val concreteTraderTag = scala.reflect.classTag[RangeTrader]
  
  /** Currencies to trade */
  val SYMBOL = "Symbol"
  /** Volume to trade */
  val VOLUME = "Volume"
  /** Size of the window during which we will send order, expressed in percent of the range total size */
  val ORDER_WINDOW = "OrderWindow"
  
  override def strategyRequiredParameters = Map(
    SYMBOL -> CurrencyPairParameter,
    VOLUME -> RealNumberParameter,
    ORDER_WINDOW -> CoefficientParameter
  )
}

/** 
 * The strategy used by this trader is a classical mean reversion strategy.
 * We define to range the resistance and the support.  
 * The resistance is considered as a ceiling and when prices are close to it we sell since we expect prices to go back to normal
 * The support is considered as a floor ans when pricess are close to it is a ggod time to buy. But note that if prices breaks the
 * support then we liquidate our position. We avoid the risk that prices will crash.
 * @param volume the volume that we want to buy
 */
class RangeTrader(uid : Long, parameters: StrategyParameters)
    extends Trader(uid, parameters) {

  override def companion = RangeTrader
 
  
  val (whatC, withC) = parameters.get[(Currency, Currency)](RangeTrader.SYMBOL)
  val volume = parameters.get[Double](RangeTrader.VOLUME)
  val orderWindow = parameters.get[Double](RangeTrader.ORDER_WINDOW)
  
  var recomputeRange : Boolean = true 
  var resistance : Double = Double.MaxValue
  var support : Double = Double.MinValue
  var rangeSize : Double = 0.0
  var oid: Long = 0;
  var currentPrice: Double = 0.0

  val marketId = MarketNames.FOREX_ID
  val oneMinute : Long = 1000*60
  val ohlcIndicator = context.actorOf(Props(classOf[OhlcIndicator], marketId, (whatC, withC), oneMinute),"ohlcIndicator")
  println(ohlcIndicator.path)
  
  //period of time over which we will force the recomputation of our range (expressed in OHLC)
  val timePeriod = 10
  val tolerance = 1
  val rangeIndicator = context.actorOf(Props(classOf[RangeIndicator], timePeriod, tolerance), "rangeIndicator")
  
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
    
    /**We forward the quote that we receive to
     * ohlcIndicator*/
    case quote : Quote => {
      ohlcIndicator ! quote
    }
    
    /**OHLC we allow us to take decision order by knowing current price
     * we also forward them to rangeIndicator in order to know support and 
     * resistance */
    case ohlc : OHLC => {
      rangeIndicator ! ohlc
      currentPrice = ohlc.close
      println("RangeTrader : received an OHLC")
      println("price : "+ohlc.close+", support : "+support+", resistance : "+resistance)
      
      if(rangeReady) {
        /**
         * We are in the sell window
         */
        if(currentPrice >= resistance - (rangeSize * orderWindow) && holdings > 0.0) {
          send(MarketAskOrder(oid, uid, System.currentTimeMillis(), whatC, withC, volume, -1))
          oid += 1
          holdings = 0.0
          recomputeRange = true 
          log.debug("sell")
          println("sell")
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
          println("buy")
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
          println("panic sell")
        }
        
        /**
         * We are breaking the resistance with no holdings recompute the range
         */
        else if(currentPrice > resistance) {
          recomputeRange = true
          log.debug("resitance broken allow range recomputation")
          println("resitance broken allow range recomputation")
        }
        
        else if(currentPrice < support ){
          log.debug("we break support with no holdings -> recompute range")
          recomputeRange = true
        }
        
        else {
          log.debug("nothing is done")
          println("nothing is done")
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