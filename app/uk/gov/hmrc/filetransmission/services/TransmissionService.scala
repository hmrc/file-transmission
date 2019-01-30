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

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.filetransmission.connector._
import uk.gov.hmrc.filetransmission.model.TransmissionRequestEnvelope
import uk.gov.hmrc.filetransmission.services.queue.WorkItemService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

sealed trait TransmissionResult
case object TransmissionSuccess extends TransmissionResult
case object TransmissionPending extends TransmissionResult
case object TransmissionFailure extends TransmissionResult

trait TransmissionService {
  def transmit(requestEnvelope: TransmissionRequestEnvelope)(implicit hc: HeaderCarrier): Future[TransmissionResult]
}

@Singleton
class RetryQueueBackedTransmissionService @Inject()(
    mdgConnector: MdgConnector,
    workItemService: WorkItemService,
    callbackSender: CallbackSender
)(implicit ec: ExecutionContext)extends TransmissionService {

  private val mdgResultToTransmissionResult: PartialFunction[MdgRequestResult, TransmissionResult] = {
    case MdgRequestSuccessful    => TransmissionSuccess
    case MdgRequestFatalError(_) => TransmissionFailure
    case MdgRequestError(_)      => TransmissionPending
  }


  override def transmit(requestEnvelope: TransmissionRequestEnvelope)(implicit hc: HeaderCarrier): Future[TransmissionResult] = {

    mdgConnector
      .requestTransmission(requestEnvelope.request)
      .map(callbackOrEnqueue(_, requestEnvelope))
      .map(mdgResultToTransmissionResult)
      .recoverWith { case _ => enqueueForLaterDeliveryAttempt(requestEnvelope) }
  }

  private def callbackOrEnqueue(mdgResult: MdgRequestResult,
                                requestEnvelope: TransmissionRequestEnvelope)
                                (implicit hc: HeaderCarrier): MdgRequestResult = {
    mdgResult match {
      case MdgRequestSuccessful    => callbackSender.sendSuccessfulCallback(requestEnvelope.request)
      case MdgRequestFatalError(e) => callbackSender.sendFailedCallback(requestEnvelope.request, e)
      case MdgRequestError(_)      => enqueueForLaterDeliveryAttempt(requestEnvelope)
    }
    mdgResult
  }

  private def enqueueForLaterDeliveryAttempt(requestEnvelope: TransmissionRequestEnvelope): Future[TransmissionResult] = {
    workItemService
      .enqueue(requestEnvelope)
      .map(_ => TransmissionPending)
      .recover{ case _ => TransmissionFailure }
  }
}
