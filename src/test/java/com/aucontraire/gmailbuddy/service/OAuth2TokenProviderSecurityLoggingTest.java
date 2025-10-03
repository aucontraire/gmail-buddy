package com.aucontraire.gmailbuddy.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import com.aucontraire.gmailbuddy.exception.AuthenticationException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Security-focused test suite for OAuth2TokenProvider logging functionality.
 *
 * This test class specifically verifies that sensitive authentication data
 * (tokens, credentials, secrets) is never exposed in application logs.
 * It captures actual log output and analyzes it for security vulnerabilities.
 *
 * <p>Security Requirements Tested:
 * <ul>
 *   <li>No full access tokens appear in logs</li>
 *   <li>No Bearer tokens are exposed in logs</li>
 *   <li>No sensitive credentials are logged</li>
 *   <li>Token masking is applied consistently</li>
 *   <li>Debug information is useful without being sensitive</li>
 * </ul>
 *
 * @author Gmail Buddy Team
 * @since Sprint 2 - Security Logging Enhancement
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth2TokenProvider Security Logging Tests")
class OAuth2TokenProviderSecurityLoggingTest {

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

    private OAuth2TokenProvider tokenProvider;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger oauth2TokenProviderLogger;

    // Test tokens that represent realistic sensitive values
    private static final String LONG_OAUTH2_TOKEN = "ya29.a0AfH6SMC_SENSITIVE_DATA_1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String JWT_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
    private static final String API_KEY_TOKEN = "sk-SENSITIVE_API_KEY_DATA_1234567890abcdefghijklmnopqrstuvwxyz";
    private static final String GITHUB_TOKEN = "ghp_SENSITIVE_GITHUB_TOKEN_1234567890abcdefghijklmnopqrstuvwxyz";
    private static final String USER_EMAIL = "test@example.com";
    private static final String CLIENT_REGISTRATION_ID = "google";

    @BeforeEach
    void setUp() {
        tokenProvider = new OAuth2TokenProvider(authorizedClientService, properties, tokenValidator);

        // Setup log capture
        oauth2TokenProviderLogger = (Logger) LoggerFactory.getLogger(OAuth2TokenProvider.class);
        oauth2TokenProviderLogger.setLevel(Level.DEBUG);
        logAppender = new ListAppender<>();
        logAppender.start();
        oauth2TokenProviderLogger.addAppender(logAppender);

        // Setup mocks
        SecurityContextHolder.setContext(securityContext);
        when(properties.oauth2()).thenReturn(oauth2Config);
        when(oauth2Config.clientRegistrationId()).thenReturn(CLIENT_REGISTRATION_ID);
    }

    @AfterEach
    void tearDown() {
        oauth2TokenProviderLogger.detachAppender(logAppender);
        logAppender.stop();
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("Access Token Retrieval Logging Security")
    class AccessTokenRetrievalLoggingSecurityTests {

        @ParameterizedTest
        @MethodSource("provideSensitiveTokens")
        @DisplayName("Should never log full access tokens when retrieving by user ID")
        void shouldNeverLogFullAccessTokensWhenRetrievingByUserId(String sensitiveToken) {
            // Given
            mockOAuth2AuthorizedClient(sensitiveToken);

            // When
            String result = tokenProvider.getAccessToken(USER_EMAIL);

            // Then
            assertThat(result).isEqualTo(sensitiveToken);
            verifyNoSensitiveDataInLogs(sensitiveToken);
            verifyTokenMaskingInLogs(sensitiveToken);
        }

        @Test
        @DisplayName("Should log masked token during successful token retrieval")
        void shouldLogMaskedTokenDuringSuccessfulTokenRetrieval() {
            // Given
            mockOAuth2AuthorizedClient(LONG_OAUTH2_TOKEN);

            // When
            tokenProvider.getAccessToken(USER_EMAIL);

            // Then
            List<String> logMessages = getLogMessages();
            assertThat(logMessages)
                .anyMatch(message -> message.contains("Successfully retrieved access token"))
                .anyMatch(message -> message.contains("ya29****WXYZ"))
                .noneMatch(message -> message.contains("SENSITIVE_DATA"))
                .noneMatch(message -> message.contains("a0AfH6SMC"));
        }

        @Test
        @DisplayName("Should handle expired token logging securely")
        void shouldHandleExpiredTokenLoggingSecurely() {
            // Given
            when(authorizedClientService.loadAuthorizedClient(CLIENT_REGISTRATION_ID, USER_EMAIL))
                .thenReturn(authorizedClient);
            when(authorizedClient.getAccessToken()).thenReturn(accessToken);
            when(accessToken.getTokenValue()).thenReturn(LONG_OAUTH2_TOKEN);
            when(accessToken.getExpiresAt()).thenReturn(Instant.now().minusSeconds(3600)); // Expired

            // When & Then
            assertThatThrownBy(() -> tokenProvider.getAccessToken(USER_EMAIL))
                .isInstanceOf(AuthenticationException.class);

            // Verify no sensitive data in logs even during error scenarios
            verifyNoSensitiveDataInLogs(LONG_OAUTH2_TOKEN);
        }
    }

    @Nested
    @DisplayName("Bearer Token Processing Logging Security")
    class BearerTokenProcessingLoggingSecurityTests {

        @ParameterizedTest
        @MethodSource("provideSensitiveTokens")
        @DisplayName("Should never log full Bearer tokens")
        void shouldNeverLogFullBearerTokens(String sensitiveToken) {
            // Given
            mockHttpRequestContext(sensitiveToken);
            when(tokenValidator.isValidGoogleToken(sensitiveToken)).thenReturn(true);

            // When
            String result = tokenProvider.getBearerToken();

            // Then
            assertThat(result).isEqualTo(sensitiveToken);
            verifyNoSensitiveDataInLogs(sensitiveToken);
        }

        @Test
        @DisplayName("Should log masked Bearer token during extraction")
        void shouldLogMaskedBearerTokenDuringExtraction() {
            // Given
            mockHttpRequestContext(JWT_TOKEN);

            // When
            tokenProvider.getBearerToken();

            // Then
            List<String> logMessages = getLogMessages();
            assertThat(logMessages)
                .anyMatch(message -> message.contains("Successfully extracted Bearer token"))
                .anyMatch(message -> message.contains("eyJh****sw5c"))
                .noneMatch(message -> message.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"))
                .noneMatch(message -> message.contains("SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"));
        }

        @Test
        @DisplayName("Should handle Bearer token validation logging securely")
        void shouldHandleBearerTokenValidationLoggingSecurely() {
            // Given
            mockHttpRequestContext(API_KEY_TOKEN);
            when(tokenValidator.isValidGoogleToken(API_KEY_TOKEN)).thenReturn(true);

            // When
            tokenProvider.getTokenFromContext();

            // Then
            List<String> logMessages = getLogMessages();
            assertThat(logMessages)
                .anyMatch(message -> message.contains("Successfully authenticated using Bearer token"))
                .anyMatch(message -> message.contains("sk-S****xyz"))
                .noneMatch(message -> message.contains("SENSITIVE_API_KEY_DATA"))
                .noneMatch(message -> message.contains("1234567890abcdefghijklmnopqrstuvwxyz"));
        }
    }

    @Nested
    @DisplayName("API Client Authentication Logging Security")
    class ApiClientAuthenticationLoggingSecurityTests {

        @Test
        @DisplayName("Should mask token in API client authentication logs")
        void shouldMaskTokenInApiClientAuthenticationLogs() {
            // Given
            mockHttpRequestContext(null);
            mockApiClientAuthentication(GITHUB_TOKEN);

            // When
            String result = tokenProvider.getTokenFromContext();

            // Then
            assertThat(result).isEqualTo(GITHUB_TOKEN);

            List<String> logMessages = getLogMessages();
            assertThat(logMessages)
                .anyMatch(message -> message.contains("Successfully retrieved token from API client"))
                .anyMatch(message -> message.contains("ghp_****xyz"))
                .noneMatch(message -> message.contains("SENSITIVE_GITHUB_TOKEN"))
                .noneMatch(message -> message.contains("1234567890abcdefghijklmnopqrstuvwxyz"));
        }

        @Test
        @DisplayName("Should handle API client authentication failures securely")
        void shouldHandleApiClientAuthenticationFailuresSecurely() {
            // Given
            mockHttpRequestContext(null);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getAuthorities()).thenAnswer(invocation -> Arrays.asList(new SimpleGrantedAuthority("ROLE_API_USER")));
            when(authentication.getCredentials()).thenReturn(LONG_OAUTH2_TOKEN);

            // Mock OAuth2 fallback to fail as well
            when(authentication.getName()).thenReturn(USER_EMAIL);
            when(authorizedClientService.loadAuthorizedClient(any(), any()))
                .thenThrow(new RuntimeException("OAuth2 service unavailable"));

            // When & Then
            assertThatThrownBy(() -> tokenProvider.getTokenFromContext())
                .isInstanceOf(AuthenticationException.class);

            // Verify no sensitive data leaked during error handling
            verifyNoSensitiveDataInLogs(LONG_OAUTH2_TOKEN);
        }
    }

    @Nested
    @DisplayName("OAuth2 Context Logging Security")
    class OAuth2ContextLoggingSecurityTests {

        @Test
        @DisplayName("Should mask OAuth2 token in context resolution logs")
        void shouldMaskOAuth2TokenInContextResolutionLogs() {
            // Given
            mockHttpRequestContext(null);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getAuthorities()).thenAnswer(invocation -> Arrays.asList(new SimpleGrantedAuthority("ROLE_USER")));
            mockOAuth2Fallback(JWT_TOKEN);

            // When
            String result = tokenProvider.getTokenFromContext();

            // Then
            assertThat(result).isEqualTo(JWT_TOKEN);

            List<String> logMessages = getLogMessages();
            assertThat(logMessages)
                .anyMatch(message -> message.contains("Successfully authenticated using OAuth2 context"))
                .anyMatch(message -> message.contains("eyJh****sw5c"))
                .noneMatch(message -> message.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"))
                .noneMatch(message -> message.contains("SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"));
        }

        @Test
        @DisplayName("Should handle OAuth2 fallback failures without exposing tokens")
        void shouldHandleOAuth2FallbackFailuresWithoutExposingTokens() {
            // Given
            mockHttpRequestContext(INVALID_BEARER_TOKEN);
            when(tokenValidator.isValidGoogleToken(INVALID_BEARER_TOKEN)).thenReturn(false);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getAuthorities()).thenAnswer(invocation -> Arrays.asList(new SimpleGrantedAuthority("ROLE_USER")));
            when(authentication.getName()).thenReturn(USER_EMAIL);
            when(authorizedClientService.loadAuthorizedClient(CLIENT_REGISTRATION_ID, USER_EMAIL))
                .thenThrow(new RuntimeException("OAuth2 client not found"));

            // When & Then
            assertThatThrownBy(() -> tokenProvider.getTokenFromContext())
                .isInstanceOf(AuthenticationException.class);

            // Verify error logs don't contain sensitive data
            List<String> logMessages = getLogMessages();
            assertThat(logMessages)
                .anyMatch(message -> message.contains("All authentication methods failed"))
                .noneMatch(message -> message.contains(INVALID_BEARER_TOKEN));
        }
    }

    @Nested
    @DisplayName("Cross-Method Security Verification")
    class CrossMethodSecurityVerificationTests {

        @Test
        @DisplayName("Should maintain security across all authentication paths")
        void shouldMaintainSecurityAcrossAllAuthenticationPaths() {
            // Given - Test all authentication paths with different sensitive tokens
            String[] testTokens = {LONG_OAUTH2_TOKEN, JWT_TOKEN, API_KEY_TOKEN, GITHUB_TOKEN};

            for (String token : testTokens) {
                logAppender.list.clear(); // Clear logs between tests

                // Test Bearer token path
                mockHttpRequestContext(token);
                when(tokenValidator.isValidGoogleToken(token)).thenReturn(true);

                // When
                tokenProvider.getTokenFromContext();

                // Then
                verifyNoSensitiveDataInLogs(token);
                verifyTokenMaskingInLogs(token);
            }
        }

        @Test
        @DisplayName("Should handle complex authentication scenarios securely")
        void shouldHandleComplexAuthenticationScenariosSecurely() {
            // Given - Complex scenario with multiple fallbacks
            mockHttpRequestContext(INVALID_BEARER_TOKEN);
            when(tokenValidator.isValidGoogleToken(INVALID_BEARER_TOKEN)).thenReturn(false);
            mockApiClientAuthentication(GITHUB_TOKEN);

            // When
            String result = tokenProvider.getTokenFromContext();

            // Then
            assertThat(result).isEqualTo(GITHUB_TOKEN);

            // Verify both tokens are properly masked in logs
            verifyNoSensitiveDataInLogs(INVALID_BEARER_TOKEN);
            verifyNoSensitiveDataInLogs(GITHUB_TOKEN);

            List<String> logMessages = getLogMessages();
            assertThat(logMessages)
                .anyMatch(message -> message.contains("Bearer token validation failed"))
                .anyMatch(message -> message.contains("Successfully retrieved token from API client"))
                .anyMatch(message -> message.contains("ghp_****xyz"));
        }
    }

    // Helper methods

    private static final String INVALID_BEARER_TOKEN = "invalid-bearer-token-12345";

    private static Stream<Arguments> provideSensitiveTokens() {
        return Stream.of(
            Arguments.of(LONG_OAUTH2_TOKEN),
            Arguments.of(JWT_TOKEN),
            Arguments.of(API_KEY_TOKEN),
            Arguments.of(GITHUB_TOKEN)
        );
    }

    private void mockHttpRequestContext(String bearerToken) {
        String authHeader = bearerToken != null ? "Bearer " + bearerToken : null;
        try (MockedStatic<RequestContextHolder> mockedRequestContextHolder = mockStatic(RequestContextHolder.class)) {
            mockedRequestContextHolder.when(RequestContextHolder::getRequestAttributes).thenReturn(requestAttributes);
            when(requestAttributes.getRequest()).thenReturn(httpRequest);
            when(httpRequest.getHeader("Authorization")).thenReturn(authHeader);
        }
    }

    private void mockApiClientAuthentication(String token) {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getAuthorities()).thenAnswer(invocation -> Arrays.asList(new SimpleGrantedAuthority("ROLE_API_USER")));
        when(authentication.getCredentials()).thenReturn(token);
    }

    private void mockOAuth2AuthorizedClient(String tokenValue) {
        when(authorizedClientService.loadAuthorizedClient(CLIENT_REGISTRATION_ID, USER_EMAIL))
            .thenReturn(authorizedClient);
        when(authorizedClient.getAccessToken()).thenReturn(accessToken);
        when(accessToken.getTokenValue()).thenReturn(tokenValue);
        when(accessToken.getExpiresAt()).thenReturn(Instant.now().plusSeconds(3600));
    }

    private void mockOAuth2Fallback(String tokenValue) {
        when(authentication.getName()).thenReturn(USER_EMAIL);
        mockOAuth2AuthorizedClient(tokenValue);
    }

    private List<String> getLogMessages() {
        return logAppender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .toList();
    }

    private void verifyNoSensitiveDataInLogs(String sensitiveToken) {
        if (sensitiveToken == null || sensitiveToken.length() < 12) {
            return; // Short tokens are handled differently
        }

        // Extract the sensitive middle part that should never appear in logs
        String sensitiveMiddle = sensitiveToken.substring(4, sensitiveToken.length() - 4);

        List<String> logMessages = getLogMessages();

        // Verify no full token appears in logs
        assertThat(logMessages)
            .as("Full token should never appear in logs")
            .noneMatch(message -> message.contains(sensitiveToken));

        // Verify no sensitive middle part appears in logs
        assertThat(logMessages)
            .as("Sensitive token middle part should never appear in logs")
            .noneMatch(message -> message.contains(sensitiveMiddle));
    }

    private void verifyTokenMaskingInLogs(String originalToken) {
        if (originalToken == null || originalToken.length() < 12) {
            return; // Short tokens are handled differently
        }

        String expectedPrefix = originalToken.substring(0, 4);
        String expectedSuffix = originalToken.substring(originalToken.length() - 4);
        String expectedMasked = expectedPrefix + "****" + expectedSuffix;

        List<String> logMessages = getLogMessages();

        // Verify that masked version appears in logs
        assertThat(logMessages)
            .as("Masked token should appear in logs")
            .anyMatch(message -> message.contains(expectedMasked));
    }
}