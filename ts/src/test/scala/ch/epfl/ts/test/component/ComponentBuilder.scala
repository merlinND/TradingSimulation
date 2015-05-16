package ch.epfl.ts.test.component

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.reflect.ClassTag
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import akka.actor.Props
import ch.epfl.ts.component.fetch.PullFetchComponent
import ch.epfl.ts.component.fetch.TrueFxFetcher
import ch.epfl.ts.component.utils.Printer
import ch.epfl.ts.data.Currency
import ch.epfl.ts.data.Quote
import ch.epfl.ts.data.StrategyParameters
import ch.epfl.ts.data.WalletParameter
import ch.epfl.ts.evaluation.Evaluator
import ch.epfl.ts.test.ActorTestSuite
import ch.epfl.ts.traders.SimpleTraderWithBroker
import ch.epfl.ts.component.ComponentRef
import ch.epfl.ts.component.fetch.FetchingComponent
import ch.epfl.ts.component.fetch.FetchingComponent

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
    
    "Create components under the correct root" in {
      val printer = builder.createRef(Props(classOf[Printer], "Printer"), "printer")
      val fetcher = builder.createRef(Props(classOf[PullFetchComponent[Quote]], new TrueFxFetcher, implicitly[ClassTag[Quote]]), "fetcher")
      
      val traderId = 42L
      val parameters = new StrategyParameters(
        SimpleTraderWithBroker.INITIAL_FUNDS -> WalletParameter(Map(Currency.CHF -> 1000.0))
      )
      val trader = SimpleTraderWithBroker.getInstance(traderId, List(1L), parameters, "trader")
      
      val period = 10 seconds
      val referenceCurrency = Currency.CHF
      val evaluator = builder.createRef(Props(classOf[Evaluator], trader.ar, traderId, trader.name, referenceCurrency, period), "evaluator")

      def testName(root: String, component: ComponentRef) = {
        val protocol = component.ar.path.address.protocol
        assert(component.ar.path.toString() === protocol + "://" + system.name + "/user/" + root + "/" + component.name)
      }
      
      testName("other", printer)
      testName("fetchers", fetcher)
      testName("traders", trader)
      testName("evaluators", evaluator)
    }
  }
  
}