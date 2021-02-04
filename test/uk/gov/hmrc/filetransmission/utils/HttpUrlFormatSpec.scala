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

package uk.gov.hmrc.filetransmission.utils

import java.net.URL

import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json._

class HttpUrlFormatSpec extends WordSpec with Matchers {

  "reads should properly read string url" in {
    HttpUrlFormat.reads(JsString("https://127.0.0.1")) shouldBe JsSuccess(new URL("https://127.0.0.1"))
  }

  "reads should fail if value is not a string" in {
    HttpUrlFormat.reads(JsNumber(1234)) shouldBe JsError("error.expected.url")
  }

  "reads should fail if value is not a valid url" in {
    HttpUrlFormat.reads(JsString("invalid-url")) shouldBe JsError("error.expected.url")
  }

  "should properly write url" in {
    HttpUrlFormat.writes(new URL("https://127.0.0.1")) shouldBe JsString("https://127.0.0.1")
  }

}
