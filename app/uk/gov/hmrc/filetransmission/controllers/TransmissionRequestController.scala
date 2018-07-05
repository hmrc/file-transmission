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

import javax.inject.{Inject, Singleton}
import play.api.libs.json.{Json, Reads}
import play.api.mvc._
import uk.gov.hmrc.filetransmission.config.ServiceConfiguration
import uk.gov.hmrc.filetransmission.model._
import uk.gov.hmrc.filetransmission.services.TransmissionService
import uk.gov.hmrc.filetransmission.utils.UserAgentFilter
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.ExecutionContext

@Singleton()
class TransmissionRequestController @Inject()(
  transmissionService: TransmissionService,
  override val configuration: ServiceConfiguration)(implicit ec: ExecutionContext)
    extends BaseController
    with UserAgentFilter {

  implicit val fileReads: Reads[File] = Json.reads[File]

  implicit val propertyReads: Reads[Property] = Json.reads[Property]

  implicit val journeyReads: Reads[Journey] = Json.reads[Journey]

  implicit val batchReads: Reads[Batch] = Json.reads[Batch]

  implicit val transmissionRequestReads: Reads[TransmissionRequest] = Json.reads[TransmissionRequest]

  def requestTransmission() = Action.async(parse.json) { implicit request =>
    onlyAllowedServices { serviceName =>
      withJsonBody[TransmissionRequest] { transmissionRequest =>
        for {
          _ <- transmissionService.request(transmissionRequest, serviceName)
        } yield (NoContent)

      }
    }
  }

}
