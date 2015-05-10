package ch.epfl.ts.test.traders

import scala.language.postfixOps
import scala.util.Try
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.pattern.ask
import ch.epfl.ts.component.ComponentBuilder
import ch.epfl.ts.data.StrategyParameters
import ch.epfl.ts.data.StrategyParameters
import ch.epfl.ts.test.ActorTestSuite
import ch.epfl.ts.traders.Arbitrageur
import ch.epfl.ts.traders.MadTrader
import ch.epfl.ts.traders.MovingAverageTrader
import ch.epfl.ts.traders.SimpleTraderWithBroker
import ch.epfl.ts.traders.SobiTrader
import ch.epfl.ts.traders.TraderCompanion
import ch.epfl.ts.traders.TransactionVwapTrader
import ch.epfl.ts.engine.GetTraderParameters
import akka.util.Timeout
import ch.epfl.ts.data.StrategyParameters
import akka.testkit.TestActorRef
import ch.epfl.ts.data.StrategyParameters
import scala.util.Success
import scala.concurrent.Await

@RunWith(classOf[JUnitRunner])
class TraderTestSuite
  extends ActorTestSuite("ConcreteStrategyTest") {
  
  /**
   * Shutdown all actors in between each test
   */
  override def afterEach() = {
    val f = builder.shutdownManagedActors(shutdownTimeout)
    Await.result(f, shutdownTimeout)
    assert(builder.instances === List(), "ComponentBuilder was not cleared correctly after a test had finished!")
  }
  
  /** Simple tests for strategy's parameterization */
  new ConcreteStrategyTestSuite(MadTrader)
  new ConcreteStrategyTestSuite(TransactionVwapTrader)
  new ConcreteStrategyTestSuite(MovingAverageTrader)
  new ConcreteStrategyTestSuite(SimpleTraderWithBroker)
  new ConcreteStrategyTestSuite(Arbitrageur)
  new ConcreteStrategyTestSuite(SobiTrader)

  /**
   * Generic test suite to ensure that concrete trading strategy implementation is not
   * wrong in an obvious way. We check its behavior when instantiated with both
   * legal & illegal parameters.
   */
  class ConcreteStrategyTestSuite(val strategyCompanion: TraderCompanion)
                                  (implicit builder: ComponentBuilder) {

    val marketId = 1L
    val traderId = 42L
    def make(p: StrategyParameters) = {
      // Need to give actors a unique name
      val suffix = System.currentTimeMillis() + (Math.random() * 100000L).toLong
      val name = "TraderBeingTested-" + suffix.toString
      strategyCompanion.getInstance(traderId, List(marketId),p, name)
    }
    val emptyParameters = new StrategyParameters()
    val required = strategyCompanion.requiredParameters
    val optional = strategyCompanion.optionalParameters

    // TODO: test optional parameters

    val requiredDefaultValues = for {
        pair <- required.toSeq
        key = pair._1
        parameter = pair._2.getInstance(pair._2.defaultValue)
      } yield (key, parameter)
    val requiredDefaultParameterization = new StrategyParameters(requiredDefaultValues: _*)
    
    "A " + strategyCompanion.toString() should {
      // ----- Strategies not having any required parameter
      if(required.isEmpty) {
        "should allow instantiation with no parameters" in {
          val attempt = Try(make(emptyParameters))
          assert(attempt.isSuccess, attempt.failed)
        }
      }
      // ----- Strategies with required parameters
      else {
        "not allow instantiation with no parameters" in {
          val attempt = Try(make(emptyParameters))
          assert(attempt.isFailure)
        }

        "should allow instantiation with parameters' default values" in {
          val attempt = Try(make(requiredDefaultParameterization))
          assert(attempt.isSuccess, attempt.failed)
        }
      }
      
      // ----- All strategies
      "should give out its strategy parameters when asked" in {
    	  implicit val timeout = Timeout(500 milliseconds)
        
        val askee = make(requiredDefaultParameterization)
        
        builder.start
        val f = (askee.ar ? GetTraderParameters)
        val p = Await.result(f, timeout.duration)
        //val Success(p: StrategyParameters) = f.value.get
        assert(p === requiredDefaultParameterization)
        builder.stop
      }
    }
    
  }
}
