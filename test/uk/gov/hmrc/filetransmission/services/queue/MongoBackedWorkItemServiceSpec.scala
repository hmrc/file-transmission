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

import java.time.{Clock, Instant, ZoneId}
import java.time.temporal.ChronoUnit

import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentCaptor, ArgumentMatchers, Mockito, MockitoSugar}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, GivenWhenThen}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.filetransmission.config.ServiceConfiguration
import uk.gov.hmrc.filetransmission.model.TransmissionRequestEnvelope
import uk.gov.hmrc.filetransmission.utils.JodaTimeConverters.{ClockJodaExtensions, fromJoda}
import uk.gov.hmrc.filetransmission.utils.SampleTransmissionRequest
import uk.gov.hmrc.workitem
import uk.gov.hmrc.workitem._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class MongoBackedWorkItemServiceSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen
    with MockitoSugar
    with BeforeAndAfterEach
    with ScalaFutures {

  val configuration: ServiceConfiguration = mock[ServiceConfiguration]

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(
    timeout = scaled(Span(10, Seconds)),
    interval = scaled(Span(150, Millis))
  )

  override def beforeEach() {
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

      when(
        repository.pushNew(
          any[TransmissionRequestEnvelope](),
          any())(any[ExecutionContext]))
        .thenReturn(Future.successful(createWorkItem(envelope)))

      when(repository.updateWorkItemBodyDeliveryAttempts(any[BSONObjectID], any[TransmissionRequestEnvelope]))
        .thenReturn(Future.successful(true))

      val service =
        new MongoBackedWorkItemService(repository, job, configuration, clock)

      When("the request was enqueued")
      service.enqueue(envelope).futureValue

      Then("the request has been stored to MongoDB queue")
      Mockito
        .verify(repository)
        .pushNew(envelope, clock.nowAsJoda)

    }

    "do nothing and return false if there are no items in the queue" in {
      Given("there are no messages in the queue")
      val repository = mock[TransmissionRequestWorkItemRepository]
      val job = mock[QueueJob]
      val clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())

      val service =
        new MongoBackedWorkItemService(repository, job, configuration, clock)

      when(
        repository.pullOutstanding(
          any(),
          any())(any[ExecutionContext]))
        .thenReturn(Future.successful(None))

      When("requested processing next item from the queue")
      val moreItems = service.processOne().futureValue

      Then("nothing was processed")
      Mockito.verifyZeroInteractions(job)

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

      when(
        repository.pullOutstanding(
          any(),
          any())(any[ExecutionContext]))
        .thenReturn(Future.successful(Some(workItem)))

      And("processing for this item ends with success")
      when(job.process(any(), any(), any()))
        .thenReturn(Future.successful(ProcessingSuccessful))

      When("requested processing next item from the queue")
      val moreItems = service.processOne().futureValue

      Then("the item has been processed (and we assume we can retry it)")
      Mockito.verify(job).process(ArgumentMatchers.eq(envelope), any(), any())

      And("item has been marked as done in the database")
      Mockito.verify(repository).complete(workItem.id, Succeeded)

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

      when(
        repository.pullOutstanding(
          any(),
          any())(any[ExecutionContext]))
        .thenReturn(Future.successful(Some(workItem)))

      when(repository.markAs(any[BSONObjectID], any[ProcessingStatus], any[Option[DateTime]])(any[ExecutionContext]))
          .thenReturn(Future.successful(true))
      when(repository.updateWorkItemBodyDeliveryAttempts(any[BSONObjectID], any[TransmissionRequestEnvelope]))
        .thenReturn(Future.successful(true))

      And(
        "processing for this item ends with failure for which we don't want to retry")
      when(job.process(any(), any(), any()))
        .thenReturn(Future.successful(
          ProcessingFailed("client error")))

      When("requested processing next item from the queue")
      val moreItems = service.processOne().futureValue

      And("item has been marked as failed in the database")
      val retryTimeCaptor: ArgumentCaptor[Option[DateTime]] =
        ArgumentCaptor.forClass(classOf[Option[DateTime]])
      Mockito
        .verify(repository)
        .markAs(ArgumentMatchers.eq(workItem.id),
                ArgumentMatchers.eq(Failed),
                retryTimeCaptor.capture())(any())

      And("retry time is 10.seconds * 2 ^ tries so far = 10 * 4 = 40s")
      val retryTime: Instant = retryTimeCaptor.getValue.get

      val retryDuration = java.time.Duration.between(clock.instant().truncatedTo(ChronoUnit.MILLIS), retryTime)

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

      when(
        repository.pullOutstanding(
          any(),
          any())(any[ExecutionContext]))
        .thenReturn(Future.successful(Some(workItem)))

      when(repository.markAs(any[BSONObjectID], any[ProcessingStatus], any[Option[DateTime]])(any[ExecutionContext]))
        .thenReturn(Future.successful(true))
      when(repository.updateWorkItemBodyDeliveryAttempts(any[BSONObjectID], any[TransmissionRequestEnvelope]))
        .thenReturn(Future.successful(true))

      And(
        "processing for this item ends with failure for which we don't want to retry")
      when(job.process(any(), any(), any()))
        .thenReturn(Future.successful(
          ProcessingFailedDoNotRetry("client error")))

      When("requested processing next item from the queue")
      val moreItems = service.processOne().futureValue

      And("item has been marked as permanently failed in the database")
      Mockito.verify(repository).markAs(workItem.id, PermanentlyFailed, None)

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

      when(
        repository.pullOutstanding(
          any(),
          any())(any[ExecutionContext]))
        .thenReturn(Future.successful(Some(workItem)))

      when(repository.markAs(any[BSONObjectID], any[ProcessingStatus], any[Option[DateTime]])(any[ExecutionContext]))
        .thenReturn(Future.successful(true))
      when(repository.updateWorkItemBodyDeliveryAttempts(any[BSONObjectID], any[TransmissionRequestEnvelope]))
        .thenReturn(Future.successful(true))

      And(
        "processing for this item ends with failure for which we don't want to retry")
      when(job.process(any(), any(), any()))
        .thenReturn(Future.successful(
          ProcessingFailedDoNotRetry("client error")))

      When("requested processing next item from the queue")
      val moreItems = service.processOne().futureValue

      And("item has been marked as permanently failed in the database")
      Mockito.verify(repository).markAs(workItem.id, PermanentlyFailed, None)

      And("the service responded that the queue wasn't empty")
      moreItems shouldBe true

    }

    def createWorkItem(
        request: TransmissionRequestEnvelope,
        creationTime: DateTime = DateTime.now(),
        retrySoFar: Int = 0): WorkItem[TransmissionRequestEnvelope] =
      WorkItem(
        BSONObjectID.generate(),
        creationTime,
        DateTime.now(),
        DateTime.now(),
        workitem.ToDo,
        retrySoFar,
        request
      )

  }

}
