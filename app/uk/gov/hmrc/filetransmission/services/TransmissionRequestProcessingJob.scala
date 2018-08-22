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
import uk.gov.hmrc.filetransmission.config.ServiceConfiguration
import uk.gov.hmrc.filetransmission.connector.{MdgRequestSuccessful, _}
import uk.gov.hmrc.filetransmission.model.TransmissionRequestEnvelope
import uk.gov.hmrc.filetransmission.services.queue._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class TransmissionRequestProcessingJob @Inject()(
    mdgConnector: MdgConnector,
    callbackSender: CallbackSender,
    configuration: ServiceConfiguration)(implicit ec: ExecutionContext)
    extends QueueJob {

  override def process(item: TransmissionRequestEnvelope,
                       canRetry: Boolean): Future[ProcessingResult] = {
    implicit val hc = HeaderCarrier()

    for (result <- mdgConnector.requestTransmission(item.request)) yield {
      logResult(item, result)
      result match {
        case MdgRequestSuccessful =>
          sendSuccessfulCallback(item)
          ProcessingSuccessful
        case MdgRequestFatalError(error) =>
          sendFailureCallback(item, error)
          ProcessingFailedDoNotRetry(error)
        case MdgRequestError(error) if canRetry =>
          ProcessingFailed(error)
        case MdgRequestError(error) =>
          sendFailureCallback(item, error)
          ProcessingFailedDoNotRetry(error)
      }
    }

  }

  private def logResult(request: TransmissionRequestEnvelope,
                        result: MdgRequestResult): Unit =
    result match {
      case MdgRequestSuccessful =>
        Logger.info(s"Request ${request.describe} processed successfully")
      case MdgRequestFatalError(error) =>
        Logger.warn(
          s"Processing request ${request.describe} failed - non recoverable error",
          error)
      case MdgRequestError(error) =>
        Logger.warn(s"Processing request ${request.describe} failed", error)
    }

  private def sendSuccessfulCallback(request: TransmissionRequestEnvelope)(
      implicit hc: HeaderCarrier): Unit = {
    val callbackSendingResult =
      callbackSender.sendSuccessfulCallback(request.request)
    callbackSendingResult.onFailure {
      case t: Throwable =>
        Logger.warn(s"Failed to send callback for request ${request.describe}",
                    t)
    }

  }

  private def sendFailureCallback(
      request: TransmissionRequestEnvelope,
      error: Throwable)(implicit hc: HeaderCarrier): Unit = {

    val callbackSendingResult =
      callbackSender.sendFailedCallback(request.request, error)
    callbackSendingResult.onFailure {
      case t: Throwable =>
        Logger.warn(s"Failed to send callback for request ${request.describe}",
                    t)
    }

  }

}
