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

import akka.util.Timeout
import org.mockito.Mockito
import org.scalatest.{GivenWhenThen, Matchers, WordSpec}
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.Result
import play.api.mvc.Results._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.filetransmission.config.ServiceConfiguration

import scala.concurrent.Future
import scala.concurrent.duration._

class UserAgentFilterSpec extends WordSpec with Matchers with GivenWhenThen with MockitoSugar {

  class UserAgentFilterImpl(override val configuration: ServiceConfiguration) extends UserAgentFilter

  "UserAgentFilter" should {
    def block: String => Future[Result] = { callingService =>
      Future.successful(Ok(s"This is a successful result done by $callingService"))
    }

    implicit val timeout = Timeout(3.seconds)

    "accept request if user agent in whitelist" in {
      Given("a service configuration with no whitelist set")
      val config = mock[ServiceConfiguration]
      Mockito.when(config.allowedUserAgents).thenReturn(List("VALID-AGENT"))
      val filter = new UserAgentFilterImpl(config)

      When("a request is received")
      val result = filter.onlyAllowedServices(block)(FakeRequest().withHeaders(("User-Agent", "VALID-AGENT")))

      Then("the request should be passed through the filter")
      Helpers.status(result)                  shouldBe 200
      Helpers.contentAsString(result) shouldBe "This is a successful result done by VALID-AGENT"
    }

    "reject request if user agent not in whitelist" in {
      Given("a service configuration with no whitelist set")
      val config = mock[ServiceConfiguration]
      Mockito.when(config.allowedUserAgents).thenReturn(List("VALID-AGENT"))
      val filter = new UserAgentFilterImpl(config)

      When("a request is received")
      val result = filter.onlyAllowedServices(block)(FakeRequest())

      Then("the filter should reject as forbidden")
      Helpers.status(result) shouldBe 403
      Helpers.contentAsString(result) shouldBe "This service is not allowed to use file-transmission. " +
        "If you need to use this service, please contact Platform Services team."
    }
  }
}
