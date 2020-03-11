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

package uk.gov.hmrc.filetransmission.utils

import play.api.Logger
import play.api.http.HeaderNames
import play.api.mvc.{Request, Result}
import play.api.mvc.Results.Forbidden
import uk.gov.hmrc.filetransmission.config.ServiceConfiguration

import scala.concurrent.Future

trait UserAgentFilter {

  protected val configuration: ServiceConfiguration

  private val userAgents: Seq[String] = configuration.allowedUserAgents

  def onlyAllowedServices(block: String => Future[Result])(implicit request: Request[_]): Future[Result] =
    request.headers.get(HeaderNames.USER_AGENT) match {
      case Some(userAgent) if allowedUserAgent(userAgent) =>
        block(userAgent)
      case _ => {
        Logger.warn(s"Invalid User-Agent: [${request.headers.get(HeaderNames.USER_AGENT)}].")

        Future.successful(
          Forbidden(
            "This service is not allowed to use file-transmission. " +
              "If you need to use this service, please contact Platform Services team."))
      }
    }

  private def allowedUserAgent(userAgent: String): Boolean =
    userAgents.contains(userAgent)
}
