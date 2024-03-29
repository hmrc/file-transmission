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

package uk.gov.hmrc.filetransmission.config

import javax.inject.Inject
import org.apache.commons.lang3.StringUtils.isNotBlank
import play.api.Configuration

import scala.concurrent.duration.Duration

trait ServiceConfiguration {

  def allowedUserAgents: Seq[String]

  def queuePollingInterval: Duration

  def queueRetryAfterFailureInterval: Duration

  def inFlightLockDuration: Duration

  def initialBackoffAfterFailure: Duration

  def defaultDeliveryWindowDuration: Duration

  def allowedCallbackProtocols: Seq[String]

  def mdgEndpoint: String

  def mdgAuthorizationToken: String
}

class PlayBasedServiceConfiguration @Inject()(configuration: Configuration)
    extends ServiceConfiguration {

  override def allowedCallbackProtocols: Seq[String] =
    configuration
      .getOptional[String]("callbackValidation.allowedProtocols")
      .map {
        _.split(",").toSeq
          .filter(isNotBlank)
      }
      .getOrElse(Nil)

  override def allowedUserAgents: Seq[String] =
    configuration
      .getOptional[String]("userAgentFilter.allowedUserAgents")
      .map {
        _.split(",").toSeq
          .filter(isNotBlank)
      }
      .getOrElse(Nil)

  override def mdgEndpoint: String =
    getRequired[String](configuration.getOptional[String](_), "mdg.endpoint")
  override def queuePollingInterval: Duration =
    getDuration("queuePollingInterval")
  override def queueRetryAfterFailureInterval: Duration =
    getDuration("queueRetryAfterFailureInterval")
  override def inFlightLockDuration: Duration =
    getDuration("inFlightLockDuration")
  override def initialBackoffAfterFailure: Duration =
    getDuration("initialBackoffAfterFailure")
  override def defaultDeliveryWindowDuration: Duration =
    getDuration("deliveryWindowDuration")

  private def getDuration(key: String) =
    getRequired(configuration.getOptional[Duration](_), key)

  private def getRequired[T](function: String => Option[T], key: String) =
    function(key).getOrElse(
      throw new IllegalStateException(s"Configuration key not found: $key"))

  override def mdgAuthorizationToken: String =
    getRequired[String](configuration.getOptional[String](_),
                        "mdg.authorizationToken")
}
