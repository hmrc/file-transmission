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

package uk.gov.hmrc.filetransmission.services

import org.joda.time.DateTime
import play.api.Logger
import uk.gov.hmrc.filetransmission.config.ServiceConfiguration
import uk.gov.hmrc.filetransmission.connector.{MdgRequestSuccessful, _}
import uk.gov.hmrc.filetransmission.model.{FailedDeliveryAttempt, TransmissionRequestEnvelope}
import uk.gov.hmrc.filetransmission.services.queue._
import uk.gov.hmrc.filetransmission.utils.JodaTimeConverters._
import uk.gov.hmrc.filetransmission.utils.LoggingOps.withLoggedContext
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Clock
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class TransmissionRequestProcessingJob @Inject()(
    mdgConnector: MdgConnector,
    callbackSender: CallbackSender,
    configuration: ServiceConfiguration,
    clock: Clock)(implicit ec: ExecutionContext)
    extends QueueJob {

  private val logger = Logger(getClass)

  override def process(item: TransmissionRequestEnvelope,
                       nextRetryTime: DateTime,
                       timeToGiveUp: DateTime): Future[ProcessingResult] = {
    implicit val hc = HeaderCarrier()
    val now = clock.instant

    for (result <- mdgConnector.requestTransmission(item.request)) yield {
      withLoggedContext(item.request) {

        logIfDeliveryWindowHasExpired(item, now, timeToGiveUp, result.error)

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

  private def logIfDeliveryWindowHasExpired(envelope: TransmissionRequestEnvelope,
                                            now: java.time.Instant,
                                            timeToGiveUp: java.time.Instant,
                                            error: Option[Throwable] = None): Unit = {
    if (now.isAfter(timeToGiveUp)) {

      val newFailedDeliveryAttempt: Option[FailedDeliveryAttempt] = error.map {
        ex => new FailedDeliveryAttempt(now, ex.getMessage)
      }

      val updatedDeliveryAttempts: Seq[FailedDeliveryAttempt] = envelope.deliveryAttempts ++ newFailedDeliveryAttempt

      logger.warn(
        s"""Failed to deliver notification within delivery window for file reference: [${envelope.request.file.reference}] before [$timeToGiveUp].
          | Failed delivery attempts were: [${updatedDeliveryAttempts.mkString(",")}].
          | """.stripMargin)
    }
  }
}
