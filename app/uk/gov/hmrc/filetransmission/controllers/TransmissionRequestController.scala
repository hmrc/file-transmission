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
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.filetransmission.config.ServiceConfiguration
import uk.gov.hmrc.filetransmission.model._
import uk.gov.hmrc.filetransmission.services.queue.WorkItemService
import uk.gov.hmrc.filetransmission.utils.UserAgentFilter
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class TransmissionRequestController @Inject()(
    workItemService: WorkItemService,
    requestValidator: RequestValidator,
    override val configuration: ServiceConfiguration)(
    implicit ec: ExecutionContext)
    extends BaseController
    with UserAgentFilter {

  def requestTransmission() = Action.async(parse.json) {
    implicit request: Request[JsValue] =>
      onlyAllowedServices { serviceName =>
        withJsonBody[TransmissionRequest] { transmissionRequest =>
          requestValidator.validate(transmissionRequest) match {
            case Left(e) => Future.successful(BadRequest(e))
            case _ =>
              val envelope =
                TransmissionRequestEnvelope(transmissionRequest, serviceName)
              workItemService.enqueue(envelope).map(_ => NoContent)
          }
        }
      }
  }

  def clearRequestQueue() = Action.async {
    workItemService.clearQueue().map(cleared => Ok(Json.parse(s"""{"cleared":"$cleared"}""")))
  }
}

