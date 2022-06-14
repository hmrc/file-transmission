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

import java.time.Clock

import com.typesafe.config.Config
import javax.inject.Inject
import org.joda.time.{DateTime, Duration}
import play.api.libs.functional.syntax.{unlift, _}
import play.api.libs.json.{Format, Json, Reads, __}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONObjectIDFormat
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.filetransmission.config.ServiceConfiguration
import uk.gov.hmrc.filetransmission.model.TransmissionRequestEnvelope
import uk.gov.hmrc.filetransmission.utils.JodaTimeConverters._
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.workitem.{WorkItem, _}

import scala.concurrent.{ExecutionContext, Future}

class TransmissionRequestWorkItemRepository @Inject()(
    mongoComponent: ReactiveMongoComponent,
    configuration: ServiceConfiguration,
    clock: Clock,
    config: Config)(implicit ec: ExecutionContext)
    extends WorkItemRepository[TransmissionRequestEnvelope, BSONObjectID](
      collectionName = "transmission-request",
      mongo = mongoComponent.mongoConnector.db,
      itemFormat = WorkItemFormat.workItemMongoFormat[TransmissionRequestEnvelope],
      config = config
    ) {

  override def now: DateTime = clock.nowAsJoda

  override def inProgressRetryAfterProperty: String =
    ??? // we don't use this, we override inProgressRetryAfter instead

  override lazy val inProgressRetryAfter: Duration =
    Duration.millis(configuration.inFlightLockDuration.toMillis)

  override def workItemFields = new WorkItemFieldNames {
    val receivedAt = "modifiedDetails.createdAt"
    val updatedAt = "modifiedDetails.lastUpdated"
    val availableAt = "modifiedDetails.availableAt"
    val status = "status"
    val id = "_id"
    val failureCount = "failures"
  }

  def clearRequestQueue(): Future[Boolean] = {
    collection.drop(failIfNotFound = false)
  }

  def updateWorkItemBodyDeliveryAttempts(workItemId: BSONObjectID,
                                         body: TransmissionRequestEnvelope): Future[Boolean] = {
    val selector = Json.obj(workItemFields.id -> workItemId)

    val updater  = Json.obj("$set" -> Json.obj("body.deliveryAttempts" -> body.deliveryAttempts))

    collection.update(ordered = false).one(selector, updater).map(_.n > 0)
  }
}

object WorkItemFormat {

  def workItemMongoFormat[T](implicit tFormat: Format[T]): Format[WorkItem[T]] =
    ReactiveMongoFormats.mongoEntity(
      ticketFormat(ReactiveMongoFormats.objectIdFormats,
                   ReactiveMongoFormats.dateTimeFormats,
                   tFormat))

  private def ticketFormat[T](implicit bsonIdFormat: Format[BSONObjectID],
                              dateTimeFormat: Format[DateTime],
                              tFormat: Format[T]): Format[WorkItem[T]] = {
    val reads = (
      (__ \ "id").read[BSONObjectID] and
        (__ \ "modifiedDetails" \ "createdAt").read[DateTime] and
        (__ \ "modifiedDetails" \ "lastUpdated").read[DateTime] and
        (__ \ "modifiedDetails" \ "availableAt").read[DateTime] and
        (__ \ "status").read[uk.gov.hmrc.workitem.ProcessingStatus] and
        (__ \ "failures").read[Int].orElse(Reads.pure(0)) and
        (__ \ "body").read[T]
    )(WorkItem.apply[T] _)

    val writes = (
      (__ \ "id").write[BSONObjectID] and
        (__ \ "modifiedDetails" \ "createdAt").write[DateTime] and
        (__ \ "modifiedDetails" \ "lastUpdated").write[DateTime] and
        (__ \ "modifiedDetails" \ "availableAt").write[DateTime] and
        (__ \ "status").write[uk.gov.hmrc.workitem.ProcessingStatus] and
        (__ \ "failures").write[Int] and
        (__ \ "body").write[T]
    )(unlift(WorkItem.unapply[T]))

    Format(reads, writes)
  }

}
