package net.gfxmonk.backpressure.fs2

import cats.effect.laws.util.TestContext
import cats.effect.{ContextShift, IO, Timer}
import fs2.Stream
import net.gfxmonk.backpressure.internal.Cause.{Busy, Waiting}
import net.gfxmonk.backpressure.internal.Metric.{Count, Duration, Load, Variance}
import net.gfxmonk.backpressure.testkit.TestStats
import weaver.SimpleIOSuite

import scala.concurrent.duration.DurationInt
import scala.util.Success
import fs2.Chunk

object Fs2BackpressureSensorSpec extends SimpleIOSuite {
  class Ctx() {
    val scheduler = TestContext()
    val stats = new TestStats()
    val cs: ContextShift[IO] = scheduler.contextShift
  }

  pureTest("metrics") {
    val ctx = new Ctx()
    val pipe = BackpressureSensor.pipeChunked[IO, Int](ctx.stats)(implicitly, ctx.scheduler.timer.clock)
    implicit val timer: Timer[IO] = ctx.scheduler.timer
    val future = (ctx.scheduler.contextShift.shift *> Stream(Chunk(1), Chunk(2, 2, 2, 2), Chunk(3))
      .evalMap(i => IO.sleep(i.head.get.micros).as(i))
      .flatMap(chunk => Stream.chunk(chunk))
      .through(pipe)
      .chunks
      .evalMap(i => { val t = i.head.get; IO.sleep((t * t).micros).as(i) })
      .compile.drain
    ).unsafeToFuture()

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
      .and(expect(ctx.stats.counts == List(
        (Count, 1),
        (Count, 4),
        (Count, 1),
      )))
      .and(expect(ctx.stats.floats == List(
        (Load, Busy, 1.0 / (1 + 1)),
        (Load, Busy, 4.0 / (2 + 4)),
        (Load, Busy, 9.0 / (9 + 3))
      )))
  }
}
