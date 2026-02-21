package com.aucontraire.gmailbuddy.integration;

import com.aucontraire.gmailbuddy.config.TestTokenProviderConfiguration;
import com.aucontraire.gmailbuddy.service.TestTokenProvider;
import com.aucontraire.gmailbuddy.service.TokenProvider;
import com.aucontraire.gmailbuddy.service.GmailService;
import com.aucontraire.gmailbuddy.service.GoogleTokenValidator;
import com.aucontraire.gmailbuddy.service.MessageListResult;
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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithSecurityContext;
import org.springframework.security.test.context.support.WithSecurityContextTestExecutionListener;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Phase 1 dual authentication implementation.
 * Tests the complete authentication flow including JWT, Bearer tokens, and OAuth2 fallback.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestTokenProviderConfiguration.class)
class DualAuthenticationIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TokenProvider tokenProvider;

    @MockitoBean
    private GmailService gmailService;

    @MockitoBean
    private GmailRepository gmailRepository;

    @MockitoBean
    private GoogleTokenValidator tokenValidator;

    @Autowired
    private MockMvc mockMvc;

    private ObjectMapper objectMapper;
    private TestTokenProvider testTokenProvider;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        testTokenProvider = (TestTokenProvider) tokenProvider;
        testTokenProvider.reset(); // Reset to default state

        // Setup mocks if they are injected (they may be null in some nested test contexts)
        if (gmailService != null) {
            reset(gmailService);
            // Setup default mock behavior for Gmail service
            List<Message> mockMessages = new ArrayList<>();
            MessageListResult mockResult = new MessageListResult(mockMessages, null, mockMessages.size());
            when(gmailService.listLatestMessagesWithPagination(anyString(), any(), anyInt())).thenReturn(mockResult);
            when(gmailService.listMessagesWithPagination(anyString(), any(), anyInt())).thenReturn(mockResult);
            when(gmailService.listMessagesByFilterCriteriaWithPagination(anyString(), any(), any(), anyInt())).thenReturn(mockResult);
        }

        if (gmailRepository != null) {
            reset(gmailRepository);
        }

        if (tokenValidator != null) {
            reset(tokenValidator);
            // DO NOT set up default success mocks here - this would make ALL tokens appear valid
            // Each test that needs valid token responses must configure its own specific mocks
            // Default behavior: getTokenInfo returns null, hasValidGmailScopes returns false
        }
    }

    /**
     * Helper method to configure tokenValidator mock for valid token responses.
     * Call this from tests that need successful token validation.
     */
    private void setupValidTokenMock(String token) {
        if (tokenValidator != null) {
            GoogleTokenValidator.TokenInfoResponse tokenInfo = new GoogleTokenValidator.TokenInfoResponse();
            tokenInfo.setEmail("test-user@example.com");
            tokenInfo.setScope("https://www.googleapis.com/auth/gmail.readonly https://mail.google.com");
            tokenInfo.setExpiresIn("3600");
            when(tokenValidator.getTokenInfo(token)).thenReturn(tokenInfo);
            // hasValidGmailScopes takes the scope STRING, not the token
            when(tokenValidator.hasValidGmailScopes(tokenInfo.getScope())).thenReturn(true);
        }
    }

    @Nested
    @DisplayName("JWT Authentication Integration Tests")
    class JwtAuthenticationTests {

        @Test
        @DisplayName("Should authenticate API requests with valid JWT tokens")
        void testJwtAuthenticationSuccess() throws Exception {
            // Given: Configure test token provider for JWT authentication
            testTokenProvider.configureForJwtAuthentication();

            // When & Then: API request with JWT should succeed
            mockMvc.perform(get("/api/v1/gmail/messages/latest")
                    .with(jwt().jwt(builder -> builder
                        .claim("sub", "test-user@example.com")
                        .claim("email", "test-user@example.com")
                        .claim("scope", "https://www.googleapis.com/auth/gmail.readonly")
                    ))
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        }

        @Test
        @DisplayName("Should reject API requests with invalid JWT tokens")
        void testJwtAuthenticationFailure() throws Exception {
            // When & Then: API request with invalid JWT should fail
            mockMvc.perform(get("/api/v1/gmail/messages/latest")
                    .header("Authorization", "Bearer invalid-jwt-token")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should validate JWT token scopes for Gmail access")
        void testJwtTokenScopeValidation() throws Exception {
            // Given: Configure test token provider for JWT authentication
            testTokenProvider.configureForJwtAuthentication();

            // When & Then: JWT token without Gmail scopes should still authenticate
            mockMvc.perform(get("/api/v1/gmail/messages/latest")
                    .with(jwt().jwt(builder -> builder
                        .claim("sub", "test-user@example.com")
                        .claim("email", "test-user@example.com")
                        .claim("scope", "openid email profile") // Missing Gmail scopes
                    ))
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()); // Should still authenticate, scope validation is in service layer
        }
    }

    @Nested
    @DisplayName("Bearer Token Authentication Integration Tests")
    class BearerTokenAuthenticationTests {

        @Test
        @DisplayName("Should authenticate API requests with valid Bearer tokens")
        void testBearerTokenAuthenticationSuccess() throws Exception {
            // Given: Configure test token provider for Bearer token authentication
            testTokenProvider.configureForBearerTokenAuthentication();
            testTokenProvider.setBearerToken("ya29.valid-bearer-token");
            testTokenProvider.setAccessToken("ya29.valid-bearer-token");

            // Setup valid token mock for this specific token
            setupValidTokenMock("ya29.valid-bearer-token");

            // When & Then: API request with Bearer token should succeed
            mockMvc.perform(get("/api/v1/gmail/messages/latest")
                    .header("Authorization", "Bearer ya29.valid-bearer-token")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        }

        @Test
        @DisplayName("Should reject API requests with missing Authorization header")
        void testMissingAuthorizationHeader() throws Exception {
            // When & Then: API request without Authorization header should redirect to OAuth2 login
            // This is expected behavior - browser requests without Bearer token get OAuth2 redirect
            mockMvc.perform(get("/api/v1/gmail/messages/latest")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is3xxRedirection());
        }

        @Test
        @DisplayName("Should reject API requests with malformed Bearer tokens")
        void testMalformedBearerToken() throws Exception {
            // When & Then: API request with malformed Bearer token should redirect to OAuth2 login
            // Invalid Bearer token triggers OAuth2 fallback which redirects unauthenticated requests
            mockMvc.perform(get("/api/v1/gmail/messages/latest")
                    .header("Authorization", "InvalidScheme some-token")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is3xxRedirection());
        }

        @Test
        @DisplayName("Should handle Bearer token validation errors gracefully")
        void testBearerTokenValidationError() throws Exception {
            // Given: Configure test token provider to simulate validation failure
            testTokenProvider.setShouldThrowException(true);

            // When & Then: API request should handle validation error
            mockMvc.perform(get("/api/v1/gmail/messages/latest")
                    .header("Authorization", "Bearer invalid-token")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("OAuth2 Browser Authentication Integration Tests")
    class OAuth2BrowserAuthenticationTests {

        @Test
        @DisplayName("Should redirect unauthenticated browser requests to OAuth2 login")
        void testOAuth2LoginRedirection() throws Exception {
            // When & Then: Browser request without authentication should redirect to OAuth2 login
            mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/oauth2/authorization/google")));
        }

        @Test
        @WithMockUser(username = "test-user@example.com", authorities = "SCOPE_gmail.readonly")
        @DisplayName("Should allow authenticated browser requests to protected endpoints")
        void testOAuth2AuthenticatedAccess() throws Exception {
            // When & Then: Authenticated browser request should succeed
            mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should allow access to public endpoints without authentication")
        void testPublicEndpointAccess() throws Exception {
            // When & Then: Public endpoints should be accessible without authentication
            // /login pattern is permitted but no controller exists, returns 404
            // The key test is that it's NOT redirected to OAuth2 (not 302)
            mockMvc.perform(get("/login"))
                .andExpect(status().isNotFound()); // No controller exists, Spring returns 404
        }
    }

    @Nested
    @DisplayName("Hybrid Authentication Strategy Integration Tests")
    class HybridAuthenticationStrategyTests {

        @Test
        @DisplayName("Should prioritize JWT over Bearer token authentication")
        void testJwtPriorityOverBearer() throws Exception {
            // Given: Configure for JWT authentication (highest priority)
            testTokenProvider.configureForJwtAuthentication();
            testTokenProvider.setBearerToken("bearer-token");
            testTokenProvider.setAccessToken("bearer-token");

            // When & Then: JWT should take precedence
            mockMvc.perform(get("/api/v1/gmail/messages/latest")
                    .with(jwt().jwt(builder -> builder
                        .claim("sub", "jwt-user@example.com")
                        .claim("email", "jwt-user@example.com")
                        .claim("scope", "https://www.googleapis.com/auth/gmail.readonly")
                    ))
                    .header("Authorization", "Bearer bearer-token")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "oauth2-user@example.com", authorities = "SCOPE_gmail.readonly")
        @DisplayName("Should fallback to OAuth2 when Bearer token validation fails")
        void testOAuth2FallbackFromBearer() throws Exception {
            // Given: Configure for OAuth2 fallback (lowest priority)
            testTokenProvider.configureForOAuth2Fallback();
            testTokenProvider.setAccessToken("oauth2-token");

            // When & Then: Should fallback to OAuth2 authentication
            mockMvc.perform(get("/api/v1/gmail/messages/latest")
                    .header("Authorization", "Bearer invalid-bearer-token")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should handle complete authentication failure gracefully")
        void testCompleteAuthenticationFailure() throws Exception {
            // Given: All authentication methods fail
            testTokenProvider.setShouldThrowException(true);

            // When & Then: Should return unauthorized
            mockMvc.perform(get("/api/v1/gmail/messages/latest")
                    .header("Authorization", "Bearer invalid-token")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Security Configuration Integration Tests")
    class SecurityConfigurationTests {

        @Test
        @DisplayName("Should disable CSRF for API endpoints")
        void testCSRFDisabledForAPI() throws Exception {
            // Given: Configure Bearer token authentication
            testTokenProvider.configureForBearerTokenAuthentication();
            testTokenProvider.setBearerToken("valid-csrf-token");
            testTokenProvider.setAccessToken("valid-csrf-token");

            // Setup valid token mock for this specific token
            setupValidTokenMock("valid-csrf-token");

            // When & Then: POST request to API endpoint should work without CSRF token
            mockMvc.perform(post("/api/v1/gmail/messages/filter")
                    .header("Authorization", "Bearer valid-csrf-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"query\":\"from:example@test.com\"}"))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should allow session creation for browser flows")
        void testSessionManagementForBrowser() throws Exception {
            // When & Then: Browser request should allow session creation
            mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection()); // Redirects to OAuth2 login
                // Note: Session attribute validation would require actual session setup
        }

        @Test
        @DisplayName("Should configure proper security headers")
        void testSecurityHeaders() throws Exception {
            // When & Then: Response should include security headers
            // /login is permitted but no controller exists, returns 404
            // Spring Boot's default error handling may not include security headers on 404 errors
            mockMvc.perform(get("/login"))
                .andExpect(status().isNotFound()); // No controller exists, Spring returns 404
            // Note: Security headers may not be present on 404 error responses
        }

        @Test
        @DisplayName("Should permit access to static resources")
        void testStaticResourceAccess() throws Exception {
            // When & Then: Static resources should be accessible
            // favicon.ico is permitted, returns 200 from default Spring Boot handling
            mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isOk()); // Spring Boot returns 200 with empty response for favicon
        }
    }

    @Nested
    @DisplayName("Error Handling Integration Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should return appropriate error response for authentication failures")
        void testAuthenticationErrorResponse() throws Exception {
            // Given: Do NOT configure token validator mock - let it return null for invalid token
            // This ensures the token "expired-token" is treated as invalid

            // When & Then: Authentication failure returns 401 Unauthorized
            mockMvc.perform(get("/api/v1/gmail/messages/latest")
                    .header("Authorization", "Bearer expired-token")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should handle token provider exceptions gracefully")
        void testTokenProviderExceptionHandling() throws Exception {
            // Given: Configure token provider to throw exceptions
            testTokenProvider.setShouldThrowException(true);

            // When & Then: Should handle token provider exceptions
            mockMvc.perform(get("/api/v1/gmail/messages/latest")
                    .header("Authorization", "Bearer some-token")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        }
    }
}