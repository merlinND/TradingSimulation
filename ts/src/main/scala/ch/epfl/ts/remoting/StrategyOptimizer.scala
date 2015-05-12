package ch.epfl.ts.remoting

import ch.epfl.ts.component.Component
import ch.epfl.ts.traders.TraderCompanion
import akka.actor.Props
import ch.epfl.ts.data.Parameter
import ch.epfl.ts.data.StrategyParameters
import ch.epfl.ts.data.StrategyParameters
import ch.epfl.ts.traders.TraderCompanion

object StrategyOptimizer {
	private type GridPoint = Map[String, Parameter]

  /**
   * 
   * @param strategyToOptimize
   * @param parametersToOptimize Set of parameter names (as defined in the TraderCompanion) to optimize on
   * @param otherParameterValues Values for each parameter we are not optimizing on
   * @param maxInstances Maximum number of traders to instantiate
   */
  def generateParameterizations(strategyToOptimize: TraderCompanion,
                                parametersToOptimize: Set[String],
                                otherParameterValues: Map[String, Parameter],
                                maxInstances: Int = 50) = {
  
    // Check that all keys passed actually exist for this strategy
    parametersToOptimize.foreach {
      key => assert(strategyToOptimize.parameters.contains(key), "Strategy " + strategyToOptimize + " doesn't have a parameter " + key)
    }
    
    val dimensions = parametersToOptimize.size    
    val grid = generateGrid(strategyToOptimize, parametersToOptimize, (maxInstances / dimensions.toDouble).floor.toInt)
    val parameterizations = generateParametersWith(grid, otherParameterValues)
    parameterizations.foreach(p => strategyToOptimize.verifyParameters(p))

    parameterizations
  }
  
  private def generateGrid(strategyToOptimize: TraderCompanion, parametersToOptimize: Set[String], nPerDimension: Int): List[GridPoint] = {
    // TODO: find a clever way to balance over parameter space
    // TODO: some dimensions may have very small size, balance out with the other ones
    val axes = parametersToOptimize.map(key => {
      // TODO: randomize somehow, instead of always taking
      val parameter = strategyToOptimize.parameters.get(key).get
      val values = parameter.validValues.take(nPerDimension)
      val parameterInstances: Iterable[Parameter] = values.map((v: parameter.T) => parameter.getInstance(v))
      (key -> parameterInstances.toSet)
    }).toList
    
    subgrid(axes)
  }
  
  // Generate all points of our `dimensions`-dimensional grid
  private def subgrid(l: List[(String, Set[Parameter])]): List[GridPoint]= {
    if(l.isEmpty) List()
    else if(l.length == 1) {
      l.head._2.toList.map(v => Map(l.head._1 -> v))
    }
    else {
      // Enumerate all subgrids from one axis
      val key = l.head._1
      val values = l.head._2
      
      values.toList.flatMap(v => {
        // TODO: there is likely a lot of repeated work, should use dynamic programming?
        subgrid(l.tail).map(m => m + (key -> v))
      })
    }
  }
  
  /**
   * Provided values for the parameters we are *not* optimizing on, 
   * generate and verify actual `StrategyParameters`
   */
  private def generateParametersWith(grid: List[GridPoint], otherParameterValues: Map[String, Parameter]): List[StrategyParameters] = {
    val merged = grid.map(m => m ++ otherParameterValues)
    
    merged.map(m => { new StrategyParameters(m.toList : _*) })
  }
}