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

package uk.gov.hmrc.filetransmission.config

import javax.inject.Inject
import org.apache.commons.lang3.StringUtils.isNotBlank
import play.api.Configuration

trait ServiceConfiguration {
  def allowedUserAgents: Seq[String]
  def allowedCallbackProtocols: Seq[String]
  def mdgEndpoint: String
}

class PlayBasedServiceConfiguration @Inject()(configuration: Configuration) extends ServiceConfiguration {

  override def allowedCallbackProtocols: Seq[String] =
    configuration
      .getString("callbackValidation.allowedProtocols")
      .map {
        _.split(",").toSeq
          .filter(isNotBlank)
      }
      .getOrElse(Nil)

  override def allowedUserAgents: Seq[String] =
    configuration
      .getString("userAgentFilter.allowedUserAgents")
      .map {
        _.split(",").toSeq
          .filter(isNotBlank)
      }
      .getOrElse(Nil)

  override def mdgEndpoint: String =
    configuration.getString("mdgEndpoint").getOrElse(throw new RuntimeException("'mdgEndpoint' property is missing"))
}
