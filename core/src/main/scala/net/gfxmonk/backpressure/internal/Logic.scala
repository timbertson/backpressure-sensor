package net.gfxmonk.backpressure.internal

import net.gfxmonk.backpressure.stats.StatsClient

import java.util.concurrent.atomic.AtomicLong

sealed trait Cause
object Cause {
  case object Waiting extends Cause
  case object Busy extends Cause
}

sealed trait IntegerMetric
sealed trait FloatMetric
object Metric {
  case object Duration extends IntegerMetric
  case object Variance extends IntegerMetric
  case object Load extends FloatMetric
}

private [backpressure] trait Clock {
  def microsMonotonic(): Long
}

private [backpressure] class Logic(clock: Clock, stats: StatsClient) {
  private def time() = clock.microsMonotonic()

  private val lastEventTime = new AtomicLong(time())
  private val lastWaitDuration = new AtomicLong(0L)
  private val lastBusyDuration = new AtomicLong(0L)

  private def updateTimestamp(): Long = {
    val now = time()
    val lastTime = lastEventTime.getAndSet(now)
    (now - lastTime)
  }

  private def emitTime(cause: Cause): Long = {
    val diff = updateTimestamp()
    stats.measure(Metric.Duration, cause, diff)
    diff
  }

  def onWaitComplete(): Long = {
    emitTime(Cause.Waiting)
  }

  def onBusyComplete(waitingDuration: Long): Unit = {
    val busyDuration = emitTime(Cause.Busy)
    val waitingVariance = Math.abs(waitingDuration - lastWaitDuration.getAndSet(waitingDuration))
    val busyVariance = Math.abs(busyDuration - lastBusyDuration.getAndSet(busyDuration))
    stats.measure(Metric.Variance, Cause.Waiting, waitingVariance)
    stats.measure(Metric.Variance, Cause.Busy, busyVariance)

    val total = (waitingDuration + busyDuration).toDouble
    if (total > 0) {
      // load metric is "what proportion of time was spent busy" (0-1)
      stats.measure(Metric.Load, Cause.Busy, busyDuration / total)
    }
  }
}
