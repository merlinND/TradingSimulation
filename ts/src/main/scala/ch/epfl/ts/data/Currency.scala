package ch.epfl.ts.data

/**
 * Enum for Currencies
 */
object Currency extends Serializable{
  type Currency = CurrencyWrapper
  
  // Cryptocurrencies
  val BTC = CurrencyWrapper("btc")
  val LTC = CurrencyWrapper("ltc")

  // Real-life currencies
  val USD = CurrencyWrapper("usd")
  val CHF = CurrencyWrapper("chf")
  val RUR = CurrencyWrapper("rur")
  val EUR = CurrencyWrapper("eur")
  val JPY = CurrencyWrapper("jpy")
  val GBP = CurrencyWrapper("gbp")
  val AUD = CurrencyWrapper("aud")
  val CAD = CurrencyWrapper("cad")

  // Fallback ("default")
  val DEF = CurrencyWrapper("def")

  def values = Seq(BTC, LTC, USD, CHF, RUR, EUR, JPY, GBP, AUD, CAD, DEF)
  
  def fromString(s: String): Currency = {
    this.values.find(v => v.toString().toLowerCase() == s.toLowerCase()) match {
      case Some(currency) => CurrencyWrapper(currency.toString())
      case None => {
        throw new UnsupportedOperationException("Currency " + s + " is not supported.")
      }
    }
  }

  def supportedCurrencies(): Set[Currency] = Set(
    BTC, LTC,
    USD, CHF, RUR, EUR, JPY, GBP, AUD, CAD
  )

  /**
   * Creates a tuple of currencies given a string
   *
   * @param s  Input string of length six, three characters for each currency. Case insensitive.
   *           Example: "EURCHF" returns (Currency.EUR, Currency.CHF)
   */
  def pairFromString(s: String): (Currency, Currency) = {
    ( Currency.fromString(s.slice(0, 3)), Currency.fromString(s.slice(3,6)) )
  }
}

/**
 * Need this (as a top level class) to help serializability
 */
class CurrencyWrapper(val s: String) extends AnyVal with Serializable {
  override def toString() = s
}
object CurrencyWrapper {
  def apply(s: String) = new CurrencyWrapper(s)
}
