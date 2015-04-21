package ch.epfl.ts.traders
import ch.epfl.ts.data.Quote
import ch.epfl.ts.data.LimitAskOrder
import ch.epfl.ts.data.LimitBidOrder
import ch.epfl.ts.data.Currency._
import ch.epfl.ts.component.Component
import ch.epfl.ts.data.Currency
import ch.epfl.ts.data.Transaction



//TODO wait for wallet access before implementing market makers. 



/**
 * A market maker provide liquidity on the market.
 * So our basic market maker will simply post limit order
 * at ask and bid price.
 * Note this market maker plays only on EUR/USD 
 */
class MarketMaker(id : Long ) extends Component {
  
  val NUMBER_OF_MILLI_PER_DAY : Long = 86400000L
  
  var currentBid : Double = 0.0
  var currentAsk : Double = 0.0
  
 
  //TODO which volume to offer ?
  //we need access to volume exchanged last day for example. 
  var volume : Double = 1.0
  
  // This is the previous day volume and we will based our today volume on this. 
  var previousDayVolume = 0.0
  var startTime : Long = 0L
  
  var newDay : Boolean = true
  
  
  val oid : Long = 456L
  
  override def receiver = {
    case q : Quote => {
           
      if (q.whatC == Currency.EUR && q.withC == Currency.USD) {
      
        currentBid = q.bid
        currentAsk = q.ask
      
        println("market maker recieve quote : "+q.bid+", "+q.bid)
        println("market maker send limit ask and bid")
       
      } 
    }
    
    case t : Transaction => {
      
      println("Market Maker receive a transaction")
      if(startTime == 0) {
        startTime = t.timestamp
      }
      
      if(t.timestamp < startTime + NUMBER_OF_MILLI_PER_DAY){
        previousDayVolume += t.volume
      }
      else {
        startTime = 0
        previousDayVolume = 0.0 
        passOrder
      }
    }
    
    case _ => println("MarketMaker receive unknown ! ")
  }
  
  def passOrder = {
    println("market maker send limit ask and bid")
    send(LimitAskOrder(oid, id, System.currentTimeMillis(), EUR, USD, previousDayVolume/10, currentAsk))
    send(LimitBidOrder(oid, id, System.currentTimeMillis(), EUR, USD, previousDayVolume/10, currentBid))
  }
}