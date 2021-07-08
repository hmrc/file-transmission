/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.filetransmission.connector

import javax.inject.Inject
import play.api.Logger
import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.filetransmission.model.TransmissionRequest
import uk.gov.hmrc.filetransmission.services.CallbackSender
import uk.gov.hmrc.filetransmission.utils.LoggingOps.withLoggedContext
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

class HttpCallbackSender @Inject()(httpClient: HttpClient)(implicit ec: ExecutionContext) extends CallbackSender {

  private val logger = Logger(getClass)

  case class SuccessfulCallback(fileReference: String, batchId: String, outcome: String = "SUCCESS")
  case class FailureCallback(fileReference: String, batchId: String, outcome: String    = "FAILURE", errorDetails: String)

  implicit val successfulCallback: Writes[SuccessfulCallback] = Json.writes[SuccessfulCallback]

  implicit val failureCallback: Writes[FailureCallback] = Json.writes[FailureCallback]

  override def sendSuccessfulCallback(request: TransmissionRequest)(implicit hc: HeaderCarrier): Future[Unit] = {

    val callback = SuccessfulCallback(fileReference = request.file.reference, batchId = request.batch.id)

    httpClient
      .POST[SuccessfulCallback, HttpResponse](request.callbackUrl.toString, callback)
      .map { response =>
        withLoggedContext(request) {
          logger.info(s"""Response from: [${request.callbackUrl}], to delivery successful callback: [$callback], was: [${response.status}].""")
        }
      } recoverWith {
      case t: Throwable =>
        withLoggedContext(request) {
          logger.error(s"Failed to send delivery successful callback to: [${request.callbackUrl}], for request: [$request].", t)
          Future.failed(t)
        }
    }
  }

  override def sendFailedCallback(request: TransmissionRequest, reason: Throwable)(
    implicit hc: HeaderCarrier): Future[Unit] = {

    val callback = FailureCallback(
      fileReference = request.file.reference,
      batchId       = request.batch.id,
      errorDetails  = reason.getMessage)

    httpClient
      .POST[FailureCallback, HttpResponse](request.callbackUrl.toString, callback)
      .map { response =>
        withLoggedContext(request) {
          logger.info(s"Response from: [${request.callbackUrl}], to delivery failure callback: [$callback], was: [${response.status}].")
        }
      } recoverWith  {
      case t: Throwable =>
        withLoggedContext(request) {
          logger.error(s"""Failed to send delivery failure callback to: [${request.callbackUrl}], for request: [$request].""", t)
          Future.failed(t)
        }
    }
  }
}
