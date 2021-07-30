package net.gfxmonk.backpressure.monix

import monix.eval.Task
import monix.reactive.Observable
import net.gfxmonk.backpressure.testkit.BackpressureSpecBase

import scala.concurrent.Future

object MonixBackpressureSensorSpec extends BackpressureSpecBase {
  override def runStream(ctx: BackpressureSpecBase.Ctx, inputs: List[Int], preSleep: Int => Task[Int], postSleep: Int => Task[Int]): Future[Unit] = {
    val operator = BackpressureSensor.operator[Int](ctx.stats)
    Observable(1, 2, 3)
      .mapEval(preSleep)
      .liftByOperator(operator)
      .mapEval(postSleep)
      .completedL
      .runToFuture(ctx.scheduler)
  }
}
