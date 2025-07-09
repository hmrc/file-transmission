/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.filetransmission.model

import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, Json, __}
import uk.gov.hmrc.filetransmission.utils.HttpUrlFormat
import uk.gov.hmrc.filetransmission.utils.LoggingOps.ContextExtractor

import java.net.URL
import java.time.Instant
import scala.concurrent.duration._

case class FailedDeliveryAttempt(
  time         : Instant,
  failureReason: String
)

case class TransmissionRequest(
  batch                 : Batch,
  interface             : Interface,
  file                  : File,
  properties            : Seq[Property],
  callbackUrl           : URL,
  deliveryWindowDuration: Option[FiniteDuration]
)

case class TransmissionRequestEnvelope(
  request         : TransmissionRequest,
  serviceName     : String,
  deliveryAttempts: Seq[FailedDeliveryAttempt] = Seq.empty
) {
  def withFailedDeliveryAttempt(
    da: FailedDeliveryAttempt
  ): TransmissionRequestEnvelope =
    this.copy(
      deliveryAttempts = deliveryAttempts :+ da
    )

  def describe =
    s"consumingService: [$serviceName] fileReference: [${request.file.reference}] batchId: [${request.batch.id}]"
}

case class Batch(id: String, fileCount: Int)

case class Interface(name: String, version: String)

case class File(
  reference      : String,
  location       : URL,
  name           : String,
  mimeType       : String,
  checksum       : String,
  sequenceNumber : Int,
  size           : Int,
  uploadTimestamp: Instant
)

case class Property(
  name : String,
  value: String
)

object TransmissionRequest {
  given Format[URL] = HttpUrlFormat

  given Format[File] = Json.format[File]

  given Format[Property] = Json.format[Property]

  given Format[Interface] = Json.format[Interface]

  given Format[Batch] = Json.format[Batch]

  val timeInSecondsFormat: Format[FiniteDuration] =
    summon[Format[Int]].inmap(_.seconds, _.toSeconds.toInt)

  given Format[TransmissionRequest] =
    ( (__ \ "batch"                          ).format[Batch]
    ~ (__ \ "interface"                      ).format[Interface]
    ~ (__ \ "file"                           ).format[File]
    ~ (__ \ "properties"                     ).format[Seq[Property]]
    ~ (__ \ "callbackUrl"                    ).format[URL]
    ~ (__ \ "deliveryWindowDurationInSeconds").formatNullable(timeInSecondsFormat)
    )(TransmissionRequest.apply, o => Tuple.fromProductTyped(o))

  given ContextExtractor[TransmissionRequest] =
    new ContextExtractor[TransmissionRequest] {
      override def extract(request: TransmissionRequest): Map[String, String] =
        Map(
          "file-reference"  -> request.file.reference,
          "batch-reference" -> request.batch.id
        )
    }
}

object FailedDeliveryAttempt {
  given Format[FailedDeliveryAttempt] =
    ( (__ \ "time"         ).format[Instant] // formats as string
    ~ (__ \ "failureReason").format[String]
    )(FailedDeliveryAttempt.apply, o => Tuple.fromProductTyped(o))
}

object TransmissionRequestEnvelope {
  given transmissionRequestEnvelopeFormat: Format[TransmissionRequestEnvelope] =
    ( (__ \ "request"         ).format[TransmissionRequest]
    ~ (__ \ "serviceName"     ).format[String]
    ~ (__ \ "deliveryAttempts").format[Seq[FailedDeliveryAttempt]]
    )(TransmissionRequestEnvelope.apply, o => Tuple.fromProductTyped(o))
}
