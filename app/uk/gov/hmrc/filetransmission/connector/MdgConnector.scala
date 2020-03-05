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

package uk.gov.hmrc.filetransmission.connector

import java.util.UUID

import cats.implicits._
import javax.inject.Inject
import play.api.Logger
import play.api.http.{ContentTypes, HeaderNames, MimeTypes}
import play.mvc.Http
import uk.gov.hmrc.filetransmission.config.ServiceConfiguration
import uk.gov.hmrc.filetransmission.model.TransmissionRequest
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

sealed trait MdgRequestResult {
  def error: Option[Throwable]
}
case object MdgRequestSuccessful extends MdgRequestResult {
  override def error = None
}
case class MdgRequestError(e: Throwable) extends MdgRequestResult {
  override def error = Some(e)
}
case class MdgRequestFatalError(e: Throwable) extends MdgRequestResult {
  override def error = Some(e)
}

class MdgConnector @Inject()(
    httpClient: HttpClient,
    serviceConfiguration: ServiceConfiguration,
    requestSerializer: MdgRequestSerializer)(implicit ec: ExecutionContext) {

  def requestTransmission(request: TransmissionRequest)(
      implicit hc: HeaderCarrier): Future[MdgRequestResult] = {

    val serializedRequest: String = requestSerializer.serialize(request)
    val correlationId = generateCorrelationId()
    val headers = buildHeaders(correlationId)

    if (Logger.isDebugEnabled) {
      val safeHeaders =
        headers.filterNot(_._1 == Http.HeaderNames.AUTHORIZATION)
      Logger.debug(
        s"Sent request to MDG [${serviceConfiguration.mdgEndpoint}] with body [$serializedRequest], headers [$safeHeaders]")
    }

    for (result <- httpClient
           .POSTString[HttpResponse](serviceConfiguration.mdgEndpoint,
                                     serializedRequest,
                                     headers)
           .attempt) yield {
      result match {
        case Right(_) =>
          Logger.info(
            s"Sending request for file with reference [${request.file.reference}] was successful. MDG Correlation id [$correlationId]")
          MdgRequestSuccessful
        case Left(e: BadRequestException) =>
          Logger.warn(
            s"Sending request for file with reference [${request.file.reference}] failed. MDG Correlation id [$correlationId]. Cause [$e]",
            e)
          MdgRequestFatalError(e)
        case Left(e) =>
          Logger.warn(
            s"Sending request for file with reference [${request.file.reference}] failed. MDG Correlation id [$correlationId]. Cause [$e]",
            e)
          MdgRequestError(e)
      }
    }

  }

  private def generateCorrelationId() = UUID.randomUUID().toString

  private def buildHeaders(correlationId: String) =
    Seq(
      (HeaderNames.CONTENT_TYPE, ContentTypes.XML),
      (HeaderNames.ACCEPT, MimeTypes.XML),
      (HeaderNames.AUTHORIZATION,
       s"Bearer ${serviceConfiguration.mdgAuthorizationToken}"),
      "X-Correlation-ID" -> correlationId
    )

}
