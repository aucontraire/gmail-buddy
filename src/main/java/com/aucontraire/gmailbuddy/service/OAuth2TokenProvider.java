package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import com.aucontraire.gmailbuddy.exception.AuthenticationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Map;

/**
 * OAuth2 implementation of TokenProvider supporting dual authentication modes.
 *
 * This service encapsulates all OAuth2 token management logic, including token retrieval,
 * validation, and refresh operations. It supports both browser-based OAuth2 flows and
 * API client Bearer token authentication, providing a bridge between the repository layer
 * and Spring Security's OAuth2 infrastructure.
 *
 * @author Gmail Buddy Team
 * @since Sprint 2
 */
@Service
public class OAuth2TokenProvider implements TokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2TokenProvider.class);
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final GmailBuddyProperties properties;
    private final GoogleTokenValidator tokenValidator;

    public OAuth2TokenProvider(OAuth2AuthorizedClientService authorizedClientService,
                              GmailBuddyProperties properties,
                              GoogleTokenValidator tokenValidator) {
        this.authorizedClientService = authorizedClientService;
        this.properties = properties;
        this.tokenValidator = tokenValidator;
    }
    
    @Override
    public String getAccessToken() throws AuthenticationException {
        return getTokenFromContext();
    }
    
    @Override
    public String getAccessToken(String userId) throws AuthenticationException {
        logger.debug("Retrieving access token for user: {}", userId);
        
        try {
            OAuth2AuthorizedClient client = getAuthorizedClient(userId);
            OAuth2AccessToken accessToken = client.getAccessToken();
            
            // Check if token is expired
            if (accessToken.getExpiresAt() != null && 
                accessToken.getExpiresAt().isBefore(Instant.now())) {
                logger.warn("Access token is expired for user: {}", userId);
                throw new AuthenticationException("Access token is expired. Please re-authenticate.");
            }
            
            String tokenValue = accessToken.getTokenValue();
            logger.debug("Successfully retrieved access token for user: {}", userId);
            return tokenValue;
            
        } catch (Exception e) {
            logger.error("Failed to retrieve access token for user: {}", userId, e);
            throw new AuthenticationException("Failed to retrieve access token: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean isTokenValid() {
        try {
            String principalName = getCurrentPrincipalName();
            return isTokenValid(principalName);
        } catch (AuthenticationException e) {
            logger.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public boolean isTokenValid(String userId) {
        try {
            OAuth2AuthorizedClient client = getAuthorizedClient(userId);
            OAuth2AccessToken accessToken = client.getAccessToken();
            
            // Check if token exists and is not expired
            return accessToken != null && 
                   (accessToken.getExpiresAt() == null || 
                    accessToken.getExpiresAt().isAfter(Instant.now()));
                    
        } catch (Exception e) {
            logger.debug("Token validation failed for user {}: {}", userId, e.getMessage());
            return false;
        }
    }
    
    @Override
    public void refreshTokenIfNeeded() throws AuthenticationException {
        String principalName = getCurrentPrincipalName();
        refreshTokenIfNeeded(principalName);
    }
    
    @Override
    public void refreshTokenIfNeeded(String userId) throws AuthenticationException {
        // Note: In current Spring Security OAuth2 Client implementation,
        // token refresh is handled automatically by the OAuth2AuthorizedClientService
        // when loadAuthorizedClient is called and the token is expired.
        // This method is a placeholder for explicit refresh logic if needed in the future.
        
        if (!isTokenValid(userId)) {
            logger.info("Token is invalid for user: {}, attempting refresh", userId);
            
            // The refresh will happen automatically on next getAuthorizedClient call
            // due to Spring Security OAuth2 Client's built-in refresh mechanism
            try {
                getAuthorizedClient(userId);
                logger.info("Token refresh completed for user: {}", userId);
            } catch (Exception e) {
                logger.error("Token refresh failed for user: {}", userId, e);
                throw new AuthenticationException("Token refresh failed: " + e.getMessage(), e);
            }
        }
    }
    
    @Override
    public String getCurrentPrincipalName() throws AuthenticationException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationException("No authenticated user found in security context");
        }
        
        String principalName = authentication.getName();
        if (principalName == null || principalName.trim().isEmpty()) {
            throw new AuthenticationException("Principal name is null or empty");
        }
        
        logger.debug("Current principal name: {}", principalName);
        return principalName;
    }
    
    @Override
    public String getBearerToken() throws AuthenticationException {
        HttpServletRequest request = getCurrentHttpRequest();
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new AuthenticationException("No Bearer token found in Authorization header");
        }

        String token = authHeader.substring(7);
        if (token.trim().isEmpty()) {
            throw new AuthenticationException("Bearer token is empty");
        }

        logger.debug("Successfully extracted Bearer token from Authorization header");
        return token;
    }

    @Override
    public boolean isValidBearerToken(String token) {
        return tokenValidator.isValidGoogleToken(token);
    }

    @Override
    public String getTokenFromContext() throws AuthenticationException {
        Exception lastException = null;

        // First, try to extract Bearer token from HTTP request headers (API clients)
        try {
            String bearerToken = getBearerToken();
            if (isValidBearerToken(bearerToken)) {
                logger.debug("Successfully authenticated using Bearer token");
                return bearerToken;
            } else {
                logger.debug("Bearer token validation failed");
            }
        } catch (Exception e) {
            logger.debug("Bearer token authentication failed: {}", e.getMessage());
            lastException = e;
        }

        // Second, check if we have API client authentication from our custom filter
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() &&
                authentication.getAuthorities().stream().anyMatch(auth -> auth.getAuthority().equals("ROLE_API_USER"))) {
                // API client authenticated via our custom filter, use the token stored as credentials
                Object credentials = authentication.getCredentials();
                if (credentials instanceof String) {
                    String token = (String) credentials;
                    logger.debug("Successfully retrieved token from API client authentication");
                    return token;
                }
            }
        } catch (Exception e) {
            logger.debug("API client authentication token extraction failed: {}", e.getMessage());
            lastException = e;
        }

        // Finally, fallback to OAuth2 SecurityContext for browser sessions
        try {
            String principalName = getCurrentPrincipalName();
            String oauth2Token = getAccessToken(principalName);
            logger.debug("Successfully authenticated using OAuth2 context");
            return oauth2Token;
        } catch (Exception e) {
            logger.error("All authentication methods failed. Bearer: {}, API Client: {}, OAuth2: {}",
                        lastException != null ? lastException.getMessage() : "None",
                        "No API client authentication found",
                        e.getMessage());
            throw new AuthenticationException("Authentication failed: No valid authentication method found", e);
        }
    }

    /**
     * Retrieves the current HttpServletRequest from the request context.
     *
     * @return the current HttpServletRequest
     * @throws AuthenticationException if no request context is available
     */
    private HttpServletRequest getCurrentHttpRequest() throws AuthenticationException {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new AuthenticationException("No HTTP request context available");
        }
        return attributes.getRequest();
    }

    /**
     * Retrieves the OAuth2AuthorizedClient for the specified user.
     *
     * @param principalName the principal name (username)
     * @return the OAuth2AuthorizedClient
     * @throws AuthenticationException if client retrieval fails
     */
    private OAuth2AuthorizedClient getAuthorizedClient(String principalName) throws AuthenticationException {
        String registrationId = properties.oauth2().clientRegistrationId();

        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
            registrationId,
            principalName
        );

        if (client == null) {
            logger.error("OAuth2AuthorizedClient is null for clientRegistrationId: {}, principalName: {}",
                        registrationId, principalName);
            throw new AuthenticationException(
                String.format("OAuth2AuthorizedClient not found for user '%s' with registration '%s'. " +
                             "Please re-authenticate.", principalName, registrationId)
            );
        }

        logger.debug("Successfully retrieved OAuth2AuthorizedClient for user: {}", principalName);
        return client;
    }
}