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
package influxdbreporter.core.metrics.push

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

import influxdbreporter.core.Tag
import influxdbreporter.core.metrics.Metric._
import influxdbreporter.core.metrics.MetricByTag._
import influxdbreporter.core.metrics.{Metric, MetricByTag, UniquenessTagAppender}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

class DiscreteGauge[T] extends Metric[CodehaleGauge[T]] with UniquenessTagAppender {

  private val metricByTags = new AtomicReference(new ConcurrentLinkedQueue[MetricByTag[CodehaleGauge[T]]]())

  def addValue(value: T, tags: Tag*): Unit = {
    val newMetric = new CodehaleGauge[T] {
      override def getValue: T = value
    }
    metricByTags.get().add(MetricByTag(tags.toList, newMetric))
  }

  override def popMetrics(implicit ec: ExecutionContext): Future[MetricByTags[CodehaleGauge[T]]] = {
    val snapshot = metricByTags.getAndSet(new ConcurrentLinkedQueue[MetricByTag[CodehaleGauge[T]]]())
    Future.successful(mapListByAddingUniqueTagToEachMetric(snapshot.asScala.toList))
  }

}