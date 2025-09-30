package com.aucontraire.gmailbuddy.config;

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
 * - Filter chain processing with valid and invalid tokens
 * - API endpoint filtering logic
 * - Security context establishment
 * - Integration with GoogleTokenValidator
 * - Error handling and edge cases
 *
 * @author Gmail Buddy Team
 * @since Sprint 2 - Phase 2 OAuth2 Security Context Decoupling
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TokenAuthenticationFilter")
class TokenAuthenticationFilterTest {

    @Mock
    private GoogleTokenValidator tokenValidator;

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

    private TokenAuthenticationFilter filter;

    private static final String VALID_BEARER_TOKEN = "ya29.a0ARrdaM-valid-token";
    private static final String INVALID_BEARER_TOKEN = "invalid-token";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @BeforeEach
    void setUp() {
        filter = new TokenAuthenticationFilter(tokenValidator);
        SecurityContextHolder.setContext(securityContext);
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
            when(tokenValidator.isValidGoogleToken(VALID_BEARER_TOKEN)).thenReturn(true);
            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(createTokenInfo("user@example.com"));

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(tokenValidator).isValidGoogleToken(VALID_BEARER_TOKEN);
            verify(tokenValidator).getTokenInfo(VALID_BEARER_TOKEN);
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
            when(tokenValidator.isValidGoogleToken(VALID_BEARER_TOKEN)).thenReturn(true);
            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(createTokenInfo("user@example.com"));

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(tokenValidator).isValidGoogleToken(VALID_BEARER_TOKEN);
            verify(tokenValidator).getTokenInfo(VALID_BEARER_TOKEN);
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
            when(tokenValidator.isValidGoogleToken(VALID_BEARER_TOKEN)).thenReturn(true);
            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(createTokenInfo("user@example.com"));

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(tokenValidator).isValidGoogleToken(VALID_BEARER_TOKEN);
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should extract Bearer token with extra whitespace")
        void shouldExtractBearerTokenWithExtraWhitespace() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn("Bearer   " + VALID_BEARER_TOKEN + "   ");
            when(securityContext.getAuthentication()).thenReturn(null);
            when(tokenValidator.isValidGoogleToken(VALID_BEARER_TOKEN)).thenReturn(true);
            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(createTokenInfo("user@example.com"));

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(tokenValidator).isValidGoogleToken(VALID_BEARER_TOKEN);
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
            when(tokenValidator.isValidGoogleToken("")).thenReturn(false);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            // "Bearer " becomes empty string after trim(), which should be validated as invalid
            verify(tokenValidator).isValidGoogleToken("");
            verify(tokenValidator, never()).getTokenInfo(any());
            verify(securityContext, never()).setAuthentication(any());
            verify(filterChain).doFilter(request, response);
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
            when(tokenValidator.isValidGoogleToken(VALID_BEARER_TOKEN)).thenReturn(true);
            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(createTokenInfo(userEmail));

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(securityContext).setAuthentication(argThat(auth -> {
                assertThat(auth).isInstanceOf(UsernamePasswordAuthenticationToken.class);
                assertThat(auth.getName()).isEqualTo(userEmail);
                assertThat(auth.getCredentials()).isEqualTo(VALID_BEARER_TOKEN);
                assertThat(auth.getAuthorities()).hasSize(1);
                assertThat(auth.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_API_USER");
                return true;
            }));
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
            when(tokenValidator.isValidGoogleToken(VALID_BEARER_TOKEN)).thenReturn(true);

            GoogleTokenValidator.TokenInfoResponse tokenInfo = createTokenInfo(null);
            tokenInfo.setUserId(userId);
            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(tokenInfo);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
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
            when(tokenValidator.isValidGoogleToken(VALID_BEARER_TOKEN)).thenReturn(true);

            GoogleTokenValidator.TokenInfoResponse tokenInfo = createTokenInfo("   ");
            tokenInfo.setUserId(userId);
            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(tokenInfo);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
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
            when(tokenValidator.isValidGoogleToken(VALID_BEARER_TOKEN)).thenReturn(true);

            GoogleTokenValidator.TokenInfoResponse tokenInfo = createTokenInfo(null);
            tokenInfo.setUserId(null);
            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(tokenInfo);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
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
            when(tokenValidator.isValidGoogleToken(VALID_BEARER_TOKEN)).thenReturn(true);

            GoogleTokenValidator.TokenInfoResponse tokenInfo = createTokenInfo("  ");
            tokenInfo.setUserId("  ");
            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(tokenInfo);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
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
            when(tokenValidator.isValidGoogleToken(INVALID_BEARER_TOKEN)).thenReturn(false);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(tokenValidator).isValidGoogleToken(INVALID_BEARER_TOKEN);
            verify(tokenValidator, never()).getTokenInfo(any());
            verify(securityContext, never()).setAuthentication(any());
            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle token validation exception gracefully")
        void shouldHandleTokenValidationExceptionGracefully() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_PREFIX + VALID_BEARER_TOKEN);
            when(securityContext.getAuthentication()).thenReturn(null);
            when(tokenValidator.isValidGoogleToken(VALID_BEARER_TOKEN)).thenThrow(new RuntimeException("Token validation failed"));

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(tokenValidator).isValidGoogleToken(VALID_BEARER_TOKEN);
            verify(securityContext, never()).setAuthentication(any());
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should handle getTokenInfo exception gracefully")
        void shouldHandleGetTokenInfoExceptionGracefully() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_PREFIX + VALID_BEARER_TOKEN);
            when(securityContext.getAuthentication()).thenReturn(null);
            when(tokenValidator.isValidGoogleToken(VALID_BEARER_TOKEN)).thenReturn(true);
            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenThrow(new RuntimeException("Failed to get token info"));

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(tokenValidator).isValidGoogleToken(VALID_BEARER_TOKEN);
            verify(tokenValidator).getTokenInfo(VALID_BEARER_TOKEN);
            verify(securityContext, never()).setAuthentication(any());
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should continue filter chain even when authentication fails")
        void shouldContinueFilterChainEvenWhenAuthenticationFails() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_PREFIX + INVALID_BEARER_TOKEN);
            when(securityContext.getAuthentication()).thenReturn(null);
            when(tokenValidator.isValidGoogleToken(INVALID_BEARER_TOKEN)).thenReturn(false);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should handle SecurityContext exceptions gracefully")
        void shouldHandleSecurityContextExceptionsGracefully() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_PREFIX + VALID_BEARER_TOKEN);
            when(securityContext.getAuthentication()).thenReturn(null);
            when(tokenValidator.isValidGoogleToken(VALID_BEARER_TOKEN)).thenReturn(true);
            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(createTokenInfo("user@example.com"));
            doThrow(new RuntimeException("SecurityContext error")).when(securityContext).setAuthentication(any());

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(tokenValidator).isValidGoogleToken(VALID_BEARER_TOKEN);
            verify(tokenValidator).getTokenInfo(VALID_BEARER_TOKEN);
            verify(securityContext).setAuthentication(any());
            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("Filter Chain Integration Tests")
    class FilterChainIntegrationTests {

        @Test
        @DisplayName("Should always call filter chain doFilter method")
        void shouldAlwaysCallFilterChainDoFilterMethod() throws ServletException, IOException {
            // Given - Various scenarios
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_PREFIX + VALID_BEARER_TOKEN);
            when(securityContext.getAuthentication()).thenReturn(null);
            when(tokenValidator.isValidGoogleToken(VALID_BEARER_TOKEN)).thenReturn(true);
            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(createTokenInfo("user@example.com"));

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should not modify request or response objects")
        void shouldNotModifyRequestOrResponseObjects() throws ServletException, IOException {
            // Given
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_PREFIX + VALID_BEARER_TOKEN);
            when(securityContext.getAuthentication()).thenReturn(null);
            when(tokenValidator.isValidGoogleToken(VALID_BEARER_TOKEN)).thenReturn(true);
            when(tokenValidator.getTokenInfo(VALID_BEARER_TOKEN)).thenReturn(createTokenInfo("user@example.com"));

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