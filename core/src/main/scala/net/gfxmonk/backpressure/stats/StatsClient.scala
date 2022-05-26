package net.gfxmonk.backpressure.stats

import net.gfxmonk.backpressure.internal.{Cause, FloatMetric, IntegerMetric}

trait StatsClient {
  def measure(metric: IntegerMetric, cause: Cause, value: Long): Unit
  def measure(metric: FloatMetric, cause: Cause, value: Double): Unit
}

trait StatsClientBuilder {
  def build(metricPrefix: String, sampleRate: Double, baseTags: Map[String, String]): StatsClient
}