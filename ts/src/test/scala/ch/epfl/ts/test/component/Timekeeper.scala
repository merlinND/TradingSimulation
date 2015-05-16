package ch.epfl.ts.test.component

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import akka.actor.Props
import ch.epfl.ts.component.utils.Timekeeper
import ch.epfl.ts.data.TheTimeIs
import ch.epfl.ts.test.ActorTestSuite
import ch.epfl.ts.component.Component

@RunWith(classOf[JUnitRunner])
class TimekeeperTestSuite extends ActorTestSuite("TimekeeperTestSuite") {
  
  "A Timekeeper" should {
    val base = 42L;
	  val timePeriod = (250 milliseconds)
	
    val test = system.actorOf(Props(classOf[Timekeeper], testActor, base, timePeriod), "Timekeeper")
    val offset = now
    
    "Send out a first timestamp right away, using the given base" in {
      val timeout = (100 milliseconds)
      within(timeout) {
        val received = expectMsgType[TheTimeIs]
        assert(received.now >= base, "Timestamps should never be smaller than the base timestamp")
        assert(received.now <= base + timeout.toMillis, "Timestamps should never be larger than base + physical time")
      } 
    }
    
	  "Send messages periodically" in {
      val timeout = (2100 milliseconds)
		  within(timeout) {
        val received = receiveN(8).toList
        assert(received.forall { m => m.isInstanceOf[TheTimeIs] })
        val times = received.map { m => m.asInstanceOf[TheTimeIs].now }
        assert(times.sorted === times, "Timestamps should be increasing")
        assert(times.forall { t => t >= base}, "Timestamps should never be smaller than the base timestamp")
        assert(times.forall { t => t <= base + timeout.toMillis }, "Timestamps should never be larger than base + physical time")
        
		  }
	  }
    
  }
  
}