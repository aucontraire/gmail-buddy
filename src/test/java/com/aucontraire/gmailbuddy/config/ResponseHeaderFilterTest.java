package com.aucontraire.gmailbuddy.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ResponseHeaderFilter.
 * Tests request ID generation, MDC storage, response time calculation, and header addition.
 */
class ResponseHeaderFilterTest {

    private ResponseHeaderFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new ResponseHeaderFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = mock(FilterChain.class);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void doFilter_shouldGenerateRequestIdWhenNotProvided() throws ServletException, IOException {
        // Arrange - configure filterChain to capture MDC during execution
        doAnswer(invocation -> {
            String requestId = MDC.get("requestId");
            assertThat(requestId).isNotNull();
            assertThat(requestId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            return null;
        }).when(filterChain).doFilter(any(), any());

        // Act
        filter.doFilter(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(eq(request), any());
    }

    @Test
    void doFilter_shouldUseExistingRequestIdWhenProvided() throws ServletException, IOException {
        // Arrange
        String existingRequestId = "550e8400-e29b-41d4-a716-446655440000";
        request.addHeader("X-Request-ID", existingRequestId);

        doAnswer(invocation -> {
            String requestId = MDC.get("requestId");
            assertThat(requestId).isEqualTo(existingRequestId);
            return null;
        }).when(filterChain).doFilter(any(), any());

        // Act
        filter.doFilter(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(eq(request), any());
    }

    @Test
    void doFilter_shouldIgnoreEmptyRequestIdHeader() throws ServletException, IOException {
        // Arrange
        request.addHeader("X-Request-ID", "");

        doAnswer(invocation -> {
            String requestId = MDC.get("requestId");
            assertThat(requestId).isNotNull();
            assertThat(requestId).isNotEmpty();
            assertThat(requestId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            return null;
        }).when(filterChain).doFilter(any(), any());

        // Act
        filter.doFilter(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(eq(request), any());
    }

    @Test
    void doFilter_shouldAddRequestIdHeaderToResponse() throws ServletException, IOException {
        // Arrange
        String existingRequestId = "test-request-id-123";
        request.addHeader("X-Request-ID", existingRequestId);

        // Simulate response writing to trigger header addition
        doAnswer(invocation -> {
            HttpServletResponse wrappedResponse = invocation.getArgument(1);
            wrappedResponse.getWriter().write("test response");
            return null;
        }).when(filterChain).doFilter(any(), any());

        // Act
        filter.doFilter(request, response, filterChain);

        // Assert
        String responseHeader = response.getHeader("X-Request-ID");
        assertThat(responseHeader).isEqualTo(existingRequestId);
    }

    @Test
    void doFilter_shouldAddResponseTimeHeaderToResponse() throws ServletException, IOException {
        // Arrange - simulate some processing time
        doAnswer(invocation -> {
            Thread.sleep(10);
            HttpServletResponse wrappedResponse = invocation.getArgument(1);
            wrappedResponse.getWriter().write("test response");
            return null;
        }).when(filterChain).doFilter(any(), any());

        // Act
        filter.doFilter(request, response, filterChain);

        // Assert
        String responseTimeHeader = response.getHeader("X-Response-Time");
        assertThat(responseTimeHeader).isNotNull();
        Long responseTime = Long.parseLong(responseTimeHeader);
        assertThat(responseTime).isGreaterThanOrEqualTo(10L);
        assertThat(responseTime).isLessThan(5000L);
    }

    @Test
    void doFilter_shouldClearMDCAfterCompletion() throws ServletException, IOException {
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
    void doFilter_shouldClearMDCEvenOnException() throws ServletException, IOException {
        // Arrange
        doThrow(new RuntimeException("Test exception")).when(filterChain).doFilter(any(), any());

        // Act & Assert
        try {
            filter.doFilter(request, response, filterChain);
        } catch (RuntimeException e) {
            // Expected exception
        }

        // MDC should be cleared even when exception occurs
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void doFilter_shouldHandleSendError() throws ServletException, IOException {
        // Arrange
        String requestId = "error-request-id";
        request.addHeader("X-Request-ID", requestId);

        doAnswer(invocation -> {
            HttpServletResponse wrappedResponse = invocation.getArgument(1);
            wrappedResponse.sendError(500, "Internal Server Error");
            return null;
        }).when(filterChain).doFilter(any(), any());

        // Act
        filter.doFilter(request, response, filterChain);

        // Assert - headers should still be added
        assertThat(response.getHeader("X-Request-ID")).isEqualTo(requestId);
        assertThat(response.getHeader("X-Response-Time")).isNotNull();
    }

    @Test
    void doFilter_shouldHandleSendRedirect() throws ServletException, IOException {
        // Arrange
        String requestId = "redirect-request-id";
        request.addHeader("X-Request-ID", requestId);

        doAnswer(invocation -> {
            HttpServletResponse wrappedResponse = invocation.getArgument(1);
            wrappedResponse.sendRedirect("/new-location");
            return null;
        }).when(filterChain).doFilter(any(), any());

        // Act
        filter.doFilter(request, response, filterChain);

        // Assert - headers should still be added
        assertThat(response.getHeader("X-Request-ID")).isEqualTo(requestId);
        assertThat(response.getHeader("X-Response-Time")).isNotNull();
    }

    @Test
    void fullRequestLifecycle_shouldAddBothHeaders() throws ServletException, IOException {
        // Arrange
        String providedRequestId = "my-custom-request-id";
        request.addHeader("X-Request-ID", providedRequestId);

        doAnswer(invocation -> {
            HttpServletResponse wrappedResponse = invocation.getArgument(1);
            wrappedResponse.getWriter().write("response body");
            return null;
        }).when(filterChain).doFilter(any(), any());

        // Act
        filter.doFilter(request, response, filterChain);

        // Assert
        assertThat(response.getHeader("X-Request-ID")).isEqualTo(providedRequestId);
        assertThat(response.getHeader("X-Response-Time")).isNotNull();
        assertThat(MDC.get("requestId")).isNull(); // MDC cleaned up
    }

    @Test
    void sequentialRequests_shouldHaveDifferentRequestIds() throws ServletException, IOException {
        // Arrange
        MockHttpServletRequest request1 = new MockHttpServletRequest();
        MockHttpServletResponse response1 = new MockHttpServletResponse();
        MockHttpServletRequest request2 = new MockHttpServletRequest();
        MockHttpServletResponse response2 = new MockHttpServletResponse();

        doAnswer(invocation -> {
            HttpServletResponse wrappedResponse = invocation.getArgument(1);
            wrappedResponse.getWriter().write("response");
            return null;
        }).when(filterChain).doFilter(any(), any());

        // Act - process first request
        filter.doFilter(request1, response1, filterChain);
        String requestId1 = response1.getHeader("X-Request-ID");

        // Process second request
        filter.doFilter(request2, response2, filterChain);
        String requestId2 = response2.getHeader("X-Request-ID");

        // Assert - request IDs should be different
        assertThat(requestId1).isNotNull();
        assertThat(requestId2).isNotNull();
        assertThat(requestId1).isNotEqualTo(requestId2);
    }

    @Test
    void doFilter_shouldAddHeadersOnFlushBuffer() throws ServletException, IOException {
        // Arrange
        String requestId = "flush-request-id";
        request.addHeader("X-Request-ID", requestId);

        doAnswer(invocation -> {
            HttpServletResponse wrappedResponse = invocation.getArgument(1);
            wrappedResponse.flushBuffer();
            return null;
        }).when(filterChain).doFilter(any(), any());

        // Act
        filter.doFilter(request, response, filterChain);

        // Assert - headers should be added before flush
        assertThat(response.getHeader("X-Request-ID")).isEqualTo(requestId);
        assertThat(response.getHeader("X-Response-Time")).isNotNull();
    }
}
