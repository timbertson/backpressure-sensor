package net.gfxmonk.backpressure.monix

import _root_.monix.eval.Task
import _root_.monix.execution.{Ack, Scheduler}
import _root_.monix.reactive.Observable.Operator
import _root_.monix.reactive.observers.Subscriber
import com.timgroup.statsd.StatsDClient
import net.gfxmonk.backpressure.internal.{Clock, Logic, StatsClient}
import net.gfxmonk.backpressure.internal.statsd.StatsdImpl

import java.util.concurrent.TimeUnit
import scala.concurrent.Future

object BackpressureSensor {
  def apply(statsDClient: StatsDClient, sampleRate: Double = 1.0): BackpressureSensor = {
    new BackpressureSensor(statsDClient, sampleRate)
  }

  private [backpressure] def operator[T](stats: StatsClient) : Operator[T,T] = {
    subscriber => {
      val clock: Clock = new Clock {
        override def microsMonotonic(): Long = subscriber.scheduler.clockMonotonic(TimeUnit.MICROSECONDS)
      }
      new SubscriberImpl(subscriber, new Logic(clock, stats))
    }
  }

  private class SubscriberImpl[T](underlying: Subscriber[T], logic: Logic) extends Subscriber[T] {
    override def onNext(elem: T): Future[Ack] = {
      val task = for {
        upstreamDuration <- Task(logic.onWaitComplete())
        response <- Task.fromFuture(underlying.onNext(elem))
        _ <- Task(logic.onBusyComplete(upstreamDuration))
      } yield response
      task.runToFuture(scheduler)
    }

    override def onError(ex: Throwable): Unit = underlying.onError(ex)

    override def onComplete(): Unit = underlying.onComplete()

    override implicit def scheduler: Scheduler = underlying.scheduler
  }
}

class BackpressureSensor private(statsClient: StatsDClient, sampleRate: Double) {

  def operator[T](metricPrefix: String, tags: Map[String,String] = Map.empty) : Operator[T,T] = {
    val stats = new StatsdImpl(statsClient,
      metricPrefix = metricPrefix,
      tags = tags,
      sampleRate = sampleRate
    )
    BackpressureSensor.operator(stats)
  }
}
