package net.gfxmonk.backpressure.fs2

import _root_.fs2.{Pipe, Pull, Stream}
import cats.effect.Sync
import cats.syntax.all._
import com.timgroup.statsd.StatsDClient
import net.gfxmonk.backpressure.internal.statsd.StatsdImpl
import net.gfxmonk.backpressure.internal.{Clock, Logic, StatsClient}

import java.util.concurrent.TimeUnit

object BackpressureSensor {
  def apply(statsDClient: StatsDClient, sampleRate: Double = 1.0, baseTags: Map[String, String] = Map.empty): BackpressureSensor = {
    new BackpressureSensor(statsDClient, sampleRate, baseTags)
  }

  private class LogicImpl[F[_]](underlying: Logic, tick: F[Unit])(implicit F: Sync[F]) {
    def onWaitComplete(chunkSize: Long) = tick.map(_ => underlying.onWaitCompleteSized(chunkSize))
    def onBusyComplete(waitingDuration: Long) = tick.map(_ => underlying.onBusyComplete(waitingDuration))
  }

  private [backpressure] def pipeChunked[F[_], T](stats: StatsClient)(implicit F: Sync[F], C: cats.effect.Clock[F]): Pipe[F, T,T] = {
    input => {
      Stream.eval(LogicImpl(stats)).flatMap { logic =>
        def loop(s: Stream[F, T]): Pull[F, T, Unit] = s.pull.unconsNonEmpty.flatMap {
          case Some((chunk, tail)) => {
            Pull.eval(logic.onWaitComplete(chunk.size.toLong)).flatMap[F, T, Unit] { waitingDuration =>
              Pull.output[F, T](chunk) *>
                Pull.eval(logic.onBusyComplete(waitingDuration)) *>
                loop(tail)
            }
          }
          case None => Pull.done
        }
        loop(input).stream
      }
    }
  }


  private object LogicImpl {
    def apply[F[_]](stats: StatsClient)(implicit F: Sync[F], C: cats.effect.Clock[F]): F[LogicImpl[F]] = {
      var currentTime = 0L
      val clock: Clock = new Clock {
        override def microsMonotonic(): Long = currentTime
      }
      // Hack to provide a non-monadic time: we explicitly tick() the clock and then
      // the underlying clock just returns it
      val tick = C.monotonic(TimeUnit.MICROSECONDS).map { t => currentTime = t }
      tick.as(new LogicImpl(underlying = new Logic(clock, stats), tick))
    }
  }
}

class BackpressureSensor private(statsClient: StatsDClient, sampleRate: Double, baseTags: Map[String, String]) {
  def pipe[F[_], T](metricPrefix: String, tags: Map[String,String] = Map.empty)
    (implicit F: Sync[F], C: cats.effect.Clock[F]) : Pipe[F, T,T] = {
    val stats = new StatsdImpl(statsClient,
      metricPrefix = metricPrefix,
      tags = baseTags ++ tags,
      sampleRate = sampleRate
    )
    BackpressureSensor.pipeChunked(stats)
  }
}
