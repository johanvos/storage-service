/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.storageservice.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.net.HttpHeaders;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import io.micrometer.core.instrument.Tags;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.uri.UriTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import javax.annotation.Nullable;

class MetricsHttpChannelListenerTest {

  private MeterRegistry meterRegistry;
  private Counter requestCounter;
  private Counter requestBytesCounter;
  private Counter responseBytesCounter;
  private MetricsHttpChannelListener listener;

  @BeforeEach
  void setup() {
    meterRegistry = mock(MeterRegistry.class);
    requestCounter = mock(Counter.class);
    requestBytesCounter = mock(Counter.class);
    responseBytesCounter = mock(Counter.class);

    when(meterRegistry.counter(eq(MetricsHttpChannelListener.REQUEST_COUNTER_NAME), any(Tags.class)))
        .thenReturn(requestCounter);

    when(meterRegistry.counter(eq(MetricsHttpChannelListener.REQUEST_BYTES_COUNTER_NAME), any(Tags.class)))
        .thenReturn(requestBytesCounter);

    when(meterRegistry.counter(eq(MetricsHttpChannelListener.RESPONSE_BYTES_COUNTER_NAME), any(Tags.class)))
        .thenReturn(responseBytesCounter);

    listener = new MetricsHttpChannelListener(meterRegistry);
  }

  @Test
  void testRequests() {
    final String path = "/test";
    final String method = "GET";
    final int statusCode = 200;
    final long requestContentLength = 5;
    final long responseContentLength = 7;

    final HttpURI httpUri = mock(HttpURI.class);
    when(httpUri.getPath()).thenReturn(path);

    final Request request = mock(Request.class);
    when(request.getMethod()).thenReturn(method);
    when(request.getHeader(HttpHeaders.USER_AGENT)).thenReturn("Signal-Android/4.53.7 (Android 8.1)");
    when(request.getHttpURI()).thenReturn(httpUri);
    when(request.getContentRead()).thenReturn(requestContentLength);

    final Response response = mock(Response.class);
    when(response.getStatus()).thenReturn(statusCode);
    when(response.getContentCount()).thenReturn(responseContentLength);
    when(request.getResponse()).thenReturn(response);
    final ExtendedUriInfo extendedUriInfo = mock(ExtendedUriInfo.class);
    when(request.getAttribute(MetricsHttpChannelListener.URI_INFO_PROPERTY_NAME)).thenReturn(extendedUriInfo);
    when(extendedUriInfo.getMatchedTemplates()).thenReturn(List.of(new UriTemplate(path)));

    final ArgumentCaptor<Tags> tagCaptor = ArgumentCaptor.forClass(Tags.class);

    listener.onComplete(request);

    verify(requestCounter).increment();
    verify(requestBytesCounter).increment(requestContentLength);
    verify(responseBytesCounter).increment(responseContentLength);

    verify(meterRegistry).counter(eq(MetricsHttpChannelListener.REQUEST_COUNTER_NAME), tagCaptor.capture());

    final Set<Tag> tags = new HashSet<>();
    for (final Tag tag : tagCaptor.getValue()) {
      tags.add(tag);
    }

    assertEquals(4, tags.size());
    assertTrue(tags.contains(Tag.of(MetricsHttpChannelListener.PATH_TAG, path)));
    assertTrue(tags.contains(Tag.of(MetricsHttpChannelListener.METHOD_TAG, method)));
    assertTrue(tags.contains(Tag.of(MetricsHttpChannelListener.STATUS_CODE_TAG, String.valueOf(statusCode))));
    assertTrue(tags.contains(Tag.of(UserAgentTagUtil.PLATFORM_TAG, "android")));
  }

  @ParameterizedTest
  @MethodSource
  void normalizeMethod(@Nullable final String originalMethod, final String expectedMethod) {
    final Request request = mock(Request.class);
    when(request.getMethod()).thenReturn(originalMethod);

    assertEquals(expectedMethod, MetricsHttpChannelListener.normalizeMethod(request));
  }

  private static List<Arguments> normalizeMethod() {
    return List.of(
        Arguments.arguments(null, "unknown"),
        Arguments.arguments("", "unknown"),
        Arguments.arguments("UNEXPECTED_METHOD", "unknown"),
        Arguments.arguments("GET", "GET"),
        Arguments.arguments("get", "get")
    );
  }
}
