package net.gfxmonk.backpressure.testkit

import net.gfxmonk.backpressure.internal._

import scala.collection.mutable.ListBuffer

private [backpressure] class TestStats extends StatsClient {
  private val ints_ = ListBuffer[(IntegerMetric, Cause, Long)]()

  def ints = ints_.toList

  private val counts_ = ListBuffer[(CountMetric, Long)]()

  def counts = counts_.toList

  private val floats_ = ListBuffer[(FloatMetric, Cause, Double)]()

  def floats = floats_.toList

  override def measure(metric: IntegerMetric, cause: Cause, value: Long): Unit = {
    ints_.append((metric, cause, value))
  }

  override def measure(metric: CountMetric, value: Long): Unit = {
    counts_.append((metric, value))
  }

  override def measure(metric: FloatMetric, cause: Cause, value: Double): Unit = {
    floats_.append((metric, cause, value))
  }
}
