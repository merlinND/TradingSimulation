implicit val builder = new ComponentBuilder("example")

// market params
val marketId = 0L
val rules = new MarketRules()

// Create components
val market = builder.createRef(Props(classOf[OrderBookMarketSimulator], marketId, rules), "market")
val sobiTrader = builder.createRef(Props(classOf[SobiTrader], 123L, 3000, 2, 700.0, 50, 100.0, rules), "sobiTrader")

// Connect components
sobiTrader -> (market, classOf[LimitBidOrder], classOf[LimitAskOrder])

builder.start
