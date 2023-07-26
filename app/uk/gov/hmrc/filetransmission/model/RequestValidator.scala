/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.filetransmission.model

import java.net.URL

import javax.inject.Inject
import play.api.Logger
import uk.gov.hmrc.filetransmission.config.ServiceConfiguration
import uk.gov.hmrc.filetransmission.utils.LoggingOps.withLoggedContext

class RequestValidator @Inject()(configuration: ServiceConfiguration) {

  private val logger = Logger(getClass)
  private val allowedCallbackProtocols: Seq[String] = configuration.allowedCallbackProtocols

  def validate(request: TransmissionRequest): Either[String, Unit] = {
    withLoggedContext(request) {
      val callbackUrl: URL = request.callbackUrl
      val isAllowedCallbackProtocol: Boolean = allowedCallbackProtocols.contains(request.callbackUrl.getProtocol)

      if (isAllowedCallbackProtocol) Right(())
      else {
        logger.warn(s"Invalid callback url protocol: [$callbackUrl].")
        Left(s"Invalid callback url protocol: [$callbackUrl]. Protocol must be in: [${allowedCallbackProtocols.mkString(",")}].")
      }
    }
  }

}
