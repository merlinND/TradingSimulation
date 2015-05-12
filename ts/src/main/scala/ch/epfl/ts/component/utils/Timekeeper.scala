package ch.epfl.ts.component.utils

import akka.actor.Actor
import scala.concurrent.duration.FiniteDuration
import ch.epfl.ts.component.StopSignal
import java.util.Timer
import ch.epfl.ts.data.TheTimeIs
import akka.actor.ActorRef

/**
 * When switching from Replay to "full simulation" mode, historical data
 * had timestamps from the past; and then suddenly our simulation must take
 * charge of generating quotes. These new quotes emitted from the simulation
 * can't just jump immediately to the present time. We thus introduce this component
 * to bridge the time gap.
 *  
 * We use the last known time from historical data and add real physical time elapsed from
 * here. Note that physical time will always elapse at 1x speed.
 *  
 * This component is instantiated with a fixed beginning date (timestamp) and will then emit
 * periodically `TheTimeIs` messages.
 * 
 * @param parent The actor to send `TheTimeIs` messages
 * @param timeBase Timestamp (milliseconds) from which to start 
 * @param period Delay between two `TimeIsNow` messages.
 *               Note this also defines the granularity of time in the system.
 */
// TODO: replace the `parent` argument by simply using `context.parent`?
class Timekeeper(val parent: ActorRef, val timeBase: Long, val period: FiniteDuration) extends Actor {
  
  private val timeOffset: Long = System.currentTimeMillis()
  
  private val timer = new Timer()
  private class SendTheTime extends java.util.TimerTask {
    def run() {
      val current = timeBase + (System.currentTimeMillis() - timeOffset)
      parent ! TheTimeIs(current)
    }
  }
  timer.scheduleAtFixedRate(new SendTheTime, 0L, period.toMillis)
  
  def receive = PartialFunction.empty
  
}