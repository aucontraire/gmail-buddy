package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.exception.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Set;

/**
 * Service for validating Google OAuth2 tokens using Google's TokenInfo endpoint.
 *
 * This service replaces JWT validation since Google OAuth2 tokens are opaque (not JWT format).
 * It validates tokens against Google's tokeninfo endpoint and verifies required Gmail scopes.
 *
 * @author Gmail Buddy Team
 * @since Sprint 2 - Phase 2 OAuth2 Security Context Decoupling
 */
@Service
public class GoogleTokenValidator {

    private static final Logger logger = LoggerFactory.getLogger(GoogleTokenValidator.class);

    /**
     * Google's TokenInfo endpoint for validating OAuth2 access tokens.
     * This endpoint accepts opaque tokens (ya29.a0ARrdaM...) unlike JWT decoders.
     */
    private static final String GOOGLE_TOKEN_INFO_URL = "https://www.googleapis.com/oauth2/v1/tokeninfo";

    /**
     * Required Gmail scopes for API access.
     * At least one of these scopes must be present in the validated token.
     */
    private static final Set<String> REQUIRED_GMAIL_SCOPES = Set.of(
        "https://www.googleapis.com/auth/gmail.readonly",
        "https://www.googleapis.com/auth/gmail.modify",
        "https://mail.google.com/"
    );

    private final RestTemplate restTemplate;

    public GoogleTokenValidator(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Validates a Google OAuth2 access token and verifies Gmail scopes.
     *
     * @param accessToken the Google OAuth2 access token to validate
     * @return true if the token is valid and has required Gmail scopes
     */
    public boolean isValidGoogleToken(String accessToken) {
        if (accessToken == null || accessToken.trim().isEmpty()) {
            logger.debug("Token validation failed: token is null or empty");
            return false;
        }

        try {
            TokenInfoResponse tokenInfo = validateTokenWithGoogle(accessToken);

            if (tokenInfo == null) {
                logger.debug("Token validation failed: Google returned null response");
                return false;
            }

            boolean hasValidScopes = validateGmailScopes(tokenInfo.getScope());
            if (!hasValidScopes) {
                logger.debug("Token validation failed: missing required Gmail scopes. Current scopes: {}",
                           tokenInfo.getScope());
                return false;
            }

            logger.debug("Token validation successful. Scopes: {}, expires in: {} seconds",
                        tokenInfo.getScope(), tokenInfo.getExpiresIn());
            return true;

        } catch (Exception e) {
            logger.debug("Token validation failed with exception: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validates token with Google's TokenInfo endpoint and returns token information.
     *
     * @param accessToken the access token to validate
     * @return TokenInfoResponse containing token details, or null if validation fails
     */
    public TokenInfoResponse getTokenInfo(String accessToken) {
        if (accessToken == null || accessToken.trim().isEmpty()) {
            throw new AuthenticationException("Access token cannot be null or empty");
        }

        try {
            return validateTokenWithGoogle(accessToken);
        } catch (Exception e) {
            logger.error("Failed to get token info for token validation: {}", e.getMessage());
            throw new AuthenticationException("Token validation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Internal method to call Google's TokenInfo endpoint.
     *
     * SECURITY: Uses POST with token in request body to prevent token exposure in URL parameters,
     * logs, browser history, and HTTP referrer headers.
     *
     * @param accessToken the access token to validate
     * @return TokenInfoResponse containing token details
     * @throws Exception if the HTTP call fails or returns invalid response
     */
    private TokenInfoResponse validateTokenWithGoogle(String accessToken) throws Exception {
        // Create POST request with token in body to prevent URL exposure
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("access_token", accessToken);

        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);

        logger.debug("Validating token with Google TokenInfo endpoint using secure POST method");
        ResponseEntity<Map> response = restTemplate.exchange(GOOGLE_TOKEN_INFO_URL, HttpMethod.POST, requestEntity, Map.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            logger.debug("Google TokenInfo returned non-success status: {}", response.getStatusCode());
            return null;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null) {
            logger.debug("Google TokenInfo returned empty response body");
            return null;
        }

        // Check for error in response (Google returns error details in the response body)
        if (responseBody.containsKey("error")) {
            logger.debug("Google TokenInfo returned error: {}", responseBody.get("error"));
            return null;
        }

        return mapToTokenInfoResponse(responseBody);
    }

    /**
     * Maps Google's TokenInfo response to our internal TokenInfoResponse object.
     *
     * @param responseBody the response body from Google's TokenInfo endpoint
     * @return TokenInfoResponse object
     */
    private TokenInfoResponse mapToTokenInfoResponse(Map<String, Object> responseBody) {
        TokenInfoResponse tokenInfo = new TokenInfoResponse();

        tokenInfo.setScope((String) responseBody.get("scope"));
        tokenInfo.setAudience((String) responseBody.get("aud"));
        tokenInfo.setUserId((String) responseBody.get("user_id"));
        tokenInfo.setEmail((String) responseBody.get("email"));

        // Handle expires_in as either String or Integer
        Object expiresIn = responseBody.get("expires_in");
        if (expiresIn instanceof String) {
            tokenInfo.setExpiresIn((String) expiresIn);
        } else if (expiresIn instanceof Integer) {
            tokenInfo.setExpiresIn(String.valueOf(expiresIn));
        }

        tokenInfo.setAccessType((String) responseBody.get("access_type"));

        return tokenInfo;
    }

    /**
     * Validates that the token has at least one required Gmail scope.
     *
     * @param scope space-separated list of scopes from the token
     * @return true if at least one required Gmail scope is present
     */
    private boolean validateGmailScopes(String scope) {
        if (scope == null || scope.trim().isEmpty()) {
            return false;
        }

        // Google returns scopes as space-separated string
        String[] scopes = scope.split("\\s+");

        for (String tokenScope : scopes) {
            if (REQUIRED_GMAIL_SCOPES.contains(tokenScope.trim())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Response object for Google's TokenInfo endpoint.
     */
    public static class TokenInfoResponse {
        private String scope;
        private String audience;
        private String userId;
        private String email;
        private String expiresIn;
        private String accessType;

        // Getters and setters
        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }

        public String getAudience() { return audience; }
        public void setAudience(String audience) { this.audience = audience; }

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getExpiresIn() { return expiresIn; }
        public void setExpiresIn(String expiresIn) { this.expiresIn = expiresIn; }

        public String getAccessType() { return accessType; }
        public void setAccessType(String accessType) { this.accessType = accessType; }

        @Override
        public String toString() {
            return "TokenInfoResponse{" +
                    "scope='" + scope + '\'' +
                    ", audience='" + audience + '\'' +
                    ", userId='" + userId + '\'' +
                    ", email='" + email + '\'' +
                    ", expiresIn='" + expiresIn + '\'' +
                    ", accessType='" + accessType + '\'' +
                    '}';
        }
    }
}