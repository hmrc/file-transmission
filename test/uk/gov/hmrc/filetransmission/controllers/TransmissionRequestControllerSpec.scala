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

package uk.gov.hmrc.filetransmission.controllers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import play.api.http.Status
import play.api.libs.json.{JsValue, Json}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.filetransmission.config.ServiceConfiguration
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.duration._

class TransmissionRequestControllerSpec extends UnitSpec {

  implicit val actorSystem = ActorSystem()

  implicit val materializer = ActorMaterializer()

  implicit val timeout: akka.util.Timeout = 10 seconds

  val serviceConfiguration = new ServiceConfiguration {
    override def allowedUserAgents = Seq("VALID-AGENT")
  }

  val validRequestBody = Json.obj(
    "file" -> Json.obj(
      "reference" -> "file1",
      "sequenceNumber" -> 1,
      "name" -> "test.pdf",
      "mimeType" -> "application/pdf",
      "location" -> "http://127.0.0.1/location",
      "checksum" -> "1234"
    ),
    "journey" -> Json.obj(
      "name" -> "sampleJourney",
      "version" -> "1.0"
    ),
    "properties" -> Json.arr(
      Json.obj("name" -> "key1", "value" -> "value1"),
      Json.obj("name" -> "key2", "value" -> "value2")
    ),
    "batch" -> Json.obj(
      "id" -> "batch1",
      "fileCount" -> 2
    ),
    "callbackUrl" -> "http://127.0.0.1/callback",
    "requestTimeoutInSeconds" -> 3000
  )

  "POST /transmission" should {
    "valid request should return 200" in {
      val request: FakeRequest[JsValue] = FakeRequest()
        .withHeaders(("User-Agent", "VALID-AGENT"),
                     ("x-request-id", "some-request-id"),
                     ("x-session-id", "some-session-id"))
        .withBody(validRequestBody)

      val controller = new TransmissionRequestController(serviceConfiguration)
      val result = controller.requestTransmission()(request)

      withClue(Helpers.contentAsString(result)) {
        status(result) shouldBe Status.NO_CONTENT
      }
    }

    "invalid white-listed request should return 400" in {
      val request: FakeRequest[JsValue] = FakeRequest()
        .withHeaders(("User-Agent", "VALID-AGENT"),
                     ("x-request-id", "some-request-id"),
                     ("x-session-id", "some-session-id"))
        .withBody(Json.obj("invalid" -> "value"))

      val controller = new TransmissionRequestController(serviceConfiguration)
      val result = controller.requestTransmission()(request)

      withClue(Helpers.contentAsString(result)) {
        status(result) shouldBe Status.BAD_REQUEST
      }
    }

    "valid yet non-whitelisted request should return 403" in {
      val request: FakeRequest[JsValue] = FakeRequest()
        .withHeaders(("User-Agent", "INVALID-AGENT"),
                     ("x-request-id", "some-request-id"),
                     ("x-session-id", "some-session-id"))
        .withBody(validRequestBody)

      val controller = new TransmissionRequestController(serviceConfiguration)
      val result = controller.requestTransmission()(request)

      withClue(Helpers.contentAsString(result)) {
        status(result) shouldBe Status.FORBIDDEN
      }
    }
  }

}
