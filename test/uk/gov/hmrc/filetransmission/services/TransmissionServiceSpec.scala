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

import java.net.URL

import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito.{verify, when}
import org.mockito.{ArgumentCaptor, Mockito}
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{GivenWhenThen, Matchers}
import uk.gov.hmrc.filetransmission.connector.MdgConnector
import uk.gov.hmrc.filetransmission.model._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class TransmissionServiceSpec extends UnitSpec with Matchers with GivenWhenThen with MockitoSugar with Eventually {

  "transmission request" should {

    val request: TransmissionRequest = TransmissionRequest(
      Batch("A", 10),
      Interface("J", "1.0"),
      File("ref", new URL("http://127.0.0.1/test"), "test.xml", "application/xml", "checksum", 1, 1024),
      Seq(Property("KEY1", "VAL1"), Property("KEY2", "VAL2")),
      new URL("http://127.0.0.1/test"),
      30
    )

    "immediately return success, call MDG and send successful callback to consuming service afterwards" in {

      val mdgConnector        = mock[MdgConnector]
      val notificationService = mock[CallbackSender]

      val transmissionService = new TransmissionService(mdgConnector, notificationService)

      Given("MDG is working fine")
      when(mdgConnector.requestTransmission(any())(any())).thenReturn(Future.successful(()))

      And("consuming service is working fine")
      when(notificationService.sendSuccessfulCallback(any())(any())).thenReturn(Future.successful(()))

      When("request made to transmission service")
      val result = Await.ready(transmissionService.request(request, "callingService")(HeaderCarrier()), 10 seconds)

      Then("immediate successful response is returned")
      result.value.get.isSuccess shouldBe true

      And("call to MDG has been made")
      eventually {
        verify(mdgConnector).requestTransmission(meq(request))(any())
      }
      And("consuming service is notified")
      eventually {
        verify(notificationService).sendSuccessfulCallback(meq(request))(any())
        Mockito.verifyNoMoreInteractions(notificationService)
      }

    }

    "immediately return success when MDG fails but sends failure callback to consuming service afterwards" in {

      val mdgConnector        = mock[MdgConnector]
      val notificationService = mock[CallbackSender]
      val transmissionService = new TransmissionService(mdgConnector, notificationService)

      Given("MDG is faulty")
      when(mdgConnector.requestTransmission(any())(any()))
        .thenReturn(Future.failed(new Exception("Planned exception")))

      And("consuming service is working fine")
      when(notificationService.sendFailedCallback(any(), any())(any())).thenReturn(Future.successful(()))

      When("request made to transmission service")
      val result = Await.ready(transmissionService.request(request, "callingService")(HeaderCarrier()), 10 seconds)

      Then("immediate successful response is returned")
      result.value.get.isSuccess shouldBe true

      And("call to MDG has been made")
      eventually {
        verify(mdgConnector).requestTransmission(meq(request))(any())
      }

      And("consuming service is notified about failure")
      eventually {
        val exceptionCaptor: ArgumentCaptor[Throwable] = ArgumentCaptor.forClass(classOf[Throwable])
        verify(notificationService)
          .sendFailedCallback(meq(request), exceptionCaptor.capture())(any())
        exceptionCaptor.getValue.getMessage shouldBe "Planned exception"

      }
      Mockito.verifyNoMoreInteractions(notificationService)
    }
  }

}
