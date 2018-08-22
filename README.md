# file-transmission <a name="top"></a>

[![Build Status](https://travis-ci.org/hmrc/file-transmission.svg)](https://travis-ci.org/hmrc/file-transmission) [ ![Download](https://api.bintray.com/packages/hmrc/releases/file-transmission/images/download.svg) ](https://bintray.com/hmrc/releases/file-transmission/_latestVersion)

# File transmission user manual

## Contents
1. [Introduction](#introduction)
2. [Typical use case](#use)
3. [Onboarding requirements](#onboard)
4. [Service usage](#service)
  a. [Request file transmission](#service__request)
  b. [Example `file-transmission` request](#service__example)
  c. [Request outcome](#service__output)
  d. [Whitelisting client services](#service__whitelist)
5. [Running and maintenance of the service](#run)
  a. [Run locally](#run__local)
6. [Appendix](#appendix)
  a. [Related projects, useful links](#appendix__links)
    i. [Testing](#appendix__links__testing)
    ii. [Slack](#appendix__links__slack)
  b. [License](#appendix__license)

## Introduction <a name="introduction"></a>

Microservice that facilitates transmission of files requested by MDTP through MDG.

Services on the MDTP platform should use `file-transmission` to initiate the transmission of a batch of hosted files through MDG. `file-transmission` notifies MDG about files that the service on MDTP would like to be processed. Please note that `file-transmission` does not upload or transfer files directly. Instead, it provides data allowing MDG to identify the file and how it should be processed, along with where the file is hosted. File upload and hosting must be provided by another service, such as [`upscan`](https://github.com/hmrc/upscan-initiate).

[[Back to the top]](#top)

## Typical use case <a name="use"></a>

- Consuming service requests upload of user file(s) using `upscan`
- `Upscan` notifies the consuming service of successful file upload and the relevant URL where the file is hosted and can be downloaded
- Consuming service verifies ensures that all required files have been correctly uploaded by the user
- Consuming service can now use `file-transmission` to notify MDG that these files are ready to be processed
- `file-transmission` later sends a callback to the consuming service with either confirmation that the request
has been accepted by MDG or a relevant error
- MDG proceeds to asynchronously process the file batch as appropriate

[[Back to the top]](#top)

## Onboarding requirements <a name="onboard"></a>
To use `file-transmission`, the consuming service must let Platform Services know :
- the User-Agent request header of the service so it can be whitelisted

[[Back to the top]](#top)

## Service usage <a name="service"></a>

### Request file transmission  <a name="service__request"></a>

The basic unit of work for `file-transmission` is data pertaining to a batch consisting of one or more files.

Information about each file in the batch is passed to `file-transmission` in separate POST requests. 
Additional calls to create a batch or to notify that information about all files in the batch has
been provided are not necessary.

Transmission requests are processed asynchronously, and after each request has been sent, the consuming service
receives the callback with sending status.

Whitelisted consuming service first make a POST request to the `/file-transmission/request` endpoint. 
The request should provide data about the batch, each file in the batch, and a callback URL that will be used to asynchronously notify the consuming service when MDG has processed the request. The consuming service may also provide additional optional metadata that it wants to pass through MDG.

The body of a request for transmission of a file in a batch would typically comprise the below:
- `callbackUrl` - URL provided by the consuming service, that is used by `file-transmission` to notify whether the request was accepted by MDG. Please be aware that this should be an HTTPS endpoint.
- `requestTimeoutInSeconds` - duration during which `file-transmission` will try to deliver file details to MDG before giving up (this field is optional)
- Batch information
  - `batchId` - unique batch identifier
  - `fileCount` - number of files in the batch
- File information
  - `reference` - unique reference of the file (MDG will interpret this as the `correlationId`)
  - `fileName` - original name of the file
  - `fileSize` - size of uploaded file
  - `mimeType` - MIME type of the file
  - `checksum` - SHA256 checksum of the file in hexadecimal format
   - `location` - URL where file is hosted. This URL should be accessible by MDG, e.g. verify networking configuration and use external domain names. URLs provided by `upscan` will already meet this requirement.
  - `sequenceNumber` - relative number of the file within the batch **[the first file in the batch should have sequenceNumber '1']**
- Journey information
  - `interfaceName` - type of interface for MDG to use, specifying what process should be invoked on the file batch
  - `interfaceVersion` - the specific version of the named interface to use
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

[[Back to the top]](#top)

### Example `file-transmission` request <a name="service__example"></a>

Here is an example of a request body for `file-transmission`:
```
{
	"batch": {
		"id": "fghij67890",
		"fileCount": 10
	},
	"callbackUrl": "https://file-transmission-callback-listener.public.mdtp/file-transmission-callback-listener/listen",
	"requestTimeoutInSeconds": 300,
	"file": {				
		"reference": "abcde12345",
		"name": "someFileN.ame",
		"mimeType": "application/pdf",
		"checksum": "asdrfgvbhujk13579",
		"location": "https://file-outbound-asderfvghyujk1357690.aws.amazon.com",	
		"sequenceNumber": 3,
		"size": 1024
	},
	"interface":{
		"name": "interfaceName name",
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


[[Back to the top]](#top)

### Request outcome <a name="service__outcome"></a>

A successful POST request will receive a HTTP 204 response with an empty body.

An unsuccessful POST request will receive a HTTP-error coded response (4xx, 5xx). The response body will contain XML encoded details of the problem. See the Error Handling section for details.

**Please note that a successful response only means that the request has been parsed and stored for further processing. As MDG processing is performed asynchronously, the consuming service should wait until a callback is made from MDG before marking the batch as processed successfully.***

After the request has been successfully passed to MDG, the consuming service retrieves callback to the URL specified in the request.
The callback has the following format:
```
   {
      "fileReference":"11370e18-6e24-453e-b45a-76d3e32ea33d",
      "batchId":"32230e18-6e24-453e-b45a-76d3e32ea33d",
      "outcome":"SUCCESS"
   }
```
In case passing the request to MDG failed, the consuming service retrieves callback in the following format:
```
    {
      "fileReference":"11370e18-6e24-453e-b45a-76d3e32ea33d",
      "batchId":"32230e18-6e24-453e-b45a-76d3e32ea33d",
      "outcome":"FAILURE"
      "errorDetails": "text field from MDG"
   }
```

[[Back to the top]](#top)

### Retrying

If file-transmission fails to deliver the message to MDG it will make several attempts to redeliver it after delay.
If it fails to deliver it within certain delivery window, failure notification callback will be sent to consuming service.

Default length of delivery window is set in the service configuration (`initialBackoffAfterFailure` parameter).
It can be customized per request by setting `requestTimeoutInSeconds` parameter within the request body.

The service uses exponential backoff when performing retry attempts. Initial retry delay is defined by 
`initialBackoffAfterFailure` property set in the service configuration. After every failed attempt, this delay
is doubled.

[[Back to the top]](#top)

### Whitelisting client services <a name="service__whitelist"></a>

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

[[Back to the top]](#top)

## Running and maintenance of the service <a name="run"></a>

### Run locally <a name="run__local"></a>

#### Option #1: start all `file-transmission` dependencies using [`service-manager`](https://github.com/hmrc/service-manager)
- Execute the below command to start `file-transmission` and its relevant dependencies with required configuration:
  ```
  sm -r --start FILE_TRANSMISSION_ALL
  ```
[[Back to the top]](#top)

#### Option #2: start `file-transmission` dependencies individually using [`service-manager`](https://github.com/hmrc/service-manager)
- Execute the below commands to start `file-transmission` dependencies individually with required configuration:
  ```
  sm -r --start FILE_TRANSMISSION
  sm -r --start MDG_STUB
  ```
  
  [[Back to the top]](#top)
  
#### Option #3: start each `file-transmission` dependency individually with sbt and relevant local code
- In the `file-transmission` repository, execute the below to start the application with required configuration:
    ```
    sbt "run 9575 -DcallbackValidation.allowedProtocols="http,https" -DmdgEndpoint="http://localhost:9576/mdg-stub/request" -DuserAgentFilter.allowedUserAgents="file-transmission-acceptance-tests""
    ```
- In the `mdg-stub` repository, execute the below to start the application:
    ```
    sbt "run 9576"
    ```
    
[[Back to the top]](#top)

## Appendix <a name="appendix"></a>

### Related projects, useful links: <a name="appendix__links"></a>

* [upscan](https://github.com/hmrc/upscan-initiate) - service that manages the process of uploading files to MDTP by end users

[[Back to the top]](#top)

#### Testing <a name="appendix__links__testing"></a>
* [file-transmission-acceptance-tests](https://github.com/hmrc/file-transmisson-acceptance-tests) - acceptance tests of the `file-transmission` service
* [file-transmission-callback-listener](https://github.com/hmrc/file-transmisson-callback-listener) - helper tool used by acceptance tests to capture callback requests
* [mdg-stub](https://github.com/hmrc/mdg-stub) - mock of the MDG service that can be used for testing


[[Back to the top]](#top)

#### Slack <a name="appendix__links__slack"></a>
* [#team-plat-services](https://hmrcdigital.slack.com/messages/C705QD804/)
* [#event-upscan](https://hmrcdigital.slack.com/messages/C8XPL559N)

[[Back to the top]](#top)

### License <a name="appendix__license"></a>

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")

[[Back to the top]](#top)
