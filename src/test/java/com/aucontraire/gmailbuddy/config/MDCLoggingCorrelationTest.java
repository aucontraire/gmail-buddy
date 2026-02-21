package com.aucontraire.gmailbuddy.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests to verify that MDC (Mapped Diagnostic Context) logging correlation works correctly
 * with the ResponseHeaderFilter.
 * Ensures that request IDs stored in MDC are properly correlated with log entries.
 */
class MDCLoggingCorrelationTest {

    private ResponseHeaderFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain filterChain;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger testLogger;

    @BeforeEach
    void setUp() {
        filter = new ResponseHeaderFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = mock(FilterChain.class);
        MDC.clear();

        // Set up logback appender to capture log entries
        testLogger = (Logger) LoggerFactory.getLogger("test-logger");
        listAppender = new ListAppender<>();
        listAppender.start();
        testLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        if (testLogger != null && listAppender != null) {
            testLogger.detachAppender(listAppender);
        }
    }

    @Test
    void requestId_shouldBeAvailableInMDCDuringRequestProcessing() throws ServletException, IOException {
        // Arrange
        String providedRequestId = "test-request-123";
        request.addHeader("X-Request-ID", providedRequestId);

        doAnswer(invocation -> {
            // Assert - requestId should be in MDC during filter chain execution
            String mdcRequestId = MDC.get("requestId");
            assertThat(mdcRequestId).isEqualTo(providedRequestId);
            return null;
        }).when(filterChain).doFilter(any(), any());

        // Act
        filter.doFilter(request, response, filterChain);

        // Verify filter chain was called
        verify(filterChain).doFilter(eq(request), any());
    }

    @Test
    void logStatements_shouldIncludeRequestIdFromMDC() throws ServletException, IOException {
        // Arrange
        String providedRequestId = "log-test-request-456";
        request.addHeader("X-Request-ID", providedRequestId);

        doAnswer(invocation -> {
            // Log something during request processing
            testLogger.info("Processing request");
            return null;
        }).when(filterChain).doFilter(any(), any());

        // Act
        filter.doFilter(request, response, filterChain);

        // Assert - log entry should have requestId in MDC
        assertThat(listAppender.list).hasSize(1);
        ILoggingEvent logEvent = listAppender.list.get(0);
        assertThat(logEvent.getMessage()).isEqualTo("Processing request");

        Map<String, String> mdcPropertyMap = logEvent.getMDCPropertyMap();
        assertThat(mdcPropertyMap).containsEntry("requestId", providedRequestId);
    }

    @Test
    void mdcRequestId_shouldMatchResponseHeader() throws ServletException, IOException {
        // Arrange
        String providedRequestId = "correlation-test-789";
        request.addHeader("X-Request-ID", providedRequestId);

        final String[] mdcRequestIdDuringProcessing = {null};
        doAnswer(invocation -> {
            mdcRequestIdDuringProcessing[0] = MDC.get("requestId");
            // Trigger response write to add headers
            HttpServletResponse wrappedResponse = invocation.getArgument(1);
            wrappedResponse.getWriter().write("response");
            return null;
        }).when(filterChain).doFilter(any(), any());

        // Act
        filter.doFilter(request, response, filterChain);

        // Assert - MDC requestId should match response header
        String responseHeaderRequestId = response.getHeader("X-Request-ID");
        assertThat(mdcRequestIdDuringProcessing[0]).isEqualTo(providedRequestId);
        assertThat(responseHeaderRequestId).isEqualTo(providedRequestId);
        assertThat(mdcRequestIdDuringProcessing[0]).isEqualTo(responseHeaderRequestId);
    }

    @Test
    void generatedRequestId_shouldBeCorrelatedInMDCAndResponse() throws ServletException, IOException {
        // Arrange - no request ID provided, should be generated
        final String[] mdcRequestId = {null};
        doAnswer(invocation -> {
            mdcRequestId[0] = MDC.get("requestId");
            // Trigger response write to add headers
            HttpServletResponse wrappedResponse = invocation.getArgument(1);
            wrappedResponse.getWriter().write("response");
            return null;
        }).when(filterChain).doFilter(any(), any());

        // Act
        filter.doFilter(request, response, filterChain);
        String responseRequestId = response.getHeader("X-Request-ID");

        // Assert
        assertThat(mdcRequestId[0]).isNotNull();
        assertThat(responseRequestId).isNotNull();
        assertThat(mdcRequestId[0]).isEqualTo(responseRequestId);
    }

    @Test
    void mdcCleanup_shouldRemoveRequestIdAfterCompletion() throws ServletException, IOException {
        // Arrange
        doAnswer(invocation -> {
            assertThat(MDC.get("requestId")).isNotNull();
            return null;
        }).when(filterChain).doFilter(any(), any());

        // Act
        filter.doFilter(request, response, filterChain);

        // Assert - MDC should be cleared after filter completes
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void multipleLogStatements_shouldAllHaveSameRequestId() throws ServletException, IOException {
        // Arrange
        String providedRequestId = "multi-log-test-321";
        request.addHeader("X-Request-ID", providedRequestId);

        doAnswer(invocation -> {
            // Log multiple statements during request processing
            testLogger.info("First log statement");
            testLogger.warn("Second log statement");
            testLogger.error("Third log statement");
            return null;
        }).when(filterChain).doFilter(any(), any());

        // Act
        filter.doFilter(request, response, filterChain);

        // Assert - all log entries should have the same requestId
        assertThat(listAppender.list).hasSize(3);

        for (ILoggingEvent logEvent : listAppender.list) {
            Map<String, String> mdcPropertyMap = logEvent.getMDCPropertyMap();
            assertThat(mdcPropertyMap).containsEntry("requestId", providedRequestId);
        }
    }

    @Test
    void exceptionInRequest_shouldStillCorrelateRequestId() throws ServletException, IOException {
        // Arrange
        String providedRequestId = "exception-test-999";
        request.addHeader("X-Request-ID", providedRequestId);

        doAnswer(invocation -> {
            // Log during exception handling
            testLogger.error("Error occurred", new RuntimeException("Test exception"));
            // Trigger response write to add headers
            HttpServletResponse wrappedResponse = invocation.getArgument(1);
            wrappedResponse.getWriter().write("error response");
            return null;
        }).when(filterChain).doFilter(any(), any());

        // Act
        filter.doFilter(request, response, filterChain);

        // Assert - error log should have requestId
        assertThat(listAppender.list).hasSize(1);
        ILoggingEvent logEvent = listAppender.list.get(0);
        assertThat(logEvent.getLevel()).isEqualTo(Level.ERROR);

        Map<String, String> mdcPropertyMap = logEvent.getMDCPropertyMap();
        assertThat(mdcPropertyMap).containsEntry("requestId", providedRequestId);

        // Response header should still have requestId
        assertThat(response.getHeader("X-Request-ID")).isEqualTo(providedRequestId);
    }

    @Test
    void sequentialRequests_shouldHaveIndependentRequestIds() throws ServletException, IOException {
        // Arrange
        String firstRequestId = "sequential-1";
        String secondRequestId = "sequential-2";

        doAnswer(invocation -> {
            testLogger.info("Request log");
            HttpServletResponse wrappedResponse = invocation.getArgument(1);
            wrappedResponse.getWriter().write("response");
            return null;
        }).when(filterChain).doFilter(any(), any());

        // First request
        request.addHeader("X-Request-ID", firstRequestId);
        filter.doFilter(request, response, filterChain);

        // Second request
        MockHttpServletRequest request2 = new MockHttpServletRequest();
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        request2.addHeader("X-Request-ID", secondRequestId);
        filter.doFilter(request2, response2, filterChain);

        // Assert - log entries should have different requestIds
        assertThat(listAppender.list).hasSize(2);

        ILoggingEvent firstLog = listAppender.list.get(0);
        assertThat(firstLog.getMDCPropertyMap()).containsEntry("requestId", firstRequestId);

        ILoggingEvent secondLog = listAppender.list.get(1);
        assertThat(secondLog.getMDCPropertyMap()).containsEntry("requestId", secondRequestId);
    }

    @Test
    void noRequestIdInMDC_whenNotCalledThroughFilter() {
        // Act - log without going through filter
        testLogger.info("Logging without filter");

        // Assert - no requestId should be in MDC
        assertThat(listAppender.list).hasSize(1);
        ILoggingEvent logEvent = listAppender.list.get(0);
        Map<String, String> mdcPropertyMap = logEvent.getMDCPropertyMap();
        assertThat(mdcPropertyMap).doesNotContainKey("requestId");
    }
}
