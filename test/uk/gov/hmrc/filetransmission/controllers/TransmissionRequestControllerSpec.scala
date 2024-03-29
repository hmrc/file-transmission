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

package uk.gov.hmrc.filetransmission.controllers

import org.apache.pekko.actor.ActorSystem
import org.mockito.{Mockito, MockitoSugar, ArgumentMatchersSugar}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.http.Status
import play.api.libs.json.{JsValue, Json}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.filetransmission.config.ServiceConfiguration
import uk.gov.hmrc.filetransmission.model.{RequestValidator, TransmissionRequestEnvelope}
import uk.gov.hmrc.filetransmission.services.queue.MongoBackedWorkItemService
import uk.gov.hmrc.filetransmission.services.{TransmissionService, TransmissionSuccess}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import play.api.test.Helpers._

class TransmissionRequestControllerSpec extends AnyWordSpec with Matchers with MockitoSugar with ArgumentMatchersSugar{

  implicit val actorSystem = ActorSystem()

  //implicit val timeout: org.apache.pekko.util.Timeout = 10.seconds

  val serviceConfiguration = new ServiceConfiguration {
    override def allowedUserAgents = Seq("VALID-AGENT")
    override def mdgEndpoint: String = ???
    override def queuePollingInterval: Duration = ???
    override def queueRetryAfterFailureInterval: Duration = ???
    override def inFlightLockDuration: Duration = ???
    override def initialBackoffAfterFailure: Duration = ???
    override def allowedCallbackProtocols: Seq[String] = Seq("http", "https")
    override def defaultDeliveryWindowDuration: Duration = ???
    override def mdgAuthorizationToken: String = ???
  }

  val transmissionQueue = mock[MongoBackedWorkItemService]
  val transmissionService = mock[TransmissionService]

  val validRequestBody = Json.obj(
    "file" -> Json.obj(
      "reference" -> "file1",
      "sequenceNumber" -> 1,
      "name" -> "test.pdf",
      "mimeType" -> "application/pdf",
      "location" -> "http://127.0.0.1/location",
      "checksum" -> "1234",
      "size" -> 1024,
      "uploadTimestamp" -> "2001-12-17T09:30:47Z"
    ),
    "interface" -> Json.obj(
      "name" -> "sampleInterface",
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

  val requestBodyWithInvalidCallbackUrl = Json.obj(
    "file" -> Json.obj(
      "reference" -> "file1",
      "sequenceNumber" -> 1,
      "name" -> "test.pdf",
      "mimeType" -> "application/pdf",
      "location" -> "http://127.0.0.1/location",
      "checksum" -> "1234",
      "size" -> 1024
    ),
    "interface" -> Json.obj(
      "name" -> "sampleInterface",
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
    "callbackUrl" -> "invalidCallbackUrl",
    "requestTimeoutInSeconds" -> 3000
  )

  "POST /transmission" should {
    "valid request should return 200" in {

      val request: FakeRequest[JsValue] = FakeRequest()
        .withHeaders(("User-Agent", serviceConfiguration.allowedUserAgents.head),
                     ("x-request-id", "some-request-id"),
                     ("x-session-id", "some-session-id"))
        .withBody(validRequestBody)

      Mockito
        .when(transmissionQueue.enqueue(any))
        .thenReturn(Future.successful((): Unit))

      Mockito
        .when(transmissionService.transmit(any[TransmissionRequestEnvelope])(
          any[HeaderCarrier]))
        .thenReturn(Future.successful(TransmissionSuccess))

      val requestValidator: RequestValidator = mock[RequestValidator]
      Mockito.when(requestValidator.validate(any)).thenReturn(Right(()))

      val controller = new TransmissionRequestController(transmissionQueue,
                                                         transmissionService,
                                                         requestValidator,
                                                         serviceConfiguration, stubControllerComponents())
      val result = controller.requestTransmission()(request)

      withClue(Helpers.contentAsString(result)) {
        status(result) shouldBe Status.ACCEPTED
      }
    }

    "invalid request from allowed user-agent should return 400" in {
      val request: FakeRequest[JsValue] = FakeRequest()
        .withHeaders(("User-Agent", serviceConfiguration.allowedUserAgents.head),
                     ("x-request-id", "some-request-id"),
                     ("x-session-id", "some-session-id"))
        .withBody(Json.obj("invalid" -> "value"))

      val requestValidator: RequestValidator = mock[RequestValidator]
      Mockito.when(requestValidator.validate(any)).thenReturn(Right(()))

      val controller = new TransmissionRequestController(transmissionQueue,
                                                         transmissionService,
                                                         requestValidator,
                                                         serviceConfiguration, stubControllerComponents())
      val result = controller.requestTransmission()(request)

      withClue(Helpers.contentAsString(result)) {
        status(result) shouldBe Status.BAD_REQUEST
      }
    }

    "a request with an invalid callback url format from an allowed user-agent should receive a 400 result" in {
      val request: FakeRequest[JsValue] = FakeRequest()
        .withHeaders(("User-Agent", serviceConfiguration.allowedUserAgents.head),
                     ("x-request-id", "some-request-id"),
                     ("x-session-id", "some-session-id"))
        .withBody(requestBodyWithInvalidCallbackUrl)

      val requestValidator: RequestValidator = mock[RequestValidator]
      Mockito.when(requestValidator.validate(any)).thenReturn(Right(()))

      val controller = new TransmissionRequestController(transmissionQueue,
                                                         transmissionService,
                                                         requestValidator,
                                                         serviceConfiguration, stubControllerComponents())
      val result = controller.requestTransmission()(request)

      withClue(Helpers.contentAsString(result)) {
        status(result) shouldBe Status.BAD_REQUEST
      }
    }

    "a request with an invalid callback url protocol from an allowed user-agent should receive a 400 result" in {
      val request: FakeRequest[JsValue] = FakeRequest()
        .withHeaders(("User-Agent", serviceConfiguration.allowedUserAgents.head),
                     ("x-request-id", "some-request-id"),
                     ("x-session-id", "some-session-id"))
        .withBody(validRequestBody)

      val requestValidator: RequestValidator = mock[RequestValidator]
      Mockito
        .when(requestValidator.validate(any))
        .thenReturn(Left("InvalidRequest"))

      val controller = new TransmissionRequestController(transmissionQueue,
                                                         transmissionService,
                                                         requestValidator,
                                                         serviceConfiguration, stubControllerComponents())
      val result = controller.requestTransmission()(request)

      withClue(Helpers.contentAsString(result)) {
        status(result) shouldBe Status.BAD_REQUEST
        Helpers.contentAsString(result) shouldBe "InvalidRequest"
      }
    }

    "an otherwise valid request from an unrecognised user-agent should receive a 403 result" in {
      val request: FakeRequest[JsValue] = FakeRequest()
        .withHeaders(("User-Agent", "UNRECOGNISED-AGENT"),
                     ("x-request-id", "some-request-id"),
                     ("x-session-id", "some-session-id"))
        .withBody(validRequestBody)

      val requestValidator: RequestValidator = mock[RequestValidator]
      Mockito.when(requestValidator.validate(any)).thenReturn(Right(()))

      val controller = new TransmissionRequestController(transmissionQueue,
                                                         transmissionService,
                                                         requestValidator,
                                                         serviceConfiguration, stubControllerComponents())
      val result = controller.requestTransmission()(request)

      withClue(Helpers.contentAsString(result)) {
        status(result) shouldBe Status.FORBIDDEN
      }
    }
  }

}
