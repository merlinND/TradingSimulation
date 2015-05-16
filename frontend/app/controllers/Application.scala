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
import com.typesafe.config.ConfigFactory

object Application extends Controller {
  val config = ConfigFactory.load()

  def index = Action {
    Ok(views.html.index("hello"))
  }

  def quote = WebSocket.acceptWithActor[String, String] { request =>
    out =>
      Props(classOf[MessageToJson[Quote]], out,
        config.getString("akka.backend.fetcherActorSelection"), implicitly[ClassTag[Quote]])
  }

  def globalOhlc = WebSocket.acceptWithActor[String, String] { request =>
    out => Props(classOf[GlobalOhlc], out)
  }

  def transaction = WebSocket.acceptWithActor[String, String] { request =>
    out =>
      Props(classOf[MessageToJson[Transaction]], out, 
          config.getString("akka.backend.marketActorSelection"), implicitly[ClassTag[Transaction]])
  }

  def traderRegistration = WebSocket.acceptWithActor[String, String] { request =>
    out =>
      Props(classOf[MessageToJson[Register]], out,
        config.getString("akka.backend.traderActorSelection"), implicitly[ClassTag[Register]])
  }

  def traderParameters = WebSocket.acceptWithActor[String, String] { request =>
    out => Props(classOf[TraderParameters], out)
  }

}