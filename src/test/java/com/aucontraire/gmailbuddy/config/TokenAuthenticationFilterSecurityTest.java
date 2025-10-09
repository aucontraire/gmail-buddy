package com.aucontraire.gmailbuddy.config;

import com.aucontraire.gmailbuddy.security.TokenReference;
import com.aucontraire.gmailbuddy.security.TokenReferenceService;
import com.aucontraire.gmailbuddy.service.GoogleTokenValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Critical security tests for TokenAuthenticationFilter.
 *
 * These tests verify that the filter properly implements the Encrypted Token Reference Pattern
 * and does NOT store raw tokens in Spring Security Authentication credentials.
 *
 * SECURITY FOCUS:
 * - Verify no raw tokens in Authentication credentials
 * - Verify encrypted token reference usage
 * - Test authentication flow security
 * - Validate token reference lifecycle
 *
 * @author Gmail Buddy Security Team
 */
class TokenAuthenticationFilterSecurityTest {

    @Mock
    private GoogleTokenValidator tokenValidator;

    @Mock
    private TokenReferenceService tokenReferenceService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private TokenReference tokenReference;

    private TokenAuthenticationFilter tokenAuthenticationFilter;

    private static final String TEST_TOKEN = "ya29.a0ARrdaM-test-token-value-12345";
    private static final String TEST_USER_EMAIL = "user@example.com";
    private static final String TEST_REFERENCE_ID = "ref-12345-67890-abcdef";
    private static final String BEARER_TOKEN = "Bearer " + TEST_TOKEN;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        tokenAuthenticationFilter = new TokenAuthenticationFilter(tokenValidator, tokenReferenceService);

        // Clear security context before each test
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("SECURITY: Should NOT store raw token in Authentication credentials")
    void shouldNotStoreRawTokenInAuthenticationCredentials() throws Exception {
        // Arrange
        setupValidTokenRequest();
        setupValidTokenValidator();
        setupTokenReferenceService();

        // Act
        tokenAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert - CRITICAL SECURITY CHECK
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.isAuthenticated()).isTrue();

        // SECURITY ASSERTION: Credentials should contain reference ID, NOT raw token
        Object credentials = auth.getCredentials();
        assertThat(credentials).isNotNull();
        assertThat(credentials).isInstanceOf(String.class);
        assertThat((String) credentials).isEqualTo(TEST_REFERENCE_ID);
        assertThat((String) credentials).isNotEqualTo(TEST_TOKEN); // Must NOT be raw token
        assertThat((String) credentials).doesNotContain(TEST_TOKEN); // Must NOT contain raw token

        // Additional security checks
        assertThat(auth.getPrincipal()).isEqualTo(TEST_USER_EMAIL);
        assertThat(auth.getAuthorities()).hasSize(1);
        assertThat(auth.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_API_USER");

        // Verify secure token reference was created
        verify(tokenReferenceService).createTokenReference(TEST_TOKEN, TEST_USER_EMAIL);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should authenticate successfully with valid Bearer token")
    void shouldAuthenticateSuccessfullyWithValidBearerToken() throws Exception {
        // Arrange
        setupValidTokenRequest();
        setupValidTokenValidator();
        setupTokenReferenceService();

        // Act
        tokenAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(auth.getPrincipal()).isEqualTo(TEST_USER_EMAIL);

        verify(tokenValidator).getTokenInfo(TEST_TOKEN);
        verify(tokenValidator).hasValidGmailScopes(anyString());
        verify(tokenReferenceService).createTokenReference(TEST_TOKEN, TEST_USER_EMAIL);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should skip authentication for non-API endpoints")
    void shouldSkipAuthenticationForNonApiEndpoints() throws Exception {
        // Arrange
        when(request.getRequestURI()).thenReturn("/login");

        // Act
        tokenAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(tokenValidator, tokenReferenceService);
    }

    @Test
    @DisplayName("Should skip when authentication already exists")
    void shouldSkipWhenAuthenticationAlreadyExists() throws Exception {
        // Arrange
        setupValidTokenRequest();
        // Set existing authentication
        Authentication existingAuth = mock(Authentication.class);
        when(existingAuth.isAuthenticated()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(existingAuth);

        // Act
        tokenAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isSameAs(existingAuth);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(tokenValidator, tokenReferenceService);
    }

    @Test
    @DisplayName("Should handle missing Authorization header gracefully")
    void shouldHandleMissingAuthorizationHeaderGracefully() throws Exception {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
        when(request.getHeader("Authorization")).thenReturn(null);

        // Act
        tokenAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(tokenValidator, tokenReferenceService);
    }

    @Test
    @DisplayName("Should handle invalid Authorization header format")
    void shouldHandleInvalidAuthorizationHeaderFormat() throws Exception {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
        when(request.getHeader("Authorization")).thenReturn("Invalid format");

        // Act
        tokenAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(tokenValidator, tokenReferenceService);
    }

    @Test
    @DisplayName("Should handle invalid Google token gracefully")
    void shouldHandleInvalidGoogleTokenGracefully() throws Exception {
        // Arrange
        setupValidTokenRequest();
        when(tokenValidator.getTokenInfo(TEST_TOKEN))
            .thenThrow(new com.aucontraire.gmailbuddy.exception.AuthenticationException("Invalid token"));

        // Act
        tokenAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();

        verify(tokenValidator).getTokenInfo(TEST_TOKEN);
        verify(response).setStatus(401);
        verifyNoInteractions(tokenReferenceService);
    }

    @Test
    @DisplayName("Should handle token validation exceptions gracefully")
    void shouldHandleTokenValidationExceptionsGracefully() throws Exception {
        // Arrange
        setupValidTokenRequest();
        when(tokenValidator.getTokenInfo(TEST_TOKEN))
                .thenThrow(new RuntimeException("Token validation failed"));

        // Act
        tokenAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();

        verify(tokenValidator).getTokenInfo(TEST_TOKEN);
        verify(response).setStatus(401);
        verifyNoInteractions(tokenReferenceService);
    }

    @Test
    @DisplayName("Should handle token reference creation failure gracefully")
    void shouldHandleTokenReferenceCreationFailureGracefully() throws Exception {
        // Arrange
        setupValidTokenRequest();
        setupValidTokenValidator();
        when(tokenReferenceService.createTokenReference(TEST_TOKEN, TEST_USER_EMAIL))
                .thenThrow(new RuntimeException("Token reference creation failed"));

        // Act
        tokenAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull(); // Authentication should fail gracefully

        verify(tokenValidator).getTokenInfo(TEST_TOKEN);
        verify(tokenValidator).hasValidGmailScopes(anyString());
        verify(tokenReferenceService).createTokenReference(TEST_TOKEN, TEST_USER_EMAIL);
        verify(response).setStatus(401);
    }

    @Test
    @DisplayName("Should handle various user identifier scenarios")
    void shouldHandleVariousUserIdentifierScenarios() throws Exception {
        // Test with null email, should use user ID
        setupValidTokenRequest();

        GoogleTokenValidator.TokenInfoResponse tokenInfo = mock(GoogleTokenValidator.TokenInfoResponse.class);
        when(tokenInfo.getEmail()).thenReturn(null);
        when(tokenInfo.getUserId()).thenReturn("user123");
        when(tokenInfo.getScope()).thenReturn("https://www.googleapis.com/auth/gmail.readonly");
        when(tokenValidator.getTokenInfo(TEST_TOKEN)).thenReturn(tokenInfo);
        when(tokenValidator.hasValidGmailScopes("https://www.googleapis.com/auth/gmail.readonly")).thenReturn(true);

        setupTokenReferenceServiceForUser("user123");

        // Act
        tokenAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("user123");
        verify(tokenReferenceService).createTokenReference(TEST_TOKEN, "user123");
    }

    @Test
    @DisplayName("Should use fallback user ID when both email and user ID are null")
    void shouldUseFallbackUserIdWhenBothEmailAndUserIdAreNull() throws Exception {
        // Arrange
        setupValidTokenRequest();

        GoogleTokenValidator.TokenInfoResponse tokenInfo = mock(GoogleTokenValidator.TokenInfoResponse.class);
        when(tokenInfo.getEmail()).thenReturn(null);
        when(tokenInfo.getUserId()).thenReturn(null);
        when(tokenInfo.getScope()).thenReturn("https://www.googleapis.com/auth/gmail.readonly");
        when(tokenValidator.getTokenInfo(TEST_TOKEN)).thenReturn(tokenInfo);
        when(tokenValidator.hasValidGmailScopes("https://www.googleapis.com/auth/gmail.readonly")).thenReturn(true);

        setupTokenReferenceServiceForUser("api-user");

        // Act
        tokenAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("api-user");
        verify(tokenReferenceService).createTokenReference(TEST_TOKEN, "api-user");
    }

    @Test
    @DisplayName("SECURITY: Should never expose raw tokens in any logs or exceptions")
    void shouldNeverExposeRawTokensInAnyLogsOrExceptions() throws Exception {
        // This test ensures that even in failure scenarios, raw tokens are not exposed
        // We can't easily test log output, but we can ensure the filter handles failures securely

        setupValidTokenRequest();
        when(tokenValidator.isValidGoogleToken(TEST_TOKEN)).thenReturn(true);

        GoogleTokenValidator.TokenInfoResponse tokenInfo = mock(GoogleTokenValidator.TokenInfoResponse.class);
        when(tokenInfo.getEmail()).thenReturn(TEST_USER_EMAIL);
        when(tokenValidator.getTokenInfo(TEST_TOKEN)).thenReturn(tokenInfo);

        // Simulate token reference service failure
        when(tokenReferenceService.createTokenReference(anyString(), anyString()))
                .thenThrow(new RuntimeException("Simulated failure"));

        // Act & Assert - should not throw exception that could expose token
        assertThatCode(() ->
            tokenAuthenticationFilter.doFilterInternal(request, response, filterChain)
        ).doesNotThrowAnyException();

        // Should fail gracefully without authentication
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();
    }

    // Helper methods for test setup

    private void setupValidTokenRequest() {
        when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
        when(request.getHeader("Authorization")).thenReturn(BEARER_TOKEN);
    }

    private void setupValidTokenValidator() {
        GoogleTokenValidator.TokenInfoResponse tokenInfo = mock(GoogleTokenValidator.TokenInfoResponse.class);
        when(tokenInfo.getEmail()).thenReturn(TEST_USER_EMAIL);
        when(tokenInfo.getScope()).thenReturn("https://www.googleapis.com/auth/gmail.readonly");
        when(tokenValidator.getTokenInfo(TEST_TOKEN)).thenReturn(tokenInfo);
        when(tokenValidator.hasValidGmailScopes("https://www.googleapis.com/auth/gmail.readonly")).thenReturn(true);
    }

    private void setupTokenReferenceService() {
        setupTokenReferenceServiceForUser(TEST_USER_EMAIL);
    }

    private void setupTokenReferenceServiceForUser(String userId) {
        when(tokenReference.getReferenceId()).thenReturn(TEST_REFERENCE_ID);
        when(tokenReferenceService.createTokenReference(TEST_TOKEN, userId))
                .thenReturn(tokenReference);
    }
}