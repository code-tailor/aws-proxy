# Introduction

- It's a proxy to AWS APIs created using zuul.
- It's mainly used to inject authorization headers (e.g. `Authorization`, `x-amz-content-sha256`, `X-Amz-Date`) during proxy. It expects aws accessKey and secretKey is provided by user as environment variable. Using them it calculates signature using algorithm [AWS Signature Version 4](https://docs.aws.amazon.com/AmazonS3/latest/API/sig-v4-header-based-auth.html#create-signature-presign-entire-payload).
- Uses first path segmenet as domain-name while doing proxy. e.g. If I have deployed app at `http://localhost:8080/aws-proxy/` and access `http://localhost:8080/aws-proxy/rekognition.us-east-1.amazonaws.com` request is proxied to `https://rekognition.us-east-1.amazonaws.com`.
- You have noticed that it always uses SSL/https while doing proxy.

# [Getting Started](wiki/getting-started.md)