/*
 * Copyright 2019 HM Revenue & Customs
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

import uk.gov.hmrc.filetransmission.model.TransmissionRequest

class MdgRequestSerializer {

  def serialize(request: TransmissionRequest): String = {

    val printer = new scala.xml.PrettyPrinter(24, 4)

    val propertiesXml = for (property <- request.properties)
      yield <mdg:property>
        <mdg:name>{property.name}</mdg:name>
        <mdg:value>{property.value}</mdg:value>
      </mdg:property>

    val xml =
      <mdg:BatchFileInterfaceMetadata
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xmlns:mdg="http://www.hmrc.gsi.gov.uk/mdg/batchFileInterfaceMetadataSchema"
      xsi:schemaLocation="http://www.hmrc.gsi.gov.uk/mdg/batchFileInterfaceMetadataSchema BatchFileInterfaceMetadata-1.0.7.xsd"
      >
        <mdg:sourceSystem>MDTP</mdg:sourceSystem>
        <mdg:sourceSystemType>AWS</mdg:sourceSystemType>
        <mdg:interfaceName>{request.interface.name}</mdg:interfaceName>
        <mdg:interfaceVersion>{request.interface.version}</mdg:interfaceVersion>
        <mdg:correlationID>{request.file.reference}</mdg:correlationID>
        <mdg:sequenceNumber>{request.file.sequenceNumber}</mdg:sequenceNumber>
        <mdg:batchID>{request.batch.id}</mdg:batchID>
        <mdg:batchSize>{request.batch.fileCount}</mdg:batchSize>
        <mdg:batchCount>{request.file.sequenceNumber}</mdg:batchCount>
        <mdg:extractEndDateTime>{request.file.uploadTimestamp}</mdg:extractEndDateTime>
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

    printer.format(xml)
  }

}
