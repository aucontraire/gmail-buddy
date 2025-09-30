package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.exception.AuthenticationException;

/**
 * Abstraction for OAuth2 token management and retrieval supporting dual authentication modes.
 *
 * This interface decouples the repository layer from Spring Security's SecurityContextHolder,
 * making the code more testable and maintainable by providing a clean abstraction for
 * token operations. It supports both browser-based OAuth2 flows and API client Bearer token authentication.
 *
 * @author Gmail Buddy Team
 * @since Sprint 2
 */
public interface TokenProvider {
    
    /**
     * Retrieves the OAuth2 access token for the currently authenticated user.
     * 
     * @return the OAuth2 access token
     * @throws AuthenticationException if the user is not authenticated, token is expired,
     *                               or token retrieval fails
     */
    String getAccessToken() throws AuthenticationException;
    
    /**
     * Retrieves the OAuth2 access token for the specified user.
     * 
     * @param userId the user identifier
     * @return the OAuth2 access token for the specified user
     * @throws AuthenticationException if the user is not authenticated, token is expired,
     *                               or token retrieval fails
     */
    String getAccessToken(String userId) throws AuthenticationException;
    
    /**
     * Checks if the current user's token is valid and not expired.
     * 
     * @return true if the token is valid, false otherwise
     */
    boolean isTokenValid();
    
    /**
     * Checks if the specified user's token is valid and not expired.
     * 
     * @param userId the user identifier
     * @return true if the token is valid, false otherwise
     */
    boolean isTokenValid(String userId);
    
    /**
     * Refreshes the OAuth2 token if it's expired or about to expire.
     * This method should be called automatically by getAccessToken() if needed.
     * 
     * @throws AuthenticationException if token refresh fails
     */
    void refreshTokenIfNeeded() throws AuthenticationException;
    
    /**
     * Refreshes the OAuth2 token for the specified user if it's expired or about to expire.
     * 
     * @param userId the user identifier
     * @throws AuthenticationException if token refresh fails
     */
    void refreshTokenIfNeeded(String userId) throws AuthenticationException;
    
    /**
     * Gets the principal name (username) of the currently authenticated user.
     *
     * @return the principal name
     * @throws AuthenticationException if no user is authenticated
     */
    String getCurrentPrincipalName() throws AuthenticationException;

    /**
     * Retrieves the access token from Bearer authentication header.
     * This method enables API client authentication using Authorization: Bearer tokens.
     *
     * @return the Bearer token if present and valid
     * @throws AuthenticationException if Bearer token is missing, invalid, or expired
     */
    String getBearerToken() throws AuthenticationException;

    /**
     * Validates a Bearer token against Google's token validation endpoint.
     *
     * @param token the Bearer token to validate
     * @return true if the token is valid and has required Gmail scopes, false otherwise
     */
    boolean isValidBearerToken(String token);

    /**
     * Detects the current authentication context and returns the appropriate token.
     * This method first attempts Bearer token authentication for API clients,
     * then falls back to OAuth2 SecurityContext for browser sessions.
     *
     * @return the access token from the detected authentication method
     * @throws AuthenticationException if both authentication methods fail
     */
    String getTokenFromContext() throws AuthenticationException;
}