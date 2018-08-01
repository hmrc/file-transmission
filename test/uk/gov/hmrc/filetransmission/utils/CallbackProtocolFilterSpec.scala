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

package uk.gov.hmrc.filetransmission.utils

import java.net.URL

import akka.util.Timeout
import org.mockito.Mockito
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{GivenWhenThen, Matchers}
import play.api.mvc.Result
import play.api.mvc.Results._
import play.api.test.Helpers
import uk.gov.hmrc.filetransmission.config.ServiceConfiguration
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future
import scala.concurrent.duration._

class CallbackProtocolFilterSpec
    extends UnitSpec
    with Matchers
    with GivenWhenThen
    with MockitoSugar {

  class CallbackProtocolFilterImpl(
      override val configuration: ServiceConfiguration)
      extends CallbackProtocolFilter

  "CallbackProtocolFilter" should {
    def block: Future[Result] =
      Future.successful(Ok(s"This is a successful result done with HTTPS."))

    implicit val timeout = Timeout(3.seconds)

    "accept request if URL with protocol in allowed list" in {
      Given("a service configuration with only https callbacks allowed")
      val config = mock[ServiceConfiguration]
      Mockito.when(config.allowedCallbackProtocols).thenReturn(Seq("https"))
      val filter = new CallbackProtocolFilterImpl(config)

      When("a request with a https callback received")
      val result =
        filter.onlyValidCallbackProtocols(new URL("https://127.0.0.1"))(block)

      Then("the request should be passed through the filter")
      status(result) shouldBe 200
      Helpers.contentAsString(result) shouldBe "This is a successful result done with HTTPS."
    }

    "reject request if URL with protocol not in allowed list" in {
      Given("a service configuration with only https callbacks allowed")
      val config = mock[ServiceConfiguration]
      Mockito.when(config.allowedCallbackProtocols).thenReturn(Seq("https"))
      val filter = new CallbackProtocolFilterImpl(config)

      When("a request with a http callback received")
      val result =
        filter.onlyValidCallbackProtocols(new URL("http://127.0.0.1"))(block)

      Then("the filter should reject as bad request")
      status(result) shouldBe 400
      Helpers.contentAsString(result) should include(
        "Invalid callback url protocol")
    }
  }
}
