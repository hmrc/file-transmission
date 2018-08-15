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
import java.time.{Clock, Instant, ZoneId}

import org.joda.time.DateTime
import org.mockito.Mockito.when
import org.mockito.{ArgumentCaptor, ArgumentMatchers, Mockito}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{GivenWhenThen, Matchers}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.filetransmission.config.ServiceConfiguration
import uk.gov.hmrc.filetransmission.model.TransmissionRequestEnvelope
import uk.gov.hmrc.filetransmission.utils.{JodaTimeConverters, SampleTransmissionRequest}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.workitem
import uk.gov.hmrc.workitem.{Failed, PermanentlyFailed, Succeeded, WorkItem}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class MongoBackedWorkItemServiceSpec extends UnitSpec with Matchers with GivenWhenThen with MockitoSugar {

  "MongoBackedWorkItemService" should {
    "allow to enqueue new transmission request to the queue" in {

      val repository    = mock[TransmissionRequestWorkItemRepository]
      val job           = mock[QueueJob]
      val configuration = mock[ServiceConfiguration]
      val clock         = Clock.fixed(Instant.now(), ZoneId.systemDefault())

      Given("there is a transmission request to process")
      val sampleRequest = SampleTransmissionRequest.get
      val envelope      = TransmissionRequestEnvelope(sampleRequest, "consumingService")

      when(
        repository.pushNew(ArgumentMatchers.any[TransmissionRequestEnvelope](), ArgumentMatchers.any())(
          ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Future.successful(createWorkItem(envelope)))

      val service = new MongoBackedWorkItemService(repository, job, configuration, clock)

      When("the request was enqueued")
      Await.result(service.enqueue(envelope), 10 seconds)

      Then("the request has been stored to MongoDB queue")
      Mockito.verify(repository).pushNew(envelope, JodaTimeConverters.toYoda(clock.instant(), clock.getZone))

    }

    "do nothing and return false if there are no items in the queue" in {
      Given("there are no messages in the queue")
      val repository    = mock[TransmissionRequestWorkItemRepository]
      val job           = mock[QueueJob]
      val configuration = mock[ServiceConfiguration]
      val clock         = Clock.fixed(Instant.now(), ZoneId.systemDefault())

      val service = new MongoBackedWorkItemService(repository, job, configuration, clock)

      when(
        repository.pullOutstanding(ArgumentMatchers.any(), ArgumentMatchers.any())(
          ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Future.successful(None))

      When("requested processing next item from the queue")
      val moreItems = Await.result(service.processOne(), 10 seconds)

      Then("nothing was processed")
      Mockito.verifyZeroInteractions(job)

      And("the service responded that the queue is empty")
      moreItems shouldBe false

    }

    "process first item in the queue and, if processing successful mark is as done" in {
      Given("there are is a request in the queue")
      val repository    = mock[TransmissionRequestWorkItemRepository]
      val job           = mock[QueueJob]
      val configuration = mock[ServiceConfiguration]
      val clock         = Clock.fixed(Instant.now(), ZoneId.systemDefault())

      val service = new MongoBackedWorkItemService(repository, job, configuration, clock)

      val sampleRequest = SampleTransmissionRequest.get
      val envelope      = TransmissionRequestEnvelope(sampleRequest, "consumingService")

      val workItem = createWorkItem(envelope)

      when(
        repository.pullOutstanding(ArgumentMatchers.any(), ArgumentMatchers.any())(
          ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Future.successful(Some(workItem)))

      And("processing for this item ends with success")
      when(job.process(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(ProcessingSuccessful))

      When("requested processing next item from the queue")
      val moreItems = Await.result(service.processOne(), 10 seconds)

      Then("the item has beenprocessed")
      Mockito.verify(job).process(envelope, 0)

      And("item has been marked as done in the database")
      Mockito.verify(repository).complete(workItem.id, Succeeded)

      And("the service responded that the queue wasn't empty")
      moreItems shouldBe true
    }

    "process first item in the queue and, if processing failed mark as failed" in {

      Given("there are is a request in the queue")
      val repository    = mock[TransmissionRequestWorkItemRepository]
      val job           = mock[QueueJob]
      val configuration = mock[ServiceConfiguration]
      val clock         = Clock.fixed(Instant.now(), ZoneId.systemDefault())
      val triesSoFar    = 2

      when(configuration.initialBackoffAfterFailure).thenReturn(10 seconds)

      val service = new MongoBackedWorkItemService(repository, job, configuration, clock)

      val sampleRequest = SampleTransmissionRequest.get
      val envelope      = TransmissionRequestEnvelope(sampleRequest, "consumingService")

      val workItem = createWorkItem(envelope, triesSoFar = triesSoFar)

      when(
        repository.pullOutstanding(ArgumentMatchers.any(), ArgumentMatchers.any())(
          ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Future.successful(Some(workItem)))

      And("processing for this item ends with failure for which we don't want to retry")
      when(job.process(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(ProcessingFailed(new Exception("Planned exception"))))

      When("requested processing next item from the queue")
      val moreItems = Await.result(service.processOne(), 10 seconds)

      And("item has been marked as failed in the database")
      val retryTimeCaptor: ArgumentCaptor[Option[DateTime]] = ArgumentCaptor.forClass(classOf[Option[DateTime]])
      Mockito
        .verify(repository)
        .markAs(ArgumentMatchers.eq(workItem.id), ArgumentMatchers.eq(Failed), retryTimeCaptor.capture())(
          ArgumentMatchers.any())

      And("retry time is 10 seconds * 2 ^ tries so far = 10 * 4 = 40s")
      val retryTime: Instant = JodaTimeConverters.fromYoda(retryTimeCaptor.getValue.get)

      val retryDuration = java.time.Duration.between(clock.instant(), retryTime)

      retryDuration shouldBe java.time.Duration.ofSeconds(40)

      And("the service responded that the queue wasn't empty")
      moreItems shouldBe true

    }

    "process first item in the queue and, if processing failed and we don't expect retries mark as permanently failed" in {

      Given("there are is a request in the queue")
      val repository    = mock[TransmissionRequestWorkItemRepository]
      val job           = mock[QueueJob]
      val configuration = mock[ServiceConfiguration]
      val clock         = Clock.fixed(Instant.now(), ZoneId.systemDefault())

      val service = new MongoBackedWorkItemService(repository, job, configuration, clock)

      val sampleRequest = SampleTransmissionRequest.get
      val envelope      = TransmissionRequestEnvelope(sampleRequest, "consumingService")

      val workItem = createWorkItem(envelope)

      when(
        repository.pullOutstanding(ArgumentMatchers.any(), ArgumentMatchers.any())(
          ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Future.successful(Some(workItem)))

      And("processing for this item ends with failure for which we don't want to retry")
      when(job.process(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(ProcessingFailedDoNotRetry(new Exception("Planned exception"))))

      When("requested processing next item from the queue")
      val moreItems = Await.result(service.processOne(), 10 seconds)

      And("item has been marked as permanently failed in the database")
      Mockito.verify(repository).markAs(workItem.id, PermanentlyFailed, None)

      And("the service responded that the queue wasn't empty")
      moreItems shouldBe true

    }

    def createWorkItem(
      request: TransmissionRequestEnvelope,
      triesSoFar: Int = 0): WorkItem[TransmissionRequestEnvelope] =
      WorkItem(
        BSONObjectID("123412341234123412341234"),
        DateTime.parse("2018-08-15T07:58:10+00:00"),
        DateTime.parse("2018-08-15T07:58:15+00:00"),
        DateTime.parse("2018-08-15T07:58:10+00:00"),
        workitem.ToDo,
        triesSoFar,
        request
      )

  }

}
