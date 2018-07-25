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

import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{GivenWhenThen, Matchers}
import uk.gov.hmrc.filetransmission.connector.MdgConnector
import uk.gov.hmrc.filetransmission.model._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class TransmissionServiceSpec extends UnitSpec with Matchers with GivenWhenThen with MockitoSugar {

  "transmission request" should {

    val request: TransmissionRequest = TransmissionRequest(
      Batch("A", 10),
      Interface("J", "1.0"),
      File("ref", new URL("http://127.0.0.1/test"), "test.xml", "application/xml", "checksum", 1, 1024),
      Seq(Property("KEY1", "VAL1"), Property("KEY2", "VAL2")),
      new URL("http://127.0.0.1/test"),
      30
    )

    "immediately return success and call MDG" in {

      val mdgConnector        = mock[MdgConnector]
      val transmissionService = new TransmissionService(mdgConnector)

      Given("MDG is working fine")
      Mockito.when(mdgConnector.requestTransmission(any())(any())).thenReturn(Future.successful(()))

      When("request made to transmission service")
      val result = Await.ready(transmissionService.request(request, "callingService")(HeaderCarrier()), 10 seconds)

      Then("immediate successful response is returned")
      result.value.get.isSuccess shouldBe true

      And("call to MDG has been made")
      Mockito.verify(mdgConnector).requestTransmission(ArgumentMatchers.eq(request))(any())

    }

    "immediately return success when MDG fails" in {

      val mdgConnector        = mock[MdgConnector]
      val transmissionService = new TransmissionService(mdgConnector)

      Given("MDG is faulty")
      Mockito
        .when(mdgConnector.requestTransmission(any())(any()))
        .thenReturn(Future.failed(new Exception("PLanned exception")))

      When("request made to transmission service")
      val result = Await.ready(transmissionService.request(request, "callingService")(HeaderCarrier()), 10 seconds)

      Then("immediate successful response is returned")
      result.value.get.isSuccess shouldBe true

      And("call to MDG has been made")
      Mockito.verify(mdgConnector).requestTransmission(ArgumentMatchers.eq(request))(any())

    }
  }

}
