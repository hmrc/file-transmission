/*
 * Copyright 2022 HM Revenue & Customs
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

import org.bson.types.ObjectId
import org.mockito.{ArgumentCaptor, Mockito, MockitoSugar, ArgumentMatchersSugar}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, GivenWhenThen}

import uk.gov.hmrc.filetransmission.config.ServiceConfiguration
import uk.gov.hmrc.filetransmission.model.TransmissionRequestEnvelope
import uk.gov.hmrc.filetransmission.utils.SampleTransmissionRequest
import uk.gov.hmrc.mongo.workitem.{WorkItem, ProcessingStatus}

import java.time.{Clock, Instant, ZoneId}
import scala.concurrent.duration._
import scala.concurrent.Future

class MongoBackedWorkItemServiceSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen
    with MockitoSugar
    with ArgumentMatchersSugar
    with BeforeAndAfterEach
    with ScalaFutures {

  import scala.concurrent.ExecutionContext.Implicits.global

  val configuration: ServiceConfiguration = mock[ServiceConfiguration]

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(
    timeout = scaled(Span(10, Seconds)),
    interval = scaled(Span(150, Millis))
  )

  override def beforeEach() = {
    when(configuration.initialBackoffAfterFailure).thenReturn(10.seconds)
    when(configuration.defaultDeliveryWindowDuration).thenReturn(10.minutes)
  }

  "MongoBackedWorkItemService" should {
    "allow to enqueue new transmission request to the queue" in {
      val repository = mock[TransmissionRequestWorkItemRepository]
      val job = mock[QueueJob]
      val clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())

      Given("there is a transmission request to process")
      val sampleRequest = SampleTransmissionRequest.get
      val envelope =
        TransmissionRequestEnvelope(sampleRequest, "consumingService")

      when(repository.pushNew(any[TransmissionRequestEnvelope], any, any[TransmissionRequestEnvelope => ProcessingStatus]))
        .thenReturn(Future.successful(createWorkItem(envelope)))
      when(repository.updateWorkItemBodyDeliveryAttempts(any[ObjectId], any[TransmissionRequestEnvelope]))
        .thenReturn(Future.successful(true))

      val service =
        new MongoBackedWorkItemService(repository, job, configuration, clock)

      When("the request was enqueued")
      service.enqueue(envelope).futureValue

      Then("the request has been stored to MongoDB queue")
      Mockito
        .verify(repository)
        .pushNew(eqTo(envelope), any[Instant], any[TransmissionRequestEnvelope => ProcessingStatus])

    }

    "do nothing and return false if there are no items in the queue" in {
      Given("there are no messages in the queue")
      val repository = mock[TransmissionRequestWorkItemRepository]
      val job = mock[QueueJob]
      val clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())

      val service =
        new MongoBackedWorkItemService(repository, job, configuration, clock)

      when(repository.pullOutstanding(any, any))
        .thenReturn(Future.successful(None))

      When("requested processing next item from the queue")
      val moreItems = service.processOne().futureValue

      Then("nothing was processed")
      Mockito.verifyNoInteractions(job)

      And("the service responded that the queue is empty")
      moreItems shouldBe false

    }

    "process first item in the queue and, if processing successful mark is as done" in {
      Given("there are is a request in the queue")
      val repository = mock[TransmissionRequestWorkItemRepository]
      val job = mock[QueueJob]
      val clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())

      val service =
        new MongoBackedWorkItemService(repository, job, configuration, clock)

      val sampleRequest = SampleTransmissionRequest.get
      val envelope =
        TransmissionRequestEnvelope(sampleRequest, "consumingService")

      val workItem = createWorkItem(envelope)

      when(repository.pullOutstanding(any, any))
        .thenReturn(Future.successful(Some(workItem)))

      And("processing for this item ends with success")
      when(job.process(any, any, any))
        .thenReturn(Future.successful(ProcessingSuccessful))

      When("requested processing next item from the queue")
      val moreItems = service.processOne().futureValue

      Then("the item has been processed (and we assume we can retry it)")
      Mockito.verify(job).process(eqTo(envelope), any, any)

      And("item has been marked as done in the database")
      Mockito.verify(repository).complete(workItem.id, ProcessingStatus.Succeeded)

      And("the service responded that the queue wasn't empty")
      moreItems shouldBe true
    }

    "process first item in the queue and, if processing failed mark as failed" in {

      Given("there are is a request in the queue without custom retry time")
      val repository = mock[TransmissionRequestWorkItemRepository]
      val job = mock[QueueJob]
      val clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
      val triesSoFar = 2

      val service =
        new MongoBackedWorkItemService(repository, job, configuration, clock)

      val sampleRequest =
        SampleTransmissionRequest.get
      val envelope =
        TransmissionRequestEnvelope(sampleRequest, "consumingService")

      val workItem = createWorkItem(envelope, retrySoFar = triesSoFar)

      when(repository.pullOutstanding(any, any))
        .thenReturn(Future.successful(Some(workItem)))
      when(repository.markAs(any[ObjectId], any[ProcessingStatus], any[Option[Instant]]))
        .thenReturn(Future.successful(true))
      when(repository.updateWorkItemBodyDeliveryAttempts(any[ObjectId], any[TransmissionRequestEnvelope]))
        .thenReturn(Future.successful(true))

      And(
        "processing for this item ends with failure for which we don't want to retry")
      when(job.process(any, any, any))
        .thenReturn(Future.successful(
          ProcessingFailed("client error")))

      When("requested processing next item from the queue")
      val moreItems = service.processOne().futureValue

      And("item has been marked as failed in the database")
      val retryTimeCaptor: ArgumentCaptor[Option[Instant]] =
        ArgumentCaptor.forClass(classOf[Option[Instant]])
      Mockito
        .verify(repository)
        .markAs(
          eqTo(workItem.id),
          eqTo(ProcessingStatus.Failed),
          retryTimeCaptor.capture()
        )

      And("retry time is 10.seconds * 2 ^ tries so far = 10 * 4 = 40s")
      val retryTime: Instant = retryTimeCaptor.getValue.get

      val retryDuration = java.time.Duration.between(clock.instant(), retryTime)

      retryDuration shouldBe java.time.Duration.ofSeconds(40)

      And("the service responded that the queue wasn't empty")
      moreItems shouldBe true

    }

    "process first item in the queue and, if processing failed and we don't expect retries mark as permanently failed" in {

      Given("there are is a request in the queue")
      val repository = mock[TransmissionRequestWorkItemRepository]
      val job = mock[QueueJob]
      val clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())

      val service =
        new MongoBackedWorkItemService(repository, job, configuration, clock)

      val sampleRequest = SampleTransmissionRequest.get
      val envelope =
        TransmissionRequestEnvelope(sampleRequest, "consumingService")

      val workItem = createWorkItem(envelope)

      when(repository.pullOutstanding(any, any))
        .thenReturn(Future.successful(Some(workItem)))
      when(repository.markAs(any[ObjectId], any[ProcessingStatus], any[Option[Instant]]))
        .thenReturn(Future.successful(true))
      when(repository.updateWorkItemBodyDeliveryAttempts(any[ObjectId], any[TransmissionRequestEnvelope]))
        .thenReturn(Future.successful(true))

      And(
        "processing for this item ends with failure for which we don't want to retry")
      when(job.process(any, any, any))
        .thenReturn(Future.successful(
          ProcessingFailedDoNotRetry("client error")))

      When("requested processing next item from the queue")
      val moreItems = service.processOne().futureValue

      And("item has been marked as permanently failed in the database")
      Mockito.verify(repository).markAs(workItem.id, ProcessingStatus.PermanentlyFailed, None)

      And("the service responded that the queue wasn't empty")
      moreItems shouldBe true

    }

    "process first item in the queue and, if processing failed and retried not allowed because of time window expired, mark as permanently failed" in {

      Given("there are is a request in the queue")
      val repository = mock[TransmissionRequestWorkItemRepository]
      val job = mock[QueueJob]
      val clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())

      val service =
        new MongoBackedWorkItemService(repository, job, configuration, clock)

      val sampleRequest = SampleTransmissionRequest.get
      val envelope =
        TransmissionRequestEnvelope(sampleRequest, "consumingService")

      val workItem = createWorkItem(envelope)

      when(repository.pullOutstanding(any, any))
        .thenReturn(Future.successful(Some(workItem)))
      when(repository.markAs(any[ObjectId], any[ProcessingStatus], any[Option[Instant]]))
        .thenReturn(Future.successful(true))
      when(repository.updateWorkItemBodyDeliveryAttempts(any[ObjectId], any[TransmissionRequestEnvelope]))
        .thenReturn(Future.successful(true))

      And(
        "processing for this item ends with failure for which we don't want to retry")
      when(job.process(any, any, any))
        .thenReturn(Future.successful(
          ProcessingFailedDoNotRetry("client error")))

      When("requested processing next item from the queue")
      val moreItems = service.processOne().futureValue

      And("item has been marked as permanently failed in the database")
      Mockito.verify(repository).markAs(workItem.id, ProcessingStatus.PermanentlyFailed, None)

      And("the service responded that the queue wasn't empty")
      moreItems shouldBe true

    }

    def createWorkItem(
        request: TransmissionRequestEnvelope,
        creationTime: Instant = Instant.now(),
        retrySoFar: Int = 0): WorkItem[TransmissionRequestEnvelope] =
      WorkItem(
        ObjectId.get(),
        creationTime,
        Instant.now(),
        Instant.now(),
        ProcessingStatus.ToDo,
        retrySoFar,
        request
      )

  }

}
