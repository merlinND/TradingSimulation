package ch.epfl.ts.brokers

import akka.actor.{ ActorLogging, Props, ActorRef }
import akka.pattern.ask
import akka.util.Timeout
import ch.epfl.ts.component.Component
import scala.concurrent.duration._
import scala.language.postfixOps
import ch.epfl.ts.engine.{
  Wallet,
  WalletConfirm,
  FundWallet,
  WalletFunds,
  GetWalletFunds,
  WalletInsufficient,
  ExecutedBidOrder,
  ExecutedAskOrder,
  WalletState,
  AcceptedOrder,
  RejectedOrder
}
import scala.Some
import ch.epfl.ts.data.{ Register, ConfirmRegistration, Order }
import ch.epfl.ts.data.Currency
import scala.collection.mutable.{ HashMap => MHashMap }
import ch.epfl.ts.data.Quote
import ch.epfl.ts.data.MarketBidOrder
import ch.epfl.ts.data.LimitBidOrder
import ch.epfl.ts.data.LimitAskOrder
import ch.epfl.ts.data.MarketShortOrder
import ch.epfl.ts.data.LimitShortOrder
import ch.epfl.ts.data.MarketAskOrder

abstract class Broker extends Component

/**
 */
class StandardBroker extends Broker with ActorLogging {
  import context.dispatcher
  
  /** Trader id -> (reference to the trader, reference to its wallet) */
  var tradersAndWallet = Map[Long, (ActorRef, ActorRef)]()
  
  /** Latest observed trading prices */
  var tradingPrices = MHashMap[(Currency, Currency), (Double, Double)]()

  
  override def receiver: PartialFunction[Any, Unit] = {
    case Register(id) => {
      log.debug("Broker: registration of agent " + id)
      log.debug("with ref: " + sender())
      if (tradersAndWallet.get(id) != None) {
        log.debug("Duplicate Id")
        //TODO(sygi): reply to the trader that registration failed
      }
      else {
        // TODO: why not simply keep an ActorRef? Lookup would cost less
        val newWallet = context.actorOf(Props[Wallet], "wallet" + id)
        tradersAndWallet = tradersAndWallet + (id -> (sender, newWallet))
        sender() ! ConfirmRegistration
      }
    }
    
    case FundWallet(uid, curr, value, allowNegative) => {
      log.debug("Broker: got a request to fund a wallet")
      val replyTo = sender
      tradersAndWallet.get(uid) match {
        case Some((trader, wallet)) => {
          executeForWallet(wallet, FundWallet(uid, curr, value, allowNegative), {
            case WalletConfirm(uid) => {
              log.debug("Broker: Wallet confirmed")
              replyTo ! WalletConfirm(uid)
            }
            case WalletInsufficient(uid) => {
              log.debug("Broker: insufficient funds")
              replyTo ! WalletInsufficient(uid)
            }
          })
        }
        case None => log.warning("Broker doesn't know any Trader with ID " + uid)
      }
      
    }
    
    case GetWalletFunds(uid, ref) => {
      log.debug("Broker: got a get show wallet request")
      val replyTo = sender
      
      tradersAndWallet.get(uid) match {
        case None => log.debug("Broker: someone asks for not - his wallet")
        case Some((_, wallet)) => executeForWallet(wallet, GetWalletFunds(uid, ref), {
            case w: WalletFunds => {
              replyTo ! w
            }
          })
      }
    }

    case e: ExecutedBidOrder =>
      finishExecutedOrder(e, e.whatC, e.volume)

    case e: ExecutedAskOrder =>
      finishExecutedOrder(e, e.withC, e.volume * e.price)

    //TODO(sygi): refactor charging the wallet/placing an order
    case o: Order if !tradersAndWallet.contains(o.chargedTraderId()) => {
      log.warning("Broker doesn't know any Trader with ID " + o.chargedTraderId())
    }
    case o: Order if (ableToProceed(o)) => {
      log.debug("Broker: received order")
      val replyTo = sender

      val uid = o.chargedTraderId()
      val allowShort = o match {
        case _: MarketShortOrder | _: LimitShortOrder => true
        case _                                        => false
      }

      val placementCost = o match {
        case _: MarketBidOrder   => o.volume * tradingPrices(o.whatC, o.withC)._2 // we buy at ask price
        case _: MarketAskOrder   => o.volume
        case _: MarketShortOrder => o.volume
        case _: LimitBidOrder    => o.volume * o.price
        case _: LimitAskOrder    => o.volume
        case _: LimitShortOrder  => o.volume

      }
      val costCurrency = o.costCurrency()
      val orderToSend = o match {
        //converting shortOrders
        case o:MarketShortOrder => MarketAskOrder(o.oid,o.uid,o.timestamp,o.whatC,o.withC,o.volume,o.price)
        case o:LimitShortOrder => LimitAskOrder(o.oid,o.uid,o.timestamp,o.whatC,o.withC,o.volume,o.price)
        case _ => o
      }
      
      val wallet = tradersAndWallet.get(uid).get._2
      executeForWallet(wallet, FundWallet(uid, costCurrency, -placementCost, allowShort), {
        case WalletConfirm(uid) => {
          log.debug("Broker: Wallet confirmed")
          send(orderToSend)
          replyTo ! AcceptedOrder(o) //means: order placed
        }
        case WalletInsufficient(uid) => {
          replyTo ! RejectedOrder(o)
          log.debug("Broker: insufficient funds")
        }
        case _ => log.debug("Unexpected message")
      })
    }
    // Failed case
    case o: Order => {
      log.warning("Broker: Unable to proceed MarketBid request before getting first quote")
      sender ! RejectedOrder.apply(o)
    }

    case q: Quote => {
      tradingPrices((q.whatC, q.withC)) = (q.bid, q.ask)
    }

    case p => log.debug("Broker: received unknown " + p)
  }

  def ableToProceed(o: Order): Boolean = {
    o match {
      case _: MarketBidOrder =>
        tradingPrices.contains((o.whatC, o.withC))
      case _ => true
    }
  }

  // TODO(sygi) - implement it
  //def addToWallet(uid: Long, currency: Currency, amount: Double, messageOnSuccess: Option[Any], messageOnFailure: Option[Any])

  def executeForWallet(wallet: ActorRef, question: WalletState, cb: PartialFunction[Any, Unit]) = {
    implicit val timeout = new Timeout(1000 milliseconds)
    val future = (wallet ? question).mapTo[WalletState]
    future onSuccess cb
    future onFailure {
      case p => log.debug("Broker: Wallet command failed: " + p)
    }
  }

  def finishExecutedOrder(e: Order, currency: Currency, amount: Double){
    // TODO(sygi): create a common subclass for ExecutedOrders
    
    tradersAndWallet.get(e.uid) match {
      case Some((trader, wallet)) => {
        val replyTo = trader
        executeForWallet(wallet, FundWallet(e.uid, currency, amount), {
          case WalletConfirm(uid) => {
            log.debug("Broker: Transaction executed")
            replyTo ! e
          }
          case p => log.debug("Broker: A wallet replied with an unexpected message: " + p)
        })
      }
      case None => log.warning("Broker doesn't know trader with ID " + e.uid)
    }
  }
}
