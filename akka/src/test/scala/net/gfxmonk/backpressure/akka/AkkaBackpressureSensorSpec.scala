package net.gfxmonk.backpressure.akka

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import monix.eval.Task
import net.gfxmonk.backpressure.testkit.BackpressureSpecBase

import scala.concurrent.Future

object AkkaBackpressureSensorSpec extends BackpressureSpecBase {
  override def runStream(ctx: BackpressureSpecBase.Ctx, inputs: List[Int], preSleep: Int => Task[Int], postSleep: Int => Task[Int]): Future[Unit] = {
    val operator = BackpressureSensor.flow[Int](ctx.clock, ctx.stats)

    implicit val system: ActorSystem = ActorSystem("test")

    Source.fromIterator(() => inputs.iterator)
      .mapAsync(1) { time =>
        preSleep(time).runToFuture(ctx.scheduler)
      }
      .via(operator)
      .mapAsync(1) { time =>
        postSleep(time).runToFuture(ctx.scheduler)
      }
      .run().map(_ => ())
  }
}
