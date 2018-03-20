package com.codetailor.awsproxy;

import com.amazonaws.DefaultRequest;
import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.internal.SignerConstants;
import com.amazonaws.http.HttpMethodName;
import com.amazonaws.util.AwsHostNameUtils;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 * This filter use to AWS proxy.<br/>
 * 1. In this proxy first path segment consider as host and remaining path consider as path. (e.g.
 * http://localhost:8080//vishal-edentify.s3.amazonaws.com/studentData) host =
 * https://vishal-edentify.s3.amazonaws.com, path = /studentData, So proxy on
 * "https://vishal-edentify.s3.amazonaws.com/studentData"<br/>
 * 2. Add authentication header and calculate signature base on
 * https://docs.aws.amazon.com/AmazonS3/latest/API/sig-v4-header-based-auth.html#create-signature-presign-entire-payload
 * Use 'aws-java-sdk-core' library and 'awsproxy.accessKey' & 'awsproxy.secretKey' configuration for
 * calculate signature.
 * 
 * @author vishal
 *
 */
@Component
public class ProxyFilter extends ZuulFilter {

  private static final Logger logger = LoggerFactory.getLogger(ProxyFilter.class);

  @Value("${awsproxy.accessKey}")
  private String accessKey;

  @Value("${awsproxy.secretKey}")
  private String secretKey;
  
  @Value("#{servletContext.contextPath}")
  private String servletContextPath;

  /**
   * This method is the core method of a ZuulFilter.
   */
  public Object run() {
    logger.debug("run:: managing AWS security...");

    String domain = validateAndGetDomain();
    validateConfig();
    RequestContext requestContext = RequestContext.getCurrentContext();

    // In application.properties zuul proxy on 'aws' serviceId(clientId), So when we
    // update that need update this serviceId.
    requestContext.set("serviceId", "aws");

    // Find domain name from first path segment and set in routeHost
    addRouteHost(domain);
    // Add AWS authentication
    doAuthentication();

    logger.info("run:: proxy on '{}'", requestContext.getRouteHost());
    return null;
  }

  /**
   * Validate the request contain at least 1 path segment.<br/>
   * Find host name as first path segment of request URI.
   * 
   * @return host name from request URI
   */
  private String validateAndGetDomain() {
    String uri = RequestContext.getCurrentContext().getRequest().getRequestURI();
    uri = uri.replace(servletContextPath, "");
    if (uri.startsWith("/")) {
      uri = uri.substring(1);
    }

    List<String> urlParams = Arrays.asList(uri.split("/"));
    if (urlParams.size() == 0 || StringUtils.isBlank(urlParams.get(0))) {
      throw new IllegalArgumentException(
          "validateAndGetDomain:: Invalid request URI '" + uri + "'.");
    }
    return urlParams.get(0);
  }

  /**
   * validate AWS credential for signature calculation.
   */
  private void validateConfig() {
    if (StringUtils.isBlank(accessKey)) {
      throw new IllegalStateException("Invalid AWS accessKey='" + accessKey + "'");
    }

    if (StringUtils.isBlank(secretKey)) {
      throw new IllegalStateException("Invalid AWS secretKey='" + secretKey + "'");
    }
  }

  /**
   * Use to found domain form 1st path segment and set into `RouteHost`.<br/>
   * 
   * @param domain name of host
   */
  private void addRouteHost(String domain) {
    RequestContext requestContext = RequestContext.getCurrentContext();

    try {
      String url = UriComponentsBuilder.fromHttpUrl("https://" + domain).path(removeDomainFromUri())
          .build().toUriString();
      requestContext.setRouteHost(new URL(url));
    } catch (MalformedURLException ex) {
      throw new RuntimeException("addRouteHost:: Invalid protocol is specified.", ex);
    }
  }

  /**
   * Use to add authentication of AWS services in requestContext.<br/>
   * It is calculate AWS signature using {@link AWS4Signer}.
   */
  @SuppressWarnings("deprecation")
  private void doAuthentication() {
    logger.debug("doAuthentication:: adding authentication of AWS serices...");
    RequestContext requestContext = RequestContext.getCurrentContext();
    URI endpointUrl = buildEndPointUri(requestContext);

    String serviceName = AwsHostNameUtils.parseServiceName(endpointUrl);
    DefaultRequest<String> req = createDefaultRequest(requestContext, endpointUrl, serviceName);

    BasicAWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
    AWS4Signer signer = new AWS4Signer();
    signer.setServiceName(serviceName);
    signer.sign(req, credentials);

    Map<String, String> headers = requestContext.getZuulRequestHeaders();
    headers.putAll(req.getHeaders());
    logZuulHeaders(headers);
  }

  /**
   * Use to build routeHost URL to URI.
   * 
   * @param requestContext instance of {@link RequestContext}
   * @return roustHost URI
   */
  private URI buildEndPointUri(RequestContext requestContext) {
    try {
      return requestContext.getRouteHost().toURI();
    } catch (URISyntaxException ex) {
      throw new IllegalArgumentException("doAuthentication:: Fail to conver URL to URI.", ex);
    }
  }

  /**
   * Use to create instance of {@link DefaultRequest}.
   * 
   * @param requestContext instance of {@link RequestContext}
   * @param endpointUrl with host and path
   * @param serviceName of AWS
   * @return {@link DefaultRequest}
   */
  private DefaultRequest<String> createDefaultRequest(RequestContext requestContext,
      URI endpointUrl, String serviceName) {
    HttpServletRequest request = requestContext.getRequest();
    String httpMethod = request.getMethod();

    DefaultRequest<String> req = new DefaultRequest<>(serviceName);
    req.setEndpoint(endpointUrl);
    req.setHttpMethod(HttpMethodName.valueOf(httpMethod));
    try {
      req.setContent(new ByteArrayInputStream(IOUtils.toByteArray(request.getInputStream())));
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }

    // added query params
    if (requestContext.getRequestQueryParams() != null) {
      req.setParameters(requestContext.getRequestQueryParams());
    }

    // add all headers
    Map<String, String> headers = req.getHeaders();
    Enumeration<String> headerNames = request.getHeaderNames();
    while (headerNames.hasMoreElements()) {
      String headerName = (String) headerNames.nextElement();

      if (StringUtils.startsWithIgnoreCase(headerName, "X-Amz")) {
        String headerValue = request.getHeader(headerName);
        headers.putIfAbsent(headerName, headerValue);
      }
    }

    // all S3 API Content-MD5 is required
    headers.put(SignerConstants.X_AMZ_CONTENT_SHA256, "required");
    logger.debug("createDefaultRequest:: serviceName:{}, method:{}", serviceName, httpMethod);
    return req;
  }

  /**
   * Remove domain name form given uri. and update current requestURI.
   */
  private String removeDomainFromUri() {
    RequestContext requestContext = RequestContext.getCurrentContext();
    String uri = requestContext.getRequest().getRequestURI();

    // remove domin from requestUri
    String reqUri = StringUtils.substringAfter(uri, validateAndGetDomain());
    requestContext.set("requestURI", "/");
    return reqUri;
  }

  /**
   * Use to print given header if debug is enable.
   * 
   * @param headers with key, value pair
   */
  private void logZuulHeaders(Map<String, String> headers) {
    if (logger.isDebugEnabled()) {

      for (Map.Entry<String, String> entry : headers.entrySet()) {
        logger.debug("logZuulHeaders:: {}={}", entry.getKey(), entry.getValue());
      }
    }
  }

  @Override
  public boolean shouldFilter() {
    return true;
  }

  @Override
  public int filterOrder() {
    return 1;
  }

  @Override
  public String filterType() {
    return "pre";
  }

}
