package com.aucontraire.gmailbuddy.config;

import com.aucontraire.gmailbuddy.ratelimit.GmailQuotaEstimator;
import com.aucontraire.gmailbuddy.ratelimit.RateLimitInfo;
import com.aucontraire.gmailbuddy.ratelimit.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

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
}
