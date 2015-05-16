package ch.epfl.ts.example

import scala.io.Source

import ch.epfl.ts.component.ComponentBuilder

/**
 * Class used for a live demo of the project
 */
object DemoExample {
  
  def main(args: Array[String]): Unit = {
    
    val names = Source.fromFile(getClass.getResource("/names.txt").toURI()).getLines().toList
    names foreach println
    
	  implicit val builder = new ComponentBuilder()
    // TODO
    
    builder.system.terminate()
  }
}
