package sparklyr

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Row
import scala.util.Random

object SamplingUtils {
  case class Sample(val priority: Double, val row: Row) extends Ordered[Sample] {
    override def compare(that: Sample): Int = {
      scala.math.signum(priority - that.priority).toInt
    }
  }

  def sampleWithReplacement(
    rdd: RDD[Row],
    weightColumn: String,
    k: Int,
    seed: Long
  ): RDD[Row] = {
    val sc = rdd.context
    if (0 == k) {
      sc.emptyRDD
    } else {
      val mapRDDs = rdd.mapPartitionsWithIndex { (index, iter) =>
        val random = new Random(seed + index)
        val samples = Array.fill[Sample](k)(
          Sample(Double.NegativeInfinity, null)
        )
        for (row <- iter) {
          var weight = row.getAs[Double](weightColumn)
          if (weight > 0)
            Range(0, k).foreach(idx => {
              val replacement = Sample(genSamplePriority(weight, random), row)
              if (samples(idx) < replacement)
                samples(idx) = replacement
            })
        }

        Iterator.single(samples)
      }

      if (0 == mapRDDs.partitions.length) {
        sc.emptyRDD
      } else {
        sc.parallelize(
          mapRDDs.reduce(
            (s1, s2) => {
              Range(0, k).foreach(idx => {
                if (s1(idx) < s2(idx)) s1(idx) = s2(idx)
              })

              s1
            }
          ).map(x => x.row)
        )
      }
    }
  }

  // generate a sampling priority for a row given the sampling weight and
  // source of randomness
  def genSamplePriority(weight: Double, random: Random): Double = {
    scala.math.log(random.nextDouble) / weight
  }
}
