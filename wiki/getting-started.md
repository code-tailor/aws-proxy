# Getting started

Application is deployed as WAR on Java web server like Tomcat, Jetty etc. Though tested with tomcat 8.0 only, but will run with jetty and other servers as well which supports Servlet 3.0.

1. Download WAR and rename it to desired name. e.g. `aws-proxy.war`.
2. Set AWS accessKey & secretKey as environment variables (`AWSPROXY_ACCESSKEY` & `AWSPROXY_SECRETKEY` respectively) for Tomcat. 
3. Restart Tomcat server.
4. Open manager page http://YOUR_IP:PORT/manager/html and deploy WAR.
5. Done!!


## Examples

### Rekognition: DetectLables API

To detectLables of image `animal.jpg` stored in S3 buket `vishal-edentify`. 

```
curl -XPOST -H 'X-Amz-Target: RekognitionService.DetectLabels' -H 'Content-Type: application/x-amz-json-1.1'  -d '{
   "Image":{
      "S3Object":{
         "Bucket":"vishal-edentify",
         "Name":"animal.jpg"
      }
   }
}' 'http://localhost:8080/aws-proxy/rekognition.us-east-1.amazonaws.com'
``` 

> Above API expects that S3 bucket is hosted in same region `us-east-1`. If you have S3 bucket in separate region, consult API documentation of Rekognition service.

### S3: GetObject API
To retrieve detail of `test.txt` file stored in bucket `vishal-edentify.s3.amazonaws.com`,
 
```
curl -XGET 'http://localhost:8080/aws-proxy/vishal-edentify.s3.amazonaws.com/test.txt'
``` 


### SNS: Send Transactional SMS with customer sender ID

```
curl -X POST \
  http://10.128.3.10:8080/aws-proxy/sns.ap-southeast-2.amazonaws.com \
  -H 'cache-control: no-cache' \
  -H 'content-type: application/x-www-form-urlencoded' \
  -H 'postman-token: 34f4d951-d168-5494-d215-7a6b42f6bcac' \
  -d 'Action=Publish&Message=Test%20Message1&PhoneNumber=%2B919898123456&MessageAttributes.entry.1.Name=AWS.SNS.SMS.SMSType&MessageAttributes.entry.1.Value.DataType=String&MessageAttributes.entry.1.Value.StringValue=Transactional&MessageAttributes.entry.2.Name=AWS.SNS.SMS.SenderID&MessageAttributes.entry.2.Value.DataType=String&MessageAttributes.entry.2.Value.StringValue=EDENTIFY'
```
  
