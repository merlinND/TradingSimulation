package controllers

import play.api._
import play.api.Play.current
import play.api.libs.iteratee.Iteratee
import play.api.mvc._
import play.api.libs.json.JsValue
import akka.actor.Props
import actors.MessageToJson
import scala.reflect.ClassTag
import ch.epfl.ts.data.OHLC
import ch.epfl.ts.indicators.SMA
import ch.epfl.ts.data.Quote
import ch.epfl.ts.data.Transaction
import ch.epfl.ts.data.Register
import actors.TraderParameters
import actors.GlobalOhlc

import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Props
 

object Application extends Controller {

  def index = Action {
    Ok(views.html.index("hello"))
  }

  def quote = WebSocket.acceptWithActor[String, String] { request =>
    out =>
      Props(classOf[MessageToJson[Quote]], out, implicitly[ClassTag[Quote]])
  }

  def globalOhlc = WebSocket.acceptWithActor[String, String] { request =>
    out => Props(classOf[GlobalOhlc], out)
  }

  def ohlc = WebSocket.acceptWithActor[String, String] { request =>
    out =>
      Props(classOf[MessageToJson[OHLC]], out, implicitly[ClassTag[OHLC]])
  }

  def transaction = WebSocket.acceptWithActor[String, String] { request =>
    out =>
      Props(classOf[MessageToJson[Transaction]], out, implicitly[ClassTag[Transaction]])
  }

  def traderRegistration = WebSocket.acceptWithActor[String, String] { request =>
    out =>
      Props(classOf[MessageToJson[Register]], out, implicitly[ClassTag[Register]])
  }

  def traderParameters = WebSocket.acceptWithActor[String, String] { request =>
    out => Props(classOf[TraderParameters], out)
  }

}