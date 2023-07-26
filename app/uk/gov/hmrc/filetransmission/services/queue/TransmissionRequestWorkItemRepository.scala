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

import java.time.{Clock, Instant}

import com.typesafe.config.Config
import javax.inject.{Inject, Singleton}
import org.bson.types.ObjectId

import uk.gov.hmrc.filetransmission.config.ServiceConfiguration
import uk.gov.hmrc.filetransmission.model.TransmissionRequestEnvelope
import uk.gov.hmrc.mongo.workitem.{WorkItemFields, WorkItemRepository}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs
import org.mongodb.scala.model.{Filters, Updates, FindOneAndUpdateOptions, ReturnDocument}

import scala.concurrent.{ExecutionContext, Future}

object TransmissionRequestWorkItemRepository {
  lazy val workItemFields =
    WorkItemFields(
      id           = "_id",
      receivedAt   = "modifiedDetails.createdAt",
      updatedAt    = "modifiedDetails.lastUpdated",
      availableAt  = "modifiedDetails.availableAt",
      status       = "status",
      failureCount = "failures",
      item         = "body"
    )
}

@Singleton
class TransmissionRequestWorkItemRepository @Inject()(
    val mongoComponent: MongoComponent,
    configuration: ServiceConfiguration,
    clock: Clock,
    config: Config)(implicit ec: ExecutionContext)
    extends WorkItemRepository[TransmissionRequestEnvelope](
      collectionName = "transmission-request",
      mongoComponent = mongoComponent,
      itemFormat     = TransmissionRequestEnvelope.transmissionRequestEnvelopeFormat,
      workItemFields = TransmissionRequestWorkItemRepository.workItemFields
    ) {

  override def now(): Instant =
    Instant.now()

  override val inProgressRetryAfter =
    java.time.Duration.ofNanos(configuration.inFlightLockDuration.toNanos)

  def clearRequestQueue(): Future[Boolean] =
    collection
      .drop()
      .toFuture()
      .map(_ => true)
      .recover { case _ => false}

  def updateWorkItemBodyDeliveryAttempts(workItemId: ObjectId, body: TransmissionRequestEnvelope): Future[Boolean] =
    collection
      .findOneAndUpdate(
        filter        = Filters.equal("_id", workItemId)
      , update        = Updates.set("body.deliveryAttempts", Codecs.toBson(body.deliveryAttempts))
      , options       = FindOneAndUpdateOptions().upsert(false).returnDocument(ReturnDocument.AFTER)
      )
      .toFutureOption()
      .map(_.isDefined)
}
