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

package uk.gov.hmrc.filetransmission.connector

import java.net.URL

import org.scalatest.GivenWhenThen
import org.xml.sax.SAXParseException
import org.xmlunit.diff.ComparisonFormatter
import uk.gov.hmrc.filetransmission.model._
import uk.gov.hmrc.play.test.UnitSpec

import scala.util.Try

class MdgRequestSerializerSpec extends UnitSpec with GivenWhenThen {

  "MdgRequestSerializer" should {

    val serializer = new MdgRequestSerializer

    "produces requests that are compliant with MDG XSD schema" in {

      val request = TransmissionRequest(
        Batch("A", 10),
        Interface("J", "1.0"),
        File("ref", new URL("http://127.0.0.1/test"), "test.xml", "application/xml", "checksum", 1, 1024),
        Seq(Property("KEY1", "VAL1"), Property("KEY2", "VAL2")),
        new URL("http://127.0.0.1/test"),
        30
      )

      val serializedRequest: String = serializer.serialize(request)

      val validationResult = validateSchema(serializedRequest)

      withClue(validationResult) {
        validationResult.isSuccess shouldBe true
      }
    }

    "populates proper values to the request" in {
      val request = TransmissionRequest(
        Batch("A", 10),
        Interface("interface1", "1.0"),
        File("ref", new URL("http://127.0.0.1/test"), "test.xml", "application/xml", "checksum", 1, 1024),
        Seq(Property("KEY1", "VAL1"), Property("KEY2", "VAL2")),
        new URL("http://127.0.0.1/test"),
        30
      )

      val serializedRequest: String = serializer.serialize(request)

      val expectedRequest =
        s"""<?xml version="1.0" encoding="UTF-8"?>
          |<!--Sample XML file generated by XMLSpy v2017 (http://www.altova.com)-->
          |<mdg:BatchFileInterfaceMetadata xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:mdg="http://www.hmrc.gsi.gov.uk/mdg/batchFileInterfaceMetadataSchema" xsi:schemaLocation="http://www.hmrc.gsi.gov.uk/mdg/batchFileInterfaceMetadataSchema BatchFileInterfaceMetadata-1.0.6.xsd">
          |    <mdg:sourceSystem>MDTP</mdg:sourceSystem>
          |    <mdg:sourceSystemType>AWS</mdg:sourceSystemType>
          |    <mdg:interfaceName>${request.interface.name}</mdg:interfaceName>
          |    <mdg:interfaceVersion>${request.interface.version}</mdg:interfaceVersion>
          |    <mdg:correlationID>${request.file.reference}</mdg:correlationID>
          |    <mdg:sequenceNumber>${request.file.sequenceNumber}</mdg:sequenceNumber>
          |    <mdg:batchID>${request.batch.id}</mdg:batchID>
          |    <mdg:batchSize>${request.batch.fileCount}</mdg:batchSize>
          |    <mdg:batchCount>${request.file.sequenceNumber}</mdg:batchCount>
          |    <mdg:checksum>${request.file.checksum}</mdg:checksum>
          |    <mdg:checksumAlgorithm>SHA-256</mdg:checksumAlgorithm>
          |    <mdg:fileSize>${request.file.size}</mdg:fileSize>
          |    <mdg:compressed>false</mdg:compressed>
          |    <mdg:encrypted>false</mdg:encrypted>
          |    <mdg:properties>
          |        <mdg:property>
          |            <mdg:name>${request.properties(0).name}</mdg:name>
          |            <mdg:value>${request.properties(0).value}</mdg:value>
          |        </mdg:property>
          |        <mdg:property>
          |            <mdg:name>${request.properties(1).name}</mdg:name>
          |            <mdg:value>${request.properties(1).value}</mdg:value>
          |        </mdg:property>
          |    </mdg:properties>
          |    <mdg:sourceLocation>${request.file.location}</mdg:sourceLocation>
          |    <mdg:sourceFileName>${request.file.name}</mdg:sourceFileName>
          |    <mdg:sourceFileMimeType>${request.file.mimeType}</mdg:sourceFileMimeType>
          |    <mdg:destinations>
          |        <mdg:destination>
          |            <mdg:destinationSystem>CDS</mdg:destinationSystem>
          |        </mdg:destination>
          |    </mdg:destinations>
          |</mdg:BatchFileInterfaceMetadata>
        """.stripMargin

      import org.xmlunit.builder.{DiffBuilder, Input}
      val d =
        DiffBuilder
          .compare(Input.fromString(expectedRequest))
          .withTest(serializedRequest)
          .ignoreWhitespace()
          .ignoreComments()
          .build

      withClue(d) {
        assert(!d.hasDifferences)
      }
    }

  }

  private def validateSchema(body: String): Try[Unit] = {

    val schemaLang = javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI
    val xsdStream =
      new javax.xml.transform.stream.StreamSource(
        this.getClass.getResourceAsStream("/BatchFileInterfaceMetadata-1.0.6.xsd"))
    val schema = javax.xml.validation.SchemaFactory.newInstance(schemaLang).newSchema(xsdStream)

    val factory = javax.xml.parsers.SAXParserFactory.newInstance()
    factory.setNamespaceAware(true)
    factory.setSchema(schema)

    val validatingParser = factory.newSAXParser()
    val xmlLoader = new scala.xml.factory.XMLLoader[scala.xml.Elem] {
      override def parser = validatingParser
      override def adapter =
        new scala.xml.parsing.NoBindingFactoryAdapter {

          override def error(e: SAXParseException): Unit =
            throw e

        }

    }

    Try(xmlLoader.loadString(body))

  }

}