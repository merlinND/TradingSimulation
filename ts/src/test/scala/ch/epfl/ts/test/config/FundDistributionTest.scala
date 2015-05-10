package ch.epfl.ts.test

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import ch.epfl.ts.config._

@RunWith(classOf[JUnitRunner]) 
class FundDistributionTest extends FunSuite {
  
  test("Local fund distribution test") {
    val fd_USD = new FundsUSA
//    val fd_CHF = new FundsSwitzerland
//    val fd_RUR = new FundsRussia
//    val fd_EUR = new FundsGermany
//    val fd_JPY = new FundsJapan
//    val fd_GBP = new FundsUK
//    val fd_AUD = new FundsAustralia
//    val fd_CAD = new FundsCanada
    
    // 1
    var distrInPercent = List(
        (1, 50.0),
        (2, 50.0))
    var maxVal = distrInPercent.maxBy(_._1)
    var distr = fd_USD.percentListToDistribution(distrInPercent)
    
    var num1s = distr.foldRight(0)((elem, acc) => if (elem == 1) acc + 1 else acc)
    var num2s = distr.foldRight(0)((elem, acc) => if (elem >= 2) acc + 1 else acc)
    
    assert(num1s == 1)
    assert(num2s == 1)
    
    
    // 2
    distrInPercent = List(
        (1, 20.0),
        (2, 20.0),
        (3, 60.0)) // 60 % of the people own more than 2 
    maxVal = distrInPercent.maxBy(_._1)
    distr = fd_USD.percentListToDistribution(distrInPercent)
    
    num1s = distr.foldRight(0)((elem, acc) => if (elem == 1) acc + 1 else acc)
    num2s = distr.foldRight(0)((elem, acc) => if (elem == 2) acc + 1 else acc)
    var num3s = distr.foldRight(0)((elem, acc) => if (elem >= 3) acc + 1 else acc)
    
    assert(num1s == 1)
    assert(num2s == 1)
    assert(num3s == 3)
    
    // 3
    distrInPercent = List(
        (1, 1.0),
        (2, 95.0),
        (3, 4.0)) // 4 % of the people own more than 2 
    maxVal = distrInPercent.maxBy(_._1)
    distr = fd_USD.percentListToDistribution(distrInPercent)
    
    num1s = distr.foldRight(0)((elem, acc) => if (elem == 1) acc + 1 else acc)
    num2s = distr.foldRight(0)((elem, acc) => if (elem == 2) acc + 1 else acc)
    num3s = distr.foldRight(0)((elem, acc) => if (elem >= 3) acc + 1 else acc)
    
    assert(num1s == 1)
    assert(num2s == 95)
    assert(num3s == 4)
    
    // 4
    distrInPercent = List(
        (1, 3.0),
        (2, 93.0),
        (3, 4.0)) // 60 % of the people own more than 2 
    maxVal = distrInPercent.maxBy(_._1)
    distr = fd_USD.percentListToDistribution(distrInPercent)
    
    num1s = distr.foldRight(0)((elem, acc) => if (elem == 1) acc + 1 else acc)
    num2s = distr.foldRight(0)((elem, acc) => if (elem == 2) acc + 1 else acc)
    num3s = distr.foldRight(0)((elem, acc) => if (elem >= 3) acc + 1 else acc)
    
    assert(num1s == 1)
    assert(num2s == 31)
    assert(num3s == 1)
  }  
  
}