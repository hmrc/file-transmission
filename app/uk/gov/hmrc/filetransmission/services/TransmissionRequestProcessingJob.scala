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

package uk.gov.hmrc.filetransmission.services

import java.time.Clock

import javax.inject.Inject
import org.joda.time.{DateTime, Instant}
import play.api.Logger
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.filetransmission.config.ServiceConfiguration
import uk.gov.hmrc.filetransmission.connector.{MdgRequestSuccessful, _}
import uk.gov.hmrc.filetransmission.model.TransmissionRequestEnvelope
import uk.gov.hmrc.filetransmission.services.queue._
import uk.gov.hmrc.filetransmission.utils.JodaTimeConverters._
import uk.gov.hmrc.filetransmission.utils.LoggingOps.withLoggedContext

import scala.concurrent.{ExecutionContext, Future}

class TransmissionRequestProcessingJob @Inject()(
    mdgConnector: MdgConnector,
    callbackSender: CallbackSender,
    configuration: ServiceConfiguration,
    clock: Clock)(implicit ec: ExecutionContext)
    extends QueueJob {

  override def process(item: TransmissionRequestEnvelope,
                       nextRetryTime: DateTime,
                       timeToGiveUp: DateTime): Future[ProcessingResult] = {
    implicit val hc = HeaderCarrier()

    for (result <- mdgConnector.requestTransmission(item.request)) yield {
      withLoggedContext(item.request) {
        logResult(item, result)
        if (clock.instant().isAfter(timeToGiveUp)) {
          Logger.warn(s"Failed to deliver notification within delivery window for file reference: [${item.request.file.reference}] before [$timeToGiveUp].")
        }
        result match {
          case MdgRequestSuccessful =>
            callbackSender.sendSuccessfulCallback(item.request)
            ProcessingSuccessful
          case MdgRequestFatalError(error) =>
            callbackSender.sendFailedCallback(item.request, error)
            ProcessingFailedDoNotRetry(error)
          case MdgRequestError(error) => {
            if (nextRetryTime < timeToGiveUp) {
              ProcessingFailed(error)
            } else {
              callbackSender.sendFailedCallback(item.request, error)
              ProcessingFailedDoNotRetry(error)
            }
          }
        }
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
}
