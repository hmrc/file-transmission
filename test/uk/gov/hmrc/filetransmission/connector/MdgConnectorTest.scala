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

package uk.gov.hmrc.filetransmission.connector

import java.net.URL

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.typesafe.config.Config
import org.mockito.Mockito
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, GivenWhenThen}
import play.api.libs.ws.ahc.AhcWSClient
import uk.gov.hmrc.filetransmission.config.ServiceConfiguration
import uk.gov.hmrc.filetransmission.model._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.ws.WSHttp
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class MdgConnectorTest extends UnitSpec with GivenWhenThen with MockitoSugar with BeforeAndAfterAll {

  val serviceConfiguration = new ServiceConfiguration {
    override def allowedUserAgents = ???

    override def mdgEndpoint: String = "http://127.0.0.1:11111/mdg"
  }

  val request: TransmissionRequest = TransmissionRequest(
    Batch("A", 10),
    Interface("J", "1.0"),
    File("ref", new URL("http://127.0.0.1/test"), "test.xml", "application/xml", "checksum", 1, 1024),
    Seq(Property("KEY1", "VAL1"), Property("KEY2", "VAL2")),
    new URL("http://127.0.0.1/test"),
    30
  )

  val httpClient = new TestHttpClient()

  val callbackServer = new WireMockServer(wireMockConfig().port(11111))

  override def beforeAll() =
    callbackServer.start()

  override def afterAll() =
    callbackServer.stop()

  private def stubCallbackReceiverToReturnValidResponse(): Unit =
    callbackServer.stubFor(
      post(urlEqualTo("/mdg"))
        .willReturn(
          aResponse()
            .withStatus(204)
        ))

  private def stubCallbackReceiverToReturnInvalidResponse(): Unit =
    callbackServer.stubFor(
      post(urlEqualTo("/mdg"))
        .willReturn(
          aResponse()
            .withStatus(503)
        ))

  "MDG Connector" should {
    "return successful response when call to MDG was successful" in {

      stubCallbackReceiverToReturnValidResponse()

      val serializer = mock[MdgRequestSerializer]
      Mockito.when(serializer.serialize(request)).thenReturn("serializedBody")

      val connector = new MdgConnector(httpClient, serviceConfiguration, serializer)

      val result =
        Await.ready(connector.requestTransmission(request)(HeaderCarrier()), 10 seconds)
      result.value.get.isSuccess shouldBe true
    }

    "return failed response when call to MDG failed" in {
      stubCallbackReceiverToReturnInvalidResponse()

      val serializer = mock[MdgRequestSerializer]
      Mockito.when(serializer.serialize(request)).thenReturn("serializedBody")

      val connector = new MdgConnector(httpClient, serviceConfiguration, serializer)

      val result =
        Await.ready(connector.requestTransmission(request)(HeaderCarrier()), 10 seconds)
      result.value.get.isFailure shouldBe true
    }
  }

}

class TestHttpClient extends HttpClient with WSHttp {
  implicit val system                             = ActorSystem()
  implicit val materializer                       = ActorMaterializer()
  override val wsClient                           = AhcWSClient()
  override lazy val configuration: Option[Config] = None
  override val hooks                              = Seq.empty
}
