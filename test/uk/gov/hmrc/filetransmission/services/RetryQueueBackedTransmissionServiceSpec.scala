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

import java.net.URL
import java.time.Instant

import org.mockito.Mockito
import org.mockito.Mockito.{verify, verifyNoMoreInteractions, when}
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.filetransmission.connector.{MdgConnector, MdgRequestError, MdgRequestFatalError, MdgRequestSuccessful}
import uk.gov.hmrc.filetransmission.model._
import uk.gov.hmrc.filetransmission.services.queue.WorkItemService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class RetryQueueBackedTransmissionServiceSpec
    extends WordSpec
    with Matchers
    with MockitoSugar
    with BeforeAndAfterEach {

  implicit val defaultTimeout: FiniteDuration = 5 seconds

  implicit val hc = HeaderCarrier()

  val mdgConnector = mock[MdgConnector]
  val workItemService = mock[WorkItemService]
  val callbackSender = mock[CallbackSender]

  val testInstance = new RetryQueueBackedTransmissionService(mdgConnector,
                                                             workItemService,
                                                             callbackSender)

  val request = TransmissionRequest(
    Batch("BatchA", 1),
    Interface("InterfaceA", "1.0"),
    File("file-reference",
         new URL("http://127.0.0.1/test"),
         "test.xml",
         "application/xml",
         "checksum",
         1,
         1024,
         Instant.now),
    Seq(Property("KEY1", "VAL1"), Property("KEY2", "VAL2")),
    new URL("http://127.0.0.1/test"),
    Some(30 seconds)
  )
  val requestEnvelope = TransmissionRequestEnvelope(
    request,
    "RetryQueueBackedTransmissionServiceSpec")

  override protected def beforeEach() =
    Mockito.reset(mdgConnector, workItemService, callbackSender)

  "RetryQueueBackedTransmissionService" should {

    "return TransmissionSuccess for successful delivery" in {
      when(mdgConnector.requestTransmission(request))
        .thenReturn(Future.successful(MdgRequestSuccessful))

      Await.result(testInstance.transmit(requestEnvelope), defaultTimeout) shouldBe TransmissionSuccess

      verify(mdgConnector).requestTransmission(request)
      verify(callbackSender).sendSuccessfulCallback(request)
      verifyNoMoreInteractions(mdgConnector, workItemService, callbackSender)
    }

    "return TransmissionPending for temporary delivery failure" in {
      when(workItemService.enqueue(requestEnvelope))
        .thenReturn(Future.successful((): Unit))
      when(mdgConnector.requestTransmission(request))
        .thenReturn(
          Future.successful(MdgRequestError(new RuntimeException("BOOM!"))))

      Await.result(testInstance.transmit(requestEnvelope), defaultTimeout) shouldBe TransmissionPending

      verify(mdgConnector).requestTransmission(request)
      verify(workItemService).enqueue(requestEnvelope)
      verifyNoMoreInteractions(mdgConnector, workItemService, callbackSender)
    }

    "return TransmissionFailure for permanent delivery failure" in {
      val error = new RuntimeException("BOOM!")

      when(mdgConnector.requestTransmission(request))
        .thenReturn(Future.successful(MdgRequestFatalError(error)))

      Await.result(testInstance.transmit(requestEnvelope), defaultTimeout) shouldBe TransmissionFailure

      verify(mdgConnector).requestTransmission(request)
      verify(callbackSender).sendFailedCallback(request, error)
      verifyNoMoreInteractions(mdgConnector, workItemService, callbackSender)
    }
  }
}
