package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.exception.AuthenticationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for GoogleTokenValidator service.
 *
 * Tests token validation against Google's tokeninfo endpoint, including:
 * - Valid token scenarios with proper Gmail scopes
 * - Invalid token rejection and error handling
 * - Expired token detection
 * - Network error scenarios
 * - Scope verification for Gmail permissions
 * - Edge cases and malformed responses
 *
 * @author Gmail Buddy Team
 * @since Sprint 2 - Phase 2 OAuth2 Security Context Decoupling
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GoogleTokenValidator")
class GoogleTokenValidatorTest {

    @Mock
    private RestTemplate restTemplate;

    private GoogleTokenValidator tokenValidator;

    private static final String VALID_GOOGLE_TOKEN = "ya29.a0ARrdaM-test-valid-token";
    private static final String INVALID_TOKEN = "invalid.jwt.token";
    private static final String EXPIRED_TOKEN = "ya29.a0ARrdaM-expired-token";
    private static final String MALFORMED_TOKEN = "malformed-token";

    private static final String GOOGLE_TOKEN_INFO_URL = "https://www.googleapis.com/oauth2/v1/tokeninfo";

    @BeforeEach
    void setUp() {
        tokenValidator = new GoogleTokenValidator(restTemplate);
    }

    @Nested
    @DisplayName("Token Validation Tests")
    class TokenValidationTests {

        @Test
        @DisplayName("Should return true for valid Google token with Gmail readonly scope")
        void shouldReturnTrueForValidTokenWithGmailReadonlyScope() throws Exception {
            // Given
            Map<String, Object> validResponse = createValidTokenResponse(
                "https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/userinfo.email",
                "3600"
            );
            mockSuccessfulTokenInfoResponse(VALID_GOOGLE_TOKEN, validResponse);

            // When
            boolean result = tokenValidator.isValidGoogleToken(VALID_GOOGLE_TOKEN);

            // Then
            assertThat(result).isTrue();
            verifyTokenInfoRequest(VALID_GOOGLE_TOKEN);
        }

        @Test
        @DisplayName("Should return true for valid Google token with Gmail modify scope")
        void shouldReturnTrueForValidTokenWithGmailModifyScope() throws Exception {
            // Given
            Map<String, Object> validResponse = createValidTokenResponse(
                "https://www.googleapis.com/auth/gmail.modify https://www.googleapis.com/auth/userinfo.profile",
                "3600"
            );
            mockSuccessfulTokenInfoResponse(VALID_GOOGLE_TOKEN, validResponse);

            // When
            boolean result = tokenValidator.isValidGoogleToken(VALID_GOOGLE_TOKEN);

            // Then
            assertThat(result).isTrue();
            verifyTokenInfoRequest(VALID_GOOGLE_TOKEN);
        }

        @Test
        @DisplayName("Should return true for valid Google token with full Gmail scope")
        void shouldReturnTrueForValidTokenWithFullGmailScope() throws Exception {
            // Given
            Map<String, Object> validResponse = createValidTokenResponse(
                "https://mail.google.com/ https://www.googleapis.com/auth/userinfo.email",
                "7200"
            );
            mockSuccessfulTokenInfoResponse(VALID_GOOGLE_TOKEN, validResponse);

            // When
            boolean result = tokenValidator.isValidGoogleToken(VALID_GOOGLE_TOKEN);

            // Then
            assertThat(result).isTrue();
            verifyTokenInfoRequest(VALID_GOOGLE_TOKEN);
        }

        @Test
        @DisplayName("Should return false for token with no Gmail scopes")
        void shouldReturnFalseForTokenWithoutGmailScopes() throws Exception {
            // Given
            Map<String, Object> responseWithoutGmailScopes = createValidTokenResponse(
                "https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/userinfo.profile",
                "3600"
            );
            mockSuccessfulTokenInfoResponse(VALID_GOOGLE_TOKEN, responseWithoutGmailScopes);

            // When
            boolean result = tokenValidator.isValidGoogleToken(VALID_GOOGLE_TOKEN);

            // Then
            assertThat(result).isFalse();
            verifyTokenInfoRequest(VALID_GOOGLE_TOKEN);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t", "\n"})
        @DisplayName("Should return false for null, empty, or whitespace tokens")
        void shouldReturnFalseForInvalidTokenStrings(String invalidToken) {
            // When
            boolean result = tokenValidator.isValidGoogleToken(invalidToken);

            // Then
            assertThat(result).isFalse();
            verifyNoInteractions(restTemplate);
        }

        @Test
        @DisplayName("Should return false for null token")
        void shouldReturnFalseForNullToken() {
            // When
            boolean result = tokenValidator.isValidGoogleToken(null);

            // Then
            assertThat(result).isFalse();
            verifyNoInteractions(restTemplate);
        }
    }

    @Nested
    @DisplayName("Google API Error Response Tests")
    class GoogleApiErrorResponseTests {

        @Test
        @DisplayName("Should return false when Google returns 400 Bad Request")
        void shouldReturnFalseWhenGoogleReturns400() throws Exception {
            // Given
            mockErrorResponse(INVALID_TOKEN, HttpStatus.BAD_REQUEST);

            // When
            boolean result = tokenValidator.isValidGoogleToken(INVALID_TOKEN);

            // Then
            assertThat(result).isFalse();
            verifyTokenInfoRequest(INVALID_TOKEN);
        }

        @Test
        @DisplayName("Should return false when Google returns 401 Unauthorized")
        void shouldReturnFalseWhenGoogleReturns401() throws Exception {
            // Given
            mockErrorResponse(EXPIRED_TOKEN, HttpStatus.UNAUTHORIZED);

            // When
            boolean result = tokenValidator.isValidGoogleToken(EXPIRED_TOKEN);

            // Then
            assertThat(result).isFalse();
            verifyTokenInfoRequest(EXPIRED_TOKEN);
        }

        @Test
        @DisplayName("Should return false when Google returns error in response body")
        void shouldReturnFalseWhenGoogleReturnsErrorInResponseBody() throws Exception {
            // Given
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "invalid_token");
            errorResponse.put("error_description", "The access token expired");

            mockSuccessfulTokenInfoResponse(EXPIRED_TOKEN, errorResponse);

            // When
            boolean result = tokenValidator.isValidGoogleToken(EXPIRED_TOKEN);

            // Then
            assertThat(result).isFalse();
            verifyTokenInfoRequest(EXPIRED_TOKEN);
        }

        @Test
        @DisplayName("Should return false when Google returns empty response body")
        void shouldReturnFalseWhenGoogleReturnsEmptyResponseBody() throws Exception {
            // Given
            when(restTemplate.exchange(eq(GOOGLE_TOKEN_INFO_URL), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

            // When
            boolean result = tokenValidator.isValidGoogleToken(VALID_GOOGLE_TOKEN);

            // Then
            assertThat(result).isFalse();
            verifyTokenInfoRequest(VALID_GOOGLE_TOKEN);
        }
    }

    @Nested
    @DisplayName("Network Error Tests")
    class NetworkErrorTests {

        @Test
        @DisplayName("Should return false when RestTemplate throws RestClientException")
        void shouldReturnFalseWhenRestTemplateThrowsException() throws Exception {
            // Given
            when(restTemplate.exchange(eq(GOOGLE_TOKEN_INFO_URL), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RestClientException("Connection timeout"));

            // When
            boolean result = tokenValidator.isValidGoogleToken(VALID_GOOGLE_TOKEN);

            // Then
            assertThat(result).isFalse();
            verifyTokenInfoRequest(VALID_GOOGLE_TOKEN);
        }

        @Test
        @DisplayName("Should return false when network error occurs")
        void shouldReturnFalseWhenNetworkErrorOccurs() throws Exception {
            // Given
            when(restTemplate.exchange(eq(GOOGLE_TOKEN_INFO_URL), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("Network unreachable"));

            // When
            boolean result = tokenValidator.isValidGoogleToken(VALID_GOOGLE_TOKEN);

            // Then
            assertThat(result).isFalse();
            verifyTokenInfoRequest(VALID_GOOGLE_TOKEN);
        }
    }

    @Nested
    @DisplayName("GetTokenInfo Method Tests")
    class GetTokenInfoMethodTests {

        @Test
        @DisplayName("Should return token info for valid token")
        void shouldReturnTokenInfoForValidToken() throws Exception {
            // Given
            Map<String, Object> validResponse = createValidTokenResponse(
                "https://www.googleapis.com/auth/gmail.readonly",
                "3600"
            );
            mockSuccessfulTokenInfoResponse(VALID_GOOGLE_TOKEN, validResponse);

            // When
            GoogleTokenValidator.TokenInfoResponse tokenInfo = tokenValidator.getTokenInfo(VALID_GOOGLE_TOKEN);

            // Then
            assertThat(tokenInfo).isNotNull();
            assertThat(tokenInfo.getScope()).isEqualTo("https://www.googleapis.com/auth/gmail.readonly");
            assertThat(tokenInfo.getExpiresIn()).isEqualTo("3600");
            assertThat(tokenInfo.getEmail()).isEqualTo("test@example.com");
            assertThat(tokenInfo.getUserId()).isEqualTo("123456789");
            assertThat(tokenInfo.getAudience()).isEqualTo("test-client-id");
            assertThat(tokenInfo.getAccessType()).isEqualTo("offline");
            verifyTokenInfoRequest(VALID_GOOGLE_TOKEN);
        }

        @Test
        @DisplayName("Should throw AuthenticationException for null token")
        void shouldThrowAuthenticationExceptionForNullToken() {
            // When & Then
            assertThatThrownBy(() -> tokenValidator.getTokenInfo(null))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("Access token cannot be null or empty");

            verifyNoInteractions(restTemplate);
        }

        @Test
        @DisplayName("Should throw AuthenticationException for empty token")
        void shouldThrowAuthenticationExceptionForEmptyToken() {
            // When & Then
            assertThatThrownBy(() -> tokenValidator.getTokenInfo("   "))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("Access token cannot be null or empty");

            verifyNoInteractions(restTemplate);
        }

        @Test
        @DisplayName("Should throw AuthenticationException when Google API call fails")
        void shouldThrowAuthenticationExceptionWhenGoogleApiCallFails() throws Exception {
            // Given
            when(restTemplate.exchange(eq(GOOGLE_TOKEN_INFO_URL), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RestClientException("API unavailable"));

            // When & Then
            assertThatThrownBy(() -> tokenValidator.getTokenInfo(VALID_GOOGLE_TOKEN))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("Token validation failed:")
                .hasCauseInstanceOf(RestClientException.class);

            verifyTokenInfoRequest(VALID_GOOGLE_TOKEN);
        }

        @Test
        @DisplayName("Should handle expires_in as integer value")
        void shouldHandleExpiresInAsInteger() throws Exception {
            // Given
            Map<String, Object> validResponse = createValidTokenResponse(
                "https://www.googleapis.com/auth/gmail.readonly",
                null
            );
            validResponse.put("expires_in", 3600); // Integer instead of String
            mockSuccessfulTokenInfoResponse(VALID_GOOGLE_TOKEN, validResponse);

            // When
            GoogleTokenValidator.TokenInfoResponse tokenInfo = tokenValidator.getTokenInfo(VALID_GOOGLE_TOKEN);

            // Then
            assertThat(tokenInfo).isNotNull();
            assertThat(tokenInfo.getExpiresIn()).isEqualTo("3600");
            verifyTokenInfoRequest(VALID_GOOGLE_TOKEN);
        }

        @Test
        @DisplayName("Should handle missing expires_in field")
        void shouldHandleMissingExpiresInField() throws Exception {
            // Given
            Map<String, Object> validResponse = createValidTokenResponse(
                "https://www.googleapis.com/auth/gmail.readonly",
                null
            );
            validResponse.remove("expires_in");
            mockSuccessfulTokenInfoResponse(VALID_GOOGLE_TOKEN, validResponse);

            // When
            GoogleTokenValidator.TokenInfoResponse tokenInfo = tokenValidator.getTokenInfo(VALID_GOOGLE_TOKEN);

            // Then
            assertThat(tokenInfo).isNotNull();
            assertThat(tokenInfo.getExpiresIn()).isNull();
            verifyTokenInfoRequest(VALID_GOOGLE_TOKEN);
        }
    }

    @Nested
    @DisplayName("Scope Validation Tests")
    class ScopeValidationTests {

        @Test
        @DisplayName("Should validate multiple Gmail scopes in single token")
        void shouldValidateMultipleGmailScopesInSingleToken() throws Exception {
            // Given
            Map<String, Object> validResponse = createValidTokenResponse(
                "https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/gmail.modify https://mail.google.com/",
                "3600"
            );
            mockSuccessfulTokenInfoResponse(VALID_GOOGLE_TOKEN, validResponse);

            // When
            boolean result = tokenValidator.isValidGoogleToken(VALID_GOOGLE_TOKEN);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should validate mixed Gmail and non-Gmail scopes")
        void shouldValidateMixedGmailAndNonGmailScopes() throws Exception {
            // Given
            Map<String, Object> validResponse = createValidTokenResponse(
                "https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/userinfo.profile",
                "3600"
            );
            mockSuccessfulTokenInfoResponse(VALID_GOOGLE_TOKEN, validResponse);

            // When
            boolean result = tokenValidator.isValidGoogleToken(VALID_GOOGLE_TOKEN);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should reject token with null scope")
        void shouldRejectTokenWithNullScope() throws Exception {
            // Given
            Map<String, Object> responseWithNullScope = createValidTokenResponse(null, "3600");
            mockSuccessfulTokenInfoResponse(VALID_GOOGLE_TOKEN, responseWithNullScope);

            // When
            boolean result = tokenValidator.isValidGoogleToken(VALID_GOOGLE_TOKEN);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should reject token with empty scope")
        void shouldRejectTokenWithEmptyScope() throws Exception {
            // Given
            Map<String, Object> responseWithEmptyScope = createValidTokenResponse("", "3600");
            mockSuccessfulTokenInfoResponse(VALID_GOOGLE_TOKEN, responseWithEmptyScope);

            // When
            boolean result = tokenValidator.isValidGoogleToken(VALID_GOOGLE_TOKEN);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should handle scopes with extra whitespace")
        void shouldHandleScopesWithExtraWhitespace() throws Exception {
            // Given
            Map<String, Object> validResponse = createValidTokenResponse(
                "  https://www.googleapis.com/auth/gmail.readonly   https://www.googleapis.com/auth/userinfo.email  ",
                "3600"
            );
            mockSuccessfulTokenInfoResponse(VALID_GOOGLE_TOKEN, validResponse);

            // When
            boolean result = tokenValidator.isValidGoogleToken(VALID_GOOGLE_TOKEN);

            // Then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("TokenInfoResponse Object Tests")
    class TokenInfoResponseObjectTests {

        @Test
        @DisplayName("Should create TokenInfoResponse with all fields populated")
        void shouldCreateTokenInfoResponseWithAllFields() throws Exception {
            // Given
            Map<String, Object> completeResponse = createValidTokenResponse(
                "https://www.googleapis.com/auth/gmail.readonly",
                "3600"
            );
            mockSuccessfulTokenInfoResponse(VALID_GOOGLE_TOKEN, completeResponse);

            // When
            GoogleTokenValidator.TokenInfoResponse tokenInfo = tokenValidator.getTokenInfo(VALID_GOOGLE_TOKEN);

            // Then
            assertThat(tokenInfo.getScope()).isEqualTo("https://www.googleapis.com/auth/gmail.readonly");
            assertThat(tokenInfo.getExpiresIn()).isEqualTo("3600");
            assertThat(tokenInfo.getEmail()).isEqualTo("test@example.com");
            assertThat(tokenInfo.getUserId()).isEqualTo("123456789");
            assertThat(tokenInfo.getAudience()).isEqualTo("test-client-id");
            assertThat(tokenInfo.getAccessType()).isEqualTo("offline");
        }

        @Test
        @DisplayName("Should handle partial TokenInfoResponse")
        void shouldHandlePartialTokenInfoResponse() throws Exception {
            // Given
            Map<String, Object> partialResponse = new HashMap<>();
            partialResponse.put("scope", "https://www.googleapis.com/auth/gmail.readonly");
            partialResponse.put("email", "partial@example.com");
            // Missing: user_id, aud, expires_in, access_type
            mockSuccessfulTokenInfoResponse(VALID_GOOGLE_TOKEN, partialResponse);

            // When
            GoogleTokenValidator.TokenInfoResponse tokenInfo = tokenValidator.getTokenInfo(VALID_GOOGLE_TOKEN);

            // Then
            assertThat(tokenInfo.getScope()).isEqualTo("https://www.googleapis.com/auth/gmail.readonly");
            assertThat(tokenInfo.getEmail()).isEqualTo("partial@example.com");
            assertThat(tokenInfo.getUserId()).isNull();
            assertThat(tokenInfo.getAudience()).isNull();
            assertThat(tokenInfo.getExpiresIn()).isNull();
            assertThat(tokenInfo.getAccessType()).isNull();
        }

        @Test
        @DisplayName("Should generate proper toString representation")
        void shouldGenerateProperToStringRepresentation() throws Exception {
            // Given
            Map<String, Object> validResponse = createValidTokenResponse(
                "https://www.googleapis.com/auth/gmail.readonly",
                "3600"
            );
            mockSuccessfulTokenInfoResponse(VALID_GOOGLE_TOKEN, validResponse);

            // When
            GoogleTokenValidator.TokenInfoResponse tokenInfo = tokenValidator.getTokenInfo(VALID_GOOGLE_TOKEN);
            String toString = tokenInfo.toString();

            // Then
            assertThat(toString).contains("TokenInfoResponse{");
            assertThat(toString).contains("scope='https://www.googleapis.com/auth/gmail.readonly'");
            assertThat(toString).contains("email='test@example.com'");
            assertThat(toString).contains("userId='123456789'");
            assertThat(toString).contains("expiresIn='3600'");
        }
    }

    @Nested
    @DisplayName("Security Tests - Token Exposure Prevention")
    class SecurityTests {

        @Test
        @DisplayName("Should use POST method instead of GET to prevent token exposure in URL")
        void shouldUsePOSTMethodInsteadOfGET() throws Exception {
            // Given
            Map<String, Object> validResponse = createValidTokenResponse(
                "https://www.googleapis.com/auth/gmail.readonly",
                "3600"
            );
            mockSuccessfulTokenInfoResponse(VALID_GOOGLE_TOKEN, validResponse);

            // When
            tokenValidator.isValidGoogleToken(VALID_GOOGLE_TOKEN);

            // Then
            ArgumentCaptor<HttpMethod> methodCaptor = ArgumentCaptor.forClass(HttpMethod.class);
            verify(restTemplate).exchange(anyString(), methodCaptor.capture(), any(HttpEntity.class), eq(Map.class));

            assertThat(methodCaptor.getValue()).isEqualTo(HttpMethod.POST);
        }

        @Test
        @DisplayName("Should send token in request body with application/x-www-form-urlencoded content type")
        void shouldSendTokenInRequestBodyWithCorrectContentType() throws Exception {
            // Given
            Map<String, Object> validResponse = createValidTokenResponse(
                "https://www.googleapis.com/auth/gmail.readonly",
                "3600"
            );
            mockSuccessfulTokenInfoResponse(VALID_GOOGLE_TOKEN, validResponse);

            // When
            tokenValidator.isValidGoogleToken(VALID_GOOGLE_TOKEN);

            // Then
            ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
            verify(restTemplate).exchange(anyString(), any(HttpMethod.class), entityCaptor.capture(), eq(Map.class));

            HttpEntity<?> capturedEntity = entityCaptor.getValue();

            // Verify Content-Type header
            assertThat(capturedEntity.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_FORM_URLENCODED);

            // Verify token is in body, not URL
            MultiValueMap<String, String> body = (MultiValueMap<String, String>) capturedEntity.getBody();
            assertThat(body).isNotNull();
            assertThat(body.getFirst("access_token")).isEqualTo(VALID_GOOGLE_TOKEN);
        }

        @Test
        @DisplayName("Should not include token in URL parameters")
        void shouldNotIncludeTokenInURLParameters() throws Exception {
            // Given
            Map<String, Object> validResponse = createValidTokenResponse(
                "https://www.googleapis.com/auth/gmail.readonly",
                "3600"
            );
            mockSuccessfulTokenInfoResponse(VALID_GOOGLE_TOKEN, validResponse);

            // When
            tokenValidator.isValidGoogleToken(VALID_GOOGLE_TOKEN);

            // Then
            ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
            verify(restTemplate).exchange(urlCaptor.capture(), any(HttpMethod.class), any(HttpEntity.class), eq(Map.class));

            String capturedUrl = urlCaptor.getValue();

            // Verify URL does not contain token or query parameters
            assertThat(capturedUrl).isEqualTo(GOOGLE_TOKEN_INFO_URL);
            assertThat(capturedUrl).doesNotContain("access_token");
            assertThat(capturedUrl).doesNotContain(VALID_GOOGLE_TOKEN);
            assertThat(capturedUrl).doesNotContain("?");
        }

        @Test
        @DisplayName("Should handle different token formats securely in POST body")
        void shouldHandleDifferentTokenFormatsSecurelyInPOSTBody() throws Exception {
            // Given - Test with realistic Google OAuth2 token format
            String realisticToken = "ya29.a0ARrdaM-AbCdEfGhIjKlMnOpQrStUvWxYz123456789_abcdefghijklmnopqrstuvwxyz";
            Map<String, Object> validResponse = createValidTokenResponse(
                "https://www.googleapis.com/auth/gmail.readonly",
                "3600"
            );
            mockSuccessfulTokenInfoResponse(realisticToken, validResponse);

            // When
            tokenValidator.isValidGoogleToken(realisticToken);

            // Then
            ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
            verify(restTemplate).exchange(anyString(), any(HttpMethod.class), entityCaptor.capture(), eq(Map.class));

            HttpEntity<?> capturedEntity = entityCaptor.getValue();
            MultiValueMap<String, String> body = (MultiValueMap<String, String>) capturedEntity.getBody();

            assertThat(body.getFirst("access_token")).isEqualTo(realisticToken);
        }

        @Test
        @DisplayName("Should prevent token logging by using debug messages without token content")
        void shouldPreventTokenLoggingInDebugMessages() throws Exception {
            // Given
            Map<String, Object> validResponse = createValidTokenResponse(
                "https://www.googleapis.com/auth/gmail.readonly",
                "3600"
            );
            mockSuccessfulTokenInfoResponse(VALID_GOOGLE_TOKEN, validResponse);

            // When
            tokenValidator.isValidGoogleToken(VALID_GOOGLE_TOKEN);

            // Then - This test ensures the debug log doesn't contain sensitive token data
            // The actual logging verification would require a logging framework test,
            // but we can verify that the method completes without exposing tokens in the call signature
            ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
            verify(restTemplate).exchange(urlCaptor.capture(), any(HttpMethod.class), any(HttpEntity.class), eq(Map.class));

            // Verify the URL passed to RestTemplate doesn't contain token
            assertThat(urlCaptor.getValue()).doesNotContain(VALID_GOOGLE_TOKEN);
        }

        @Test
        @DisplayName("Should maintain security even with malformed tokens")
        void shouldMaintainSecurityEvenWithMalformedTokens() throws Exception {
            // Given - Token with special characters that could cause URL encoding issues
            String malformedToken = "invalid+token&with=special?chars#fragment";
            when(restTemplate.exchange(eq(GOOGLE_TOKEN_INFO_URL), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RestClientException("Invalid token format"));

            // When
            boolean result = tokenValidator.isValidGoogleToken(malformedToken);

            // Then
            assertThat(result).isFalse();

            // Verify token was still sent in POST body, not URL
            ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
            verify(restTemplate).exchange(eq(GOOGLE_TOKEN_INFO_URL), eq(HttpMethod.POST), entityCaptor.capture(), eq(Map.class));

            HttpEntity<?> capturedEntity = entityCaptor.getValue();
            MultiValueMap<String, String> body = (MultiValueMap<String, String>) capturedEntity.getBody();
            assertThat(body.getFirst("access_token")).isEqualTo(malformedToken);
        }

        @Test
        @DisplayName("Should use secure POST method in getTokenInfo as well")
        void shouldUseSecurePOSTMethodInGetTokenInfo() throws Exception {
            // Given
            Map<String, Object> validResponse = createValidTokenResponse(
                "https://www.googleapis.com/auth/gmail.readonly",
                "3600"
            );
            mockSuccessfulTokenInfoResponse(VALID_GOOGLE_TOKEN, validResponse);

            // When
            tokenValidator.getTokenInfo(VALID_GOOGLE_TOKEN);

            // Then
            ArgumentCaptor<HttpMethod> methodCaptor = ArgumentCaptor.forClass(HttpMethod.class);
            ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
            verify(restTemplate).exchange(anyString(), methodCaptor.capture(), entityCaptor.capture(), eq(Map.class));

            // Verify POST method is used
            assertThat(methodCaptor.getValue()).isEqualTo(HttpMethod.POST);

            // Verify token is in body
            HttpEntity<?> capturedEntity = entityCaptor.getValue();
            MultiValueMap<String, String> body = (MultiValueMap<String, String>) capturedEntity.getBody();
            assertThat(body.getFirst("access_token")).isEqualTo(VALID_GOOGLE_TOKEN);
        }
    }

    @Nested
    @DisplayName("Enhanced Error Handling and Timeout Tests")
    class EnhancedErrorHandlingTests {

        @Test
        @DisplayName("Should handle connection timeout with POST request gracefully")
        void shouldHandleConnectionTimeoutWithPOSTRequestGracefully() throws Exception {
            // Given
            when(restTemplate.exchange(eq(GOOGLE_TOKEN_INFO_URL), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RestClientException("Read timed out"));

            // When
            boolean result = tokenValidator.isValidGoogleToken(VALID_GOOGLE_TOKEN);

            // Then
            assertThat(result).isFalse();

            // Verify the request was made with POST and proper headers
            ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
            verify(restTemplate).exchange(eq(GOOGLE_TOKEN_INFO_URL), eq(HttpMethod.POST), entityCaptor.capture(), eq(Map.class));

            HttpEntity<?> capturedEntity = entityCaptor.getValue();
            assertThat(capturedEntity.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_FORM_URLENCODED);
        }

        @Test
        @DisplayName("Should handle SSL/TLS errors with POST request")
        void shouldHandleSSLTLSErrorsWithPOSTRequest() throws Exception {
            // Given
            when(restTemplate.exchange(eq(GOOGLE_TOKEN_INFO_URL), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RestClientException("SSL handshake failed"));

            // When
            boolean result = tokenValidator.isValidGoogleToken(VALID_GOOGLE_TOKEN);

            // Then
            assertThat(result).isFalse();
            verifyTokenInfoRequest(VALID_GOOGLE_TOKEN);
        }

        @Test
        @DisplayName("Should handle HTTP 500 server errors with POST request")
        void shouldHandleHTTP500ServerErrorsWithPOSTRequest() throws Exception {
            // Given
            mockErrorResponse(VALID_GOOGLE_TOKEN, HttpStatus.INTERNAL_SERVER_ERROR);

            // When
            boolean result = tokenValidator.isValidGoogleToken(VALID_GOOGLE_TOKEN);

            // Then
            assertThat(result).isFalse();
            verifyTokenInfoRequest(VALID_GOOGLE_TOKEN);
        }

        @Test
        @DisplayName("Should handle HTTP 503 service unavailable with POST request")
        void shouldHandleHTTP503ServiceUnavailableWithPOSTRequest() throws Exception {
            // Given
            mockErrorResponse(VALID_GOOGLE_TOKEN, HttpStatus.SERVICE_UNAVAILABLE);

            // When
            boolean result = tokenValidator.isValidGoogleToken(VALID_GOOGLE_TOKEN);

            // Then
            assertThat(result).isFalse();
            verifyTokenInfoRequest(VALID_GOOGLE_TOKEN);
        }
    }

    // Helper methods

    private Map<String, Object> createValidTokenResponse(String scope, String expiresIn) {
        Map<String, Object> response = new HashMap<>();
        response.put("scope", scope);
        response.put("aud", "test-client-id");
        response.put("user_id", "123456789");
        response.put("email", "test@example.com");
        response.put("access_type", "offline");
        if (expiresIn != null) {
            response.put("expires_in", expiresIn);
        }
        return response;
    }

    private void mockSuccessfulTokenInfoResponse(String token, Map<String, Object> responseBody) throws Exception {
        ResponseEntity<Map> responseEntity = new ResponseEntity<>(responseBody, HttpStatus.OK);

        when(restTemplate.exchange(eq(GOOGLE_TOKEN_INFO_URL), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
            .thenReturn(responseEntity);
    }

    private void mockErrorResponse(String token, HttpStatus status) throws Exception {
        ResponseEntity<Map> errorResponse = new ResponseEntity<>(new HashMap<>(), status);

        when(restTemplate.exchange(eq(GOOGLE_TOKEN_INFO_URL), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
            .thenReturn(errorResponse);
    }

    private void verifyTokenInfoRequest(String token) {
        verify(restTemplate).exchange(eq(GOOGLE_TOKEN_INFO_URL), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class));
    }
}