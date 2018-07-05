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

package uk.gov.hmrc.filetransmission.services

import javax.inject.Inject
import play.api.Logger
import uk.gov.hmrc.filetransmission.model.TransmissionRequest

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class TransmissionService @Inject()(implicit ec: ExecutionContext) {

  def request(request: TransmissionRequest, callingService: String): Future[Unit] = {
    val result = Future.successful(())
    logResult(request, callingService, result)
    result
  }

  private def logResult[T](request: TransmissionRequest, callingService: String, result: Future[T]): Future[T] = {
    result.onComplete {
      case Success(_) =>
        Logger.info(s"Request ${describeRequest(request, callingService)} processed successfully")
      case Failure(e) =>
        Logger.warn(s"Processing request ${describeRequest(request, callingService)} failed", e)
    }
    result
  }

  private def describeRequest(request: TransmissionRequest, callingService: String): String =
    s"consumingService: [$callingService] fileReference: [${request.file.reference}] batchId: [${request.batch.id}]"

}
