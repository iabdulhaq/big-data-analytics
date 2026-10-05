package edu.bda.pi

import org.apache.spark.sql.SparkSession

object PiEstimatorV2 extends App {

  val spark = SparkSession.builder()
    .appName("edu.bda.pi-estimator")
    .master("local[*]")
    .getOrCreate()

  val sc = spark.sparkContext
  sc.setLogLevel("ERROR")

  // Just to spawn the first jvm spark process, so when we measure the time spent by spark
  // it is not affected by first job run initializations
  sc.parallelize(1 to 10, 4).count()

  /*
  * Accuracy vs. sample size.
  */
  for (n <- List(1000, 100000, 10000000, 100000000)) {
    val slices = 4 // number of partition
    estimatePi(n, slices)
  }

  /*
  * Effect of partitions. Fix n = 10⁷ and run with slices = 1, 2, 4 and 8
  */
  for (slices <- List(1, 2, 4, 8)) {
    val n = 10000000
    estimatePi(n, slices)
  }

  /*
  * Rewrite with reduce. Replace filter + count with a single map that returns 1 or 0, followed by reduce(_ + _)
  */
  estimatePi(10000000, 4, useFilterCount = false)

  private def estimatePi(n: Int, slices: Int, useFilterCount : Boolean = true): Unit = {
    println("---------------------------------------------------------------------")
    if(!useFilterCount)   println("estimate pi using map-reduce instead of filter-count")

    var inside: Double = 0
    var partitions: Int = 0

    spark.time {
      val points = sc.parallelize(1 to n, slices)

      // TODO 1: map each element to a random point (x, y) in [-1, 1] x [-1, 1]
      //         use: scala.util.random.nextDouble() instead of math.random to fix performance issue
      val xy = points.mapPartitionsWithIndex((partitionIndex, iterator) => {
        val random = new scala.util.Random(partitionIndex.toLong)

        val results = iterator.map(
          p => (random.nextDouble(), random.nextDouble())
        )
        results
      })
      partitions = xy.getNumPartitions // keep partitions numbers for logging purposes

      if(useFilterCount){
        // TODO 2: keep only the points inside the unit circle and count them
        inside = xy.filter(p=>p._1 * p._1 + p._2 * p._2 <= 1).count()
      } else {
        // TODO 3: Replace filter + count with a single map that returns 1 or 0, followed by reduce(_ + _)
        inside = xy.map(p => if (p._1 * p._1 + p._2 * p._2  <= 1) 1 else 0).reduce(_ + _)
      }
    }

    val piEstimate = 4.0 * inside / n

    println(f"n = $n%,d  pi ~ $piEstimate%.6f  error = ${math.abs(piEstimate - math.Pi)}%.6f partitions = $partitions%d")
  }

}