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
import uk.gov.hmrc.filetransmission.model.TransmissionRequest
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class TransmissionService @Inject()(mdgConnector: MdgConnector, callbackSender: CallbackSender)(
  implicit ec: ExecutionContext) {

  def request(request: TransmissionRequest, callingService: String)(implicit hc: HeaderCarrier): Future[Unit] = {

    for {
      requestingResult <- mdgConnector.requestTransmission(request).attempt
      _ = logResult(request, requestingResult, callingService)
    } yield sendCallback(request, requestingResult, callingService)

    Future.successful((): Unit)
  }

  private def logResult(request: TransmissionRequest, result: Either[Throwable, Unit], callingService: String): Unit =
    result match {
      case Right(_)    => Logger.info(s"Request ${describeRequest(request, callingService)} processed successfully")
      case Left(error) => Logger.warn(s"Processing request ${describeRequest(request, callingService)} failed", error)
    }

  private def sendCallback(request: TransmissionRequest, result: Either[Throwable, Unit], callingService: String)(
    implicit hc: HeaderCarrier): Unit = {
    val callbackSendingResult = result match {
      case Right(_)    => callbackSender.sendSuccessfulCallback(request)
      case Left(error) => callbackSender.sendFailedCallback(request, error)
    }

    callbackSendingResult.onFailure {
      case t: Throwable =>
        Logger.warn(s"Failed to send callback for request ${describeRequest(request, callingService)}", t)
    }

  }

  private def describeRequest(request: TransmissionRequest, callingService: String): String =
    s"consumingService: [$callingService] fileReference: [${request.file.reference}] batchId: [${request.batch.id}]"

}
