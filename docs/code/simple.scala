implicit val builder = new ComponentBuilder("ReplayFlowTesterSystem")

// Initialize the Interface to DB
val btceXactPersit = new TransactionPersistor("btce-transaction-db")
btceXactPersit.init()

// Configuration object for Replay
val replayConf = new ReplayConfig(1418737788400L, 0.01)

// Create Components
val printer = builder.createRef(Props(classOf[Printer], "printer"), "printer")
val replayer = builder.createRef(Props(classOf[Replay[Transaction]], btceXactPersit, replayConf, implicitly[ClassTag[Transaction]]), "replayer")

// Create the connections
replayer->(printer, classOf[Transaction])

// Start the system
builder.start
