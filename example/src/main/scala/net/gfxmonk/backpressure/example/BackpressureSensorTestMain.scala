package net.gfxmonk.backpressure.example

import cats.effect.ExitCode
import com.timgroup.statsd.NonBlockingStatsDClientBuilder
import monix.eval.{Task, TaskApp}
import monix.reactive.Observable
import net.gfxmonk.backpressure.monix.BackpressureSensor

import scala.concurrent.duration._
import scala.util.Random

object BackpressureSensorTestMain extends TaskApp {
  override def run(args: List[String]): Task[ExitCode] = {
    Task.parSequenceUnordered(List(
      // Stable, with the same upstream + downstream delay
      runTest("stableEven", upstreamInterval = 100.millis, downstreamInterval = 100.millis),

      // Stable, with downstream taking twice as long as upstream
      runTest("stableBusy", upstreamInterval = 100.millis, downstreamInterval = 200.millis),

      // Reverse of the above
      runTest("stableWait", upstreamInterval = 200.millis, downstreamInterval = 100.millis),

      // Variable downstream
      runTest("variableBusy", upstreamInterval = 100.millis, downstreamInterval = 100.millis, downstreamVariance = 100.millis),

      // Variable upstream
      runTest("variableWait", upstreamInterval = 100.millis, upstreamVariance = 100.millis, downstreamInterval = 100.millis),

      // Variable upstream + downstream
      runTest("variableBoth", upstreamInterval = 100.millis, upstreamVariance = 100.millis, downstreamInterval = 100.millis, downstreamVariance = 100.millis),
    )).as(ExitCode.Success)
  }

  private def runTest[I, O](
    name: String,
    upstreamInterval: FiniteDuration = Duration.Zero,
    upstreamVariance: FiniteDuration = Duration.Zero,
    downstreamInterval: FiniteDuration = Duration.Zero,
    downstreamVariance: FiniteDuration = Duration.Zero,
    numItems: Int = 20000,
  ) =

    for {
      _ <- Task(println(s"start: ${name}"))
      statsd <- buildStatsd
      sensor = BackpressureSensor(statsd)
      _ <- Observable.repeat(())
        .delayOnNext(upstreamInterval)
        .mapEval(delayUpto(upstreamVariance))
        .liftByOperator(sensor.operator("backpressure.sensor.example", Map("pipeline" -> name)))
        .delayOnNext(downstreamInterval)
        .mapEval(delayUpto(downstreamVariance))
        .take(numItems)
        .completedL
      _ <- Task(println(s"finish: ${name}"))
    } yield ()

  private def delayUpto[T](duration: FiniteDuration): T => Task[T] = {
    if (duration == Duration.Zero) {
      Task.pure
    } else { item =>
      for {
        sleepTime <- Task(Random.nextLong(duration.toMicros).micros)
        _ <- Task.sleep(sleepTime)
      } yield item
    }
  }

  private def buildStatsd = Task {
    val host = sys.env.getOrElse("STATSD_HOST", "localhost")
    val port = sys.env.get("STATSD_PORT").map(_.toInt).getOrElse(8125)
    new NonBlockingStatsDClientBuilder()
      .hostname(host).port(port)
      .build()
  }
}
