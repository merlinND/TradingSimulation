package ch.epfl.ts.test.component.fetch

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import ch.epfl.ts.component.fetch.TrueFxFetcher
import ch.epfl.ts.data.Quote
import scala.tools.nsc.interpreter.Power
import ch.epfl.ts.data.Currency
import org.scalatest.WordSpec
import org.scalatest.WordSpecLike
import java.util.Calendar


@RunWith(classOf[JUnitRunner]) 
class TrueFxFetcherTestSuite extends WordSpec {
  
  val fetcher: TrueFxFetcher = new TrueFxFetcher()
  val currencyPairs = List((Currency.EUR, Currency.CHF), (Currency.EUR, Currency.USD))
  val focusedFetcher: TrueFxFetcher = new TrueFxFetcher(currencyPairs)
  val nRequests = 10
  /** Delay between two requests in milliseconds */
  val delay = 250L
  
  val today = Calendar.getInstance.get(Calendar.DAY_OF_WEEK)
  val isWeekend = (today == Calendar.SATURDAY || today == Calendar.SUNDAY)

  if(!isWeekend) {
    "TrueFX API on weekdays" should {
      "be reachable on weekdays" in {
        val fetched = fetcher.fetch()
        assert(fetched.length > 0 || isWeekend)
      }
    
      "allows several consecutive requests" in {
        for(i <- 1 to nRequests) {
        	val fetched = fetcher.fetch()
        	assert(fetched.length > 0)   
          Thread.sleep(delay)
        }
      }
    }
   
    "TrueFXFetcher on weekdays" should {
      "give out all significant digits" in {
        val fetched = fetcher.fetch()
        val significantDigits = 6
        
        def extractStrings(quotes: List[Quote]): List[String] = fetched.flatMap(q => q match {
          case Quote(_, _, _, _, bid, ask) => List(bid.toString(), ask.toString())
        })
        
        /**
         * To verify we are given enough digits, check the last digit
         * is different for the various prices.
         * Although it may report a false negative with extremely low
         * probability, it will always fail if less significant digits
         * are given.
         * Warning Prices are on different scales, we should take care that:
         *   e.g. JPY 128.634  has as many significant digits as CHF 1.06034
         */
        val strings = extractStrings(fetched)
        
        // +1 takes into account the '.' separator, +2 is too much
        assert(strings.exists { s => s.length() == significantDigits + 1 })
        assert(strings.forall { s => s.length() < significantDigits + 2 })
      }
       
      "focus on user-specified currency pairs" in {
        val fetched = focusedFetcher.fetch()
        fetched.map(q => {
          assert(currencyPairs.contains((q.whatC, q.withC)), q + " was not of interest to the user")
        })
      }
      
      "not contain all (valid) currency pairs specified by the user" in {
        val fetched = focusedFetcher.fetch()
        
        assert(fetched.length == currencyPairs.length, "User is interested in " + currencyPairs.length + " currency pairs")
        currencyPairs.map(p => {
          assert(fetched.exists(q => q.whatC == p._1 && q.withC == p._2), "User is interested in " + p + " but it was not mentionned")
        })
      }
    }
  }
}