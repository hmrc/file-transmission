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

package uk.gov.hmrc.filetransmission.utils

import java.net.URL

import play.api.Logger
import play.api.mvc.Results.BadRequest
import play.api.mvc.Result
import uk.gov.hmrc.filetransmission.config.ServiceConfiguration

import scala.concurrent.Future

trait CallbackProtocolFilter {

  protected val configuration: ServiceConfiguration

  private val allowedCallbackProtocols: Seq[String] =
    configuration.allowedCallbackProtocols

  def onlyValidCallbackProtocols(callbackUrl: URL)(block: => Future[Result]): Future[Result] = {

    val isAllowedCallbackProtocol: Boolean = allowedCallbackProtocols.contains(callbackUrl.getProtocol)

    if (isAllowedCallbackProtocol) block
    else {
      Logger.warn(s"Invalid callback url protocol: [$callbackUrl].")

      Future.successful(BadRequest(
        s"Invalid callback url protocol: [$callbackUrl]. Protocol must be in: [$allowedCallbackProtocols]."))
    }
  }

}
