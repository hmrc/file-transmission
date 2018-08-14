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

package uk.gov.hmrc.filetransmission.model

import java.net.URL

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.filetransmission.utils.HttpUrlFormat

case class TransmissionRequest(
  batch: Batch,
  interface: Interface,
  file: File,
  properties: Seq[Property],
  callbackUrl: URL,
  requestTimeoutInSeconds: Int)

case class TransmissionRequestEnvelope(
  request: TransmissionRequest,
  serviceName: String
) {
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
  size: Int
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

  implicit val transmissionRequestReads: Format[TransmissionRequest] = Json.format[TransmissionRequest]

}

object TransmissionRequestEnvelope {
  implicit val transmissionRequestEnvelopeFormat: Format[TransmissionRequestEnvelope] =
    Json.format[TransmissionRequestEnvelope]
}
