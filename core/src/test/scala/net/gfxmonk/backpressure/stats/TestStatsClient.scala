package net.gfxmonk.backpressure.stats

import net.gfxmonk.backpressure.internal.{Cause, FloatMetric, IntegerMetric}

import scala.collection.mutable.ListBuffer

class TestStatsClient extends StatsClient {
  private val ints_ = ListBuffer[(IntegerMetric, Cause, Long)]()

  def ints = ints_.toList

  private val floats_ = ListBuffer[(FloatMetric, Cause, Double)]()

  def floats = floats_.toList

  override def measure(metric: IntegerMetric, cause: Cause, value: Long): Unit = {
    ints_.append((metric, cause, value))
  }

  override def measure(metric: FloatMetric, cause: Cause, value: Double): Unit = {
    floats_.append((metric, cause, value))
  }
}

object TestStatsClient {
  val builder: StatsClientBuilder =
    (_, _, _) => new TestStatsClient()
}
