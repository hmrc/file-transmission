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

import cats.data.OptionT
import cats.implicits._
import javax.inject.Inject
import org.joda.time.{DateTime, Duration}
import uk.gov.hmrc.filetransmission.config.ServiceConfiguration
import uk.gov.hmrc.filetransmission.model.TransmissionRequestEnvelope
import uk.gov.hmrc.workitem._

import scala.concurrent.{ExecutionContext, Future}

sealed trait ProcessingResult
case object ProcessingSuccessful extends ProcessingResult
case class ProcessingFailed(error: Throwable) extends ProcessingResult
case class ProcessingFailedDoNotRetry(error: Throwable) extends ProcessingResult

trait QueueJob {
  def process(item: TransmissionRequestEnvelope, triedSoFar: Int): Future[ProcessingResult]
}

class WorkItemService @Inject()(
  repository: TransmissionRequestWorkItemRepository,
  queueJob: QueueJob,
  configuration: ServiceConfiguration)(implicit ec: ExecutionContext) {

  def enqueue(request: TransmissionRequestEnvelope): Future[Unit] =
    repository.pushNew(request, DateTime.now()).map(_ => ())

  def processOne(): Future[Boolean] = {

    val failedBefore    = DateTime.now() //we don't use this
    val availableBefore = DateTime.now()

    val result: OptionT[Future, Unit] = for {
      firstOutstandingItem <- OptionT(repository.pullOutstanding(failedBefore, availableBefore))
      _                    <- OptionT.liftF(processWorkItem(firstOutstandingItem))
    } yield ()

    val somethingHasBeenProcessed = result.value.map(_.isDefined)

    somethingHasBeenProcessed
  }

  private def processWorkItem(workItem: WorkItem[TransmissionRequestEnvelope]): Future[Unit] = {
    val request = workItem.item
    for (processingResult <- queueJob.process(request, workItem.failureCount)) yield {
      processingResult match {
        case ProcessingSuccessful =>
          repository.complete(workItem.id, Succeeded)
        case ProcessingFailed(error) =>
          repository.markAs(workItem.id, Failed, Some(nextAvailabilityTime(workItem)))
        case ProcessingFailedDoNotRetry(error) =>
          repository.markAs(workItem.id, PermanentlyFailed, None)
      }
    }
  }

  private def nextAvailabilityTime[T](workItem: WorkItem[T]): DateTime = {
    val delay = Duration
      .millis(configuration.initialBackoffAfterFailure.toMillis)
      .multipliedBy(Math.pow(2, workItem.failureCount).toInt)

    DateTime.now().plus(delay)
  }

}
