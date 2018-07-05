# file-transmission

[![Build Status](https://travis-ci.org/hmrc/file-transmission.svg)](https://travis-ci.org/hmrc/file-transmission) [ ![Download](https://api.bintray.com/packages/hmrc/releases/file-transmission/images/download.svg) ](https://bintray.com/hmrc/releases/file-transmission/_latestVersion)

Microservice that facilitates transmission of files requested by MDTP through MDG.

Services on the MDTP platform should use `file-transmission` to initiate the transmission of a batch of hosted files through MDG. `file-transmission` notifies MDG about files that the service on MDTP would like to be processed. Please note that `file-transmission` does not upload or transfer files directly. Instead, it provides data allowing MDG to identify the file and how it should be processed, along with where the file is hosted. File upload and hosting must be provided by another service, such as [`upscan`](https://github.com/hmrc/upscan-initiate).

# Typical use case

- Consuming service requests upload of user file(s) using `upscan`
- `Upscan` notifies the consuming service of successful file upload and the relevant URL where the file is hosted and can be downloaded
- Consuming service verifies ensures that all required files have been correctly uploaded by the user
- Consuming service can now use `file-transmission` to notify MDG that these files are ready to be processed
- MDG proceeds to asynchronously process the file batch as appropriate

## Service usage

### Whitelisting

In order to initiate transmission, the consuming service must be whitelisted by `file-transmission`. See the 'Whitelisting client services' section further down in this document.

### Requesting file transmission

The basic unit of work for `file-transmission` is data pertaining to a batch consisting of one or more files.

Information about each file in the batch is passed to `file-transmission` in separate POST requests. 
Additional calls to 'create' a batch or to notify that information about all files in the batch has 
been provided are not necessary.

Whitelisted consuming service first make a POST request to the `/file-transmission/request` endpoint. 
The request should provide data about the batch, each file in the batch, and a callback URL that will be used to asynchronously notify the consuming service when MDG has processed the request. The consuming service may also provide additional optional metadata that it wants to pass through MDG.

The body of a request for transmission of a file in a batch would typically comprise the below:
- `callbackUrl` - URL provided by the consuming service used by MDG to notify it about the result of the batch processing
- `requestTimeoutInSeconds` - duration that `file-transmission` will try to deliver file details to MDG before giving up
- Batch information
  - `batchId` - unique batch identifier
  - `fileCount` - number of files in the batch
- File information
  - `reference` - unique reference of the file (MDG will interpret this as the `correlationId`)
  - `fileName` - original name of the file
  - `mimeType` - MIME type of the file
  - `checksum` - SHA256 checksum of the file in hexadecimal format
   - `location` - URL where file is hosted. This URL should be accessible by MDG, e.g. verify networking configuration and use external domain names. URLs provided by `upscan` will already meet this requirement.
  - `sequenceNumber` - relative number of the file within the batch **[the first file in the batch should have sequenceNumber '1']**
- Journey information
  - `journeyName` - type of journey for MDG to use, specifying what process should be invoked on the file batch
  - `journeyVersion` - the specific version of the named journey to use
- Additional properties - optional key/value map of custom properties to pass through MDG about the file and/or batch

The request HTTP headers should follow the below format:

| Header name | Description | Required |
|--------------|-----------|--------|
| User-Agent | Identifier of the service that calls `file-transmission` | yes |
| X-Request-ID | Identifier of the user's request | no |
| X-Session-ID | Identifier of the user's session | no |

Request-ID / Session-ID headers will be used to link the file with a relevant user's journey.

*Note:* If you are using `[http-verbs](https://github.com/hmrc/http-verbs)` to call the service, all the headers will be set automatically
(See: [HttpVerb.scala](https://github.com/hmrc/http-verbs/blob/2807dc65f64009bd7ce1f14b38b356e06dd23512/src/main/scala/uk/gov/hmrc/http/HttpVerb.scala#L53))

Here is an example of a request body for `file-transmission`:
```
{
	"batch": {
		"id": "fghij67890",
		"fileCount": 10
	},
	"callbackUrl": "https://transmission-listener.public.mdtp/upscan-listener/listen",
	"requestTimeoutInSeconds": 300,
	"file": {				
		"reference": "abcde12345",
		"name": "someFileN.ame",
		"mimeType": "application/pdf",
		"checksum": "asdrfgvbhujk13579",
		"location": "https://file-outbound-asderfvghyujk1357690.aws.amazon.com",	
		"sequenceNumber": 3
	},
	"journey":{
		"name": "someJouney name",
		"version": "1.0"
	},
	"properties":[
		{
			"name": "property1",
			"value": "value1"
		},
		{
			"name": "property2",
			"value": "value2"
		}
	]			
}
```

### Request outcome

A successful POST request will receive a HTTP 204 response with an empty body.

An unsuccessful POST request will receive a HTTP-error coded response (4xx, 5xx). The response body will contain XML encoded details of the problem. See the Error Handling section for details.

**Please note that a successful response only means that the request has been parsed and stored for further processing. As MDG processing is performed asynchronously, the consuming service should wait until a callback is made from MDG before marking the batch as processed successfully.***


### Whitelisting client services

Any service using `file-transmission` must be whitelisted. Please contact Platform Services if you would like to use this service.
Consuming services must identify themselves in requests via the `User-Agent` header. If the supplied value is not in `file-transmission`'s list of allowed services then the `/file-transmission/request` call will fail with a `403` error.

In addition to returning the `403` error, `file-transmission` will log details of the Forbidden request. For example:

```json
{
    "app":"file-transmission",
    "message":"Invalid User-Agent: [Some(my-unknown-service-name)].",
    "logger":"application",
    "level":"WARN"
}
```

*Note:* If you are using `[http-verbs](https://github.com/hmrc/http-verbs)` to call `file-transmission`, then the `User-Agent` header will be set automatically.
(See: [HttpVerb.scala](https://github.com/hmrc/http-verbs/blob/2807dc65f64009bd7ce1f14b38b356e06dd23512/src/main/scala/uk/gov/hmrc/http/HttpVerb.scala#L53))


# Running locally

`file-transmission` can be run locally using sbt (`sbt run`) or service manager (`sm --start FILE_TRANSMISSION`).

## Related projects, useful links:

* [upscan](https://github.com/hmrc/upscan-initiate) - service that manages the process of uploading files to MDTP by end users

### Testing
* [file-transmission-acceptance-tests](https://github.com/hmrc/file-transmisson-acceptance-tests) - acceptance tests of the `file-transmission` service

### Slack
* [#team-plat-services](https://hmrcdigital.slack.com/messages/C705QD804/)
* [#event-upscan](https://hmrcdigital.slack.com/messages/C8XPL559N)


### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
