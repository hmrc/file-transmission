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

import javax.inject.Inject
import play.api.http.{ContentTypes, HeaderNames}
import uk.gov.hmrc.filetransmission.config.ServiceConfiguration
import uk.gov.hmrc.filetransmission.model.TransmissionRequest
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

class MdgConnector @Inject()(
  httpClient: HttpClient,
  serviceConfiguration: ServiceConfiguration,
  requestSerializer: MdgRequestSerializer)(implicit ec: ExecutionContext) {

  def requestTransmission(request: TransmissionRequest)(implicit hc: HeaderCarrier): Future[Unit] = {

    val serializedRequest: String = requestSerializer.serialize(request)
    httpClient
      .POSTString[HttpResponse](
        serviceConfiguration.mdgEndpoint,
        serializedRequest,
        Seq((HeaderNames.CONTENT_TYPE, ContentTypes.XML)))
      .map(_ => ())
  }

}
