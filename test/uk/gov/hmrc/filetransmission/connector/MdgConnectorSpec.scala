/*
 * Copyright 2019 HM Revenue & Customs
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
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.mockito.Mockito
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, GivenWhenThen}
import uk.gov.hmrc.filetransmission.config.ServiceConfiguration
import uk.gov.hmrc.filetransmission.model._
import uk.gov.hmrc.filetransmission.utils.TestHttpClient
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class MdgConnectorSpec
  extends UnitSpec
    with GivenWhenThen
    with MockitoSugar
    with BeforeAndAfterAll {

  val serviceConfiguration = new ServiceConfiguration {
    override def allowedUserAgents = ???
    override def mdgEndpoint: String = "http://127.0.0.1:11111/mdg"
    override def queuePollingInterval: Duration = ???
    override def queueRetryAfterFailureInterval: Duration = ???
    override def inFlightLockDuration: Duration = ???
    override def initialBackoffAfterFailure: Duration = ???
    override def allowedCallbackProtocols: Seq[String] = ???
    override def defaultDeliveryWindowDuration: Duration = ???
  }

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
      Instant.now.toString),
    Seq(Property("KEY1", "VAL1"), Property("KEY2", "VAL2")),
    new URL("http://127.0.0.1/test"),
    None
  )

  val httpClient = new TestHttpClient()

  val mdgServer = new WireMockServer(wireMockConfig().port(11111))

  override def beforeAll() =
    mdgServer.start()

  override def afterAll() =
    mdgServer.stop()

  private def stubMdgToReturnValidResponse(): Unit =
    mdgServer.stubFor(
      post(urlEqualTo("/mdg"))
        .willReturn(
          aResponse()
            .withStatus(204)
        ))

  private def stubMdgToReturnInvalidResponse(): Unit =
    mdgServer.stubFor(
      post(urlEqualTo("/mdg"))
        .willReturn(
          aResponse()
            .withStatus(503)
        ))

  private def stubMdgToReturnBadRequestResponse(): Unit =
    mdgServer.stubFor(
      post(urlEqualTo("/mdg"))
        .willReturn(
          aResponse()
            .withStatus(400)
        ))

  "MDG Connector" should {
    "return successful response when call to MDG was successful" in {

      stubMdgToReturnValidResponse()

      val serializer = mock[MdgRequestSerializer]
      Mockito.when(serializer.serialize(request)).thenReturn("serializedBody")

      val connector =
        new MdgConnector(httpClient, serviceConfiguration, serializer)

      Await.result(connector.requestTransmission(request)(HeaderCarrier()),
        10 seconds) shouldBe MdgRequestSuccessful
    }

    "return failed response when call to MDG failed" in {
      stubMdgToReturnInvalidResponse()

      val serializer = mock[MdgRequestSerializer]
      Mockito.when(serializer.serialize(request)).thenReturn("serializedBody")

      val connector =
        new MdgConnector(httpClient, serviceConfiguration, serializer)

      Await.result(connector.requestTransmission(request)(HeaderCarrier()),
        10 seconds) shouldBe a[MdgRequestError]
    }

    "return fatally failed response when call to MDG failed and returned HTTP 400 bad request" in {
      stubMdgToReturnBadRequestResponse()

      val serializer = mock[MdgRequestSerializer]
      Mockito.when(serializer.serialize(request)).thenReturn("serializedBody")

      val connector =
        new MdgConnector(httpClient, serviceConfiguration, serializer)

      val result =
        Await.ready(connector.requestTransmission(request)(HeaderCarrier()),
          10 seconds)
      Await.result(connector.requestTransmission(request)(HeaderCarrier()),
        10 seconds) shouldBe a[MdgRequestFatalError]
    }
  }

}
