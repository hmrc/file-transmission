import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.PatienceConfiguration.{Interval, Timeout}
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, GivenWhenThen}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.HeaderNames.USER_AGENT
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.play.test.UnitSpec

class FileTransmissionAcceptanceTest
    extends UnitSpec
    with GuiceOneAppPerSuite
    with GivenWhenThen
    with BeforeAndAfterAll
    with Eventually {
  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "userAgentFilter.allowedUserAgents" -> "PrepareUploadControllerISpec",
      "auditing.enabled" -> "false",
      "mdgEndpoint" -> "http://localhost:11111/mdg",
      "callbackValidation.allowedProtocols" -> "http, https"
    )
    .build()

  val mdgServer = new WireMockServer(wireMockConfig().port(11111))

  val consumingServiceServer = new WireMockServer(wireMockConfig().port(11112))

  override def beforeAll() = {
    mdgServer.start()
    consumingServiceServer.start()
  }

  override def afterAll() = {
    mdgServer.stop()
    consumingServiceServer.stop()
  }

  private def stubMdgToReturnValidResponse(): Unit =
    mdgServer.stubFor(
      post(urlEqualTo("/mdg"))
        .willReturn(
          aResponse()
            .withStatus(204)
        ))

  private def stubMdgToReturnInvalidResponse(): Unit =
    mdgServer.stubFor(
      post(urlEqualTo("/mdg"))
        .willReturn(
          aResponse()
            .withStatus(503)
        ))

  private def stubConsumingServiceToReturnValidResponse(): Unit =
    consumingServiceServer.stubFor(
      post(urlEqualTo("/listen"))
        .willReturn(
          aResponse()
            .withStatus(200)
        ))

  private def stubConsumingServiceToReturnInvalidResponse(): Unit =
    consumingServiceServer.stubFor(
      post(urlEqualTo("/listen"))
        .willReturn(
          aResponse()
            .withStatus(503)
        ))

  "File Transmission Service" should {

    "pass valid request to MDG and confirm response to callback service" in {
      val requestBody = Json.parse(validRequestBody)

      Given("we have an invalid request")
      val request = transmissionRequest(requestBody)

      And("MDG is up and running")
      stubMdgToReturnValidResponse()

      And("consuming service is up and running")
      stubConsumingServiceToReturnValidResponse()

      When("the request is posted to the /request endpoint")
      val response = route(app, request).get

      Then("the response should that request has been consumed")
      status(response) shouldBe 204

      And("MDG should receive the request")
      eventually(Timeout(scaled(Span(2, Seconds))),
                 Interval(scaled(Span(200, Millis)))) {
        mdgServer.verify(postRequestedFor(urlEqualTo("/mdg")))
      }

      And(
        "consuming service should receive confirmation that the request has been processed successfuly")
      eventually(Timeout(scaled(Span(2, Seconds))),
                 Interval(scaled(Span(200, Millis)))) {
        consumingServiceServer.verify(postRequestedFor(urlEqualTo("/listen")))
      }

    }

    "reject invalid request" in {
      val requestBody = Json.parse("""{"invalid" : "request"}""".stripMargin)

      Given("we have an invalid request")
      val request = transmissionRequest(requestBody)

      When("the request is posted to the /request endpoint")
      val response = route(app, request).get

      Then("the response should indicate bad request")
      status(response) shouldBe 400
    }

  }

  private def transmissionRequest(body: JsValue,
                                  userAgent: String =
                                    "PrepareUploadControllerISpec") =
    FakeRequest(POST,
                "/file-transmission/request",
                FakeHeaders(Seq((USER_AGENT, userAgent))),
                body)

  private def validRequestBody =
    """
      |{
      |	"batch": {
      |		"id": "fghij67890",
      |		"fileCount": 10
      |	},
      |	"callbackUrl": "http://localhost:11112/listen",
      |	"requestTimeoutInSeconds": 300,
      |	"file": {				
      |		"reference": "abcde12345",
      |		"name": "someFileN.ame",
      |		"mimeType": "application/pdf",
      |		"checksum": "asdrfgvbhujk13579",
      |		"location": "https://localhost",
      |		"sequenceNumber": 3,
      |		"size": 1024
      |	},
      |	"interface":{
      |		"name": "interfaceName name",
      |		"version": "1.0"
      |	},
      |	"properties":[
      |		{
      |			"name": "property1",
      |			"value": "value1"
      |		},
      |		{
      |			"name": "property2",
      |			"value": "value2"
      |		}
      |	]			
      |}
    """.stripMargin

}
