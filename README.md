# file-transmission

[![Build Status](https://travis-ci.org/hmrc/file-transmission.svg)](https://travis-ci.org/hmrc/file-transmission) [ ![Download](https://api.bintray.com/packages/hmrc/releases/file-transmission/images/download.svg) ](https://bintray.com/hmrc/releases/file-transmission/_latestVersion)

Microservice that facilitates transmission of files between MDTP and MDG.

Whenever service on MDTP platform wants to send bundle (batch) of files to MDG it will need to use this service. The file-transmission
service notifies MDG that files prepared by service on MDTP are ready to be processed. Please note that file-transmission service 
do not transfer file contents. The service just passes data about file location. The file content has to be provided by other service (eg. upscan)

#Typical usage scenario

* Consuming service requests upload of the file from user using `upscan` service
* `Upscan` notifies consuming service that the file has been uploaded and is ready to be processed - giving consuming 
service the URL that allows to download the file.
* Consuming service ensures that all required files have been uploaded by the user
* Consuming service uses file-transmission service to notify MDG that files are ready to be processes
* MDG asynchronously uses the file

## Service usage

### Whitelisting

In order to initiate an upload the consuming service must be whitelisted by file-transmission. See the 'Whitelisting client services' section further down in this document.

### Requesting a file transmission

The basic unit of work for file-transmission service is a batch that might consist of one or more files.

Information about every file in the batch is passed to the file-transmission service as a separate POST request. 
There is no need to make additional call to create a batch or to notify that data about all files in the batch have 
been provided.

Assuming the consuming service is whitelisted, it makes a POST request to the `/file-transmission/request` endpoint. 
The request contains data about batch, details of the file and any additional metadata which consuming service wants
to pass to MDG. The request should also contain callback URL that will be used to asynchronously notify consuming service 
that MDG has processed the request.

The following information has to pe passed within request:
- Batch identifier
- Number of files in the batch
- `callbackUrl` that will be used by MDG to notify upstream service about result of batch processing.
- `journeyName` / `journeyVersion` - information for MDG about meaning of files in the batch, determining what process should be invoked.
- `requestTimeoutInSeconds` - time describing how long file-transmission service will try to deliver file details to MDG. 
- Details about the file
-- `location` - URL allowing to download file contents. Please be aware that this URL has to be accessible by MDG (verify networking configuration and use external domain names). Download URL's provided by upscan service are valid.
-- `fileName` / `mimeType` - original name of the file and it's MIME type
-- `checksum` - SHA256 checksum of the file in hexadecimal format
-- `reference` - unique reference of the file (this will be interpreted by MDG as `correlationId`)
-- `sequenceNumber` -  sequence number of the file in the batch (**please be aware that this should be 1-based**)
- Additional properties - key/value map of custom properties that can be attached to the file

The request has to include the following HTTP headers:

| Header name|Description|Required|
|--------------|-----------|--------|
| User-Agent | Identifier of the service that calls upscan | yes |
| X-Session-ID | Identifier of the user's session | no  |
| X-Request-ID | Identifier of the user's request | no |

Session-ID / Request-ID headers will be used to link the file with user's journey.

*Note:* If you are using `[http-verbs](https://github.com/hmrc/http-verbs)` to call the service, all the headers will be set automatically
(See: [HttpVerb.scala](https://github.com/hmrc/http-verbs/blob/2807dc65f64009bd7ce1f14b38b356e06dd23512/src/main/scala/uk/gov/hmrc/http/HttpVerb.scala#L53))

Here is an example of the request body:
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
		"checksum": "asdrfgvbhujk13579",
		"location": "https://file-outbound-asderfvghyujk1357690.aws.amazon.com",
		"name": "someFileN.ame",
		"mimeType": "application/pdf",				
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

### Request outcomes

If the POST is not successful, the service will return a HTTP error code (4xx, 5xx). The response body will contain XML encoded details of the problem. See the Error handling section for details.

If the POST is successful, the service returns a HTTP 204 response with an empty body.

**Please not that successful result means only that the request has been parsed and has been stored for further processing. Because MDG
processing is performed asynchronously, consuming service should wait until callback from MDG is made to mark batch as processed successfully.***


### Whitelisting client services

Any service using `file-transmission` must be whitelisted. Please contact platform-services if you want to use the service.
Consuming services must identify themselves in requests via the `User-Agent` header. If the supplied value is not in file-transmissions list of allowed services then the `/initiate` call will fail with a `403` error.

In addition to returning a `403` error, Upscan will log details of the Forbidden request. For example:

```json
{
    "app":"file-transmission",
    "message":"Invalid User-Agent: [Some(my-unknown-service-name)].",
    "logger":"application",
    "level":"WARN"
}
```

*Note:* If you are using `[http-verbs](https://github.com/hmrc/http-verbs)` to call Upscan, then the `User-Agent` header will be set automatically.
(See: [HttpVerb.scala](https://github.com/hmrc/http-verbs/blob/2807dc65f64009bd7ce1f14b38b356e06dd23512/src/main/scala/uk/gov/hmrc/http/HttpVerb.scala#L53))


# Running locally

Service can be run locally by sbt (`sbt run`) or using service manager (`sm --start FILE_TRANSMISSION`)

## Related projects, useful links:

* [upscan](https://github.com/hmrc/upscan-initiate) - service that manages the process of uploading files to MDTP by end users

### Testing
* [file-transmission-acceptance-tests](https://github.com/hmrc/file-transmisson-acceptance-tests) - acceptance tests of the service

### Slack
* [#team-plat-services](https://hmrcdigital.slack.com/messages/C705QD804/)
* [#event-upscan](https://hmrcdigital.slack.com/messages/C8XPL559N)


### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")