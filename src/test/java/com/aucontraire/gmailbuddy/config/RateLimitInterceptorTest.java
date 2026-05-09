package com.aucontraire.gmailbuddy.config;

import com.aucontraire.gmailbuddy.ratelimit.GmailQuotaEstimator;
import com.aucontraire.gmailbuddy.ratelimit.RateLimitInfo;
import com.aucontraire.gmailbuddy.ratelimit.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private GmailQuotaEstimator quotaEstimator;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private RateLimitInterceptor interceptor;

    private RateLimitInfo mockRateLimitInfo;

    @BeforeEach
    void setUp() {
        mockRateLimitInfo = new RateLimitInfo(1000, 999, 1234567890L);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void testPreHandle_authenticatedUser() {
        // Arrange
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("user123");
        when(authentication.getName()).thenReturn("user123");
        when(rateLimitService.recordRequest("user123")).thenReturn(mockRateLimitInfo);
        when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
        when(request.getMethod()).thenReturn("GET");
        when(quotaEstimator.estimateListMessagesQuota(0)).thenReturn(5);

        // Act
        boolean result = interceptor.preHandle(request, response, new Object());

        // Assert
        assertTrue(result, "preHandle should return true");
        verify(rateLimitService).recordRequest("user123");
        verify(request).setAttribute(ResponseHeaderFilter.ATTR_RATE_LIMIT_INFO, mockRateLimitInfo);
        verify(request).setAttribute(ResponseHeaderFilter.ATTR_GMAIL_QUOTA_USED, 5);
    }

    @Test
    void testPreHandle_anonymousUser() {
        // Arrange
        when(securityContext.getAuthentication()).thenReturn(null);
        when(rateLimitService.recordRequest("anonymous")).thenReturn(mockRateLimitInfo);
        when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
        when(request.getMethod()).thenReturn("GET");

        // Act
        boolean result = interceptor.preHandle(request, response, new Object());

        // Assert
        assertTrue(result, "preHandle should return true");
        verify(rateLimitService).recordRequest("anonymous");
    }

    @Test
    void testPreHandle_deleteMessageEndpoint() {
        // Arrange
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("user123");
        when(authentication.getName()).thenReturn("user123");
        when(rateLimitService.recordRequest(anyString())).thenReturn(mockRateLimitInfo);
        when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages/msg123");
        when(request.getMethod()).thenReturn("DELETE");
        when(quotaEstimator.estimateDeleteMessageQuota()).thenReturn(10);

        // Act
        interceptor.preHandle(request, response, new Object());

        // Assert
        verify(quotaEstimator).estimateDeleteMessageQuota();
        verify(request).setAttribute(ResponseHeaderFilter.ATTR_GMAIL_QUOTA_USED, 10);
    }

    @Test
    void testPreHandle_getMessageBodyEndpoint() {
        // Arrange
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("user123");
        when(authentication.getName()).thenReturn("user123");
        when(rateLimitService.recordRequest(anyString())).thenReturn(mockRateLimitInfo);
        when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages/msg123/body");
        when(request.getMethod()).thenReturn("GET");
        when(quotaEstimator.estimateGetMessageQuota()).thenReturn(5);

        // Act
        interceptor.preHandle(request, response, new Object());

        // Assert
        verify(quotaEstimator).estimateGetMessageQuota();
        verify(request).setAttribute(ResponseHeaderFilter.ATTR_GMAIL_QUOTA_USED, 5);
    }

    @Test
    void testPreHandle_batchDeleteEndpoint() {
        // Arrange
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("user123");
        when(authentication.getName()).thenReturn("user123");
        when(rateLimitService.recordRequest(anyString())).thenReturn(mockRateLimitInfo);
        when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages/filter");
        when(request.getMethod()).thenReturn("DELETE");
        when(quotaEstimator.estimateBatchDeleteQuota(500, 50)).thenReturn(500);

        // Act
        interceptor.preHandle(request, response, new Object());

        // Assert
        verify(quotaEstimator).estimateBatchDeleteQuota(500, 50);
        verify(request).setAttribute(ResponseHeaderFilter.ATTR_GMAIL_QUOTA_USED, 500);
    }

    @Test
    void testPreHandle_batchModifyLabelsEndpoint() {
        // Arrange
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("user123");
        when(authentication.getName()).thenReturn("user123");
        when(rateLimitService.recordRequest(anyString())).thenReturn(mockRateLimitInfo);
        when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages/filter/modifyLabels");
        when(request.getMethod()).thenReturn("POST");
        when(quotaEstimator.estimateBatchModifyQuota(500, 50)).thenReturn(500);

        // Act
        interceptor.preHandle(request, response, new Object());

        // Assert
        verify(quotaEstimator).estimateBatchModifyQuota(500, 50);
        verify(request).setAttribute(ResponseHeaderFilter.ATTR_GMAIL_QUOTA_USED, 500);
    }

    @Test
    void testPreHandle_filterMessagesEndpoint() {
        // Arrange
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("user123");
        when(authentication.getName()).thenReturn("user123");
        when(rateLimitService.recordRequest(anyString())).thenReturn(mockRateLimitInfo);
        when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages/filter");
        when(request.getMethod()).thenReturn("POST");
        when(quotaEstimator.estimateFilterMessagesQuota(0)).thenReturn(5);

        // Act
        interceptor.preHandle(request, response, new Object());

        // Assert
        verify(quotaEstimator).estimateFilterMessagesQuota(0);
        verify(request).setAttribute(ResponseHeaderFilter.ATTR_GMAIL_QUOTA_USED, 5);
    }

    @Test
    void testPreHandle_nonGmailEndpoint() {
        // Arrange
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("user123");
        when(authentication.getName()).thenReturn("user123");
        when(rateLimitService.recordRequest(anyString())).thenReturn(mockRateLimitInfo);
        when(request.getRequestURI()).thenReturn("/dashboard");
        when(request.getMethod()).thenReturn("GET");

        // Act
        interceptor.preHandle(request, response, new Object());

        // Assert
        verify(quotaEstimator, never()).estimateListMessagesQuota(anyInt());
        verify(quotaEstimator, never()).estimateGetMessageQuota();
        verify(request, never()).setAttribute(eq(ResponseHeaderFilter.ATTR_GMAIL_QUOTA_USED), any());
    }

    @Test
    void testPreHandle_anonymousUserPrincipal() {
        // Arrange
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("anonymousUser");
        when(rateLimitService.recordRequest("anonymous")).thenReturn(mockRateLimitInfo);
        when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
        when(request.getMethod()).thenReturn("GET");

        // Act
        interceptor.preHandle(request, response, new Object());

        // Assert
        verify(rateLimitService).recordRequest("anonymous");
    }

    @Test
    void testPreHandle_getMessageEndpoint() {
        // Arrange
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("user123");
        when(authentication.getName()).thenReturn("user123");
        when(rateLimitService.recordRequest(anyString())).thenReturn(mockRateLimitInfo);
        when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages/msg123");
        when(request.getMethod()).thenReturn("GET");
        when(quotaEstimator.estimateGetMessageQuota()).thenReturn(5);

        // Act
        interceptor.preHandle(request, response, new Object());

        // Assert
        verify(quotaEstimator).estimateGetMessageQuota();
        verify(request).setAttribute(ResponseHeaderFilter.ATTR_GMAIL_QUOTA_USED, 5);
    }

    @Test
    void testPreHandle_modifyMessageEndpoint() {
        // Arrange
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("user123");
        when(authentication.getName()).thenReturn("user123");
        when(rateLimitService.recordRequest(anyString())).thenReturn(mockRateLimitInfo);
        when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages/msg123/read");
        when(request.getMethod()).thenReturn("PUT");
        when(quotaEstimator.estimateModifyMessageQuota()).thenReturn(5);

        // Act
        interceptor.preHandle(request, response, new Object());

        // Assert
        verify(quotaEstimator).estimateModifyMessageQuota();
        verify(request).setAttribute(ResponseHeaderFilter.ATTR_GMAIL_QUOTA_USED, 5);
    }

    // -------------------------------------------------------------------------
    // isThreadedRequest — T057 coverage gaps (lines 225, 228, 237)
    // These tests exercise estimateQuotaForEndpoint() directly using a real
    // CachedBodyHttpServletRequest so that the isThreadedRequest() branch
    // that inspects the cached body is covered.
    // -------------------------------------------------------------------------

    /**
     * Builds a real {@link CachedBodyHttpServletRequest} wrapping a mock underlying
     * request whose body bytes equal the supplied JSON string.
     */
    private CachedBodyHttpServletRequest buildCachedRequest(String bodyJson) throws IOException {
        jakarta.servlet.http.HttpServletRequest underlying = mock(HttpServletRequest.class);
        byte[] bodyBytes = bodyJson.getBytes(StandardCharsets.UTF_8);
        jakarta.servlet.ServletInputStream servletInputStream = new jakarta.servlet.ServletInputStream() {
            private final java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(bodyBytes);
            @Override public int read() { return bais.read(); }
            @Override public boolean isFinished() { return bais.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(jakarta.servlet.ReadListener l) {}
        };
        when(underlying.getInputStream()).thenReturn(servletInputStream);
        return new CachedBodyHttpServletRequest(underlying);
    }

    @Test
    @DisplayName("estimateQuotaForEndpoint — POST /messages with inReplyToMessageId in body routes to threaded quota (105)")
    void estimateQuotaForEndpoint_postMessages_threadedBody_usesThreadedSendQuota() throws IOException {
        // Arrange: body contains inReplyToMessageId — triggers threaded path (line 237)
        String threadedBody = "{\"to\":\"a@b.com\",\"subject\":\"Hi\",\"body\":\"text\",\"inReplyToMessageId\":\"1a2b3c4d5e6f7a8b\"}";
        CachedBodyHttpServletRequest cachedRequest = buildCachedRequest(threadedBody);

        when(quotaEstimator.estimateThreadedSendMessageQuota()).thenReturn(105);

        // Act
        int quota = interceptor.estimateQuotaForEndpoint(cachedRequest, "/api/v1/gmail/messages", "POST");

        // Assert: threaded estimator was used
        assertEquals(105, quota);
        verify(quotaEstimator).estimateThreadedSendMessageQuota();
    }

    @Test
    @DisplayName("estimateQuotaForEndpoint — POST /messages with null inReplyToMessageId in body uses non-threaded quota (100)")
    void estimateQuotaForEndpoint_postMessages_nullInReplyToInBody_usesNonThreadedSendQuota() throws IOException {
        // Arrange: body has inReplyToMessageId: null (JSON null) — must NOT trigger threaded path
        String body = "{\"to\":\"a@b.com\",\"subject\":\"Hi\",\"body\":\"text\",\"inReplyToMessageId\":null}";
        CachedBodyHttpServletRequest cachedRequest = buildCachedRequest(body);

        when(quotaEstimator.estimateSendMessageQuota()).thenReturn(100);

        // Act
        int quota = interceptor.estimateQuotaForEndpoint(cachedRequest, "/api/v1/gmail/messages", "POST");

        // Assert: non-threaded estimator used for null value
        assertEquals(100, quota);
        verify(quotaEstimator).estimateSendMessageQuota();
    }

    @Test
    @DisplayName("estimateQuotaForEndpoint — POST /messages with empty body (line 237 false branch) uses non-threaded quota")
    void estimateQuotaForEndpoint_postMessages_emptyBody_usesNonThreadedSendQuota() throws IOException {
        // Arrange: empty body — isThreadedRequest returns false (line 237 branch)
        CachedBodyHttpServletRequest cachedRequest = buildCachedRequest("");

        when(quotaEstimator.estimateSendMessageQuota()).thenReturn(100);

        // Act
        int quota = interceptor.estimateQuotaForEndpoint(cachedRequest, "/api/v1/gmail/messages", "POST");

        // Assert: non-threaded path used for empty body
        assertEquals(100, quota);
        verify(quotaEstimator).estimateSendMessageQuota();
    }

    @Test
    @DisplayName("estimateQuotaForEndpoint — POST /drafts with inReplyToMessageId in body routes to threaded draft quota (15)")
    void estimateQuotaForEndpoint_postDrafts_threadedBody_usesThreadedCreateDraftQuota() throws IOException {
        // Arrange: draft body contains inReplyToMessageId — triggers threaded draft path
        String threadedBody = "{\"to\":\"a@b.com\",\"subject\":\"Hi\",\"body\":\"text\",\"inReplyToMessageId\":\"abc123\"}";
        CachedBodyHttpServletRequest cachedRequest = buildCachedRequest(threadedBody);

        when(quotaEstimator.estimateThreadedCreateDraftQuota()).thenReturn(15);

        // Act
        int quota = interceptor.estimateQuotaForEndpoint(cachedRequest, "/api/v1/gmail/drafts", "POST");

        // Assert: threaded draft estimator was used
        assertEquals(15, quota);
        verify(quotaEstimator).estimateThreadedCreateDraftQuota();
    }

    @Test
    @DisplayName("estimateQuotaForEndpoint — POST /messages with plain mock (not CachedBody) falls back to non-threaded (line 225 + 228)")
    void estimateQuotaForEndpoint_postMessages_nonCachedRequest_fallsBackToNonThreaded() {
        // Arrange: plain mock HttpServletRequest — not a CachedBodyHttpServletRequest
        // This covers lines 225 (instanceof check) and 228 (debug log + return false)
        when(quotaEstimator.estimateSendMessageQuota()).thenReturn(100);

        // Act: use the plain mock request (set up in @BeforeEach)
        int quota = interceptor.estimateQuotaForEndpoint(request, "/api/v1/gmail/messages", "POST");

        // Assert: falls back to non-threaded estimate
        assertEquals(100, quota);
        verify(quotaEstimator).estimateSendMessageQuota();
    }
}
