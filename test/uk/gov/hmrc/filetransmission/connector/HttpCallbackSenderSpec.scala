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

package uk.gov.hmrc.filetransmission.connector

import java.net.URL
import java.time.Instant

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, post, urlEqualTo, _}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.mockito.MockitoSugar
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, GivenWhenThen}
import uk.gov.hmrc.filetransmission.model._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.HttpClientSupport

import scala.concurrent.ExecutionContext.Implicits.global

class HttpCallbackSenderSpec
    extends AnyWordSpec
    with GivenWhenThen
    with MockitoSugar
    with Matchers
    with BeforeAndAfterAll
    with HttpClientSupport
    with ScalaFutures {

  val wiremockPort = 11111

  val callbackServer = new WireMockServer(wireMockConfig().port(wiremockPort))

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(
    timeout = scaled(Span(10, Seconds)),
    interval = scaled(Span(150, Millis))
  )

  override def beforeAll(): Unit =
    callbackServer.start()

  override def afterAll(): Unit =
    callbackServer.stop()

  private def stubCallbackReceiverToReturnValidResponse(): Unit =
    callbackServer.stubFor(
      post(urlEqualTo("/listen"))
        .willReturn(
          aResponse()
            .withStatus(204)
        ))

  private def stubCallbackReceiverToReturnInvalidResponse(): Unit =
    callbackServer.stubFor(
      post(urlEqualTo("/listen"))
        .willReturn(
          aResponse()
            .withStatus(503)
        ))

  val callbackUrl = new URL(s"http://127.0.0.1:$wiremockPort/listen")

  val request: TransmissionRequest = TransmissionRequest(
    Batch("A", 10),
    Interface("J", "1.0"),
    File("ref",
         new URL("http://127.0.0.1:11111/test"),
         "test.xml",
         "application/xml",
         "checksum",
         1,
         1024,
         Instant.now),
    Seq(Property("KEY1", "VAL1"), Property("KEY2", "VAL2")),
    callbackUrl,
    None
  )

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "Callback sender" should {

    val callbackSender = new HttpCallbackSender(httpClient)

    "allow to send success notifications to consuming services" in {

      stubCallbackReceiverToReturnValidResponse()

      callbackSender.sendSuccessfulCallback(request).futureValue shouldBe((): Unit)

      callbackServer.verify(
        postRequestedFor(urlEqualTo("/listen"))
          .withRequestBody(equalToJson(s"""
               | {
               |   "fileReference" : "${request.file.reference}",
               |   "batchId" : "${request.batch.id}",
               |   "outcome" : "SUCCESS"
               | }
               | """.stripMargin)))

    }

    "properly handle errors when sending success notifications" in {

      stubCallbackReceiverToReturnInvalidResponse()

      assert(callbackSender.sendFailedCallback(request, "Planned exception").failed.futureValue.isInstanceOf[Exception])

    }

    "allow to send error notifications to consuming services" in {

      stubCallbackReceiverToReturnValidResponse()

      callbackSender.sendFailedCallback(request, "Planned exception").futureValue shouldBe((): Unit)

      callbackServer.verify(
        postRequestedFor(urlEqualTo("/listen"))
          .withRequestBody(equalToJson(
            s"""
               | {
               |   "fileReference" : "${request.file.reference}",
               |   "batchId" : "${request.batch.id}",
               |   "outcome" : "FAILURE",
               |   "errorDetails" : "Planned exception"
               | }
               | """.stripMargin)))
    }

    "properly handle errors when sending error notifications" in {

      stubCallbackReceiverToReturnInvalidResponse()

      assert(callbackSender.sendFailedCallback(request, "Planned exception").failed.futureValue.isInstanceOf[Exception])
    }

  }

}
