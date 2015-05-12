package ch.epfl.ts.test.optimization

import scala.concurrent.duration.DurationLong
import scala.language.postfixOps
import scala.util.Try
import org.junit.runner.RunWith
import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner
import ch.epfl.ts.data.BooleanParameter
import ch.epfl.ts.data.ParameterTrait
import ch.epfl.ts.data.StrategyParameters
import ch.epfl.ts.engine.ForexMarketRules
import ch.epfl.ts.test.ActorTestSuite
import ch.epfl.ts.traders.Trader
import ch.epfl.ts.traders.TraderCompanion
import ch.epfl.ts.optimization.StrategyOptimizer
import scala.util.Failure
import scala.util.Success
import ch.epfl.ts.data.WalletParameter

@RunWith(classOf[JUnitRunner])
class StrategyOptimizerTestSuite extends WordSpec {

  private object MyEasyTrader extends TraderCompanion {
    type ConcreteTrader = MyEasyTrader
    override protected val concreteTraderTag = scala.reflect.classTag[MyEasyTrader]
    
    val FLAG_TO_OPTIMIZE_1 = "FlagToOptimize1"
    val FLAG_TO_OPTIMIZE_2 = "FlagToOptimize2"
    val DUMMY_FLAG = "DummyFlag"
  
    override def strategyRequiredParameters: Map[Key, ParameterTrait] = Map(
        DUMMY_FLAG -> BooleanParameter,
        FLAG_TO_OPTIMIZE_1 -> BooleanParameter,
        FLAG_TO_OPTIMIZE_2 -> BooleanParameter
      )
  }
  
  private class MyEasyTrader(uid: Long, marketIds: List[Long], parameters: StrategyParameters)
      extends Trader(uid, marketIds, parameters) {
    def companion = MyEasyTrader
    
    def receiver = PartialFunction.empty
  }
  
  "A StrategyOptimizer" should {
    val strategyToOptimize = MyEasyTrader
    val parametersToOptimize = Set(
        MyEasyTrader.FLAG_TO_OPTIMIZE_1,
        MyEasyTrader.FLAG_TO_OPTIMIZE_2)
    val otherParameterValues = Map(
        MyEasyTrader.INITIAL_FUNDS -> WalletParameter(WalletParameter.defaultValue),
        MyEasyTrader.DUMMY_FLAG -> BooleanParameter(true))
    
    def make(maxInstances: Int = 50) =
      StrategyOptimizer.generateParameterizations(strategyToOptimize, parametersToOptimize, otherParameterValues, maxInstances)
    
    "generate valid parametizations" in {
      val attempt = Try(make())
      assert(attempt.isSuccess)
      attempt match {
        case Failure(e) => fail(e)
        case Success(parameterizations) => parameterizations.foreach(p => {
          val verification = Try(strategyToOptimize.verifyParameters(p))
          assert(verification.isSuccess)
        })
      }
    }
    
    "generate the right number of parameterizations (even if we allow for more)" in {
      val parameterizations = make(50)
      assert(parameterizations.size === 4)
    }
    
    "allow itself to round up the number of allowed instances if it makes sense" in {
      val parameterizations = make(2)
      assert(parameterizations.size === 4)
    }
    
    "throw an exception if we do not give the right to enough instances" in {
      // Cannot optimize 2 dimensions with a single instance
      val attempt = Try(make(1))
      assert(attempt.isFailure, attempt + " should have failed")
    }
    
    "generate all combinations of parameters to optimize" in {
      def combination(b1: Boolean, b2: Boolean) = {
        val parameters = Map(
        strategyToOptimize.FLAG_TO_OPTIMIZE_1 -> BooleanParameter(b1),
        strategyToOptimize.FLAG_TO_OPTIMIZE_2 -> BooleanParameter(b2)) ++ otherParameterValues
        
        new StrategyParameters(parameters.toList: _*)
      }
      
      val parameterizations = make()
      val expected = Set(combination(true, true), combination(true, false),
                         combination(false, true), combination(false, false))
                         
      assert(expected === parameterizations.toSet)
    }
    
  }
}
