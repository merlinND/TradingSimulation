package ch.epfl.ts.traders

import ch.epfl.ts.component.StartSignal
import ch.epfl.ts.component.Component
import scala.concurrent.duration.DurationInt
import ch.epfl.ts.data.{MarketAskOrder, MarketBidOrder}
import ch.epfl.ts.data.Currency._

case class SendMarketOrder()

class SimpleTrader(intervalMillis: Int, orderVolume: Double) extends Component {
  import context._

  val uid = 132
  var orderId = 4567
  val initDelayMillis = 10000

  var alternate = 0

  override def receiver = {
    case StartSignal() => start
    case SendMarketOrder => {
      if (alternate % 2 == 0) {
        println("SimpleTrader: sending market bid order")
        send(new MarketBidOrder(orderId, uid, System.currentTimeMillis(), USD, USD, 50, 0))
      } else {
        println("SimpleTrader: sending market ask order")
        send(new MarketAskOrder(orderId, uid, System.currentTimeMillis(), USD, USD, 0, 50))
      }
      alternate = alternate + 1
      orderId = orderId + 1
    }
    case _ => println("SimpleTrader: received unknown")
  }

  def start = {

    system.scheduler.schedule(initDelayMillis milliseconds, intervalMillis milliseconds, self, SendMarketOrder)
  }

}