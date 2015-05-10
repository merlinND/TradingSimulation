package ch.epfl.ts.config

/**
 * Local money distributions.
 * Each country is implemented as a class that inherits from FundDistribution.
 * Statistics are entered in tuples:
 * _1 : amount in local currency (upper boundary of bins in statistics)
 * _2 : percentage of people earning this amount 
 *
 * Internally, a list of integers is created that contains as many entries of
 * a particular amount as the percentage indicates.
 *
 * The last entry of the list has no upper bound i.e. it indicates how many people
 * earn MORE than this amount. The sampled earnings can therefore be replaced with 
 * any amount larger than indicated here/ 
 *
 * When calling "get" a sample out of the internal list is returned using a Random object.
 */

trait FundDistribution {
  var bins : List[Int];
  val rand = new java.util.Random();
  
  def percentListToDistribution(binsInPercent: List[(Int, Double)]) : List[Int] = {
    
    assert(Math.abs(binsInPercent.foldRight(0.0)((elem, acc) => elem._2 + acc) - 100.0) < 0.001);
    
    val minPercent = binsInPercent.minBy(_._2)._2;
    return binsInPercent.flatMap(x => List.fill(Math.round(x._2 / minPercent).toInt)(x._1));
  }
  
  def get: Int = {
    val len = bins.length;
    val idx = Math.floor(rand.nextDouble() * len).toInt;
    bins(idx);
  }
  
}

class FundsGermany extends FundDistribution {
  // from http://de.statista.com/statistik/daten/studie/202/umfrage/jahreseinkommen-einkommensteuerpflichtiger-2004/
  val binsInPercent = List(
      (2500, 3.2),
      (5000, 2.2),
      (7500, 2.7),
      (10000, 3.7),
      (12500, 4.9),
      (15000, 4.9),
      (20000, 9.4),
      (25000, 9.4),
      (30000, 9.2),
      (37500, 11.9),
      (50000, 13.9),
      (75000, 13.8),
      (100000, 5.4),
      (125000, 2.2),
      (175000, 1.6),
      (250000, 0.8),
      (375000, 0.4),
      (500000, 0.2), // changed from 0.1 to 0.2 to add up to 100 %
      (1000000, 0.1),
      (1000001, 0.1)); // people earning more than this
  
  override var bins = percentListToDistribution(binsInPercent);
  
}

class FundsSwitzerland extends FundDistribution {
  // from http://www.bfs.admin.ch/bfs/portal/en/index/themen/03/04/blank/key/lohnstruktur/lohnverteilung.html
  // - data were given as weekly income (have to multiply by 12)
  val binsInPercent = List(
      (3000, 2.3),
      (4000, 12.6),
      (5000, 23.7),
      (6000, 20.9),
      (7000, 12.9),
      (8000, 8.3),
      (8001, 19.3)).map(x => (x._1 * 12, x._2)); // original data was given as monthly income
  
  override var bins = percentListToDistribution(binsInPercent);
  
}

class FundsUSA extends FundDistribution {
  // from https://introductorystats.wordpress.com
  val binsInPercent = List(
      (10000, 7.8),
      (20000, 12.1),
      (30000, 11.5),
      (40000, 10.2),
      (50000, 8.9),
      (60000, 7.8),
      (70000, 6.7),
      (80000, 5.8),
      (90000, 4.9),
      (100000, 3.9),
      (110000, 3.6),
      (120000, 2.7),
      (130000, 2.3),
      (140000, 1.9),
      (150000, 1.5),
      (160000, 1.4),
      (170000, 1.0),
      (180000, 0.9),
      (190000, 0.7),
      (200000, 0.5),
      (250000, 1.8),
      (250001, 2.1)); // changed from 2.5 to 2.1 to add up to 100 %
  
  override var bins = percentListToDistribution(binsInPercent);
  
}

class FundsJapan extends FundDistribution {
  // from http://nbakki.hatenablog.com/entry/2014/07/17/184742
  val binsInPercent = List(
      (100000, 6.2),
      (200000, 13.2),
      (300000, 13.3),
      (400000, 13.2),
      (500000, 11.0),
      (600000, 9.0),
      (700000, 7.3),
      (800000, 6.5),
      (900000, 5.2),
      (1000000, 3.8),
      (1100000, 3.0),
      (1200000, 2.0),
      (1300000, 1.5),
      (1400000, 1.1),
      (1500000, 0.9),
      (1600000, 0.6),
      (1700000, 0.5),
      (1800000, 0.3),
      (1900000, 0.2),
      (2000000, 0.2),
      (2000001, 1.0)); // people earning more than this
  
  override var bins = percentListToDistribution(binsInPercent);
  
}

class FundsRussia extends FundDistribution {
  // from http://www.gks.ru/bgd/regl/b12_110/Main.htm (first blue arrow, first file)
  // alternatively in USD http://www.forbes.com/sites/markadomanis/2012/09/10/what-is-the-russian-middle-class-probably-not-what-you-think/
  // - data were given as monthly income (have to multiply by 12)
  val binsInPercent = List(
      (5000, 7.3),
      (7000, 8.2),
      (10000, 13.5),
      (14000, 16.3),
      (19000, 15.6),
      (27000, 15.9),
      (45000, 15.0),
      (45001, 8.2)).map(x => (x._1 * 12, x._2)); // people earning more than this
  
  override var bins = percentListToDistribution(binsInPercent);
  
}

class FundsCanada extends FundDistribution {
  // from http://www.statcan.gc.ca/tables-tableaux/sum-som/l01/cst01/famil105a-eng.htm
  // source data were given in the form "total of xxx and over, number of people" and converted accordingly
  val binsInPercent = List(
//      (0, 1932690.0),
//      (5000, 23864820.0),
//      (10000, 22058710.0),
//      (15000, 19712100.0),
//      (20000, 17173030.0),
//      (25000, 15058920.0),
//      (35000, 11733230.0),
//      (50000, 7597110.0),
//      (75000, 3681450.0),
//      (100000, 1788000.0),
//      (150000, 619050.0),
//      (200000, 312930.0),
//      (250000, 192250.0)); // people earning more than this
      (5000, 1932690),
      (10000, 1806110),
      (15000, 2346610),
      (20000, 2539070),
      (25000, 2114110),
      (35000, 3325690),
      (50000, 4136120),
      (75000, 3915660),
      (100000, 1893450),
      (150000, 1168950),
      (200000, 306120),
      (250000, 120680),
      (250001, 192250)).map(x => (x._1, x._2 * 100 / 25797510.0)); // people earning more than this
  
  override var bins = percentListToDistribution(binsInPercent);
  
}

class FundsUK extends FundDistribution {
  // from http://en.wikipedia.org/wiki/Income_in_the_United_Kingdom
  val binsInPercent = List(
      (5000, 3.0),
      (10000, 7.0),
      (15000, 21.0),
      (20000, 19.0),
      (25000, 16.0),
      (30000, 11.0),
      (35000, 8.0),
      (40000, 5.0),
      (45000, 3.0),
      (50000, 2.0),
      (60000, 2.0),
      (75000, 2.0),
      (75001, 1.0)); // people earning more than this
  
  override var bins = percentListToDistribution(binsInPercent);
  
}

class FundsAustralia extends FundDistribution {
  // from http://profile.id.com.au/australia/individual-income
  // - data were given as weekly income (have to multiply by 52)
  // - negative/NIL income was added to the lowest income bin
  // - not stated was added to the highest income bin
  val binsInPercent = List(
      (200, 7.4 + 8.2),
      (300, 10.4),
      (400, 9.9),
      (600, 11.6),
      (800, 10.4),
      (1000, 8.3),
      (1250, 7.9),
      (1500, 5.5),
      (2000, 6.4),
      (2001, 6.2 + 7.8)).map(x => (x._1 * 52, x._2)); // people earning more than this
  
  override var bins = percentListToDistribution(binsInPercent);
  
}