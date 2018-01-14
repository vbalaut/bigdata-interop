/**
 * Copyright 2014 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.hadoop.util;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonError.ErrorInfo;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.Json;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.testing.http.HttpTesting;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import java.io.EOFException;
import java.io.IOError;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import javax.net.ssl.SSLException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.*;

/**
 * Unit-tests for ApiErrorExtractor class.
 */
@RunWith(JUnit4.class)
public class ApiErrorExtractorTest {
  private GoogleJsonResponseException accessDenied;  // STATUS_CODE_FORBIDDEN
  private GoogleJsonResponseException statusOk;  // STATUS_CODE_OK
  private GoogleJsonResponseException notFound;  // STATUS_CODE_NOT_FOUND
  private GoogleJsonResponseException badRange;  // STATUS_CODE_RANGE_NOT_SATISFIABLE;
  private GoogleJsonResponseException alreadyExists;  // STATUS_CODE_CONFLICT
  private GoogleJsonResponseException rateLimited;  // rate limited
  private GoogleJsonResponseException notRateLimited;  // not rate limited because of domain
  private GoogleJsonResponseException resourceNotReady;
  private GoogleJsonResponseException bigqueryRateLimited;  // bigquery rate limited
  private static final int POSSIBLE_RATE_LIMIT = 429;  // Can be many things, but not STATUS_CODE_OK

  private ApiErrorExtractor errorExtractor = new ApiErrorExtractor();

  @Before
  public void setUp() throws Exception {
    accessDenied = googleJsonResponseException(
        HttpStatusCodes.STATUS_CODE_FORBIDDEN, "Forbidden", "Forbidden");
    statusOk = googleJsonResponseException(
        HttpStatusCodes.STATUS_CODE_OK, "A reason", "ok");
    notFound = googleJsonResponseException(
        HttpStatusCodes.STATUS_CODE_NOT_FOUND, "Not found", "Not found");
    badRange = googleJsonResponseException(
        ApiErrorExtractor.STATUS_CODE_RANGE_NOT_SATISFIABLE, "Bad range", "Bad range");
    alreadyExists = googleJsonResponseException(
        409, "409", "409");
    resourceNotReady = googleJsonResponseException(
        400, ApiErrorExtractor.RESOURCE_NOT_READY_REASON_CODE, "Resource not ready");

    // This works because googleJsonResponseException takes final ErrorInfo
    ErrorInfo errorInfo = new ErrorInfo();
    errorInfo.setReason(ApiErrorExtractor.RATE_LIMITED_REASON_CODE);
    notRateLimited = googleJsonResponseException(POSSIBLE_RATE_LIMIT, errorInfo, "");
    errorInfo.setDomain(ApiErrorExtractor.USAGE_LIMITS_DOMAIN);
    rateLimited = googleJsonResponseException(POSSIBLE_RATE_LIMIT, errorInfo, "");
    errorInfo.setDomain(ApiErrorExtractor.GLOBAL_DOMAIN);
    bigqueryRateLimited = googleJsonResponseException(POSSIBLE_RATE_LIMIT, errorInfo, "");
  }

  /**
   * Validates accessDenied().
   */
  @Test
  public void testAccessDenied() {
    // Check success case.
    assertTrue(errorExtractor.accessDenied(accessDenied));
    assertTrue(errorExtractor.accessDenied(new IOException(accessDenied)));
    assertTrue(errorExtractor.accessDenied(
        new IOException(new IOException(accessDenied))));

    // Check failure case.
    assertFalse(errorExtractor.accessDenied(statusOk));
    assertFalse(errorExtractor.accessDenied(new IOException(statusOk)));
  }

  /**
   * Validates itemAlreadyExists().
   */
  @Test
  public void testItemAlreadyExists() {
    // Check success cases.
    assertTrue(errorExtractor.itemAlreadyExists(alreadyExists));
    assertTrue(errorExtractor.itemAlreadyExists(new IOException(alreadyExists)));
    assertTrue(errorExtractor.itemAlreadyExists(
        new IOException(new IOException(alreadyExists))));

    // Check failure cases.
    assertFalse(errorExtractor.itemAlreadyExists(statusOk));
    assertFalse(errorExtractor.itemAlreadyExists(new IOException(statusOk)));
  }

  /**
   * Validates itemNotFound().
   */
  @Test
  public void testItemNotFound() {
    // Check success cases.
    assertTrue(errorExtractor.itemNotFound(notFound));
    GoogleJsonError gje = new GoogleJsonError();
    gje.setCode(HttpStatusCodes.STATUS_CODE_NOT_FOUND);
    assertTrue(errorExtractor.itemNotFound(gje));
    assertTrue(errorExtractor.itemNotFound(new IOException(notFound)));
    assertTrue(errorExtractor.itemNotFound(new IOException(new IOException(notFound))));

    // Check failure case.
    assertFalse(errorExtractor.itemNotFound(statusOk));
    assertFalse(errorExtractor.itemNotFound(new IOException()));
    assertFalse(errorExtractor.itemNotFound(new IOException(new IOException())));
  }

  /**
   * Validates rangeNotSatisfiable().
   */
  @Test
  public void testRangeNotSatisfiable() {
    // Check success case.
    assertTrue(errorExtractor.rangeNotSatisfiable(badRange));
    assertTrue(errorExtractor.rangeNotSatisfiable(new IOException(badRange)));
    assertTrue(errorExtractor.rangeNotSatisfiable(
        new IOException(new IOException(badRange))));

    // Check failure case.
    assertFalse(errorExtractor.rangeNotSatisfiable(statusOk));
    assertFalse(errorExtractor.rangeNotSatisfiable(notFound));
    assertFalse(errorExtractor.rangeNotSatisfiable(new IOException(notFound)));
  }

  /**
   * Validates rateLimited().
   */
  @Test
  public void testRateLimited() {
    // Check success case.
    assertTrue(errorExtractor.rateLimited(rateLimited));
    assertTrue(errorExtractor.rateLimited(new IOException(rateLimited)));
    assertTrue(errorExtractor.rateLimited(new IOException(new IOException(rateLimited))));

    // Check failure cases.
    assertFalse(errorExtractor.rateLimited(notRateLimited));
    assertFalse(errorExtractor.rateLimited(new IOException(notRateLimited)));
  }

  /**
   * Validates rateLimited() with BigQuery domain / reason codes
   */
  @Test
  public void testBigQueryRateLimited() {
    // Check success case.
    assertTrue(errorExtractor.rateLimited(bigqueryRateLimited));
    assertTrue(errorExtractor.rateLimited(new IOException(bigqueryRateLimited)));
    assertTrue(errorExtractor.rateLimited(
        new IOException(new IOException(bigqueryRateLimited))));

    // Check failure cases.
    assertFalse(errorExtractor.rateLimited(notRateLimited));
  }

  /**
   * Validates ioError().
   */
  @Test
  public void testIOError() {
    // Check true cases.
    Throwable ioError1 = new EOFException("io error 1");
    assertTrue(errorExtractor.ioError(ioError1));
    assertTrue(errorExtractor.ioError(new Exception(ioError1)));
    assertTrue(errorExtractor.ioError(new RuntimeException(new RuntimeException(ioError1))));

    Throwable ioError2 = new IOException("io error 2");
    assertTrue(errorExtractor.ioError(ioError2));
    assertTrue(errorExtractor.ioError(new Exception(ioError2)));
    assertTrue(errorExtractor.ioError(new RuntimeException(new RuntimeException(ioError2))));

    Throwable ioError3 = new IOError(new Exception("io error 3"));
    assertTrue(errorExtractor.ioError(ioError3));
    assertTrue(errorExtractor.ioError(new Exception(ioError3)));
    assertTrue(errorExtractor.ioError(new RuntimeException(new RuntimeException(ioError3))));

    // Check false cases.
    Throwable notIOError = new Exception("not io error");
    assertFalse(errorExtractor.ioError(notIOError));
    assertFalse(errorExtractor.ioError(new RuntimeException(notIOError)));
  }

  /**
   * Validates socketError().
   */
  @Test
  public void testSocketError() {
    // Check true cases.
    Throwable socketError1 = new SocketTimeoutException("socket error 1");
    assertTrue(errorExtractor.socketError(socketError1));
    assertTrue(errorExtractor.socketError(new Exception(socketError1)));
    assertTrue(errorExtractor.socketError(new IOException(new IOException(socketError1))));

    Throwable socketError2 = new SocketException("socket error 2");
    assertTrue(errorExtractor.socketError(socketError2));
    assertTrue(errorExtractor.socketError(new Exception(socketError2)));
    assertTrue(errorExtractor.socketError(new IOException(new IOException(socketError2))));

    Throwable socketError3 = new SSLException("ssl exception", new EOFException("eof"));
    assertTrue(errorExtractor.socketError(socketError2));
    assertTrue(errorExtractor.socketError(new Exception(socketError2)));
    assertTrue(errorExtractor.socketError(new IOException(new IOException(socketError2))));

    // Check false cases.
    Throwable notSocketError = new Exception("not socket error");
    Throwable notIOError = new Exception("not io error");
    assertFalse(errorExtractor.socketError(notSocketError));
    assertFalse(errorExtractor.socketError(new IOException(notSocketError)));
    assertFalse(errorExtractor.socketError(new SSLException("handshake failed", notIOError)));
  }

  /**
   * Validates readTimedOut().
   */
  @Test
  public void testReadTimedOut() {
    // Check success case.
    IOException x = new SocketTimeoutException("Read timed out");
    assertTrue(errorExtractor.readTimedOut(x));

    // Check failure cases.
    x = new IOException("not a SocketTimeoutException");
    assertFalse(errorExtractor.readTimedOut(x));
    x = new SocketTimeoutException("not the right kind of timeout");
    assertFalse(errorExtractor.readTimedOut(x));
  }

  /**
   * Validates resourceNotReady().
   */
  @Test
  public void testResourceNotReady() {
    // Check success case.
    assertTrue(errorExtractor.resourceNotReady(resourceNotReady));
    assertTrue(errorExtractor.resourceNotReady(new IOException(resourceNotReady)));
    assertTrue(errorExtractor.resourceNotReady(
        new IOException(new IOException(resourceNotReady))));

    // Check failure case.
    assertFalse(errorExtractor.resourceNotReady(statusOk));
    assertFalse(errorExtractor.resourceNotReady(new IOException(statusOk)));
  }

  @Test
  public void testGetErrorMessage() throws IOException {
    IOException withJsonError = googleJsonResponseException(
        42, "Detail Reason", "Detail message", "Top Level HTTP Message");
    assertEquals("Top Level HTTP Message", errorExtractor.getErrorMessage(withJsonError));

    IOException nullJsonError = googleJsonResponseException(
        42, null, null, "Top Level HTTP Message");
    assertEquals("Top Level HTTP Message", errorExtractor.getErrorMessage(nullJsonError));
  }

  @Test
  public void testUnwrapJsonError() throws IOException {
    GoogleJsonResponseException withJsonError = googleJsonResponseException(
          42, "Detail Reason", "Detail message", "Top Level HTTP Message");

    GoogleJsonError originalError = errorExtractor.unwrapJsonError(withJsonError);
    assertNotNull(originalError);
    assertEquals(originalError.getCode(), 42);
    assertEquals(originalError.getMessage(), "Top Level HTTP Message");

    IOException wrappedException = new IOException(withJsonError.getDetails().toString());
    GoogleJsonError wrappedError = errorExtractor.unwrapJsonError(wrappedException);
    assertNotNull(wrappedError);
    assertEquals(wrappedError.getCode(), 42);
    assertEquals(wrappedError.getMessage(), "Top Level HTTP Message");

    IOException nestedException = new IOException(new IOException(withJsonError.getDetails().toString()));
    GoogleJsonError nestedError = errorExtractor.unwrapJsonError(nestedException);
    assertNotNull(nestedError);
    assertEquals(nestedError.getCode(), 42);
    assertEquals(nestedError.getMessage(), "Top Level HTTP Message");

    IOException multiException = new IOException();
    multiException.addSuppressed(new IOException());
    multiException.addSuppressed(new IOException(new IOException(withJsonError.getDetails().toString())));
    GoogleJsonError multiError = errorExtractor.unwrapJsonError(multiException);
    assertNotNull(multiError);
    assertEquals(multiError.getCode(), 42);
    assertEquals(multiError.getMessage(), "Top Level HTTP Message");
  }

  /**
   * Builds a fake GoogleJsonResponseException for testing API error handling.
   */
  private static GoogleJsonResponseException googleJsonResponseException(
      int httpStatus, String reason, String message) throws IOException {
    return googleJsonResponseException(httpStatus, reason, message, message);
  }

  /**
   * Builds a fake GoogleJsonResponseException for testing API error handling.
   */
  private static GoogleJsonResponseException googleJsonResponseException(
      int httpStatus, String reason, String message, String httpStatusString) throws IOException {
    ErrorInfo errorInfo = new ErrorInfo();
    errorInfo.setReason(reason);
    errorInfo.setMessage(message);
    return googleJsonResponseException(httpStatus, errorInfo, httpStatusString);
  }

  private static GoogleJsonResponseException googleJsonResponseException(
      final int status, final ErrorInfo errorInfo, final String httpStatusString)
      throws IOException {
    final JsonFactory jsonFactory = new JacksonFactory();
    HttpTransport transport = new MockHttpTransport() {
      @Override
      public LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
        errorInfo.setFactory(jsonFactory);
        GoogleJsonError jsonError = new GoogleJsonError();
        jsonError.setCode(status);
        jsonError.setErrors(Arrays.asList(errorInfo));
        jsonError.setMessage(httpStatusString);
        jsonError.setFactory(jsonFactory);
        GenericJson errorResponse = new GenericJson();
        errorResponse.set("error", jsonError);
        errorResponse.setFactory(jsonFactory);
        return new MockLowLevelHttpRequest().setResponse(
            new MockLowLevelHttpResponse().setContent(errorResponse.toPrettyString())
            .setContentType(Json.MEDIA_TYPE).setStatusCode(status));
        }
    };
    HttpRequest request =
        transport.createRequestFactory().buildGetRequest(HttpTesting.SIMPLE_GENERIC_URL);
    request.setThrowExceptionOnExecuteError(false);
    HttpResponse response = request.execute();
    return GoogleJsonResponseException.from(jsonFactory, response);
  }
}
