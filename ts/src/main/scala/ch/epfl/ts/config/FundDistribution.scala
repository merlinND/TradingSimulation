package ch.epfl.ts.config

/**
 * Local money distributions.
 * Each country is implemented as a class that inherits from FundDistribution.
 * Statistics are entered in tuples (amount in local currency, percentage of people earning this amount).
 * Internally, a list of integers is created that contains as many entries of
 * a particular amount as the percentage indicates.
 * When calling "get" a sample out of the internal list is returned using a Random object.
 */

trait FundDistribution {
  var bins : List[Int];
  val rand = new java.util.Random();
  
  def percentListToDistribution(binsInPercent: List[(Int, Double)]) : List[Int] = {
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
      (500000, 0.1),
      (1000000, 0.1),
      (1000001, 0.1));
  
  override var bins = percentListToDistribution(binsInPercent);
  
}

class FundsSwitzerland extends FundDistribution {
  // from http://www.bfs.admin.ch/bfs/portal/en/index/themen/03/04/blank/key/lohnstruktur/lohnverteilung.html
  val binsInPercent = List(
      (3000, 2.3),
      (4000, 12.6),
      (5000, 23.7),
      (6000, 20.9),
      (7000, 12.9),
      (8000, 8.3),
      (8001, 19.3)).map(x => (x._1 * 12, x._2));
  
  override var bins = percentListToDistribution(binsInPercent);
  
}