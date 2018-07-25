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

import org.mockito.ArgumentMatchers.any
import org.scalatest.GivenWhenThen
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.filetransmission.config.ServiceConfiguration
import uk.gov.hmrc.filetransmission.model._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await

class MdgConnectorTest extends UnitSpec with GivenWhenThen with MockitoSugar {

  "MDG Connector" should {
    "make successfull call to MDG" ignore {

      val serviceConfiguration = new ServiceConfiguration {
        override def allowedUserAgents = ???

        override def mdgEndpoint: String = "http://127.0.0.1/mdg"
      }

      val request: TransmissionRequest = TransmissionRequest(
        Batch("A", 10),
        Interface("J", "1.0"),
        File("ref", new URL("http://127.0.0.1/test"), "test.xml", "application/xml", "checksum", 1, 1024),
        Seq(Property("KEY1", "VAL1"), Property("KEY2", "VAL2")),
        new URL("http://127.0.0.1/test"),
        30
      )

      val httpClient = mock[HttpClient]
      val serializer = mock[MdgRequestSerializer]

      val connector = new MdgConnector(httpClient, serviceConfiguration, serializer)

      val result = Await.ready(connector.requestTransmission(request)(any()), 10 seconds)
      //TODO
    }
  }

}
