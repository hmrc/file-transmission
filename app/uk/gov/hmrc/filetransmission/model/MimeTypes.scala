/*
 * Copyright 2026 HM Revenue & Customs
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

object MimeTypes {
  private val mimeToExtension = Map(
    "application/pdf" -> ".pdf",
    "image/jpeg" -> ".jpg",
    "image/png" -> ".png",
    "image/gif" -> ".gif",
    "text/plain" -> ".txt",
    "text/csv" -> ".csv",
    "application/json" -> ".json",
    "application/xml" -> ".xml",
    "text/xml" -> ".xml",
    "application/zip" -> ".zip",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> ".xlsx"
  )

  def extensionFor(mimeType: String): Option[String] =
    mimeToExtension.get(mimeType.toLowerCase)
}
