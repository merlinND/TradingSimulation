package utils

import net.liftweb.json._

/**
 * Extends Lift JSON serialization to convert scala Infinity and NaN to a string 
 * since JSON does not have any support for Infinity or NaN 
 */
object DoubleSerializer extends Serializer[Double] {
  def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case d: Double if d.isPosInfinity=> JString("inf")
    case d: Double if d.isNegInfinity => JString("-inf")
    case d: Double if d.isNaN => JString("NaN")
  }

  def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), Double] = {
    sys.error("Not interested.")
  }
}

