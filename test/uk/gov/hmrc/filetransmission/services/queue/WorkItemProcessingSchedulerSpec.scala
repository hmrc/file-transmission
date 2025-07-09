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

package uk.gov.hmrc.filetransmission.services.queue

import org.apache.pekko.actor.ActorSystem
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.GivenWhenThen
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.PatienceConfiguration.{Interval, Timeout}
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.filetransmission.config.ServiceConfiguration
import uk.gov.hmrc.filetransmission.model.TransmissionRequestEnvelope

import scala.concurrent.Future
import scala.concurrent.duration.{Duration, _}

class WorkItemProcessingSchedulerSpec
  extends AnyWordSpec
     with Matchers
     with GivenWhenThen
     with MockitoSugar
     with Eventually {

  class MockWorkItemService extends WorkItemService {
    var remainingItems = 0

    var processedItems = 0

    var shouldFail = false

    def addRemainingItems(count: Int): Unit =
      synchronized {
        remainingItems = remainingItems + count
      }

    def simulateFailure(): Unit = synchronized {
      shouldFail = true
    }

    override def enqueue(request: TransmissionRequestEnvelope): Future[Unit] =
      ???

    override def processOne(): Future[Boolean] =
      synchronized {
        if (shouldFail) {
          shouldFail = false
          Future.failed(Exception("planned failure"))
        } else if (remainingItems > 0) {
          remainingItems = remainingItems - 1
          processedItems = processedItems + 1
          Future.successful(true)
        } else
          Future.successful(false)
      }

    override def clearQueue(): Future[Boolean] = {
      remainingItems = 0
      Future.successful(true)
    }
  }

  given ActorSystem = ActorSystem.create()

  given ApplicationLifecycle = new ApplicationLifecycle {
    override def addStopHook(hook: () => Future[_]): Unit =
      ()

    override def stop(): Future[_] =
      Future.successful(())
  }

  "scheduler" should {
    val configuration = new ServiceConfiguration {
      override def mdgEndpoint: String = ???
      override def allowedUserAgents: Seq[String] = ???
      override def queuePollingInterval: Duration = 1.second
      override def queueRetryAfterFailureInterval: Duration = 2.seconds
      override def inFlightLockDuration: Duration = ???
      override def initialBackoffAfterFailure: Duration = ???
      override def allowedCallbackProtocols: Seq[String] = ???
      override def defaultDeliveryWindowDuration: Duration = ???

      override def mdgAuthorizationToken: String = ???
    }

    "process all requests waiting in the queue" in {
      val workItemService = MockWorkItemService()

      workItemService.addRemainingItems(3)

      val workItemProcessingScheduler =
        WorkItemProcessingScheduler(workItemService, configuration)

      eventually(Timeout(scaled(Span(2, Seconds))),
                 Interval(scaled(Span(200, Millis)))) {
        workItemService.processedItems == 3
      }

      workItemProcessingScheduler.shutDown()
    }

    "continue polling after a while when there are no messages in the queue" in {
      val workItemService = MockWorkItemService()

      workItemService.addRemainingItems(3)

      val workItemProcessingScheduler =
        WorkItemProcessingScheduler(workItemService, configuration)

      eventually(Timeout(scaled(Span(2, Seconds))),
                 Interval(scaled(Span(200, Millis)))) {
        workItemService.processedItems shouldBe 3
      }

      Thread.sleep(1000)

      workItemService.addRemainingItems(3)

      eventually(Timeout(scaled(Span(2, Seconds))),
                 Interval(scaled(Span(200, Millis)))) {
        workItemService.processedItems shouldBe 6
      }

      workItemProcessingScheduler.shutDown()
    }

    "continue polling after a while when there was an error processing the message" in {
      val workItemService = MockWorkItemService()

      workItemService.addRemainingItems(3)

      val workItemProcessingScheduler =
        WorkItemProcessingScheduler(workItemService, configuration)

      eventually(Timeout(scaled(Span(2, Seconds))),
                 Interval(scaled(Span(200, Millis)))) {
        workItemService.processedItems shouldBe 3
      }

      workItemService.simulateFailure()

      workItemService.addRemainingItems(3)

      eventually(Timeout(scaled(Span(5, Seconds))),
                 Interval(scaled(Span(200, Millis)))) {
        workItemService.processedItems shouldBe 6
      }

      workItemProcessingScheduler.shutDown()
    }
  }
}
