package net.gfxmonk.backpressure.monix

import monix.eval.Task
import monix.execution.schedulers.TestScheduler
import monix.reactive.Observable
import net.gfxmonk.backpressure.internal.Cause.{Busy, Waiting}
import net.gfxmonk.backpressure.internal.Metric.{Duration, Load, Variance}
import net.gfxmonk.backpressure.internal._
import weaver.monixcompat.SimpleTaskSuite

import java.util.concurrent.TimeUnit
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.DurationInt
import scala.util.Success

object MonixBackpressureSensorSpec extends SimpleTaskSuite {
  class TestStats extends StatsClient {
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

  class Ctx() {
    val scheduler = TestScheduler()
    val stats = new TestStats()
    val clock: Clock = new Clock {
      override def microsMonotonic(): Long = scheduler.clockMonotonic(TimeUnit.MICROSECONDS)
    }
  }

  pureTest("metrics") {
    val ctx = new Ctx()
    val operator = BackpressureSensor.operator[Int](ctx.stats)
    val future = Observable(1, 2, 3)
      .mapEval(i => Task.sleep(i.micros).as(i))
      .liftByOperator(operator)
      .mapEval(i => Task.sleep((i * i).micros).as(i))
      .completedL
      .runToFuture(ctx.scheduler)

    ctx.scheduler.tick(999.days)
    expect(future.value == Some(Success(())))
      .and(expect(ctx.stats.ints.filter(_._1 == Duration) == List(
        // item 1
        (Duration, Waiting, 1),
        (Duration, Busy, 1),

        // item 2
        (Duration, Waiting, 2),
        (Duration, Busy, 4),

        // item 3
        (Duration, Waiting, 3),
        (Duration, Busy, 9),
      )))
      .and(expect(ctx.stats.ints.filter(_._1 == Variance) == List(
        // Beginning -> item 1
        (Variance, Waiting, 1), // 0 -> 1
        (Variance, Busy, 1), // 0 -> 1

        // item 1 -> item 2
        (Variance, Waiting, 1), // 1 -> 2
        (Variance, Busy, 3), // 1 -> 4

        // item 2 -> item 3
        (Variance, Waiting, 1), // 2 -> 3
        (Variance, Busy, 5), // 4 -> 9
      )))
      .and(expect(ctx.stats.floats == List(
        (Load, Busy, 1.0 / (1 + 1)),
        (Load, Busy, 4.0 / (2 + 4)),
        (Load, Busy, 9.0 / (9 + 3))
      )))
  }
}
