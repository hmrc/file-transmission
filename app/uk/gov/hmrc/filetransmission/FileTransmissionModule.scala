/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.filetransmission

import java.time.Clock

import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.filetransmission.config.{PlayBasedServiceConfiguration, ServiceConfiguration}
import uk.gov.hmrc.filetransmission.connector.HttpCallbackSender
import uk.gov.hmrc.filetransmission.services.queue.{MongoBackedWorkItemService, QueueJob, WorkItemProcessingScheduler, WorkItemService}
import uk.gov.hmrc.filetransmission.services.{CallbackSender, RetryQueueBackedTransmissionService, TransmissionRequestProcessingJob, TransmissionService}

class FileTransmissionModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    Seq(
      bind[ServiceConfiguration].to[PlayBasedServiceConfiguration],
      bind[CallbackSender].to[HttpCallbackSender],
      bind[QueueJob].to[TransmissionRequestProcessingJob],
      bind[WorkItemProcessingScheduler].toSelf.eagerly(),
      bind[WorkItemService].to[MongoBackedWorkItemService],
      bind[TransmissionService].to[RetryQueueBackedTransmissionService],
      bind[Clock].toInstance(Clock.systemUTC())
    )
}
