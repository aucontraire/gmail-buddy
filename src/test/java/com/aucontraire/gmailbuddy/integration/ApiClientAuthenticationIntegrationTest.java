package com.aucontraire.gmailbuddy.integration;

import com.aucontraire.gmailbuddy.service.GoogleTokenValidator;
import com.aucontraire.gmailbuddy.service.GmailService;
import com.aucontraire.gmailbuddy.repository.GmailRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.gmail.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration tests for API client authentication.
 *
 * Tests complete Postman-style authentication flows including:
 * - Full Bearer token authentication for Gmail API endpoints
 * - Error responses for invalid authentication
 * - OAuth2 fallback behavior for browser clients
 * - CSRF protection configuration for API vs browser requests
 * - Complete request/response cycles with realistic scenarios
 * - Multiple concurrent API client requests
 * - Token validation with Google TokenInfo endpoint simulation
 *
 * @author Gmail Buddy Team
 * @since Sprint 2 - Phase 2 OAuth2 Security Context Decoupling
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("API Client Authentication Integration")
class ApiClientAuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GoogleTokenValidator tokenValidator;

    @MockitoBean
    private GmailService gmailService;

    @MockitoBean
    private GmailRepository gmailRepository;

    private static final String VALID_GOOGLE_TOKEN = "ya29.a0ARrdaM-valid-google-token";
    private static final String INVALID_TOKEN = "invalid-token-format";
    private static final String EXPIRED_TOKEN = "ya29.a0ARrdaM-expired-token";
    private static final String MALFORMED_TOKEN = "malformed";

    private static final String API_BASE_PATH = "/api/v1/gmail";

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        reset(tokenValidator, gmailService, gmailRepository);
    }

    @Nested
    @DisplayName("Successful API Authentication Tests")
    class SuccessfulApiAuthenticationTests {

        @Test
        @DisplayName("Should authenticate and access Gmail messages endpoint with valid Bearer token")
        void shouldAuthenticateAndAccessGmailMessagesEndpointWithValidBearerToken() throws Exception {
            // Given
            mockValidTokenResponse();

            // When & Then
            mockMvc.perform(get(API_BASE_PATH + "/messages")
                    .header("Authorization", "Bearer " + VALID_GOOGLE_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

            verifyTokenValidation(VALID_GOOGLE_TOKEN);
        }

        @Test
        @DisplayName("Should authenticate and access Gmail messages filter endpoint with valid Bearer token")
        void shouldAuthenticateAndAccessGmailMessagesFilterEndpointWithValidBearerToken() throws Exception {
            // Given
            mockValidTokenResponse();
            String filterRequest = createFilterRequest();

            // When & Then
            mockMvc.perform(post(API_BASE_PATH + "/messages/filter")
                    .header("Authorization", "Bearer " + VALID_GOOGLE_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(filterRequest))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

            verifyTokenValidation(VALID_GOOGLE_TOKEN);
        }

        @Test
        @DisplayName("Should authenticate and access specific Gmail message endpoint")
        void shouldAuthenticateAndAccessSpecificGmailMessageEndpoint() throws Exception {
            // Given
            mockValidTokenResponse();
            String messageId = "1a2b3c4d5e6f7g8h";

            // When & Then
            mockMvc.perform(get(API_BASE_PATH + "/messages/" + messageId + "/body")
                    .header("Authorization", "Bearer " + VALID_GOOGLE_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
                // Note: Content-Type varies based on message body content

            verifyTokenValidation(VALID_GOOGLE_TOKEN);
        }

        @Test
        @DisplayName("Should authenticate and perform bulk delete operation")
        void shouldAuthenticateAndPerformBulkDeleteOperation() throws Exception {
            // Given
            mockValidTokenResponse();
            String deleteRequest = createDeleteFilterRequest();

            // When & Then
            mockMvc.perform(delete(API_BASE_PATH + "/messages/filter")
                    .header("Authorization", "Bearer " + VALID_GOOGLE_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(deleteRequest))
                .andExpect(status().isNoContent()); // 204 is correct for DELETE operations

            verifyTokenValidation(VALID_GOOGLE_TOKEN);
        }

        @Test
        @DisplayName("Should authenticate and modify labels on messages")
        void shouldAuthenticateAndModifyLabelsOnMessages() throws Exception {
            // Given
            mockValidTokenResponse();
            String labelModificationRequest = createLabelModificationRequest();

            // When & Then
            mockMvc.perform(post(API_BASE_PATH + "/messages/filter/modifyLabels")
                    .header("Authorization", "Bearer " + VALID_GOOGLE_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(labelModificationRequest))
                .andExpect(status().isNoContent()); // 204 is correct when no response body is returned

            verifyTokenValidation(VALID_GOOGLE_TOKEN);
        }

        @Test
        @DisplayName("Should handle multiple concurrent API requests with same token")
        void shouldHandleMultipleConcurrentApiRequestsWithSameToken() throws Exception {
            // Given
            mockValidTokenResponse();

            // When & Then - Multiple requests should all succeed
            for (int i = 0; i < 5; i++) {
                mockMvc.perform(get(API_BASE_PATH + "/messages/latest")
                        .header("Authorization", "Bearer " + VALID_GOOGLE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON));
            }

            verify(tokenValidator, times(5)).getTokenInfo(VALID_GOOGLE_TOKEN);
            verify(tokenValidator, times(5)).hasValidGmailScopes(anyString());
        }
    }

    @Nested
    @DisplayName("Authentication Failure Tests")
    class AuthenticationFailureTests {

        @Test
        @DisplayName("Should reject request with invalid Bearer token")
        void shouldRejectRequestWithInvalidBearerToken() throws Exception {
            // Given
            when(tokenValidator.getTokenInfo(INVALID_TOKEN))
                .thenThrow(new com.aucontraire.gmailbuddy.exception.AuthenticationException("Invalid token"));

            // When & Then
            mockMvc.perform(get(API_BASE_PATH + "/messages")
                    .header("Authorization", "Bearer " + INVALID_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

            verify(tokenValidator).getTokenInfo(INVALID_TOKEN);
        }

        @Test
        @DisplayName("Should reject request with expired Bearer token")
        void shouldRejectRequestWithExpiredBearerToken() throws Exception {
            // Given
            when(tokenValidator.getTokenInfo(EXPIRED_TOKEN))
                .thenThrow(new com.aucontraire.gmailbuddy.exception.AuthenticationException("Token expired"));

            // When & Then
            mockMvc.perform(get(API_BASE_PATH + "/messages")
                    .header("Authorization", "Bearer " + EXPIRED_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

            verify(tokenValidator).getTokenInfo(EXPIRED_TOKEN);
        }

        @Test
        @DisplayName("Should reject request with malformed Authorization header")
        void shouldRejectRequestWithMalformedAuthorizationHeader() throws Exception {
            // When & Then
            mockMvc.perform(get(API_BASE_PATH + "/messages")
                    .header("Authorization", "Malformed " + VALID_GOOGLE_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/oauth2/authorization/google"));

            verifyNoInteractions(tokenValidator);
        }

        @Test
        @DisplayName("Should reject request with missing Authorization header")
        void shouldRejectRequestWithMissingAuthorizationHeader() throws Exception {
            // When & Then
            mockMvc.perform(get(API_BASE_PATH + "/messages")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/oauth2/authorization/google"));

            verifyNoInteractions(tokenValidator);
        }

        @Test
        @DisplayName("Should reject request with empty Bearer token")
        void shouldRejectRequestWithEmptyBearerToken() throws Exception {
            // Given - empty token will trigger validation which fails
            when(tokenValidator.getTokenInfo(""))
                .thenThrow(new com.aucontraire.gmailbuddy.exception.AuthenticationException("Empty token"));

            // When & Then
            mockMvc.perform(get(API_BASE_PATH + "/messages")
                    .header("Authorization", "Bearer ")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

            verify(tokenValidator).getTokenInfo("");
        }

        @Test
        @DisplayName("Should handle token validation service failures gracefully")
        void shouldHandleTokenValidationServiceFailuresGracefully() throws Exception {
            // Given
            when(tokenValidator.getTokenInfo(VALID_GOOGLE_TOKEN))
                .thenThrow(new RuntimeException("Google TokenInfo service unavailable"));

            // When & Then
            mockMvc.perform(get(API_BASE_PATH + "/messages")
                    .header("Authorization", "Bearer " + VALID_GOOGLE_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

            verify(tokenValidator).getTokenInfo(VALID_GOOGLE_TOKEN);
        }
    }

    @Nested
    @DisplayName("CSRF Protection Tests")
    class CsrfProtectionTests {

        @Test
        @DisplayName("Should disable CSRF for API POST requests with valid Bearer token")
        void shouldDisableCsrfForApiPostRequestsWithValidBearerToken() throws Exception {
            // Given
            mockValidTokenResponse();
            String filterRequest = createFilterRequest();

            // When & Then - POST request should work without CSRF token
            mockMvc.perform(post(API_BASE_PATH + "/messages/filter")
                    .header("Authorization", "Bearer " + VALID_GOOGLE_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(filterRequest))
                .andExpect(status().isOk());

            verifyTokenValidation(VALID_GOOGLE_TOKEN);
        }

        @Test
        @DisplayName("Should disable CSRF for API DELETE requests with valid Bearer token")
        void shouldDisableCsrfForApiDeleteRequestsWithValidBearerToken() throws Exception {
            // Given
            mockValidTokenResponse();
            String deleteRequest = createDeleteFilterRequest();

            // When & Then - DELETE request should work without CSRF token
            mockMvc.perform(delete(API_BASE_PATH + "/messages/filter")
                    .header("Authorization", "Bearer " + VALID_GOOGLE_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(deleteRequest))
                .andExpect(status().isNoContent()); // 204 is correct for DELETE operations

            verifyTokenValidation(VALID_GOOGLE_TOKEN);
        }

        @Test
        @DisplayName("Should disable CSRF for API PUT requests with valid Bearer token")
        void shouldDisableCsrfForApiPutRequestsWithValidBearerToken() throws Exception {
            // Given
            mockValidTokenResponse();
            String messageId = "1a2b3c4d5e6f7g8h";

            // When & Then - PUT request should work without CSRF token
            mockMvc.perform(put(API_BASE_PATH + "/messages/" + messageId + "/read")
                    .header("Authorization", "Bearer " + VALID_GOOGLE_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent()); // 204 is correct for PUT operations with no response body

            verifyTokenValidation(VALID_GOOGLE_TOKEN);
        }
    }

    @Nested
    @DisplayName("OAuth2 Fallback Tests")
    class OAuth2FallbackTests {

        @Test
        @DisplayName("Should fallback to OAuth2 for browser requests without Bearer token")
        void shouldFallbackToOAuth2ForBrowserRequestsWithoutBearerToken() throws Exception {
            // When & Then
            mockMvc.perform(get(API_BASE_PATH + "/messages")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/oauth2/authorization/google"));

            verifyNoInteractions(tokenValidator);
        }

        @Test
        @DisplayName("Should not process Bearer tokens for non-API endpoints")
        void shouldNotProcessBearerTokensForNonApiEndpoints() throws Exception {
            // When & Then
            mockMvc.perform(get("/dashboard")
                    .header("Authorization", "Bearer " + VALID_GOOGLE_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/oauth2/authorization/google"));

            verifyNoInteractions(tokenValidator);
        }
    }

    @Nested
    @DisplayName("Token Validation Edge Cases Tests")
    class TokenValidationEdgeCasesTests {

        @Test
        @DisplayName("Should handle token with insufficient Gmail scopes")
        void shouldHandleTokenWithInsufficientGmailScopes() throws Exception {
            // Given - Valid token but without Gmail scopes
            GoogleTokenValidator.TokenInfoResponse tokenInfo = createValidTokenInfo();
            tokenInfo.setScope("openid email profile"); // No Gmail scopes
            when(tokenValidator.getTokenInfo(VALID_GOOGLE_TOKEN)).thenReturn(tokenInfo);
            when(tokenValidator.hasValidGmailScopes(tokenInfo.getScope())).thenReturn(false);

            // When & Then
            mockMvc.perform(get(API_BASE_PATH + "/messages")
                    .header("Authorization", "Bearer " + VALID_GOOGLE_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

            verify(tokenValidator).getTokenInfo(VALID_GOOGLE_TOKEN);
            verify(tokenValidator).hasValidGmailScopes(tokenInfo.getScope());
        }

        @Test
        @DisplayName("Should handle very long Bearer tokens")
        void shouldHandleVeryLongBearerTokens() throws Exception {
            // Given
            String longToken = "ya29.a0ARrdaM-" + "x".repeat(1000); // Very long token
            GoogleTokenValidator.TokenInfoResponse tokenInfo = createValidTokenInfo();
            when(tokenValidator.getTokenInfo(longToken)).thenReturn(tokenInfo);
            when(tokenValidator.hasValidGmailScopes(tokenInfo.getScope())).thenReturn(true);
            when(gmailService.listMessages(anyString())).thenReturn(new ArrayList<>());

            // When & Then
            mockMvc.perform(get(API_BASE_PATH + "/messages")
                    .header("Authorization", "Bearer " + longToken)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

            verify(tokenValidator).getTokenInfo(longToken);
        }

        @Test
        @DisplayName("Should handle Bearer tokens with special characters")
        void shouldHandleBearerTokensWithSpecialCharacters() throws Exception {
            // Given
            String tokenWithSpecialChars = "ya29.a0ARrdaM-token_with-special.chars";
            GoogleTokenValidator.TokenInfoResponse tokenInfo = createValidTokenInfo();
            when(tokenValidator.getTokenInfo(tokenWithSpecialChars)).thenReturn(tokenInfo);
            when(tokenValidator.hasValidGmailScopes(tokenInfo.getScope())).thenReturn(true);
            when(gmailService.listMessages(anyString())).thenReturn(new ArrayList<>());

            // When & Then
            mockMvc.perform(get(API_BASE_PATH + "/messages")
                    .header("Authorization", "Bearer " + tokenWithSpecialChars)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

            verify(tokenValidator).getTokenInfo(tokenWithSpecialChars);
        }
    }

    @Nested
    @DisplayName("Real-world Postman Simulation Tests")
    class RealWorldPostmanSimulationTests {

        @Test
        @DisplayName("Should simulate complete Postman workflow for Gmail message retrieval")
        void shouldSimulateCompletePostmanWorkflowForGmailMessageRetrieval() throws Exception {
            // Given - Simulate real Postman request
            mockValidTokenResponse();

            // When & Then - GET all messages
            mockMvc.perform(get(API_BASE_PATH + "/messages")
                    .header("Authorization", "Bearer " + VALID_GOOGLE_TOKEN)
                    .header("User-Agent", "PostmanRuntime/7.29.2")
                    .header("Accept", "*/*")
                    .header("Cache-Control", "no-cache")
                    .header("Host", "localhost")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Connection", "keep-alive"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

            verifyTokenValidation(VALID_GOOGLE_TOKEN);
        }

        @Test
        @DisplayName("Should simulate Postman filter request with complex criteria")
        void shouldSimulatePostmanFilterRequestWithComplexCriteria() throws Exception {
            // Given
            mockValidTokenResponse();
            String complexFilterRequest = createComplexFilterRequest();

            // When & Then
            mockMvc.perform(post(API_BASE_PATH + "/messages/filter")
                    .header("Authorization", "Bearer " + VALID_GOOGLE_TOKEN)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "PostmanRuntime/7.29.2")
                    .content(complexFilterRequest))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

            verifyTokenValidation(VALID_GOOGLE_TOKEN);
        }

        @Test
        @DisplayName("Should simulate Postman bulk operations workflow")
        void shouldSimulatePostmanBulkOperationsWorkflow() throws Exception {
            // Given
            mockValidTokenResponse();

            // Step 1: Filter messages
            String filterRequest = createFilterRequest();
            mockMvc.perform(post(API_BASE_PATH + "/messages/filter")
                    .header("Authorization", "Bearer " + VALID_GOOGLE_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(filterRequest))
                .andExpect(status().isOk());

            // Step 2: Modify labels on filtered messages
            String labelModRequest = createLabelModificationRequest();
            mockMvc.perform(post(API_BASE_PATH + "/messages/filter/modifyLabels")
                    .header("Authorization", "Bearer " + VALID_GOOGLE_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(labelModRequest))
                .andExpect(status().isNoContent()); // 204 is correct when no response body is returned

            // Step 3: Bulk delete filtered messages
            String deleteRequest = createDeleteFilterRequest();
            mockMvc.perform(delete(API_BASE_PATH + "/messages/filter")
                    .header("Authorization", "Bearer " + VALID_GOOGLE_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(deleteRequest))
                .andExpect(status().isNoContent()); // 204 is correct for DELETE operations

            verify(tokenValidator, times(3)).getTokenInfo(VALID_GOOGLE_TOKEN);
            verify(tokenValidator, times(3)).hasValidGmailScopes(anyString());
        }
    }

    // Helper methods

    private void mockValidTokenResponse() throws Exception {
        GoogleTokenValidator.TokenInfoResponse tokenInfo = createValidTokenInfo();
        when(tokenValidator.getTokenInfo(VALID_GOOGLE_TOKEN)).thenReturn(tokenInfo);
        when(tokenValidator.hasValidGmailScopes(tokenInfo.getScope())).thenReturn(true);

        // Mock GmailService to return empty list (we're testing auth, not Gmail functionality)
        when(gmailService.listMessages(anyString())).thenReturn(new ArrayList<>());
        when(gmailService.listLatestMessages(anyString(), anyInt())).thenReturn(new ArrayList<>());
        when(gmailService.listMessagesByFilterCriteria(anyString(), any())).thenReturn(new ArrayList<>());
        when(gmailService.getMessageBody(anyString(), anyString())).thenReturn("");
        doNothing().when(gmailService).deleteMessage(anyString(), anyString());
        doNothing().when(gmailService).deleteMessagesByFilterCriteria(anyString(), any());
    }

    private void verifyTokenValidation(String token) {
        verify(tokenValidator).getTokenInfo(token);
        verify(tokenValidator).hasValidGmailScopes(anyString());
    }

    private GoogleTokenValidator.TokenInfoResponse createValidTokenInfo() {
        GoogleTokenValidator.TokenInfoResponse tokenInfo = new GoogleTokenValidator.TokenInfoResponse();
        tokenInfo.setEmail("api-user@example.com");
        tokenInfo.setUserId("123456789");
        tokenInfo.setScope("https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/gmail.modify");
        tokenInfo.setAudience("test-client-id");
        tokenInfo.setExpiresIn("3600");
        tokenInfo.setAccessType("offline");
        return tokenInfo;
    }

    private String createFilterRequest() throws Exception {
        Map<String, Object> filter = new HashMap<>();
        filter.put("from", "sender@example.com");
        filter.put("subject", "Test Subject");
        filter.put("maxResults", 50);
        return objectMapper.writeValueAsString(filter);
    }

    private String createComplexFilterRequest() throws Exception {
        Map<String, Object> filter = new HashMap<>();
        filter.put("from", "automated@example.com");
        filter.put("subject", "Daily Report");
        filter.put("hasAttachment", true);
        filter.put("isUnread", true);
        filter.put("maxResults", 100);
        filter.put("after", "2024/01/01");
        filter.put("before", "2024/12/31");
        return objectMapper.writeValueAsString(filter);
    }

    private String createDeleteFilterRequest() throws Exception {
        Map<String, Object> deleteFilter = new HashMap<>();
        deleteFilter.put("from", "spam@example.com");
        deleteFilter.put("isUnread", false);
        deleteFilter.put("maxResults", 10);
        return objectMapper.writeValueAsString(deleteFilter);
    }

    private String createLabelModificationRequest() throws Exception {
        Map<String, Object> labelMod = new HashMap<>();

        // Filter criteria
        Map<String, Object> filterCriteria = new HashMap<>();
        filterCriteria.put("from", "newsletter@example.com");
        filterCriteria.put("maxResults", 25);

        // Label modifications
        Map<String, Object> labelModifications = new HashMap<>();
        labelModifications.put("addLabelIds", new String[]{"INBOX", "IMPORTANT"});
        labelModifications.put("removeLabelIds", new String[]{"UNREAD"});

        labelMod.put("filterCriteria", filterCriteria);
        labelMod.put("labelModifications", labelModifications);

        return objectMapper.writeValueAsString(labelMod);
    }
}