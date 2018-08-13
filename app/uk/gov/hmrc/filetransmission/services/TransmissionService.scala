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

package uk.gov.hmrc.filetransmission.services

import cats.implicits._
import javax.inject.Inject
import play.api.Logger
import uk.gov.hmrc.filetransmission.connector.MdgConnector
import uk.gov.hmrc.filetransmission.model.{TransmissionRequest, TransmissionRequestEnvelope}
import uk.gov.hmrc.filetransmission.services.queue._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class TransmissionService @Inject()(mdgConnector: MdgConnector, callbackSender: CallbackSender)(
  implicit ec: ExecutionContext)
    extends QueueJob {

  val maxRetries: Int = 4

  override def process(item: TransmissionRequestEnvelope, triedSoFar: Int): Future[ProcessingResult] =
    request(item.request, item.serviceName, triedSoFar)(HeaderCarrier())

  def request(request: TransmissionRequest, callingService: String, triedSoFar: Integer)(
    implicit hc: HeaderCarrier): Future[ProcessingResult] = {

    val requestingResult = mdgConnector.requestTransmission(request).attempt

    for (result <- requestingResult) yield {
      logResult(request, result, callingService)
      result match {
        case Right(_) =>
          sendSuccessfulCallback(request, callingService)
          ProcessingSuccessful
        case Left(error) if triedSoFar < maxRetries =>
          ProcessingFailed(error)
        case Left(error) =>
          sendFailureCallback(request, error, callingService)
          ProcessingFailedDoNotRetry(error)
      }

    }

  }

  private def logResult(request: TransmissionRequest, result: Either[Throwable, Unit], callingService: String): Unit =
    result match {
      case Right(_)    => Logger.info(s"Request ${describeRequest(request, callingService)} processed successfully")
      case Left(error) => Logger.warn(s"Processing request ${describeRequest(request, callingService)} failed", error)
    }

  private def sendSuccessfulCallback(request: TransmissionRequest, callingService: String)(
    implicit hc: HeaderCarrier): Unit = {
    val callbackSendingResult = callbackSender.sendSuccessfulCallback(request)
    callbackSendingResult.onFailure {
      case t: Throwable =>
        Logger.warn(s"Failed to send callback for request ${describeRequest(request, callingService)}", t)
    }

  }

  private def sendFailureCallback(request: TransmissionRequest, error: Throwable, callingService: String)(
    implicit hc: HeaderCarrier): Unit = {

    val callbackSendingResult = callbackSender.sendFailedCallback(request, error)

    callbackSendingResult.onFailure {
      case t: Throwable =>
        Logger.warn(s"Failed to send callback for request ${describeRequest(request, callingService)}", t)
    }

  }

  private def describeRequest(request: TransmissionRequest, callingService: String): String =
    s"consumingService: [$callingService] fileReference: [${request.file.reference}] batchId: [${request.batch.id}]"
}
