import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.PatienceConfiguration.{Interval, Timeout}
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, GivenWhenThen}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.HeaderNames.USER_AGENT
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.filetransmission.model.TransmissionRequest
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.duration._
import scala.concurrent.duration.Duration
import scala.xml.PrettyPrinter

class FileTransmissionAcceptanceTests
    extends UnitSpec
    with GuiceOneAppPerSuite
    with GivenWhenThen
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with Eventually {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "application.router" -> "testOnlyDoNotUseInAppConf.Routes",
      "userAgentFilter.allowedUserAgents" -> "PrepareUploadControllerISpec",
      "auditing.enabled" -> "false",
      "mdgEndpoint" -> "http://localhost:11111/mdg",
      "callbackValidation.allowedProtocols" -> "http",
      "initialBackoffAfterFailure" -> "75 milliseconds",
      "deliveryWindowDuration" -> "15 seconds"
    )
    .build()

  val mdgServer = new WireMockServer(wireMockConfig().port(11111))

  val consumingServiceServer = new WireMockServer(wireMockConfig().port(11112))

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

  "File Transmission Service" should {

    "pass valid request to MDG and confirm response to callback service" in {
      Given("we have valid request")
      val request = transmissionRequest(Json.parse(validRequestBody()))

      And("MDG is up and running")
      stubMdgToReturnValidResponse()

      And("consuming service is up and running")
      stubConsumingServiceToReturnValidResponse()

      When("the request is posted to the /request endpoint")
      val response = route(app, request).get

      Then("the response should that request has been consumed")
      status(response) shouldBe 202

      And("MDG should receive the request, with expected xml body")
      verifyMdgReceivedRequestWithBody(consumingServiceJsonRequestBodyToMdgXmlRequestBody(validRequestBody()))

      And("consuming service should receive confirmation that the request has been processed successfully")
      verifyConsumingServiceRetrievesSuccessfulCallback

    }

    "not retry if MDG returned bad request" in {
      Given("we have valid request")
      val request = transmissionRequest(Json.parse(validRequestBody()))

      And("MDG returns HTTP 400 bad requestsb")
      stubMdgToReturnBadRequest()

      And("consuming service is up and running")
      stubConsumingServiceToReturnValidResponse()

      When("the request is posted to the /request endpoint")
      val response = route(app, request).get

      Then("the response should that request has been consumed")
      status(response) shouldBe 202

      And(
        "consuming service should receive notification that the request processing failed")
      verifyConsumingServiceRetrievesFailureCallback(
        "POST of 'http://localhost:11111/mdg' returned 400 (Bad Request). Response body ''")

      And("MDG was called once and only once")
      mdgServer.verify(1, postRequestedFor(urlEqualTo("/mdg")))
    }

    "retry call to MDG if it failed for one time" in {
      Given("we have a valid request")
      val request = transmissionRequest(Json.parse(validRequestBody()))

      And("MDG is up and running")
      stubMdgToFailOnceAndSucceedAfterwards()

      And("consuming service is up and running")
      stubConsumingServiceToReturnValidResponse()

      When("the request is posted to the /request endpoint")
      val response = route(app, request).get

      Then("the response should that request has been consumed")
      status(response) shouldBe 202

      And("MDG should receive the request")
      verifyMdgReceivedRequest

      And(
        "consuming service should receive confirmation that the request has been processed successfully")
      verifyConsumingServiceRetrievesSuccessfulCallback
    }

    "when retrying, custom retry window duration should be honored" in {
      Given("we have a valid request")

      val request = transmissionRequest(
        Json.parse(validRequestBody(requestTimeout = Some(8 seconds))))

      And("MDG is failing repeatedly")
      stubMdgToFail()

      And("consuming service is up and running")
      stubConsumingServiceToReturnValidResponse()

      When("the request is posted to the /request endpoint")
      val response = route(app, request).get

      Then("the response should that request has been consumed")
      status(response) shouldBe 202

      And("MDG should receive the request")
      verifyMdgReceivedRequest

      And(
        "consuming service should receive confirmation that the request processing has failed")
      verifyConsumingServiceRetrievesFailureCallback(
        "POST of 'http://localhost:11111/mdg' returned 503. Response body: ''",
        timeout = 8 seconds)
    }

    "if MDG fails repeatedly, consuming service retrieves callback with failure" in {
      Given("we have a valid request")
      val request = transmissionRequest(Json.parse(validRequestBody()))

      And("MDG is failing repeatedly")
      stubMdgToFail()

      And("consuming service is up and running")
      stubConsumingServiceToReturnValidResponse()

      When("the request is posted to the /request endpoint")
      val response = route(app, request).get

      Then("the response should that request has been consumed")
      status(response) shouldBe 202

      And("MDG should receive the request")
      verifyMdgReceivedRequest

      And(
        "consuming service should receive confirmation that the request processing has failed")
      verifyConsumingServiceRetrievesFailureCallback(
        "POST of 'http://localhost:11111/mdg' returned 503. Response body: ''")
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

    "clear the request queue" in {
      val request = FakeRequest(GET, "/file-transmission/test-only/requests/clear")

      val response = route(app, request).get

      status(response) shouldBe OK
      contentAsString(response) shouldBe s"""{"cleared":"true"}"""
    }
  }

  private def verifyMdgReceivedRequest =
    buildAndVerifyMdgReceivedRequest {
      postRequestedFor(urlEqualTo("/mdg"))
    }

  private def verifyMdgReceivedRequestWithBody(expectedXmlRequest: String) =
    buildAndVerifyMdgReceivedRequest {
      postRequestedFor(urlEqualTo("/mdg"))
        .withRequestBody(equalToXml(expectedXmlRequest))
    }

  private def buildAndVerifyMdgReceivedRequest(requestBuilder: => RequestPatternBuilder) =
    eventually(Timeout(scaled(Span(2, Seconds))),
      Interval(scaled(Span(200, Millis)))) {
      mdgServer.verify(requestBuilder)
    }

  private def verifyConsumingServiceRetrievesSuccessfulCallback =
    eventually(Timeout(scaled(Span(2, Seconds))),
               Interval(scaled(Span(200, Millis)))) {
      consumingServiceServer.verify(
        postRequestedFor(urlEqualTo("/listen"))
          .withRequestBody(equalToJson(s"""
               | {
               |   "fileReference" : "abcde12345",
               |   "batchId" : "fghij67890",
               |   "outcome" : "SUCCESS"
               | }""".stripMargin)))
    }

  private def verifyConsumingServiceRetrievesFailureCallback(message: String,
                                                             timeout: Duration =
                                                               20 seconds) =
    eventually(Timeout(scaled(Span(timeout.toSeconds, Seconds))),
               Interval(scaled(Span(200, Millis)))) {
      consumingServiceServer.verify(
        postRequestedFor(urlEqualTo("/listen"))
          .withRequestBody(equalToJson(s"""
                                          | {
                                          |   "fileReference" : "abcde12345",
                                          |   "batchId" : "fghij67890",
                                          |   "outcome" : "FAILURE",
                                          |    "errorDetails": "$message"
                                          | }""".stripMargin)))
    }

  private def stubMdgToReturnValidResponse(): Unit =
    mdgServer.stubFor(
      post(urlEqualTo("/mdg")).willReturn(aResponse().withStatus(204)))

  private def stubMdgToFail(): Unit =
    mdgServer.stubFor(
      post(urlEqualTo("/mdg")).willReturn(aResponse().withStatus(503)))

  private def stubMdgToReturnBadRequest() =
    mdgServer.stubFor(
      post(urlEqualTo("/mdg")).willReturn(aResponse().withStatus(400)))

  private def stubMdgToFailOnceAndSucceedAfterwards() = {
    mdgServer.stubFor(
      post(urlEqualTo("/mdg"))
        .inScenario("FailOnceSucceedLater")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withStatus(503)
        )
        .willSetStateTo("FailedOnce"))

    mdgServer.stubFor(
      post(urlEqualTo("/mdg"))
        .inScenario("FailOnceSucceedLater")
        .whenScenarioStateIs("FailedOnce")
        .willReturn(
          aResponse()
            .withStatus(200)
        ))
  }

  private def stubConsumingServiceToReturnValidResponse(): Unit =
    consumingServiceServer.stubFor(
      post(urlEqualTo("/listen")).willReturn(aResponse().withStatus(200)))

  private def stubConsumingServiceToReturnInvalidResponse(): Unit =
    consumingServiceServer.stubFor(
      post(urlEqualTo("/listen")).willReturn(aResponse().withStatus(503)))

  private def transmissionRequest(body: JsValue,
                                  userAgent: String =
                                    "PrepareUploadControllerISpec") =
    FakeRequest(POST,
                "/file-transmission/request",
                FakeHeaders(Seq((USER_AGENT, userAgent))),
                body)

  private def requestTimeoutField(requestTimeout: Option[Duration]) =
    requestTimeout
      .map(value =>
        s""" "deliveryWindowDurationInSeconds": ${value.toSeconds},""")
      .getOrElse("")

  private def validRequestBody(requestTimeout: Option[Duration] = None) =
    s"""
      |{
      |	"batch": {
      |		"id": "fghij67890",
      |		"fileCount": 10
      |	},
      |	"callbackUrl": "http://localhost:11112/listen",
      | ${requestTimeoutField(requestTimeout)}
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

  private def consumingServiceJsonRequestBodyToMdgXmlRequestBody(body: String): String = {
    val request: TransmissionRequest = Json.parse(body).as[TransmissionRequest]

    val propertiesXml = for (property <- request.properties) yield <mdg:property>
      <mdg:name>{property.name}</mdg:name>
      <mdg:value>{property.value}</mdg:value>
    </mdg:property>
    val xml =
      <mdg:BatchFileInterfaceMetadata
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xmlns:mdg="http://www.hmrc.gsi.gov.uk/mdg/batchFileInterfaceMetadataSchema"
      xsi:schemaLocation="http://www.hmrc.gsi.gov.uk/mdg/batchFileInterfaceMetadataSchema BatchFileInterfaceMetadata-1.0.6.xsd">
        <mdg:sourceSystem>MDTP</mdg:sourceSystem>
        <mdg:sourceSystemType>AWS</mdg:sourceSystemType>
        <mdg:interfaceName>{request.interface.name}</mdg:interfaceName>
        <mdg:interfaceVersion>{request.interface.version}</mdg:interfaceVersion>
        <mdg:correlationID>{request.file.reference}</mdg:correlationID>
        <mdg:sequenceNumber>{request.file.sequenceNumber}</mdg:sequenceNumber>
        <mdg:batchID>{request.batch.id}</mdg:batchID>
        <mdg:batchSize>{request.batch.fileCount}</mdg:batchSize>
        <mdg:batchCount>{request.file.sequenceNumber}</mdg:batchCount>
        <mdg:checksum>{request.file.checksum}</mdg:checksum>
        <mdg:checksumAlgorithm>SHA-256</mdg:checksumAlgorithm>
        <mdg:fileSize>{request.file.size}</mdg:fileSize>
        <mdg:compressed>false</mdg:compressed>
        <mdg:encrypted>false</mdg:encrypted>
        <mdg:properties>{propertiesXml}</mdg:properties>
        <mdg:sourceLocation>{request.file.location.toString}</mdg:sourceLocation>
        <mdg:sourceFileName>{request.file.name}</mdg:sourceFileName>
        <mdg:sourceFileMimeType>{request.file.mimeType}</mdg:sourceFileMimeType>
        <mdg:destinations>
          <mdg:destination>
            <mdg:destinationSystem>CDS</mdg:destinationSystem>
          </mdg:destination>
        </mdg:destinations>
      </mdg:BatchFileInterfaceMetadata>

    new PrettyPrinter(24, 4).format(xml)
  }
}
