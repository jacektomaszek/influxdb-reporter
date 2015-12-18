/*
 * Copyright 2015
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package influxdbreporter.core

import com.codahale.metrics.Gauge
import influxdbreporter.core.collectors.{CollectorOps, MetricCollector}
import influxdbreporter.core.metrics.Metric._
import influxdbreporter.core.metrics._

import scala.collection.concurrent.TrieMap

trait MetricRegistry {

  def register[T](metricName: String, magnet: RegisterMagnet[T]): T

  def unregister(name: String): Unit

  def getMetricsMap: Map[String, (Metric[CodehaleMetric], MetricCollector[CodehaleMetric])]

  def getCodehaleMetricsMap: Map[String, (CodehaleMetric, MetricCollector[CodehaleMetric])]
}

object MetricRegistry {
  def apply(prefix: String): MetricRegistry = new MetricRegistryImpl(prefix)
}

private class MetricRegistryImpl(prefix: String) extends MetricRegistry {

  private val metrics = TrieMap[String, (Object, MetricCollector[CodehaleMetric])]()

  def getMetricsMap: Map[String, (Metric[CodehaleMetric], MetricCollector[CodehaleMetric])] =
    metrics.flatMap {
      case (name, (metric: Metric[_], collector)) =>
        Some((name, (metric.asInstanceOf[Metric[CodehaleMetric]], collector)))
      case _ =>
        None
    }.toMap

  override def getCodehaleMetricsMap: Map[String, (CodehaleMetric, MetricCollector[CodehaleMetric])] =
    metrics.flatMap {
      case (name, (metric: CodehaleMetric, collector)) =>
        Some((name, (metric.asInstanceOf[CodehaleMetric], collector)))
      case _ =>
        None
    }.toMap

  override def register[T](metricName: String, magnet: RegisterMagnet[T]): T = magnet(metricName, this)

  def registerMetricWithCollector[T <: Metric[U], U <: CodehaleMetric](name: String, metric: T, collector: MetricCollector[U]): T = {
    val metricName = createMetricName(name)
    val previouslyRegisteredMetric = metrics.putIfAbsent(
      metricName,
      (metric.asInstanceOf[Metric[CodehaleMetric]], collector.asInstanceOf[MetricCollector[CodehaleMetric]])
    )
    if (previouslyRegisteredMetric.isDefined)
      throw new IllegalArgumentException(s"Can't register metric. There is already defined metric for $metricName")
    else
      metric
  }

  def registerCodehaleMetricWithCollector[T <: CodehaleMetric](name: String, metric: T, collector: MetricCollector[T]): T = {
    val metricName = createMetricName(name)
    val previouslyRegisteredMetric = metrics.putIfAbsent(
      metricName,
      (metric.asInstanceOf[CodehaleMetric], collector.asInstanceOf[MetricCollector[CodehaleMetric]])
    )
    if (previouslyRegisteredMetric.isDefined)
      throw new IllegalArgumentException(s"Can't register metric. There is already defined metric for $metricName")
    else
      metric
  }

  override def unregister(name: String): Unit = {
    metrics.remove(createMetricName(name))
  }

  private def createMetricName(name: String) = s"$prefix.$name"

}

trait RegisterMagnet[T] {

  def apply(metricName: String, registryImpl: MetricRegistryImpl): T
}

object RegisterMagnet {

  import CollectorOps._

  private val registerMagnetForCounters = new RegisterMagnetFromMetric[Counter, CodehaleCounter]
  private val registerMagnetForCodehaleCounters = new RegisterMagnetFromCodehaleMetric[CodehaleCounter]
  private val registerMagnetForHistogram = new RegisterMagnetFromMetric[Histogram, CodehaleHistogram]
  private val registerMagnetForCodehaleHistogram = new RegisterMagnetFromCodehaleMetric[CodehaleHistogram]
  private val registerMagnetForMeter = new RegisterMagnetFromMetric[Meter, CodehaleMeter]
  private val registerMagnetForCodehaleMeter = new RegisterMagnetFromCodehaleMetric[CodehaleMeter]
  private val registerMagnetForTimer = new RegisterMagnetFromMetric[Timer, CodehaleTimer]
  private val registerMagnetForCodehaleTimer = new RegisterMagnetFromCodehaleMetric[CodehaleTimer]

  implicit def influxCounterToRegisterMagnet(counter: Counter): RegisterMagnet[Counter] = {
    registerMagnetForCounters(counter)
  }

  implicit def influxCounterWithCollectorToRegisterMagnet(counterAndCollector: (Counter, MetricCollector[CodehaleCounter])):
  RegisterMagnet[Counter] = {
    val (counter, collector) = counterAndCollector
    registerMagnetForCounters(counter, Some(collector))
  }

  implicit def counterToRegisterMagnet(counter: CodehaleCounter): RegisterMagnet[CodehaleCounter] = {
    registerMagnetForCodehaleCounters(counter)
  }

  implicit def counterWithCollectorToRegisterMagnet(counterAndCollector: (CodehaleCounter, MetricCollector[CodehaleCounter])):
  RegisterMagnet[CodehaleCounter] = {
    val (counter, collector) = counterAndCollector
    registerMagnetForCodehaleCounters(counter, Some(collector))
  }

  implicit def gaugeToRegisterMagnet[T](gauge: Gauge[T]): RegisterMagnet[Gauge[T]] = {
    (new RegisterMagnetFromCodehaleMetric[Gauge[T]]()(CollectorOps.CollectorForGauge[T]))(gauge)
  }

  implicit def gaugeWithCollectorToRegisterMagnet[T](gaugeAndCollector: (Gauge[T], MetricCollector[Gauge[T]])):
  RegisterMagnet[Gauge[T]] = {
    val (gauge, collector) = gaugeAndCollector
    (new RegisterMagnetFromCodehaleMetric[Gauge[T]]()(CollectorOps.CollectorForGauge[T]))(gauge, Some(collector))
  }

  implicit def influxHistogramToRegisterMagnet(histogram: Histogram): RegisterMagnet[Histogram] = {
    registerMagnetForHistogram(histogram)
  }

  implicit def influxHistogramWithCollectorToRegisterMagnet(nameAndHistogramAndCollector: (Histogram, MetricCollector[CodehaleHistogram])):
  RegisterMagnet[Histogram] = {
    val (histogram, collector) = nameAndHistogramAndCollector
    registerMagnetForHistogram(histogram, Some(collector))
  }

  implicit def histogramToRegisterMagnet(histogram: CodehaleHistogram): RegisterMagnet[CodehaleHistogram] = {
    registerMagnetForCodehaleHistogram(histogram)
  }

  implicit def histogramWithCollectorToRegisterMagnet(histogramAndCollector: (CodehaleHistogram, MetricCollector[CodehaleHistogram])):
  RegisterMagnet[CodehaleHistogram] = {
    val (histogram, collector) = histogramAndCollector
    registerMagnetForCodehaleHistogram(histogram, Some(collector))
  }

  implicit def influxMeterToRegisterMagnet(meter: Meter): RegisterMagnet[Meter] = {
    registerMagnetForMeter(meter)
  }

  implicit def influxMeterWithCollectorToRegisterMagnet(meterAndCollector: (Meter, MetricCollector[CodehaleMeter])):
  RegisterMagnet[Meter] = {
    val (meter, collector) = meterAndCollector
    registerMagnetForMeter(meter, Some(collector))
  }

  implicit def meterToRegisterMagnet(meter: CodehaleMeter): RegisterMagnet[CodehaleMeter] = {
    registerMagnetForCodehaleMeter(meter)
  }

  implicit def meterWithCollectorToRegisterMagnet(meterAndCollector: (CodehaleMeter, MetricCollector[CodehaleMeter])):
  RegisterMagnet[CodehaleMeter] = {
    val (meter, collector) = meterAndCollector
    registerMagnetForCodehaleMeter(meter, Some(collector))
  }

  implicit def influxTimerToRegisterMagnet(timer: Timer): RegisterMagnet[Timer] = {
    registerMagnetForTimer(timer)
  }

  implicit def influxTimerWithCollectorToRegisterMagnet(timerAndCollector: (Timer, MetricCollector[CodehaleTimer])):
  RegisterMagnet[Timer] = {
    val (timer, collector) = timerAndCollector
    registerMagnetForTimer(timer, Some(collector))
  }

  implicit def timerToRegisterMagnet(timer: CodehaleTimer): RegisterMagnet[CodehaleTimer] = {
    registerMagnetForCodehaleTimer(timer)
  }

  implicit def timerWithCollectorToRegisterMagnet(timerAndCollector: (CodehaleTimer, MetricCollector[CodehaleTimer])):
  RegisterMagnet[CodehaleTimer] = {
    val (timer, collector) = timerAndCollector
    registerMagnetForCodehaleTimer(timer, Some(collector))
  }

  private class RegisterMagnetFromMetric[U <: Metric[T], T <: CodehaleMetric](implicit co: CollectorOps[T]) {

    def apply(metric: U, collector: Option[MetricCollector[T]] = None): RegisterMagnet[U] =
      new RegisterMagnet[U] {
        override def apply(metricName: String, registryImpl: MetricRegistryImpl): U =
          registryImpl.registerMetricWithCollector(metricName, metric, collector.getOrElse(co.collector))
      }
  }

  private class RegisterMagnetFromCodehaleMetric[T <: CodehaleMetric](implicit co: CollectorOps[T]) {

    def apply(metric: T, collector: Option[MetricCollector[T]] = None): RegisterMagnet[T] =
      new RegisterMagnet[T] {
        override def apply(metricName: String, registryImpl: MetricRegistryImpl): T =
          registryImpl.registerCodehaleMetricWithCollector(metricName, metric, collector.getOrElse(co.collector))
      }
  }

}