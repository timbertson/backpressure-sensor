package net.gfxmonk.backpressure.akka

import _root_.akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import _root_.akka.stream.{Attributes, FlowShape, Graph, Inlet, Outlet}
import akka.NotUsed
import com.timgroup.statsd.StatsDClient
import net.gfxmonk.backpressure.internal
import net.gfxmonk.backpressure.internal.{Clock, Logic, StatsClient, StatsClientBuilder}
import net.gfxmonk.backpressure.internal.statsd.StatsdImpl

import java.util.concurrent.atomic.AtomicLong

object BackpressureSensor {
  def apply(statsClientBuilder: StatsClientBuilder, sampleRate: Double = 1.0, baseTags: Map[String, String] = Map.empty): BackpressureSensor = {
    new BackpressureSensor(statsClientBuilder, sampleRate, baseTags)
  }

  def statsD(statsDClient: StatsDClient, sampleRate: Double = 1.0, baseTags: Map[String, String] = Map.empty): BackpressureSensor = {
    BackpressureSensor(StatsdImpl.builder(statsDClient), sampleRate, baseTags)
  }

  private [backpressure] def flow[T](clock: Clock, stats: StatsClient) : Graph[FlowShape[T, T], NotUsed] = {
    new AkkaFlow[T](new Logic(clock, stats))
  }

  private class AkkaFlow[T](logic: Logic) extends GraphStage[FlowShape[T, T]] {
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

class BackpressureSensor private(statsClientBuilder: StatsClientBuilder, sampleRate: Double, baseTags: Map[String, String]) {
  def flow[T](metricPrefix: String, tags: Map[String, String] = Map.empty) : Graph[FlowShape[T, T], NotUsed] = {
    val clockImpl = new internal.Clock {
      override def microsMonotonic(): Long = System.nanoTime() / 1000L
    }
    BackpressureSensor.flow[T](clockImpl, statsClientBuilder.build(metricPrefix, sampleRate, baseTags ++ tags))
  }
}
