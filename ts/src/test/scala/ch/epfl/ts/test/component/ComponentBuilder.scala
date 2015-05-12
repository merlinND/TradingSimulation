package ch.epfl.ts.test.component

import scala.language.postfixOps
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import ch.epfl.ts.test.ActorTestSuite
import akka.actor.Props
import ch.epfl.ts.component.Component
import scala.concurrent.Await
import ch.epfl.ts.component.utils.Printer

@RunWith(classOf[JUnitRunner])
class ComponentBuilderTestSuite extends ActorTestSuite("ComponentBuilderTestSuite") {
  
  "A component builder" should {
    
    "Terminate actors should complete immediatly when there are no managed components" in {
      val components = Set()
      
      assert(builder.instances.toSet === components)
      val f = builder.shutdownManagedActors()
      Await.result(f, 100 milliseconds)
      assert(builder.instances === List())
    }
    
    "Terminate actors and clean its `instances` list when done" in {
      val components = Set(builder.createRef(Props(classOf[Printer], "Printer"), "DummyActor1"),
                           builder.createRef(Props(classOf[Printer], "Printer"), "DummyActor2"),
                           builder.createRef(Props(classOf[Printer], "Printer"), "DummyActor3"))
      
      assert(builder.instances.toSet === components)
      val f = builder.shutdownManagedActors()
      Await.result(f, 3 seconds)
      assert(builder.instances === List())
    }
    
  }
  
}