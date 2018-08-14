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

package uk.gov.hmrc.filetransmission.services.queue

import akka.actor.{Actor, ActorSystem, PoisonPill, Props}
import akka.event.Logging
import javax.inject.Inject
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.filetransmission.config.ServiceConfiguration

import scala.concurrent.Future
import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.{Failure, Success}

class WorkItemProcessingScheduler @Inject()(queueProcessor: WorkItemService, configuration: ServiceConfiguration)(
  implicit actorSystem: ActorSystem,
  applicationLifecycle: ApplicationLifecycle) {

  val pollingInterval: FiniteDuration = FiniteDuration.apply(configuration.queuePollingInterval.toMillis, MILLISECONDS)

  val retryAfterFailureInterval: FiniteDuration =
    FiniteDuration.apply(configuration.queueRetryAfterFailureInterval.toMillis, MILLISECONDS)

  case object Poll

  class ContinuousPollingActor extends Actor {

    import context.dispatcher

    val log = Logging(context.system, this)

    override def receive: Receive = {

      case Poll =>
        queueProcessor.processOne() andThen {
          case Success(true) =>
            self ! Poll
          case Success(false) =>
            context.system.scheduler.scheduleOnce(pollingInterval, self, Poll)
          case Failure(f) =>
            log.error(f, s"Queue processing failed")
            context.system.scheduler.scheduleOnce(retryAfterFailureInterval, self, Poll)
        }
    }

  }

  private val pollingActor = actorSystem.actorOf(Props(new ContinuousPollingActor()), "Poller")

  pollingActor ! Poll

  applicationLifecycle.addStopHook { () =>
    pollingActor ! PoisonPill
    Future.successful(())
  }

}
