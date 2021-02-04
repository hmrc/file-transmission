/*
 * Copyright 2021 HM Revenue & Customs
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
import org.joda.time.DateTime
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.{Format, Json}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.workitem
import uk.gov.hmrc.workitem.WorkItem

class WorkItemFormatSpec extends WordSpec with Matchers  {

  case class SampleObject(value: String)

  implicit val sampleObjectFormat: Format[SampleObject] = Json.format[SampleObject]

  "WorkItemFormat" should {
    "be able to serialize and then deserialize work items" in {

      val id = BSONObjectID.generate()
      val workItem = WorkItem(
        id,
        DateTime.parse("2018-08-15T07:58:10+00:00"),
        DateTime.parse("2018-08-15T07:58:15+00:00"),
        DateTime.parse("2018-08-15T07:58:10+00:00"),
        workitem.Failed,
        10,
        SampleObject("test")
      )

      val serialized =
        WorkItemFormat.workItemMongoFormat[SampleObject].writes(workItem)

      val deserialized = WorkItemFormat.workItemMongoFormat[SampleObject].reads(serialized)

      workItem shouldBe deserialized.get

    }
  }

}
