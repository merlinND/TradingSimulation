package ch.epfl.ts.test.data

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import ch.epfl.ts.data.Currency

@RunWith(classOf[JUnitRunner])
class CurrencyTestSuite extends FunSuite {

  test("toString and fromString are inverting each other") {
    val currencies = Currency.values;
    currencies.foreach(c => {
      assert(Currency.fromString(c.toString) == c, c + " fromString(toString) should be the same currency")
    })
  }

  test("pairFromString handles different case input") {
    val lowercase = "eurchf"
    val uppercase = "EURCHF"
    val mixedcase = "EurChf"
    assert(Currency.pairFromString(lowercase) == (Currency.EUR, Currency.CHF), "Lower case")
    assert(Currency.pairFromString(uppercase) == (Currency.EUR, Currency.CHF), "Upper case")
    assert(Currency.pairFromString(mixedcase) == (Currency.EUR, Currency.CHF), "Mixed case")
  }
}
