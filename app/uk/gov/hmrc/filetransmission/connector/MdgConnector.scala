/*
 * Copyright 2022 HM Revenue & Customs
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

import java.util.UUID

import javax.inject.Inject
import play.api.Logger
import play.api.http.{ContentTypes, HeaderNames, MimeTypes, Status}
import play.mvc.Http
import uk.gov.hmrc.filetransmission.config.ServiceConfiguration
import uk.gov.hmrc.filetransmission.model.TransmissionRequest
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

sealed trait MdgRequestResult {
  def error: Option[String]
}
case object MdgRequestSuccessful extends MdgRequestResult {
  override def error: Option[String] = None
}
case class MdgRequestError(e: String) extends MdgRequestResult {
  override def error: Option[String] = Some(e)
}
case class MdgRequestFatalError(e: String) extends MdgRequestResult {
  override def error: Option[String] = Some(e)
}

class MdgConnector @Inject()(
    httpClient: HttpClient,
    serviceConfiguration: ServiceConfiguration,
    requestSerializer: MdgRequestSerializer)(implicit ec: ExecutionContext) {

  def requestTransmission(request: TransmissionRequest)(
      implicit hc: HeaderCarrier): Future[MdgRequestResult] = {

    val logger = Logger(getClass)
    val serializedRequest: String = requestSerializer.serialize(request)
    val correlationId = generateCorrelationId()
    val headers = buildHeaders(correlationId)

    if (logger.isDebugEnabled) {
      val safeHeaders =
        headers.filterNot(_._1 == Http.HeaderNames.AUTHORIZATION)
      logger.debug(
        s"Sent request to MDG [${serviceConfiguration.mdgEndpoint}] with body [$serializedRequest], headers [$safeHeaders]")
    }

    httpClient.POSTString[HttpResponse](serviceConfiguration.mdgEndpoint,serializedRequest,headers).map { response =>
      response.status match {
        case s if Status.isSuccessful(s) =>
          logger.info(
            s"Sending request for file with reference [${request.file.reference}] was successful. MDG Correlation id [$correlationId]")
          MdgRequestSuccessful

        case s if Status.isClientError(s) =>
          logger.warn(
            s"Sending request for file with reference [${request.file.reference}] failed. MDG Correlation id [$correlationId]. Cause [${response.body}]")
          MdgRequestFatalError(s"POST of '${serviceConfiguration.mdgEndpoint}' returned status $s. Response body: '${response.body}'")

        case s =>
          logger.warn(
            s"Sending request for file with reference [${request.file.reference}] failed. MDG Correlation id [$correlationId]. Cause [${response.body}]")
          MdgRequestError(s"POST of '${serviceConfiguration.mdgEndpoint}' returned status $s. Response body: '${response.body}'")
      }
    }
  }

  private def generateCorrelationId(): String = UUID.randomUUID().toString

  private def buildHeaders(correlationId: String) =
    Seq(
      (HeaderNames.CONTENT_TYPE, ContentTypes.XML),
      (HeaderNames.ACCEPT, MimeTypes.XML),
      (HeaderNames.AUTHORIZATION,
       s"Bearer ${serviceConfiguration.mdgAuthorizationToken}"),
      "X-Correlation-ID" -> correlationId
    )

}
