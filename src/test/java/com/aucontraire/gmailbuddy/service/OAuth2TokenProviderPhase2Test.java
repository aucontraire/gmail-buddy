package com.aucontraire.gmailbuddy.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import com.aucontraire.gmailbuddy.exception.AuthenticationException;
import com.aucontraire.gmailbuddy.security.TokenReferenceService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Comprehensive test suite for OAuth2TokenProvider Phase 2 integration.
 *
 * Tests the updated OAuth2TokenProvider with Phase 2 enhancements including:
 * - GoogleTokenValidator integration
 * - Bearer token processing with HTTP context
 * - API client authentication via custom filter
 * - Fallback behavior between Bearer and OAuth2 tokens
 * - SecurityContextHolder dependency removal for API clients
 * - Error handling and logging for new authentication methods
 *
 * @author Gmail Buddy Team
 * @since Sprint 2 - Phase 2 OAuth2 Security Context Decoupling
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth2TokenProvider Phase 2 Integration")
class OAuth2TokenProviderPhase2Test {

    @Mock
    private OAuth2AuthorizedClientService authorizedClientService;

    @Mock
    private GmailBuddyProperties properties;

    @Mock
    private GmailBuddyProperties.OAuth2 oauth2Config;

    @Mock
    private GoogleTokenValidator tokenValidator;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @Mock
    private OAuth2AuthorizedClient authorizedClient;

    @Mock
    private OAuth2AccessToken accessToken;

    @Mock
    private HttpServletRequest httpRequest;

    @Mock
    private ServletRequestAttributes requestAttributes;

    @Mock
    private TokenReferenceService tokenReferenceService;

    private OAuth2TokenProvider tokenProvider;
    private MockedStatic<RequestContextHolder> mockedRequestContextHolder;
    private MockedStatic<SecurityContextHolder> mockedSecurityContextHolder;

    private static final String VALID_BEARER_TOKEN = "ya29.a0ARrdaM-valid-bearer-token";
    private static final String INVALID_BEARER_TOKEN = "invalid-bearer-token";
    private static final String OAUTH2_TOKEN = "ya29.a0ARrdaM-oauth2-token";
    private static final String USER_EMAIL = "test@example.com";
    private static final String CLIENT_REGISTRATION_ID = "google";

    @BeforeEach
    void setUp() {
        tokenProvider =
                new OAuth2TokenProvider(authorizedClientService, properties, tokenValidator, tokenReferenceService);

        // Setup static mocks
        mockedRequestContextHolder = mockStatic(RequestContextHolder.class);
        mockedSecurityContextHolder = mockStatic(SecurityContextHolder.class);

        SecurityContextHolder.setContext(securityContext);
        mockedSecurityContextHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);

        // Setup default property mocks (lenient to handle tests that don't use them)
        lenient().when(properties.oauth2()).thenReturn(oauth2Config);
        lenient().when(oauth2Config.clientRegistrationId()).thenReturn(CLIENT_REGISTRATION_ID);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();

        // Close static mocks
        if (mockedRequestContextHolder != null) {
            mockedRequestContextHolder.close();
        }
        if (mockedSecurityContextHolder != null) {
            mockedSecurityContextHolder.close();
        }
    }

    @Nested
    @DisplayName("Bearer Token Processing Tests")
    class BearerTokenProcessingTests {

        @Test
        @DisplayName("Should extract and validate Bearer token from HTTP request")
        void shouldExtractAndValidateBearerTokenFromHttpRequest() {
            // Given
            mockHttpRequestContext(VALID_BEARER_TOKEN);
            lenient()
                    .when(tokenValidator.isValidGoogleToken(VALID_BEARER_TOKEN))
                    .thenReturn(true);

            // When
            String result = tokenProvider.getBearerToken();

            // Then
            assertThat(result).isEqualTo(VALID_BEARER_TOKEN);
            verify(tokenValidator, never()).isValidGoogleToken(any()); // getBearerToken doesn't validate
        }

        @Test
        @DisplayName("Should validate Bearer token using GoogleTokenValidator")
        void shouldValidateBearerTokenUsingGoogleTokenValidator() {
            // Given
            when(tokenValidator.isValidGoogleToken(VALID_BEARER_TOKEN)).thenReturn(true);

            // When
            boolean result = tokenProvider.isValidBearerToken(VALID_BEARER_TOKEN);

            // Then
            assertThat(result).isTrue();
            verify(tokenValidator).isValidGoogleToken(VALID_BEARER_TOKEN);
        }

        @Test
        @DisplayName("Should reject invalid Bearer token")
        void shouldRejectInvalidBearerToken() {
            // Given
            when(tokenValidator.isValidGoogleToken(INVALID_BEARER_TOKEN)).thenReturn(false);

            // When
            boolean result = tokenProvider.isValidBearerToken(INVALID_BEARER_TOKEN);

            // Then
            assertThat(result).isFalse();
            verify(tokenValidator).isValidGoogleToken(INVALID_BEARER_TOKEN);
        }

        @Test
        @DisplayName("Should throw exception when Authorization header is missing")
        void shouldThrowExceptionWhenAuthorizationHeaderIsMissing() {
            // Given
            mockHttpRequestContext(null);

            // When & Then
            assertThatThrownBy(() -> tokenProvider.getBearerToken())
                    .isInstanceOf(AuthenticationException.class)
                    .hasMessage("No Bearer token found in Authorization header");
        }

        @Test
        @DisplayName("Should throw exception when Authorization header is not Bearer")
        void shouldThrowExceptionWhenAuthorizationHeaderIsNotBearer() {
            // Given
            mockHttpRequestContextWithHeader("Basic dXNlcjpwYXNz");

            // When & Then
            assertThatThrownBy(() -> tokenProvider.getBearerToken())
                    .isInstanceOf(AuthenticationException.class)
                    .hasMessage("No Bearer token found in Authorization header");
        }

        @Test
        @DisplayName("Should throw exception when Bearer token is empty")
        void shouldThrowExceptionWhenBearerTokenIsEmpty() {
            // Given
            mockHttpRequestContextWithHeader("Bearer ");

            // When & Then
            assertThatThrownBy(() -> tokenProvider.getBearerToken())
                    .isInstanceOf(AuthenticationException.class)
                    .hasMessage("Bearer token is empty");
        }

        @Test
        @DisplayName("Should throw exception when no HTTP request context available")
        void shouldThrowExceptionWhenNoHttpRequestContextAvailable() {
            // Given
            mockedRequestContextHolder
                    .when(RequestContextHolder::getRequestAttributes)
                    .thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> tokenProvider.getBearerToken())
                    .isInstanceOf(AuthenticationException.class)
                    .hasMessage("No HTTP request context available");
        }
    }

    @Nested
    @DisplayName("API Client Authentication Tests")
    class ApiClientAuthenticationTests {

        @Test
        @DisplayName("Should retrieve token from API client authentication in SecurityContext with zero live validation"
                + " (US2/FR-003/FR-005)")
        void shouldRetrieveTokenFromApiClientAuthenticationInSecurityContext() {
            // Given
            mockApiClientAuthentication();
            mockHttpRequestContext(null); // No Bearer token in request

            // When
            String result = tokenProvider.getTokenFromContext();

            // Then
            assertThat(result).isEqualTo(VALID_BEARER_TOKEN);
            verify(authentication).getCredentials();
            // ADR-002 / FR-003: the reference path must not perform a second live validation
            // round-trip - the filter already validated this request once.
            verify(tokenValidator, never()).isValidGoogleToken(any());
        }

        @Test
        @DisplayName(
                "Should prioritize API client (token-reference) authentication over Bearer token, with no redundant"
                        + " re-validation")
        void shouldPrioritizeApiClientAuthenticationOverBearerToken() {
            // Given - a directly-presented Bearer header is also present, but ADR-002 requires the
            // already-authenticated ROLE_API_USER request to be served from the token reference,
            // never by re-reading/re-trusting/re-validating the raw header token.
            String directBearerToken = "ya29.a0ARrdaM-direct-bearer";
            mockHttpRequestContext(directBearerToken);
            mockApiClientAuthentication();

            // When
            String result = tokenProvider.getTokenFromContext();

            // Then
            assertThat(result).isEqualTo(VALID_BEARER_TOKEN); // from the token reference, not the raw header
            verify(authentication).getCredentials();
            verify(tokenValidator, never()).isValidGoogleToken(any()); // no redundant live validation
        }

        @Test
        @DisplayName("Should use API client (token-reference) authentication regardless of Bearer token validity")
        void shouldUseApiClientAuthenticationRegardlessOfBearerTokenValidity() {
            // Given - an invalid Bearer header is present, but since ROLE_API_USER is already
            // authenticated the reference path wins outright and the invalid header is never consulted.
            mockHttpRequestContext(INVALID_BEARER_TOKEN);
            mockApiClientAuthentication();

            // When
            String result = tokenProvider.getTokenFromContext();

            // Then
            assertThat(result).isEqualTo(VALID_BEARER_TOKEN);
            verify(authentication).getCredentials();
            verify(tokenValidator, never()).isValidGoogleToken(any());
        }

        @Test
        @DisplayName("Should handle non-string credentials in API client authentication")
        void shouldHandleNonStringCredentialsInApiClientAuthentication() {
            // Given
            mockHttpRequestContext(null);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getAuthorities())
                    .thenAnswer(invocation -> java.util.Arrays.asList(new SimpleGrantedAuthority("ROLE_API_USER")));
            when(authentication.getCredentials()).thenReturn(123); // Non-string credentials
            mockOAuth2Fallback();

            // When
            String result = tokenProvider.getTokenFromContext();

            // Then
            assertThat(result).isEqualTo(OAUTH2_TOKEN);
            verify(authentication).getCredentials();
            verify(authorizedClientService).loadAuthorizedClient(CLIENT_REGISTRATION_ID, USER_EMAIL);
        }
    }

    @Nested
    @DisplayName("OAuth2 Fallback Authentication Tests")
    class OAuth2FallbackAuthenticationTests {

        @Test
        @DisplayName("Should fallback to OAuth2 when no Bearer token or API client authentication")
        void shouldFallbackToOAuth2WhenNoBearerTokenOrApiClientAuthentication() {
            // Given
            mockHttpRequestContext(null);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getAuthorities())
                    .thenAnswer(invocation -> java.util.Arrays.asList(new SimpleGrantedAuthority("ROLE_USER")));
            mockOAuth2Fallback();

            // When
            String result = tokenProvider.getTokenFromContext();

            // Then
            assertThat(result).isEqualTo(OAUTH2_TOKEN);
            verify(authorizedClientService).loadAuthorizedClient(CLIENT_REGISTRATION_ID, USER_EMAIL);
        }

        @Test
        @DisplayName("Should fallback to OAuth2 when Bearer token validation fails and no API client authentication")
        void shouldFallbackToOAuth2WhenBearerTokenValidationFailsAndNoApiClientAuthentication() {
            // Given
            mockHttpRequestContext(INVALID_BEARER_TOKEN);
            when(tokenValidator.isValidGoogleToken(INVALID_BEARER_TOKEN)).thenReturn(false);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getAuthorities())
                    .thenAnswer(invocation -> java.util.Arrays.asList(new SimpleGrantedAuthority("ROLE_USER")));
            mockOAuth2Fallback();

            // When
            String result = tokenProvider.getTokenFromContext();

            // Then
            assertThat(result).isEqualTo(OAUTH2_TOKEN);
            verify(tokenValidator).isValidGoogleToken(INVALID_BEARER_TOKEN);
            verify(authorizedClientService).loadAuthorizedClient(CLIENT_REGISTRATION_ID, USER_EMAIL);
        }

        @Test
        @DisplayName("Should throw exception when all authentication methods fail")
        void shouldThrowExceptionWhenAllAuthenticationMethodsFail() {
            // Given
            mockHttpRequestContext(null);
            when(securityContext.getAuthentication()).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> tokenProvider.getTokenFromContext())
                    .isInstanceOf(AuthenticationException.class)
                    .hasMessageContaining("Authentication failed: No valid authentication method found");
        }
    }

    @Nested
    @DisplayName("Token Context Resolution Priority Tests")
    class TokenContextResolutionPriorityTests {

        @Test
        @DisplayName("Should follow correct priority: API Client (token reference) > Bearer > OAuth2")
        void shouldFollowCorrectPriorityApiClientBearerOAuth2() {
            // Given - All three authentication methods present
            mockHttpRequestContext(VALID_BEARER_TOKEN);
            mockApiClientAuthentication();
            mockOAuth2Fallback();

            // When
            String result = tokenProvider.getTokenFromContext();

            // Then
            assertThat(result).isEqualTo(VALID_BEARER_TOKEN);
            verify(authentication).getCredentials(); // Reference path wins
            verify(tokenValidator, never()).isValidGoogleToken(any()); // no redundant live validation (ADR-002)
            verify(authorizedClientService, never())
                    .loadAuthorizedClient(any(), any()); // Should not fallback to OAuth2
        }

        @Test
        @DisplayName("Should fall back to Bearer token validation when no API client (ROLE_API_USER) authentication"
                + " is present")
        void shouldFallBackToBearerTokenWhenNoApiClientAuthenticationPresent() {
            // Given - no ROLE_API_USER context, so the reference path is unavailable and the
            // Bearer-header fallback (with its live validation) is exercised.
            mockHttpRequestContext(VALID_BEARER_TOKEN);
            when(tokenValidator.isValidGoogleToken(VALID_BEARER_TOKEN)).thenReturn(true);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getAuthorities())
                    .thenAnswer(invocation -> java.util.Arrays.asList(new SimpleGrantedAuthority("ROLE_USER")));
            mockOAuth2Fallback(); // Also available but should not be used

            // When
            String result = tokenProvider.getTokenFromContext();

            // Then
            assertThat(result).isEqualTo(VALID_BEARER_TOKEN);
            verify(tokenValidator).isValidGoogleToken(VALID_BEARER_TOKEN);
            verify(authorizedClientService, never()).loadAuthorizedClient(any(), any());
        }

        @Test
        @DisplayName("Should skip to OAuth2 when Bearer token and API client authentication fail")
        void shouldSkipToOAuth2WhenBearerTokenAndApiClientAuthenticationFail() {
            // Given
            mockHttpRequestContext(INVALID_BEARER_TOKEN);
            when(tokenValidator.isValidGoogleToken(INVALID_BEARER_TOKEN)).thenReturn(false);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getAuthorities())
                    .thenAnswer(invocation -> java.util.Arrays.asList(new SimpleGrantedAuthority("ROLE_USER")));
            mockOAuth2Fallback();

            // When
            String result = tokenProvider.getTokenFromContext();

            // Then
            assertThat(result).isEqualTo(OAUTH2_TOKEN);
            verify(tokenValidator).isValidGoogleToken(INVALID_BEARER_TOKEN);
            verify(authorizedClientService).loadAuthorizedClient(CLIENT_REGISTRATION_ID, USER_EMAIL);
        }
    }

    @Nested
    @DisplayName("Error Handling and Edge Cases Tests")
    class ErrorHandlingAndEdgeCasesTests {

        @Test
        @DisplayName("Should handle GoogleTokenValidator exceptions gracefully on Bearer fallback")
        void shouldHandleGoogleTokenValidatorExceptionsGracefully() {
            // Given - no ROLE_API_USER authentication, so the Bearer fallback (with its live
            // validation call) is reached and must gracefully fall through on error.
            mockHttpRequestContext(VALID_BEARER_TOKEN);
            when(tokenValidator.isValidGoogleToken(VALID_BEARER_TOKEN))
                    .thenThrow(new RuntimeException("Token validation service unavailable"));
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getAuthorities())
                    .thenAnswer(invocation -> java.util.Arrays.asList(new SimpleGrantedAuthority("ROLE_USER")));
            mockOAuth2Fallback();

            // When
            String result = tokenProvider.getTokenFromContext();

            // Then
            assertThat(result).isEqualTo(OAUTH2_TOKEN);
            verify(tokenValidator).isValidGoogleToken(VALID_BEARER_TOKEN);
            verify(authorizedClientService).loadAuthorizedClient(CLIENT_REGISTRATION_ID, USER_EMAIL);
        }

        @Test
        @DisplayName("Should handle HTTP request context exceptions gracefully on Bearer fallback")
        void shouldHandleHttpRequestContextExceptionsGracefully() {
            // Given - no ROLE_API_USER authentication, so the Bearer fallback (which needs the
            // HTTP request context) is reached and must gracefully fall through on error.
            mockedRequestContextHolder
                    .when(RequestContextHolder::getRequestAttributes)
                    .thenThrow(new RuntimeException("Request context error"));
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getAuthorities())
                    .thenAnswer(invocation -> java.util.Arrays.asList(new SimpleGrantedAuthority("ROLE_USER")));
            mockOAuth2Fallback();

            // When
            String result = tokenProvider.getTokenFromContext();

            // Then
            assertThat(result).isEqualTo(OAUTH2_TOKEN);
            verify(authorizedClientService).loadAuthorizedClient(CLIENT_REGISTRATION_ID, USER_EMAIL);
        }

        @Test
        @DisplayName("Should handle SecurityContext exceptions gracefully")
        void shouldHandleSecurityContextExceptionsGracefully() {
            // Given
            mockHttpRequestContext(null);
            when(securityContext.getAuthentication()).thenThrow(new RuntimeException("SecurityContext error"));

            // When & Then
            assertThatThrownBy(() -> tokenProvider.getTokenFromContext())
                    .isInstanceOf(AuthenticationException.class)
                    .hasMessageContaining("Authentication failed: No valid authentication method found");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t", "\n"})
        @DisplayName("Should handle whitespace-only Bearer tokens")
        void shouldHandleWhitespaceOnlyBearerTokens(String whitespaceToken) {
            // Given
            mockHttpRequestContextWithHeader("Bearer " + whitespaceToken);

            // When & Then
            assertThatThrownBy(() -> tokenProvider.getBearerToken())
                    .isInstanceOf(AuthenticationException.class)
                    .hasMessage("Bearer token is empty");
        }

        @Test
        @DisplayName("Should handle null authentication in SecurityContext")
        void shouldHandleNullAuthenticationInSecurityContext() {
            // Given
            mockHttpRequestContext(null);
            when(securityContext.getAuthentication()).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> tokenProvider.getTokenFromContext())
                    .isInstanceOf(AuthenticationException.class)
                    .hasMessageContaining("Authentication failed: No valid authentication method found");
        }

        @Test
        @DisplayName("Should handle unauthenticated security context")
        void shouldHandleUnauthenticatedSecurityContext() {
            // Given
            mockHttpRequestContext(null);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.isAuthenticated()).thenReturn(false);

            // When & Then
            assertThatThrownBy(() -> tokenProvider.getTokenFromContext())
                    .isInstanceOf(AuthenticationException.class)
                    .hasMessageContaining("Authentication failed: No valid authentication method found");
        }
    }

    @Nested
    @DisplayName("Integration with Existing OAuth2 Methods Tests")
    class IntegrationWithExistingOAuth2MethodsTests {

        @Test
        @DisplayName("Should maintain backward compatibility with existing OAuth2 methods")
        void shouldMaintainBackwardCompatibilityWithExistingOAuth2Methods() {
            // Given
            mockOAuth2Fallback();

            // When
            String result = tokenProvider.getAccessToken(USER_EMAIL);

            // Then
            assertThat(result).isEqualTo(OAUTH2_TOKEN);
            verify(authorizedClientService).loadAuthorizedClient(CLIENT_REGISTRATION_ID, USER_EMAIL);
        }

        @Test
        @DisplayName("Should use OAuth2 methods when no HTTP context is available")
        void shouldUseOAuth2MethodsWhenNoHttpContextIsAvailable() {
            // Given
            mockedRequestContextHolder
                    .when(RequestContextHolder::getRequestAttributes)
                    .thenReturn(null);
            mockOAuth2Fallback();

            // When
            String result = tokenProvider.getTokenFromContext();

            // Then
            assertThat(result).isEqualTo(OAUTH2_TOKEN);
            verify(authorizedClientService).loadAuthorizedClient(CLIENT_REGISTRATION_ID, USER_EMAIL);
        }

        @Test
        @DisplayName("Should handle mixed authentication scenarios")
        void shouldHandleMixedAuthenticationScenarios() {
            // Given - API client authenticated but wants to use OAuth2 method
            mockApiClientAuthentication();
            when(authorizedClientService.loadAuthorizedClient(CLIENT_REGISTRATION_ID, USER_EMAIL))
                    .thenReturn(authorizedClient);
            when(authorizedClient.getAccessToken()).thenReturn(accessToken);
            when(accessToken.getTokenValue()).thenReturn(OAUTH2_TOKEN);
            when(accessToken.getExpiresAt()).thenReturn(Instant.now().plusSeconds(3600));

            // When
            String contextToken = tokenProvider.getTokenFromContext();
            String oauth2Token = tokenProvider.getAccessToken(USER_EMAIL);

            // Then
            assertThat(contextToken).isEqualTo(VALID_BEARER_TOKEN); // From API client authentication
            assertThat(oauth2Token).isEqualTo(OAUTH2_TOKEN); // From OAuth2 method
        }
    }

    // Helper methods

    private void mockHttpRequestContext(String bearerToken) {
        String authHeader = bearerToken != null ? "Bearer " + bearerToken : null;
        mockHttpRequestContextWithHeader(authHeader);
    }

    private void mockHttpRequestContextWithHeader(String authHeader) {
        mockedRequestContextHolder
                .when(RequestContextHolder::getRequestAttributes)
                .thenReturn(requestAttributes);
        lenient().when(requestAttributes.getRequest()).thenReturn(httpRequest);
        lenient().when(httpRequest.getHeader("Authorization")).thenReturn(authHeader);
    }

    private void mockApiClientAuthentication() {
        String tokenReferenceId = "ref_" + VALID_BEARER_TOKEN.hashCode();

        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        lenient()
                .when(authentication.getAuthorities())
                .thenAnswer(invocation -> java.util.Arrays.asList(new SimpleGrantedAuthority("ROLE_API_USER")));
        lenient().when(authentication.getCredentials()).thenReturn(tokenReferenceId);

        // Mock the token reference service to return the actual token
        lenient().when(tokenReferenceService.getToken(tokenReferenceId)).thenReturn(Optional.of(VALID_BEARER_TOKEN));
    }

    private void mockOAuth2Fallback() {
        // Setup authentication for OAuth2 fallback
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getName()).thenReturn(USER_EMAIL);
        lenient().when(authentication.isAuthenticated()).thenReturn(true);

        lenient()
                .when(authorizedClientService.loadAuthorizedClient(CLIENT_REGISTRATION_ID, USER_EMAIL))
                .thenReturn(authorizedClient);
        lenient().when(authorizedClient.getAccessToken()).thenReturn(accessToken);
        lenient().when(accessToken.getTokenValue()).thenReturn(OAUTH2_TOKEN);
        lenient().when(accessToken.getExpiresAt()).thenReturn(Instant.now().plusSeconds(3600));
    }
}
