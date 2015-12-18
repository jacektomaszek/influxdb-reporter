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
package influxdbreporter.core.metrics

import java.util.concurrent.TimeUnit

import com.codahale.metrics.Clock
import Metric._
import influxdbreporter.core.Tag

import scala.annotation.varargs

sealed trait TimerContext {
  def stop()
}

class Timer extends TagRelatedMetric[CodehaleTimer] with Metric[CodehaleTimer] {

  @varargs def time(tags: Tag*): TimerContext = new InfluxTimerContextImpl(tags.toList, this)

  override protected def createMetric(): CodehaleTimer = new CodehaleTimer()

  private def notify(tags: List[Tag], time: Long): Unit =
    increaseMetric(tags, _.update(time, TimeUnit.NANOSECONDS))

  private class InfluxTimerContextImpl(tags: List[Tag], listener: Timer)
    extends TimerContext {

    val clock = Clock.defaultClock
    val startTime = clock.getTick

    override def stop(): Unit = {
      listener.notify(tags, clock.getTick - startTime)
    }
  }

}

