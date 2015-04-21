package ch.epfl.ts.example

import ch.epfl.ts.component.ComponentBuilder
import ch.epfl.ts.engine.ForexMarketRules
import ch.epfl.ts.component.fetch.TrueFxFetcher
import ch.epfl.ts.component.persist.DummyPersistor
import ch.epfl.ts.component.fetch.MarketNames
import ch.epfl.ts.engine.MarketFXSimulator
import akka.actor.Props
import ch.epfl.ts.component.utils.BackLoop
import scala.reflect.ClassTag
import ch.epfl.ts.component.fetch.PullFetchComponent
import ch.epfl.ts.data.Quote
import ch.epfl.ts.traders.MarketMaker
import ch.epfl.ts.data.LimitAskOrder
import ch.epfl.ts.data.Transaction
import ch.epfl.ts.data.LimitBidOrder
import ch.epfl.ts.engine.RevenueComputeFX

object MarketMakerExample {
  
  def main(args : Array[String]) {
    val builder = new ComponentBuilder("simpleFX")
    val marketForexId = MarketNames.FOREX_ID

    // ----- Creating actors
    // Fetcher
    val fetcherFx: TrueFxFetcher = new TrueFxFetcher
    val fxQuoteFetcher = builder.createRef(Props(classOf[PullFetchComponent[Quote]], fetcherFx, implicitly[ClassTag[Quote]]), "trueFxFetcher")
    
    // Market
    val rules = new ForexMarketRules()
    val forexMarket = builder.createRef(Props(classOf[MarketFXSimulator], marketForexId, rules), MarketNames.FOREX_NAME)
    
    // Persistor
    val dummyPersistor = new DummyPersistor()
    
    // Backloop
    val backloop = builder.createRef(Props(classOf[BackLoop], marketForexId, dummyPersistor), "backloop")
    
    val id = 123L
    val marketMaker = builder.createRef(Props(classOf[MarketMaker], id), "marketMaker")
    
    
     // Display
    val traderNames = Map(id -> "MarketMaker")
    val display = builder.createRef(Props(classOf[RevenueComputeFX], traderNames), "display")

    // ----- Connecting actors
    fxQuoteFetcher->(Seq(forexMarket, marketMaker), classOf[Quote])

    marketMaker->(forexMarket, classOf[LimitAskOrder], classOf[LimitBidOrder])

    forexMarket->(Seq(backloop, display), classOf[Transaction])
    
    backloop->(marketMaker, classOf[Transaction])

    builder.start
  }

}