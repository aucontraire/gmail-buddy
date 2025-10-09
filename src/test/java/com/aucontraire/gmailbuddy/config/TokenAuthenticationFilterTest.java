package com.aucontraire.gmailbuddy.config;

import com.aucontraire.gmailbuddy.security.TokenReference;
import com.aucontraire.gmailbuddy.security.TokenReferenceService;
import com.aucontraire.gmailbuddy.service.GoogleTokenValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for TokenAuthenticationFilter.
 *
 * Tests Bearer token extraction, validation, and Spring Security integration including:
 * - Bearer token extraction from Authorization headers
 * - Single Google API call optimization (CRITICAL: proves double-call fix)
 * - Filter chain processing with valid and invalid tokens
 * - API endpoint filtering logic
 * - Security context establishment
 * - Integration with GoogleTokenValidator
 * - Gmail scope validation
 * - HTTP 401 response handling for authentication failures
 * - Error handling and edge cases
 *
 * PERFORMANCE FIX VERIFICATION:
 * These tests verify the critical fix that reduced Google API calls from 2 to 1 per request.
 * - Old pattern: isValidGoogleToken() + getTokenInfo() = 2 calls
 * - New pattern: getTokenInfo() + hasValidGmailScopes() = 1 call (scope check is local)
 *
 * @author Gmail Buddy Team
 * @since Sprint 2 - Phase 2 OAuth2 Security Context Decoupling
 * @since Sprint 2 - Phase 3 Double Token Validation Fix (P0-2)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TokenAuthenticationFilter")
class TokenAuthenticationFilterTest {

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
    private SecurityContext securityContext;

    @Mock
    private Authentication existingAuthentication;

    @Mock
    private TokenReference tokenReference;

    private TokenAuthenticationFilter filter;

    private static final String VALID_BEARER_TOKEN = "ya29.a0ARrdaM-valid-token";
    private static final String INVALID_BEARER_TOKEN = "invalid-token";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String TEST_REFERENCE_ID = "ref-12345-67890-abcdef";

    @BeforeEach
    void setUp() {
        filter = new TokenAuthenticationFilter(tokenValidator, tokenReferenceService);
        SecurityContextHolder.setContext(securityContext);

        // Setup default token reference mock behavior (lenient for tests that don't use them)
        lenient().when(tokenReference.getReferenceId()).thenReturn(TEST_REFERENCE_ID);
        lenient().when(tokenReferenceService.createTokenReference(anyString(), anyString())).thenReturn(tokenReference);
    }

    @Nested
    @DisplayName("API Endpoint Filtering Tests")
    class ApiEndpointFilteringTests {

        @ParameterizedTest
        @ValueSource(strings = {
            "/api/v1/gmail/messages",
            "/api/v1/gmail/messages/123",
            "/api/v1/gmail/messages/filter",
            "/api/v1/gmail/messages/123/body"
        })
        @DisplayName("Should process API endpoints")
        void shouldProcessApiEndpoints(String apiPath) throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn(apiPath);
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_PREFIX + VALID_BEARER_TOKEN);
            when(securityContext.getAuthentication()).thenReturn(null);
            GoogleTokenValidator.TokenInfoResponse tokenInfo = createTokenInfo("user@example.com");
            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(tokenInfo);
            when(tokenValidator.hasValidGmailScopes(tokenInfo.getScope())).thenReturn(true);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            // CRITICAL: Verify ONLY ONE Google API call (not two!)
            verify(tokenValidator, times(1)).getTokenInfo(VALID_BEARER_TOKEN);
            verify(tokenValidator, never()).isValidGoogleToken(anyString());
            verify(tokenValidator).hasValidGmailScopes(tokenInfo.getScope());
            verify(securityContext).setAuthentication(any(Authentication.class));
            verify(filterChain).doFilter(request, response);
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "/dashboard",
            "/login",
            "/oauth2/authorization/google",
            "/static/css/app.css",
            "/favicon.ico"
        })
        @DisplayName("Should skip non-API endpoints")
        void shouldSkipNonApiEndpoints(String nonApiPath) throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn(nonApiPath);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verifyNoInteractions(tokenValidator);
            verify(filterChain).doFilter(request, response);
            verifyNoInteractions(securityContext);
        }

        @Test
        @DisplayName("Should skip when existing authentication is present")
        void shouldSkipWhenExistingAuthenticationIsPresent() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(securityContext.getAuthentication()).thenReturn(existingAuthentication);
            when(existingAuthentication.isAuthenticated()).thenReturn(true);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verifyNoInteractions(tokenValidator);
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should process when existing authentication is not authenticated")
        void shouldProcessWhenExistingAuthenticationIsNotAuthenticated() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_PREFIX + VALID_BEARER_TOKEN);
            when(securityContext.getAuthentication()).thenReturn(existingAuthentication);
            when(existingAuthentication.isAuthenticated()).thenReturn(false);
            GoogleTokenValidator.TokenInfoResponse tokenInfo = createTokenInfo("user@example.com");
            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(tokenInfo);
            when(tokenValidator.hasValidGmailScopes(tokenInfo.getScope())).thenReturn(true);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(tokenValidator, times(1)).getTokenInfo(VALID_BEARER_TOKEN);
            verify(tokenValidator, never()).isValidGoogleToken(anyString());
            verify(tokenValidator).hasValidGmailScopes(tokenInfo.getScope());
            verify(securityContext).setAuthentication(any(Authentication.class));
            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("Bearer Token Extraction Tests")
    class BearerTokenExtractionTests {

        @Test
        @DisplayName("Should extract valid Bearer token from Authorization header")
        void shouldExtractValidBearerTokenFromAuthorizationHeader() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_PREFIX + VALID_BEARER_TOKEN);
            when(securityContext.getAuthentication()).thenReturn(null);
            GoogleTokenValidator.TokenInfoResponse tokenInfo = createTokenInfo("user@example.com");
            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(tokenInfo);
            when(tokenValidator.hasValidGmailScopes(tokenInfo.getScope())).thenReturn(true);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(tokenValidator, times(1)).getTokenInfo(VALID_BEARER_TOKEN);
            verify(tokenValidator, never()).isValidGoogleToken(anyString());
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should extract Bearer token with extra whitespace")
        void shouldExtractBearerTokenWithExtraWhitespace() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn("Bearer   " + VALID_BEARER_TOKEN + "   ");
            when(securityContext.getAuthentication()).thenReturn(null);
            GoogleTokenValidator.TokenInfoResponse tokenInfo = createTokenInfo("user@example.com");
            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(tokenInfo);
            when(tokenValidator.hasValidGmailScopes(tokenInfo.getScope())).thenReturn(true);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(tokenValidator, times(1)).getTokenInfo(VALID_BEARER_TOKEN);
            verify(tokenValidator, never()).isValidGoogleToken(anyString());
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should skip when Authorization header is missing")
        void shouldSkipWhenAuthorizationHeaderIsMissing() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(null);
            when(securityContext.getAuthentication()).thenReturn(null);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verifyNoInteractions(tokenValidator);
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should skip when Authorization header does not start with Bearer")
        void shouldSkipWhenAuthorizationHeaderDoesNotStartWithBearer() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn("Basic dXNlcjpwYXNz");
            when(securityContext.getAuthentication()).thenReturn(null);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verifyNoInteractions(tokenValidator);
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should handle Bearer header without space")
        void shouldHandleBearerHeaderWithoutSpace() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn("Bearer");
            when(securityContext.getAuthentication()).thenReturn(null);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            // "Bearer" does NOT start with "Bearer " (with space), so no token is extracted
            verifyNoInteractions(tokenValidator);
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should handle Bearer header with only space")
        void shouldHandleBearerHeaderWithOnlySpace() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn("Bearer ");
            when(securityContext.getAuthentication()).thenReturn(null);
            when(tokenValidator.getTokenInfo(""))
                .thenThrow(new com.aucontraire.gmailbuddy.exception.AuthenticationException("Access token cannot be null or empty"));

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            // "Bearer " becomes empty string after trim(), which should throw AuthenticationException
            verify(tokenValidator).getTokenInfo("");
            verify(tokenValidator, never()).isValidGoogleToken(anyString());
            verify(securityContext, never()).setAuthentication(any());
            // CRITICAL: Filter should return with 401 and NOT continue filter chain
            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("Should reject case-sensitive Bearer headers")
        void shouldRejectCaseSensitiveBearerHeaders() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn("bearer " + VALID_BEARER_TOKEN);
            when(securityContext.getAuthentication()).thenReturn(null);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            // Case-sensitive check should not match "bearer" (lowercase)
            verifyNoInteractions(tokenValidator);
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should reject uppercase Bearer headers")
        void shouldRejectUppercaseBearerHeaders() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn("BEARER " + VALID_BEARER_TOKEN);
            when(securityContext.getAuthentication()).thenReturn(null);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            // Case-sensitive check should not match "BEARER" (uppercase)
            verifyNoInteractions(tokenValidator);
            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("Token Validation Tests")
    class TokenValidationTests {

        @Test
        @DisplayName("Should create authentication for valid token")
        void shouldCreateAuthenticationForValidToken() throws ServletException, IOException {
            // Given
            String userEmail = "test@example.com";
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_PREFIX + VALID_BEARER_TOKEN);
            when(securityContext.getAuthentication()).thenReturn(null);
            GoogleTokenValidator.TokenInfoResponse tokenInfo = createTokenInfo(userEmail);
            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(tokenInfo);
            when(tokenValidator.hasValidGmailScopes(tokenInfo.getScope())).thenReturn(true);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            // CRITICAL: Verify single Google API call pattern
            verify(tokenValidator, times(1)).getTokenInfo(VALID_BEARER_TOKEN);
            verify(tokenValidator, never()).isValidGoogleToken(anyString());
            verify(tokenValidator).hasValidGmailScopes(tokenInfo.getScope());

            verify(securityContext).setAuthentication(argThat(auth -> {
                assertThat(auth).isInstanceOf(UsernamePasswordAuthenticationToken.class);
                assertThat(auth.getName()).isEqualTo(userEmail);
                // SECURITY: Verify credentials contain reference ID, NOT raw token
                assertThat(auth.getCredentials()).isEqualTo(TEST_REFERENCE_ID);
                assertThat(auth.getCredentials()).isNotEqualTo(VALID_BEARER_TOKEN); // Must NOT be raw token
                assertThat(auth.getAuthorities()).hasSize(1);
                assertThat(auth.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_API_USER");
                return true;
            }));
            // Verify token reference was created
            verify(tokenReferenceService).createTokenReference(VALID_BEARER_TOKEN, userEmail);
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should use userId as fallback when email is null")
        void shouldUseUserIdAsFallbackWhenEmailIsNull() throws ServletException, IOException {
            // Given
            String userId = "123456789";
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_PREFIX + VALID_BEARER_TOKEN);
            when(securityContext.getAuthentication()).thenReturn(null);

            GoogleTokenValidator.TokenInfoResponse tokenInfo = createTokenInfo(null);
            tokenInfo.setUserId(userId);
            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(tokenInfo);
            when(tokenValidator.hasValidGmailScopes(tokenInfo.getScope())).thenReturn(true);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(tokenValidator, times(1)).getTokenInfo(VALID_BEARER_TOKEN);
            verify(tokenValidator, never()).isValidGoogleToken(anyString());
            verify(securityContext).setAuthentication(argThat(auth -> {
                assertThat(auth.getName()).isEqualTo(userId);
                return true;
            }));
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should use userId as fallback when email is empty")
        void shouldUseUserIdAsFallbackWhenEmailIsEmpty() throws ServletException, IOException {
            // Given
            String userId = "123456789";
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_PREFIX + VALID_BEARER_TOKEN);
            when(securityContext.getAuthentication()).thenReturn(null);

            GoogleTokenValidator.TokenInfoResponse tokenInfo = createTokenInfo("   ");
            tokenInfo.setUserId(userId);
            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(tokenInfo);
            when(tokenValidator.hasValidGmailScopes(tokenInfo.getScope())).thenReturn(true);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(tokenValidator, times(1)).getTokenInfo(VALID_BEARER_TOKEN);
            verify(tokenValidator, never()).isValidGoogleToken(anyString());
            verify(securityContext).setAuthentication(argThat(auth -> {
                assertThat(auth.getName()).isEqualTo(userId);
                return true;
            }));
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should use 'api-user' as final fallback when both email and userId are null")
        void shouldUseApiUserAsFinalFallbackWhenBothEmailAndUserIdAreNull() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_PREFIX + VALID_BEARER_TOKEN);
            when(securityContext.getAuthentication()).thenReturn(null);

            GoogleTokenValidator.TokenInfoResponse tokenInfo = createTokenInfo(null);
            tokenInfo.setUserId(null);
            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(tokenInfo);
            when(tokenValidator.hasValidGmailScopes(tokenInfo.getScope())).thenReturn(true);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(tokenValidator, times(1)).getTokenInfo(VALID_BEARER_TOKEN);
            verify(tokenValidator, never()).isValidGoogleToken(anyString());
            verify(securityContext).setAuthentication(argThat(auth -> {
                assertThat(auth.getName()).isEqualTo("api-user");
                return true;
            }));
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should use 'api-user' as final fallback when both email and userId are empty")
        void shouldUseApiUserAsFinalFallbackWhenBothEmailAndUserIdAreEmpty() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_PREFIX + VALID_BEARER_TOKEN);
            when(securityContext.getAuthentication()).thenReturn(null);

            GoogleTokenValidator.TokenInfoResponse tokenInfo = createTokenInfo("  ");
            tokenInfo.setUserId("  ");
            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(tokenInfo);
            when(tokenValidator.hasValidGmailScopes(tokenInfo.getScope())).thenReturn(true);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(tokenValidator, times(1)).getTokenInfo(VALID_BEARER_TOKEN);
            verify(tokenValidator, never()).isValidGoogleToken(anyString());
            verify(securityContext).setAuthentication(argThat(auth -> {
                assertThat(auth.getName()).isEqualTo("api-user");
                return true;
            }));
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should not create authentication for invalid token")
        void shouldNotCreateAuthenticationForInvalidToken() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_PREFIX + INVALID_BEARER_TOKEN);
            when(securityContext.getAuthentication()).thenReturn(null);
            when(tokenValidator.getTokenInfo(INVALID_BEARER_TOKEN))
                .thenThrow(new com.aucontraire.gmailbuddy.exception.AuthenticationException("Invalid token"));

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(tokenValidator, times(1)).getTokenInfo(INVALID_BEARER_TOKEN);
            verify(tokenValidator, never()).isValidGoogleToken(anyString());
            verify(securityContext, never()).setAuthentication(any());
            // CRITICAL: Should return 401 and NOT continue filter chain
            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            verify(filterChain, never()).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("Single API Call Optimization Tests (P0-2 Fix Verification)")
    class SingleApiCallOptimizationTests {

        @Test
        @DisplayName("CRITICAL: Should make only ONE Google API call per request (not two)")
        void shouldMakeOnlyOneGoogleApiCallPerRequest() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_PREFIX + VALID_BEARER_TOKEN);
            when(securityContext.getAuthentication()).thenReturn(null);

            GoogleTokenValidator.TokenInfoResponse tokenInfo = createTokenInfo("user@example.com");
            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(tokenInfo);
            when(tokenValidator.hasValidGmailScopes(tokenInfo.getScope())).thenReturn(true);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then - CRITICAL VERIFICATION: This proves the double-call fix is working!
            // OLD PATTERN (BROKEN): isValidGoogleToken() + getTokenInfo() = 2 Google API calls
            // NEW PATTERN (FIXED): getTokenInfo() + hasValidGmailScopes() = 1 Google API call

            // Verify EXACTLY ONE call to getTokenInfo (the only Google API call)
            verify(tokenValidator, times(1)).getTokenInfo(VALID_BEARER_TOKEN);

            // Verify ZERO calls to deprecated isValidGoogleToken (no redundant Google API call)
            verify(tokenValidator, never()).isValidGoogleToken(anyString());

            // Verify scope validation is called (but it's a LOCAL check, not a Google API call)
            verify(tokenValidator, times(1)).hasValidGmailScopes(tokenInfo.getScope());

            // Verify authentication succeeded
            verify(securityContext).setAuthentication(any(Authentication.class));
            verify(filterChain).doFilter(request, response);
            verify(response, never()).setStatus(anyInt());
        }

        @Test
        @DisplayName("Should use getTokenInfo for validation instead of deprecated isValidGoogleToken")
        void shouldUseGetTokenInfoForValidationInsteadOfDeprecatedMethod() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_PREFIX + VALID_BEARER_TOKEN);
            when(securityContext.getAuthentication()).thenReturn(null);

            GoogleTokenValidator.TokenInfoResponse tokenInfo = createTokenInfo("test@gmail.com");
            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(tokenInfo);
            when(tokenValidator.hasValidGmailScopes(tokenInfo.getScope())).thenReturn(true);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            // Verify we're using the NEW method (getTokenInfo)
            verify(tokenValidator, times(1)).getTokenInfo(VALID_BEARER_TOKEN);

            // Verify we're NOT using the OLD deprecated method (isValidGoogleToken)
            verify(tokenValidator, never()).isValidGoogleToken(anyString());
        }

        @Test
        @DisplayName("Should perform scope validation locally without additional Google API call")
        void shouldPerformScopeValidationLocallyWithoutAdditionalGoogleApiCall() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_PREFIX + VALID_BEARER_TOKEN);
            when(securityContext.getAuthentication()).thenReturn(null);

            GoogleTokenValidator.TokenInfoResponse tokenInfo = createTokenInfo("user@example.com");
            String scopeString = "email profile https://www.googleapis.com/auth/gmail.modify";
            tokenInfo.setScope(scopeString);

            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(tokenInfo);
            when(tokenValidator.hasValidGmailScopes(scopeString)).thenReturn(true);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            // Verify scope validation is called with the scope from tokenInfo
            verify(tokenValidator).hasValidGmailScopes(scopeString);

            // Verify ONLY ONE Google API call (getTokenInfo)
            verify(tokenValidator, times(1)).getTokenInfo(VALID_BEARER_TOKEN);
        }
    }

    @Nested
    @DisplayName("Gmail Scope Validation Tests")
    class GmailScopeValidationTests {

        @Test
        @DisplayName("Should reject token missing required Gmail scopes and return 401")
        void shouldRejectTokenMissingRequiredGmailScopesAndReturn401() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_PREFIX + VALID_BEARER_TOKEN);
            when(securityContext.getAuthentication()).thenReturn(null);

            // Token is valid but missing Gmail scopes (only has basic profile scopes)
            GoogleTokenValidator.TokenInfoResponse tokenInfo = createTokenInfo("user@example.com");
            tokenInfo.setScope("email profile openid"); // Missing Gmail scopes!

            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(tokenInfo);
            when(tokenValidator.hasValidGmailScopes("email profile openid")).thenReturn(false);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(tokenValidator, times(1)).getTokenInfo(VALID_BEARER_TOKEN);
            verify(tokenValidator).hasValidGmailScopes("email profile openid");
            verify(securityContext, never()).setAuthentication(any());

            // CRITICAL: Should return 401 and NOT continue filter chain
            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("Should authenticate token with gmail.readonly scope")
        void shouldAuthenticateTokenWithGmailReadonlyScope() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_PREFIX + VALID_BEARER_TOKEN);
            when(securityContext.getAuthentication()).thenReturn(null);

            GoogleTokenValidator.TokenInfoResponse tokenInfo = createTokenInfo("user@example.com");
            tokenInfo.setScope("email profile https://www.googleapis.com/auth/gmail.readonly");

            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(tokenInfo);
            when(tokenValidator.hasValidGmailScopes(tokenInfo.getScope())).thenReturn(true);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(tokenValidator).hasValidGmailScopes(tokenInfo.getScope());
            verify(securityContext).setAuthentication(any(Authentication.class));
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should authenticate token with gmail.modify scope")
        void shouldAuthenticateTokenWithGmailModifyScope() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_PREFIX + VALID_BEARER_TOKEN);
            when(securityContext.getAuthentication()).thenReturn(null);

            GoogleTokenValidator.TokenInfoResponse tokenInfo = createTokenInfo("user@example.com");
            tokenInfo.setScope("email profile https://www.googleapis.com/auth/gmail.modify");

            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(tokenInfo);
            when(tokenValidator.hasValidGmailScopes(tokenInfo.getScope())).thenReturn(true);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(tokenValidator).hasValidGmailScopes(tokenInfo.getScope());
            verify(securityContext).setAuthentication(any(Authentication.class));
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should authenticate token with full gmail scope")
        void shouldAuthenticateTokenWithFullGmailScope() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_PREFIX + VALID_BEARER_TOKEN);
            when(securityContext.getAuthentication()).thenReturn(null);

            GoogleTokenValidator.TokenInfoResponse tokenInfo = createTokenInfo("user@example.com");
            tokenInfo.setScope("email profile https://mail.google.com/");

            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(tokenInfo);
            when(tokenValidator.hasValidGmailScopes(tokenInfo.getScope())).thenReturn(true);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(tokenValidator).hasValidGmailScopes(tokenInfo.getScope());
            verify(securityContext).setAuthentication(any(Authentication.class));
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should reject token with null scope and return 401")
        void shouldRejectTokenWithNullScopeAndReturn401() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_PREFIX + VALID_BEARER_TOKEN);
            when(securityContext.getAuthentication()).thenReturn(null);

            GoogleTokenValidator.TokenInfoResponse tokenInfo = createTokenInfo("user@example.com");
            tokenInfo.setScope(null);

            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(tokenInfo);
            when(tokenValidator.hasValidGmailScopes(null)).thenReturn(false);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(tokenValidator).hasValidGmailScopes(null);
            verify(securityContext, never()).setAuthentication(any());
            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("Should reject token with empty scope and return 401")
        void shouldRejectTokenWithEmptyScopeAndReturn401() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_PREFIX + VALID_BEARER_TOKEN);
            when(securityContext.getAuthentication()).thenReturn(null);

            GoogleTokenValidator.TokenInfoResponse tokenInfo = createTokenInfo("user@example.com");
            tokenInfo.setScope("");

            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(tokenInfo);
            when(tokenValidator.hasValidGmailScopes("")).thenReturn(false);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(tokenValidator).hasValidGmailScopes("");
            verify(securityContext, never()).setAuthentication(any());
            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            verify(filterChain, never()).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle AuthenticationException and return 401")
        void shouldHandleAuthenticationExceptionAndReturn401() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_PREFIX + VALID_BEARER_TOKEN);
            when(securityContext.getAuthentication()).thenReturn(null);
            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN))
                .thenThrow(new com.aucontraire.gmailbuddy.exception.AuthenticationException("Token expired"));

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(tokenValidator, times(1)).getTokenInfo(VALID_BEARER_TOKEN);
            verify(tokenValidator, never()).isValidGoogleToken(anyString());
            verify(securityContext, never()).setAuthentication(any());
            // CRITICAL: Should return 401 and NOT continue filter chain
            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("Should handle generic exceptions and return 401")
        void shouldHandleGenericExceptionsAndReturn401() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_PREFIX + VALID_BEARER_TOKEN);
            when(securityContext.getAuthentication()).thenReturn(null);
            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN))
                .thenThrow(new RuntimeException("Unexpected error"));

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(tokenValidator, times(1)).getTokenInfo(VALID_BEARER_TOKEN);
            verify(tokenValidator, never()).isValidGoogleToken(anyString());
            verify(securityContext, never()).setAuthentication(any());
            // CRITICAL: Should return 401 and NOT continue filter chain
            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("Should handle SecurityContext exceptions and return 401")
        void shouldHandleSecurityContextExceptionsAndReturn401() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_PREFIX + VALID_BEARER_TOKEN);
            when(securityContext.getAuthentication()).thenReturn(null);
            GoogleTokenValidator.TokenInfoResponse tokenInfo = createTokenInfo("user@example.com");
            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(tokenInfo);
            when(tokenValidator.hasValidGmailScopes(tokenInfo.getScope())).thenReturn(true);
            doThrow(new RuntimeException("SecurityContext error")).when(securityContext).setAuthentication(any());

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(tokenValidator, times(1)).getTokenInfo(VALID_BEARER_TOKEN);
            verify(tokenValidator, never()).isValidGoogleToken(anyString());
            verify(securityContext).setAuthentication(any());
            // CRITICAL: Should return 401 and NOT continue filter chain
            verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            verify(filterChain, never()).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("Filter Chain Integration Tests")
    class FilterChainIntegrationTests {

        @Test
        @DisplayName("Should call filter chain on successful authentication")
        void shouldCallFilterChainOnSuccessfulAuthentication() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_PREFIX + VALID_BEARER_TOKEN);
            when(securityContext.getAuthentication()).thenReturn(null);
            GoogleTokenValidator.TokenInfoResponse tokenInfo = createTokenInfo("user@example.com");
            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(tokenInfo);
            when(tokenValidator.hasValidGmailScopes(tokenInfo.getScope())).thenReturn(true);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(request, response);
            verify(response, never()).setStatus(anyInt());
        }

        @Test
        @DisplayName("Should not modify request or response objects on success")
        void shouldNotModifyRequestOrResponseObjectsOnSuccess() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_PREFIX + VALID_BEARER_TOKEN);
            when(securityContext.getAuthentication()).thenReturn(null);
            GoogleTokenValidator.TokenInfoResponse tokenInfo = createTokenInfo("user@example.com");
            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(tokenInfo);
            when(tokenValidator.hasValidGmailScopes(tokenInfo.getScope())).thenReturn(true);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(same(request), same(response));
        }
    }

    // Helper methods

    private GoogleTokenValidator.TokenInfoResponse createTokenInfo(String email) {
        GoogleTokenValidator.TokenInfoResponse tokenInfo = new GoogleTokenValidator.TokenInfoResponse();
        tokenInfo.setEmail(email);
        tokenInfo.setUserId("123456789");
        tokenInfo.setScope("https://www.googleapis.com/auth/gmail.readonly");
        tokenInfo.setAudience("test-client-id");
        tokenInfo.setExpiresIn("3600");
        tokenInfo.setAccessType("offline");
        return tokenInfo;
    }
}