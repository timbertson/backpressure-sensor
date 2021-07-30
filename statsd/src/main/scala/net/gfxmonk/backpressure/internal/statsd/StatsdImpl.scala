package net.gfxmonk.backpressure.internal.statsd

import com.timgroup.statsd.StatsDClient
import net.gfxmonk.backpressure.internal.{Cause, FloatMetric, IntegerMetric, Metric, StatsClient}

private [backpressure] class StatsdImpl(client: StatsDClient, metricPrefix: String, tags: Map[String, String], sampleRate: Double) extends StatsClient {
  private val histogramMetric = metricPrefix + ".micros"
  private val loadMetric = metricPrefix + ".load"
  private val varianceMetric = metricPrefix + ".variance.micros"

  private def createTagsFor(cause: String): Array[String] = {
    val tagStrings = tags.map { case (k,v) => s"$k:$v" }.toList
    (s"cause:$cause" :: tagStrings).toArray
  }

  private val waitingTags = createTagsFor("waiting")
  private val busyTags = createTagsFor("busy")

  private def tagsFor(cause: Cause) = cause match {
    case Cause.Waiting => waitingTags
    case Cause.Busy => busyTags
  }

  override def measure(metric: IntegerMetric, cause: Cause, value: Long): Unit = {
    metric match {
      case Metric.Duration => client.histogram(histogramMetric, value, sampleRate, tagsFor(cause):_*)
      case Metric.Variance => client.count(varianceMetric, value, sampleRate, tagsFor(cause):_*)
    }
  }

  override def measure(metric: FloatMetric, cause: Cause, value: Double): Unit = {
    metric match {
      case Metric.Load => client.histogram(loadMetric, value, sampleRate, tagsFor(cause):_*)
    }
  }
}

private [backpressure] object StatsdImpl {
  type Builder = (String, Map[String,String]) => StatsdImpl
}
