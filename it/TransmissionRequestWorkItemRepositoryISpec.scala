import java.net.URL

import org.joda.time.{Instant => JodaInstant}
import java.time.Instant

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, post, urlEqualTo}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, GivenWhenThen}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.filetransmission.model._
import uk.gov.hmrc.filetransmission.services.queue.{MongoBackedWorkItemService, TransmissionRequestWorkItemRepository}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.workitem.WorkItem

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class TransmissionRequestWorkItemRepositoryISpec extends UnitSpec
  with GuiceOneAppPerSuite
  with GivenWhenThen
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with Eventually {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "auditing.enabled" -> "false",
      "mdgEndpoint" -> "http://localhost:11111/mdg",
      "callbackValidation.allowedProtocols" -> "http",
      "initialBackoffAfterFailure" -> "1 milliseconds",
      "queuePollingInterval" -> "1 day",
      "deliveryWindowDuration" -> "15 seconds"
    )
    .build()

  val mdgServer = new WireMockServer(wireMockConfig().port(11111))

  val consumingServiceServer = new WireMockServer(wireMockConfig().port(11112))

  val testInstance = app.injector.instanceOf[TransmissionRequestWorkItemRepository]

  val workItemService = app.injector.instanceOf[MongoBackedWorkItemService]

  override def beforeAll() = {
    super.beforeAll()
    mdgServer.start()
    consumingServiceServer.start()
  }

  override def beforeEach() = {
    super.beforeEach()
    mdgServer.resetAll()
    consumingServiceServer.resetAll()
  }

  override def afterAll() = {
    mdgServer.stop()
    consumingServiceServer.stop()
    super.afterAll()
  }

  "TransmissionRequestEnvelopeWorkItemRepository" should {
    "persist FailedDeliveryAttempt collection within the TransmissionRequestEnvelope work item" in {

      testInstance.clearRequestQueue()

      val testStart = java.time.Instant.now

      Given("a valid TransmissionRequest")
      val request = TransmissionRequest(
        Batch("A", 10),
        Interface("J", "1.0"),
        File("ref",
          new URL("http://127.0.0.1/test"),
          "test.xml",
          "application/xml",
          "checksum",
          1,
          1024,
          Instant.now.toString),
        Seq(Property("KEY1", "VAL1"), Property("KEY2", "VAL2")),
        new URL("http://127.0.0.1/test"),
        Some(30 seconds)
      )

      val envelope = TransmissionRequestEnvelope(request, "TransmissionRequestWorkItemRepositoryISpec")

      And("MDG returns a 503 for all requests")
      stubMdgToFail()

      And("the request is added to the work item queue")
      val workItem: WorkItem[TransmissionRequestEnvelope] = await(testInstance.pushNew(envelope, JodaInstant.now.toDateTime()))

      When("the request is processed twice (both attempts with failed delivery to MDG)")
      await(workItemService.processOne()) shouldBe true
      await(workItemService.processOne()) shouldBe true

      // Pause to give Mongo time to reflect the changes above.
      Thread.sleep(50)

      Then("the work item should be updated to include a FailedDeliveryAttempt within the TransmissionRequestEnvelope")
      val updatedWorkItemMaybe = await(testInstance.findById(workItem.id))

      val deliveryAttempts = updatedWorkItemMaybe.getOrElse(throw new IllegalStateException("Failed to find Work Item")).item.deliveryAttempts

      deliveryAttempts.size shouldBe 2

      val testEnd = java.time.Instant.now

      And("the FailedDeliveryAttempt(s) should include to expected failure reason, and timestamp")
      validateDeliveryAttempt(deliveryAttempts(0), testStart, testEnd)
      validateDeliveryAttempt(deliveryAttempts(1), testStart, testEnd)
    }
  }

  private def validateDeliveryAttempt(da: FailedDeliveryAttempt, testStart: java.time.Instant, testEnd: java.time.Instant): Unit = {
    da.failureReason shouldBe "POST of 'http://localhost:11111/mdg' returned 503. Response body: ''"
    da.time.isAfter(testStart) shouldBe true
    da.time.isBefore(testEnd) shouldBe true
  }

  private def stubMdgToFail(): Unit =
    mdgServer.stubFor(
      post(urlEqualTo("/mdg")).willReturn(aResponse().withStatus(503)))
}
