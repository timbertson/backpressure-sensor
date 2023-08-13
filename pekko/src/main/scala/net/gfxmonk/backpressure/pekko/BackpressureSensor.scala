package net.gfxmonk.backpressure.pekko

import _root_.org.apache.pekko.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import _root_.org.apache.pekko.stream.{Attributes, FlowShape, Graph, Inlet, Outlet}
import org.apache.pekko.NotUsed
import com.timgroup.statsd.StatsDClient
import net.gfxmonk.backpressure.internal
import net.gfxmonk.backpressure.internal.{Clock, Logic, StatsClient}
import net.gfxmonk.backpressure.internal.statsd.StatsdImpl

import java.util.concurrent.atomic.AtomicLong

object BackpressureSensor {
  def apply(statsDClient: StatsDClient, sampleRate: Double = 1.0, baseTags: Map[String, String] = Map.empty): BackpressureSensor = {
    new BackpressureSensor(statsDClient, sampleRate, baseTags)
  }

  private [backpressure] def flow[T](clock: Clock, stats: StatsClient) : Graph[FlowShape[T, T], NotUsed] = {
    new PekkoFlow[T](new Logic(clock, stats))
  }

  private class PekkoFlow[T](logic: Logic) extends GraphStage[FlowShape[T, T]] {
    val in = Inlet[T]("in")
    val out = Outlet[T]("out")

    override val shape = FlowShape.of(in, out)

    override def createLogic(attr: Attributes): GraphStageLogic = {
      val upstreamDuration = new AtomicLong(0L)

      new GraphStageLogic(shape) {
        setHandler(in, new InHandler {
          override def onPush(): Unit = {
            val element = grab(in)
            upstreamDuration.set(logic.onWaitComplete())
            push(out, element)
          }
        })

        setHandler(out, new OutHandler {
          override def onPull(): Unit = {
            logic.onBusyComplete(upstreamDuration.get)
            pull(in)
          }
        })
      }
    }
  }
}

class BackpressureSensor private(statsClient: StatsDClient, sampleRate: Double, baseTags: Map[String, String]) {
  def flow[T](metricPrefix: String, tags: Map[String, String] = Map.empty) : Graph[FlowShape[T, T], NotUsed] = {
    val stats = new StatsdImpl(statsClient,
      metricPrefix = metricPrefix,
      tags = baseTags ++ tags,
      sampleRate = sampleRate
    )
    val clockImpl = new internal.Clock {
      override def microsMonotonic(): Long = System.nanoTime() / 1000L
    }
    BackpressureSensor.flow[T](clockImpl, stats)
  }
}
