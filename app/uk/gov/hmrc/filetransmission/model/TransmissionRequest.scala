/*
 * Copyright 2019 HM Revenue & Customs
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

import java.net.URL
import java.time.Instant

import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, JsPath, Json}
import uk.gov.hmrc.filetransmission.utils.HttpUrlFormat
import uk.gov.hmrc.filetransmission.utils.LoggingOps.ContextExtractor

import scala.concurrent.duration._

case class FailedDeliveryAttempt(time: Instant,
                                 failureReason: String)

case class TransmissionRequest(batch: Batch,
                               interface: Interface,
                               file: File,
                               properties: Seq[Property],
                               callbackUrl: URL,
                               deliveryWindowDuration: Option[FiniteDuration])

case class TransmissionRequestEnvelope(
    request: TransmissionRequest,
    serviceName: String,
    deliveryAttempts: Seq[FailedDeliveryAttempt] = Seq.empty
) {
  def withFailedDeliveryAttempt(da: FailedDeliveryAttempt): TransmissionRequestEnvelope = {
    this.copy(
      deliveryAttempts = deliveryAttempts :+ da
    )
  }

  def describe =
    s"consumingService: [$serviceName] fileReference: [${request.file.reference}] batchId: [${request.batch.id}]"
}

case class Batch(id: String, fileCount: Int)

case class Interface(name: String, version: String)

case class File(
    reference: String,
    location: URL,
    name: String,
    mimeType: String,
    checksum: String,
    sequenceNumber: Int,
    size: Int,
    uploadTimeStamp: Long
)

case class Property(
    name: String,
    value: String
)

object TransmissionRequest {
  implicit val urlReads: Format[URL] = HttpUrlFormat

  implicit val fileReads: Format[File] = Json.format[File]

  implicit val propertyReads: Format[Property] = Json.format[Property]

  implicit val interfaceReads: Format[Interface] = Json.format[Interface]

  implicit val batchReads: Format[Batch] = Json.format[Batch]

  val timeInSecondsFormat: Format[FiniteDuration] =
    implicitly[Format[Int]].inmap(_ seconds, _.toSeconds.toInt)

  implicit val transmissionRequestReads: Format[TransmissionRequest] = (
    (JsPath \ "batch").format[Batch] and
      (JsPath \ "interface").format[Interface] and
      (JsPath \ "file").format[File] and
      (JsPath \ "properties").format[Seq[Property]] and
      (JsPath \ "callbackUrl").format[URL] and
      (JsPath \ "deliveryWindowDurationInSeconds").formatNullable(
        timeInSecondsFormat)
  )(TransmissionRequest.apply, unlift(TransmissionRequest.unapply))

  implicit val requestExtractor = new ContextExtractor[TransmissionRequest] {
    override def extract(request: TransmissionRequest): Map[String, String] =
      Map(
        "file-reference"  -> request.file.reference,
        "batch-reference" -> request.batch.id
      )
  }
}

object FailedDeliveryAttempt {
  implicit val failedDeliveryFormat = Json.format[FailedDeliveryAttempt]
}

object TransmissionRequestEnvelope {
  implicit val transmissionRequestEnvelopeFormat
    : Format[TransmissionRequestEnvelope] =
    Json.format[TransmissionRequestEnvelope]
}
