package utils

import net.liftweb.json._

/**
 * Extends Lift JSON serialization to support arbitrary maps 
 * instead of only maps that have strings as keys
 */
object MapSerializer extends Serializer[Map[Any, Any]] {
  def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case m: Map[_, _] => JObject(m.map({
      case (k, v) => JField(
        k match {
          case ks: String => ks
          case ks: Symbol => ks.name
          case ks: Any    => ks.toString
        },
        Extraction.decompose(v))
    }).toList)
  }

  def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), Map[Any, Any]] = {
    sys.error("Not interested.")
  }
}