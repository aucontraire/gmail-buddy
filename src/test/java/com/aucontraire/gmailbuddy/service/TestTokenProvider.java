package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.exception.AuthenticationException;

/**
 * Test implementation of TokenProvider for integration tests.
 *
 * This provides a simple, controllable token provider that can be used
 * in integration tests without requiring a full OAuth2 setup. Supports
 * all Phase 1 authentication methods including Bearer tokens and JWT tokens.
 */
public class TestTokenProvider implements TokenProvider {

    private String accessToken = "test-access-token-123";
    private String bearerToken = "test-bearer-token-456";
    private String jwtToken = "test-jwt-token-789";
    private boolean tokenValid = true;
    private boolean bearerTokenValid = true;
    private String principalName = "testuser@example.com";
    private boolean shouldThrowException = false;
    private boolean simulateJwtAuthentication = false;
    private boolean simulateBearerTokenAuthentication = false;
    
    @Override
    public String getAccessToken() throws AuthenticationException {
        if (shouldThrowException) {
            throw new AuthenticationException("Test authentication failure");
        }
        return accessToken;
    }
    
    @Override
    public String getAccessToken(String userId) throws AuthenticationException {
        if (shouldThrowException) {
            throw new AuthenticationException("Test authentication failure for user: " + userId);
        }
        return accessToken;
    }
    
    @Override
    public boolean isTokenValid() {
        return tokenValid;
    }
    
    @Override
    public boolean isTokenValid(String userId) {
        return tokenValid;
    }
    
    @Override
    public void refreshTokenIfNeeded() throws AuthenticationException {
        if (shouldThrowException) {
            throw new AuthenticationException("Test token refresh failure");
        }
        // No-op for test implementation
    }
    
    @Override
    public void refreshTokenIfNeeded(String userId) throws AuthenticationException {
        if (shouldThrowException) {
            throw new AuthenticationException("Test token refresh failure for user: " + userId);
        }
        // No-op for test implementation
    }
    
    @Override
    public String getCurrentPrincipalName() throws AuthenticationException {
        if (shouldThrowException) {
            throw new AuthenticationException("Test principal name retrieval failure");
        }
        return principalName;
    }

    @Override
    public String getBearerToken() throws AuthenticationException {
        if (shouldThrowException) {
            throw new AuthenticationException("Test Bearer token retrieval failure");
        }
        return bearerToken;
    }

    @Override
    public boolean isValidBearerToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        return bearerTokenValid && bearerToken.equals(token);
    }

    @Override
    public String getTokenFromContext() throws AuthenticationException {
        if (shouldThrowException) {
            throw new AuthenticationException("Test token context retrieval failure");
        }

        // Simulate the hybrid authentication strategy priority:
        // 1. JWT Authentication (highest priority)
        if (simulateJwtAuthentication) {
            return jwtToken;
        }

        // 2. Bearer token authentication
        if (simulateBearerTokenAuthentication && bearerTokenValid) {
            return bearerToken;
        }

        // 3. OAuth2 fallback (lowest priority)
        return accessToken;
    }
    
    // Test utility methods for Phase 1 dual authentication testing

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public void setBearerToken(String bearerToken) {
        this.bearerToken = bearerToken;
    }

    public void setJwtToken(String jwtToken) {
        this.jwtToken = jwtToken;
    }

    public void setTokenValid(boolean tokenValid) {
        this.tokenValid = tokenValid;
    }

    public void setBearerTokenValid(boolean bearerTokenValid) {
        this.bearerTokenValid = bearerTokenValid;
    }

    public void setPrincipalName(String principalName) {
        this.principalName = principalName;
    }

    public void setShouldThrowException(boolean shouldThrowException) {
        this.shouldThrowException = shouldThrowException;
    }

    /**
     * Simulates JWT authentication being present in the security context.
     * When enabled, getTokenFromContext() will return the JWT token.
     */
    public void setSimulateJwtAuthentication(boolean simulateJwtAuthentication) {
        this.simulateJwtAuthentication = simulateJwtAuthentication;
    }

    /**
     * Simulates Bearer token authentication being available.
     * When enabled and JWT is not simulated, getTokenFromContext() will return the Bearer token.
     */
    public void setSimulateBearerTokenAuthentication(boolean simulateBearerTokenAuthentication) {
        this.simulateBearerTokenAuthentication = simulateBearerTokenAuthentication;
    }

    /**
     * Configures the test provider to simulate successful JWT authentication.
     * This sets up the highest priority authentication method.
     */
    public void configureForJwtAuthentication() {
        this.simulateJwtAuthentication = true;
        this.simulateBearerTokenAuthentication = false;
        this.tokenValid = true;
        this.bearerTokenValid = true;
        this.shouldThrowException = false;
    }

    /**
     * Configures the test provider to simulate successful Bearer token authentication.
     * This sets up the second priority authentication method.
     */
    public void configureForBearerTokenAuthentication() {
        this.simulateJwtAuthentication = false;
        this.simulateBearerTokenAuthentication = true;
        this.tokenValid = true;
        this.bearerTokenValid = true;
        this.shouldThrowException = false;
    }

    /**
     * Configures the test provider to simulate OAuth2 fallback authentication.
     * This sets up the lowest priority authentication method.
     */
    public void configureForOAuth2Fallback() {
        this.simulateJwtAuthentication = false;
        this.simulateBearerTokenAuthentication = false;
        this.tokenValid = true;
        this.bearerTokenValid = false;
        this.shouldThrowException = false;
    }

    /**
     * Configures the test provider to simulate complete authentication failure.
     */
    public void configureForAuthenticationFailure() {
        this.simulateJwtAuthentication = false;
        this.simulateBearerTokenAuthentication = false;
        this.tokenValid = false;
        this.bearerTokenValid = false;
        this.shouldThrowException = true;
    }

    public void reset() {
        this.accessToken = "test-access-token-123";
        this.bearerToken = "test-bearer-token-456";
        this.jwtToken = "test-jwt-token-789";
        this.tokenValid = true;
        this.bearerTokenValid = true;
        this.principalName = "testuser@example.com";
        this.shouldThrowException = false;
        this.simulateJwtAuthentication = false;
        this.simulateBearerTokenAuthentication = false;
    }
}