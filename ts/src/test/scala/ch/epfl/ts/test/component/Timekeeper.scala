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
     within(5 milliseconds) {
       expectMsg(TheTimeIs(base + 1L))
     } 
    }
    
	  "Send messages periodically" in {
		  within(2100 milliseconds) {
        receiveN(8)
		  }
	  }
    
  }
  
}