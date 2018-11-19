/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.filetransmission.utils
import java.time.Clock
import java.util.TimeZone

import org.joda.time.{DateTime, DateTimeZone, Duration}

object JodaTimeConverters {

  implicit def toJoda(dateTime: java.time.ZonedDateTime) =
    new DateTime(
      dateTime.toInstant.toEpochMilli,
      DateTimeZone.forTimeZone(TimeZone.getTimeZone(dateTime.getZone)))

  implicit def fromJoda(dateTime: DateTime) = dateTime.toDate.toInstant

  implicit def toJoda(input: scala.concurrent.duration.Duration): Duration = {
    Duration.millis(input.toMillis)
  }

  implicit class ClockJodaExtensions(clock: Clock) {
    def nowAsJoda: DateTime = {
      new DateTime(
        clock.instant().toEpochMilli,
        DateTimeZone.forTimeZone(TimeZone.getTimeZone(clock.getZone)))
    }
  }

  implicit class JodaDateTimeExtensions(dateTime: DateTime) {
    def +(duration: Duration) = dateTime.plus(duration)

    def <(other: DateTime) = dateTime.isBefore(other)

  }

  implicit class JodaDurationExtension(duration: Duration) {
    def *(multiplier: Long) = duration.multipliedBy(multiplier)
  }

}
