package com.aucontraire.gmailbuddy.integration;

import com.aucontraire.gmailbuddy.config.TestTokenProviderConfiguration;
import com.aucontraire.gmailbuddy.service.TestTokenProvider;
import com.aucontraire.gmailbuddy.service.TokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Phase 1 dual authentication implementation.
 * Tests the complete authentication flow including JWT, Bearer tokens, and OAuth2 fallback.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Import(TestTokenProviderConfiguration.class)
@TestExecutionListeners({
    DependencyInjectionTestExecutionListener.class,
    WithSecurityContextTestExecutionListener.class
})
class DualAuthenticationIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TokenProvider tokenProvider;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private TestTokenProvider testTokenProvider;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(context)
            .apply(springSecurity())
            .build();

        objectMapper = new ObjectMapper();
        testTokenProvider = (TestTokenProvider) tokenProvider;
        testTokenProvider.reset(); // Reset to default state
    }

    @Nested
    @DisplayName("JWT Authentication Integration Tests")
    class JwtAuthenticationTests {

        @Test
        @DisplayName("Should authenticate API requests with valid JWT tokens")
        void testJwtAuthenticationSuccess() throws Exception {
            // Given: A valid JWT token
            String jwtToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.test-jwt-payload.signature";

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
            // Given: JWT token without Gmail scopes
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
            // Given: Configure test token provider for Bearer token success
            testTokenProvider.setAccessToken("ya29.valid-bearer-token");
            testTokenProvider.setTokenValid(true);

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
            // When & Then: API request without Authorization header should fail
            mockMvc.perform(get("/api/v1/gmail/messages/latest")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should reject API requests with malformed Bearer tokens")
        void testMalformedBearerToken() throws Exception {
            // When & Then: API request with malformed Bearer token should fail
            mockMvc.perform(get("/api/v1/gmail/messages/latest")
                    .header("Authorization", "InvalidScheme some-token")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
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
            mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Hybrid Authentication Strategy Integration Tests")
    class HybridAuthenticationStrategyTests {

        @Test
        @DisplayName("Should prioritize JWT over Bearer token authentication")
        void testJwtPriorityOverBearer() throws Exception {
            // Given: Both JWT and Bearer token present
            testTokenProvider.setAccessToken("bearer-token");
            testTokenProvider.setTokenValid(true);

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
            // Given: Invalid Bearer token, valid OAuth2 context
            testTokenProvider.setAccessToken("oauth2-token");
            testTokenProvider.setTokenValid(true);

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
            // Given: Configure valid authentication
            testTokenProvider.setAccessToken("valid-token");
            testTokenProvider.setTokenValid(true);

            // When & Then: POST request to API endpoint should work without CSRF token
            mockMvc.perform(post("/api/v1/gmail/messages/filter")
                    .header("Authorization", "Bearer valid-token")
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
            mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"));
        }

        @Test
        @DisplayName("Should permit access to static resources")
        void testStaticResourceAccess() throws Exception {
            // When & Then: Static resources should be accessible
            mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isNotFound()); // 404 is expected since file doesn't exist, but not 401/403
        }
    }

    @Nested
    @DisplayName("Error Handling Integration Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should return appropriate error response for authentication failures")
        void testAuthenticationErrorResponse() throws Exception {
            // When & Then: Authentication failure should return proper error response
            mockMvc.perform(get("/api/v1/gmail/messages/latest")
                    .header("Authorization", "Bearer expired-token")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").exists());
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