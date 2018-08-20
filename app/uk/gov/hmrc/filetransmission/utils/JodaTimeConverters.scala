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
import java.time.{Instant, ZoneId}
import java.util.TimeZone

import org.joda.time.{DateTime, DateTimeZone}

object JodaTimeConverters {

  def toYoda(dateTime: java.time.ZonedDateTime) =
    new DateTime(
      dateTime.toInstant.toEpochMilli,
      DateTimeZone.forTimeZone(TimeZone.getTimeZone(dateTime.getZone)))

  def toYoda(instant: Instant, zone: ZoneId) =
    new DateTime(instant.toEpochMilli,
                 DateTimeZone.forTimeZone(TimeZone.getTimeZone(zone)))

  def fromYoda(dateTime: DateTime) = dateTime.toDate.toInstant

}
