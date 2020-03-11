/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.filetransmission.utils

import java.net.URL
import java.time.Instant

import org.mockito.Mockito
import org.scalatest.{GivenWhenThen, Matchers, WordSpec}
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.filetransmission.config.ServiceConfiguration
import uk.gov.hmrc.filetransmission.model._


class RequestValidatorSpec
    extends WordSpec
    with Matchers
    with GivenWhenThen
    with MockitoSugar {

  val httpCallback: URL = new URL("http://127.0.0.1/test")

  val httpRequest: TransmissionRequest = TransmissionRequest(
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
    httpCallback,
    None
  )

  val httpsRequest: TransmissionRequest = TransmissionRequest(
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
    new URL("https://127.0.0.1/test"),
    None
  )

  "RequestValidator.validate" should {

    "accept request if URL with protocol in allowed list" in {
      Given("a service configuration with only https callbacks allowed")
      val config = mock[ServiceConfiguration]
      Mockito.when(config.allowedCallbackProtocols).thenReturn(Seq("https"))
      val validator = new RequestValidator(config)

      When("a request with a https callback received")
      val result =
        validator.validate(httpsRequest)

      Then("the request should be passed through the validator")
      withClue(result) {
        result shouldBe Right(())
      }
    }

    "reject request if URL with protocol not in allowed list" in {
      Given("a service configuration with only https callbacks allowed")
      val config = mock[ServiceConfiguration]
      Mockito.when(config.allowedCallbackProtocols).thenReturn(Seq("https"))
      val validator = new RequestValidator(config)

      When("a request with a http callback received")
      val result = validator.validate(httpRequest)

      Then("the validator should reject as bad request")
      withClue(result) {
        result shouldBe Left(
          s"Invalid callback url protocol: [$httpCallback]. Protocol must be in: [https].")
      }
    }
  }
}
