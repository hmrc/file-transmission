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

import java.util.concurrent.TimeUnit

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

  def maxRetryCount: Int

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

  override def mdgEndpoint: String                      = getRequired[String](configuration.getString(_, None), "mdgEndpoint")
  override def queuePollingInterval: Duration           = getDuration("queuePollingInterval")
  override def queueRetryAfterFailureInterval: Duration = getDuration("queueRetryAfterFailureInterval")
  override def inFlightLockDuration: Duration           = getDuration("inFlightLockDuration")
  override def initialBackoffAfterFailure: Duration     = getDuration("initialBackoffAfterFailure")
  override def maxRetryCount: Int                       = getRequired[Int](configuration.getInt, "maxRetryCount")

  private def getDuration(key: String) =
    Duration(getRequired[Long](configuration.getMilliseconds, key), TimeUnit.MILLISECONDS)

  private def getRequired[T](function: String => Option[T], key: String) =
    function(key).getOrElse(throw new IllegalStateException(s"Configuration key not found: $key"))
}
