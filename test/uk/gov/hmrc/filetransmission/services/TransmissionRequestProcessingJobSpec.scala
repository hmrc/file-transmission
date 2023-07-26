/*
 * Copyright 2023 HM Revenue & Customs
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
import java.time.{Clock, Instant}

import org.mockito.{Mockito,ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.GivenWhenThen
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.filetransmission.config.ServiceConfiguration
import uk.gov.hmrc.filetransmission.connector.{MdgConnector, MdgRequestError, MdgRequestFatalError, MdgRequestSuccessful}
import uk.gov.hmrc.filetransmission.model._
import uk.gov.hmrc.filetransmission.services.queue.{ProcessingFailed, ProcessingFailedDoNotRetry, ProcessingSuccessful}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class TransmissionRequestProcessingJobSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen
    with Eventually
    with MockitoSugar
    with ArgumentMatchersSugar
    with ScalaFutures {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(
    timeout = scaled(Span(10, Seconds)),
    interval = scaled(Span(150, Millis))
  )

  "transmission request" should {

    val clock = Clock.systemUTC()

    val request: TransmissionRequest = TransmissionRequest(
      Batch("A", 10),
      Interface("J", "1.0"),
      File("ref",
           new URL("http://127.0.0.1/test"),
           "test.xml",
           "application/xml",
           "checksum",
           1,
           1024,
           Instant.now),
      Seq(Property("KEY1", "VAL1"), Property("KEY2", "VAL2")),
      new URL("http://127.0.0.1/test"),
      Some(30.seconds)
    )

    val envelope =
      TransmissionRequestEnvelope(request,
                                  "TransmissionRequestProcessingJobSpec")

    "immediately return success, call MDG and send successful callback to consuming service afterwards" in {

      val mdgConnector = mock[MdgConnector]
      val notificationService = mock[CallbackSender]
      val configuration = mock[ServiceConfiguration]

      val transmissionService =
        new TransmissionRequestProcessingJob(mdgConnector,
          notificationService,
          configuration,
          clock)

      Given("MDG is working fine")
      when(mdgConnector.requestTransmission(eqTo(request))(any))
        .thenReturn(Future.successful(MdgRequestSuccessful))

      And("consuming service is working fine")
      when(notificationService.sendSuccessfulCallback(any)(any))
        .thenReturn(Future.successful(()))

      When("request made to transmission service")
      val result = transmissionService.process(envelope,
        Instant.now().plusSeconds(5),
        Instant.now().minusSeconds(5)).futureValue

      Then("immediate successful response is returned")
      result shouldBe ProcessingSuccessful

      And("consuming service is notified")
      eventually {
        verify(notificationService).sendSuccessfulCallback(eqTo(request))(any)
        Mockito.verifyNoMoreInteractions(notificationService)
      }

    }

    "if call to MDG has failed but still can be retried, do not send callback but report error" in {

      val mdgConnector = mock[MdgConnector]
      val notificationService = mock[CallbackSender]
      val configuration = mock[ServiceConfiguration]

      val transmissionService =
        new TransmissionRequestProcessingJob(mdgConnector,
          notificationService,
          configuration,
          clock)

      Given("MDG is faulty")
      when(mdgConnector.requestTransmission(eqTo(request))(any))
        .thenReturn(Future.successful(
          MdgRequestError("Planned exception")))

      And("consuming service is working fine")
      when(notificationService.sendFailedCallback(any, any)(any))
        .thenReturn(Future.successful(()))

      When("request made to transmission service")
      val result = transmissionService.process(envelope,
          Instant.now(),
          Instant.now().plusSeconds(5)).futureValue

      Then("response saying that processing failed should be returned")
      result shouldBe a[ProcessingFailed]

      And("no callback is sent")
      Mockito.verifyNoInteractions(notificationService)

    }

    "if call to MDG has failed with non-recoverable error, send callback and do not retry" in {

      val mdgConnector = mock[MdgConnector]
      val notificationService = mock[CallbackSender]
      val configuration = mock[ServiceConfiguration]

      val transmissionService =
        new TransmissionRequestProcessingJob(mdgConnector,
          notificationService,
          configuration,
          clock)

      Given("MDG is faulty")
      when(mdgConnector.requestTransmission(eqTo(request))(any))
        .thenReturn(Future.successful(
          MdgRequestFatalError("Planned exception")))

      And("consuming service is working fine")
      when(notificationService.sendFailedCallback(any, any)(any))
        .thenReturn(Future.successful(()))

      When("request made to transmission service")
      val result = transmissionService.process(envelope,
          Instant.now(),
          Instant.now().plusSeconds(5)).futureValue

      Then("response saying that processing failed should be returned")
      result shouldBe a[ProcessingFailedDoNotRetry]

      And("consuming service is notified about failure")
      eventually {
        verify(notificationService).sendFailedCallback(eqTo(request), eqTo("Planned exception"))(any)
        Mockito.verifyNoMoreInteractions(notificationService)
      }
      Mockito.verifyNoMoreInteractions(notificationService)

    }

    "if call to MDG has failed but cannot be retried, send failure callback and report a persistent error" in {

      val mdgConnector = mock[MdgConnector]
      val notificationService = mock[CallbackSender]
      val configuration = mock[ServiceConfiguration]
      val transmissionService =
        new TransmissionRequestProcessingJob(mdgConnector,
                                             notificationService,
                                             configuration,
                                             clock)

      Given("MDG is faulty")
      when(mdgConnector.requestTransmission(eqTo(request))(any))
        .thenReturn(Future.successful(
          MdgRequestError("Planned exception")))

      And("consuming service is working fine")
      when(notificationService.sendFailedCallback(any, any)(any))
        .thenReturn(Future.successful(()))

      When("request made to transmission service")
      val result = transmissionService.process(envelope,
          Instant.now().plusSeconds(5),
          Instant.now()).futureValue

      Then(
        "response saying that processing failed and no more retry attemts are required, should be returned")
      result shouldBe a[ProcessingFailedDoNotRetry]

      And("consuming service is notified about failure")
      eventually {
        verify(notificationService).sendFailedCallback(eqTo(request), eqTo("Planned exception"))(any)
        Mockito.verifyNoMoreInteractions(notificationService)
      }
      Mockito.verifyNoMoreInteractions(notificationService)
    }
  }

}
