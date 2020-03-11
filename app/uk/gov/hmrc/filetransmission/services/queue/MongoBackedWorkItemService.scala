/*
 * Copyright 2020 HM Revenue & Customs
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

import cats.data.OptionT
import cats.implicits._
import java.time.{Clock, Instant}

import javax.inject.Inject
import org.joda.time.DateTime
import uk.gov.hmrc.workitem.{WorkItem, _}

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.workitem._
import uk.gov.hmrc.filetransmission.config.ServiceConfiguration
import uk.gov.hmrc.filetransmission.model.{FailedDeliveryAttempt, TransmissionRequestEnvelope}
import uk.gov.hmrc.filetransmission.utils.JodaTimeConverters.{ClockJodaExtensions, JodaDateTimeExtensions, toJoda}


sealed trait ProcessingResult
case object ProcessingSuccessful extends ProcessingResult
case class ProcessingFailed(error: Throwable) extends ProcessingResult
case class ProcessingFailedDoNotRetry(error: Throwable) extends ProcessingResult

trait QueueJob {
  def process(item: TransmissionRequestEnvelope,
              nextRetryTime: DateTime,
              timeToGiveUp: DateTime): Future[ProcessingResult]
}

trait WorkItemService {
  def enqueue(request: TransmissionRequestEnvelope): Future[Unit]

  def processOne(): Future[Boolean]

  def clearQueue(): Future[Boolean]
}

class MongoBackedWorkItemService @Inject()(
    repository: TransmissionRequestWorkItemRepository,
    queueJob: QueueJob,
    configuration: ServiceConfiguration,
    clock: Clock)(implicit ec: ExecutionContext)
    extends WorkItemService {

  def enqueue(request: TransmissionRequestEnvelope): Future[Unit] =
    repository.pushNew(request, now()).map(_ => ())

  def processOne(): Future[Boolean] = {

    val failedBefore = now() //we don't use this
    val availableBefore = now()

    val result: OptionT[Future, Unit] = for {
      firstOutstandingItem <- OptionT(
        repository.pullOutstanding(failedBefore, availableBefore))
      _ <- OptionT.liftF(processWorkItem(firstOutstandingItem))
    } yield ()

    val somethingHasBeenProcessed = result.value.map(_.isDefined)

    somethingHasBeenProcessed
  }

  override def clearQueue(): Future[Boolean] = repository.clearRequestQueue()

  private def processWorkItem(
      workItem: WorkItem[TransmissionRequestEnvelope],
      processedAt: Instant = clock.instant
  ): Future[Unit] = {

    // Update the work item with the latest FailedDeliveryAttempt, then update the status.
    def updateStatusForFailedDeliveryAttempt(status: ResultStatus,
                                             availableAt: Option[DateTime],
                                             ex: Throwable): Future[Boolean] = {
      val updatedEnvelope = workItem.item.withFailedDeliveryAttempt(FailedDeliveryAttempt(processedAt, ex.getMessage))

      for {
        _       <- repository.markAs(workItem.id, status, availableAt)
        updated <- repository.updateWorkItemBodyDeliveryAttempts(workItem.id, updatedEnvelope)
      } yield updated
    }

    val request = workItem.item

    val nextRetryTime: DateTime = nextAvailabilityTime(workItem)

    for (processingResult <- queueJob.process(request, nextRetryTime, timeToGiveUp(workItem))) yield {
      processingResult match {
        case ProcessingSuccessful =>
          repository.complete(workItem.id, Succeeded)
        case ProcessingFailed(ex) =>
          updateStatusForFailedDeliveryAttempt(Failed, Some(nextRetryTime), ex)
        case ProcessingFailedDoNotRetry(ex) =>
          updateStatusForFailedDeliveryAttempt(PermanentlyFailed, None, ex)
      }
    }
  }

  private def now(): DateTime = clock.nowAsJoda

  private def nextAvailabilityTime[T](workItem: WorkItem[T]): DateTime = {

    val dateTimeNow = now()

    val multiplier = Math.pow(2, workItem.failureCount).toInt
    val delay = configuration.initialBackoffAfterFailure * multiplier

    dateTimeNow + delay
  }

  private def timeToGiveUp(
      workItem: WorkItem[TransmissionRequestEnvelope]): DateTime = {

    val deliveryWindowDuration =
      workItem.item.request.deliveryWindowDuration
        .getOrElse(configuration.defaultDeliveryWindowDuration)

    workItem.receivedAt + deliveryWindowDuration
  }

}
