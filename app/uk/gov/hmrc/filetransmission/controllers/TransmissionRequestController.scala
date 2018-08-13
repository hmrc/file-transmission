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

import java.net.URL

import javax.inject.{Inject, Singleton}
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.filetransmission.config.ServiceConfiguration
import uk.gov.hmrc.filetransmission.model._
import uk.gov.hmrc.filetransmission.services.queue.RetryQueue
import uk.gov.hmrc.filetransmission.utils.{HttpUrlFormat, UserAgentFilter}
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}
@Singleton()
class TransmissionRequestController @Inject()(
  transmissionService: RetryQueue,
  requestValidator: RequestValidator,
  override val configuration: ServiceConfiguration)(implicit ec: ExecutionContext)
    extends BaseController
    with UserAgentFilter {

  implicit val urlReads: Reads[URL] = HttpUrlFormat

  implicit val fileReads: Reads[File] = Json.reads[File]

  implicit val propertyReads: Reads[Property] = Json.reads[Property]

  implicit val interfaceReads: Reads[Interface] = Json.reads[Interface]

  implicit val batchReads: Reads[Batch] = Json.reads[Batch]

  implicit val transmissionRequestReads: Reads[TransmissionRequest] = Json.reads[TransmissionRequest]

  def requestTransmission() = Action.async(parse.json) { implicit request: Request[JsValue] =>
    onlyAllowedServices { serviceName =>
      withJsonBody[TransmissionRequest] { transmissionRequest =>
        requestValidator.validate(transmissionRequest) match {
          case Left(e) => Future.successful(BadRequest(e))
          case _ =>
            for {
              _ <- transmissionService.enqueue(TransmissionRequestEnvelope(transmissionRequest, serviceName))
            } yield NoContent
        }
      }
    }
  }

}
