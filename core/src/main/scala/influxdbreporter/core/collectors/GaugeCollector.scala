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
package influxdbreporter.core.collectors

import com.codahale.metrics.Gauge
import influxdbreporter.core.{Field, Tag, Writer, WriterData}

class GaugeCollector[T] extends MetricCollector[Gauge[T]] {

  override def collect[U](writer: Writer[U],
                          name: String,
                          metric: Gauge[T],
                          timestamp: Long,
                          tags: Tag*): WriterData[U] =
    writer.write(s"$name.gauge", fields(metric), tags.toList, timestamp)

  private def fields(gauge: Gauge[T]): List[Field] = {
    Map(
      "value" -> gauge.getValue
    ).map {
      case (key, value) => Field(key, value)
    }.toList
  }
}
