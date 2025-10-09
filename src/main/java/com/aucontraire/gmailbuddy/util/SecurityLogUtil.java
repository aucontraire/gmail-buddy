package com.aucontraire.gmailbuddy.util;

import org.springframework.util.StringUtils;

/**
 * Utility class for secure logging that prevents sensitive data exposure.
 *
 * This utility provides methods to safely log authentication tokens, credentials,
 * and other sensitive information by masking the sensitive parts while maintaining
 * enough information for debugging purposes.
 *
 * <p>Security Features:
 * <ul>
 *   <li>Masks OAuth2 tokens showing only first 4 and last 4 characters</li>
 *   <li>Handles null and empty token values safely</li>
 *   <li>Supports various token formats (Bearer, OAuth2, JWT-style)</li>
 *   <li>Provides consistent masking patterns across the application</li>
 * </ul>
 *
 * <p>Usage Example:
 * <pre>{@code
 * logger.debug("Retrieved access token: {}", SecurityLogUtil.maskToken(accessToken));
 * logger.info("Bearer token authentication successful: {}", SecurityLogUtil.maskToken(bearerToken));
 * }</pre>
 *
 * @author Gmail Buddy Team
 * @since Sprint 2
 */
public final class SecurityLogUtil {

    private static final String MASK_PATTERN = "****";
    private static final int VISIBLE_PREFIX_LENGTH = 4;
    private static final int VISIBLE_SUFFIX_LENGTH = 4;
    private static final int MINIMUM_TOKEN_LENGTH_FOR_MASKING = 12;

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private SecurityLogUtil() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    /**
     * Masks an authentication token for safe logging.
     *
     * <p>The masking strategy depends on the token length:
     * <ul>
     *   <li>Null or empty tokens: returns "[NULL_TOKEN]" or "[EMPTY_TOKEN]"</li>
     *   <li>Short tokens (< 12 chars): completely masked as "[MASKED]"</li>
     *   <li>Long tokens: shows first 4 and last 4 characters with "****" in between</li>
     * </ul>
     *
     * <p>Examples:
     * <ul>
     *   <li>Input: "ya29.a0AfH6SMC..." → Output: "ya29****H6SM"</li>
     *   <li>Input: "short" → Output: "[MASKED]"</li>
     *   <li>Input: null → Output: "[NULL_TOKEN]"</li>
     * </ul>
     *
     * @param token the authentication token to mask
     * @return a safely masked representation of the token
     */
    public static String maskToken(String token) {
        if (token == null) {
            return "[NULL_TOKEN]";
        }

        if (token.trim().isEmpty()) {
            return "[EMPTY_TOKEN]";
        }

        // For security, completely mask short tokens
        if (token.length() < MINIMUM_TOKEN_LENGTH_FOR_MASKING) {
            return "[MASKED]";
        }

        // For longer tokens, show prefix and suffix with masking in between
        String prefix = token.substring(0, VISIBLE_PREFIX_LENGTH);
        String suffix = token.substring(token.length() - VISIBLE_SUFFIX_LENGTH);

        return prefix + MASK_PATTERN + suffix;
    }

    /**
     * Masks a Bearer token specifically, handling the "Bearer " prefix.
     *
     * <p>This method is designed for Authorization header values that include
     * the "Bearer " prefix. It extracts the actual token part and masks it
     * while preserving the Bearer prefix for debugging context.
     *
     * <p>Examples:
     * <ul>
     *   <li>Input: "Bearer ya29.a0AfH6SMC..." → Output: "Bearer ya29****H6SM"</li>
     *   <li>Input: "ya29.a0AfH6SMC..." → Output: "ya29****H6SM"</li>
     * </ul>
     *
     * @param bearerToken the bearer token string (with or without "Bearer " prefix)
     * @return a safely masked bearer token representation
     */
    public static String maskBearerToken(String bearerToken) {
        if (!StringUtils.hasText(bearerToken)) {
            return maskToken(bearerToken);
        }

        if (bearerToken.startsWith("Bearer ")) {
            String actualToken = bearerToken.substring(7);
            return "Bearer " + maskToken(actualToken);
        }

        return maskToken(bearerToken);
    }

    /**
     * Creates a secure log message for authentication events.
     *
     * <p>This method provides a standardized way to log authentication-related
     * events while ensuring no sensitive data is exposed. It's particularly
     * useful for audit logging and debugging authentication flows.
     *
     * <p>Example:
     * <pre>{@code
     * logger.info(SecurityLogUtil.createSecureAuthMessage("OAuth2", userId, "ya29.token...", true));
     * // Output: "Authentication [OAuth2] for user [user@example.com] with token [ya29****ken] - SUCCESS"
     * }</pre>
     *
     * @param authType the type of authentication (e.g., "OAuth2", "Bearer", "API Key")
     * @param userId the user identifier (will not be masked)
     * @param token the authentication token (will be masked)
     * @param success whether the authentication was successful
     * @return a formatted secure log message
     */
    public static String createSecureAuthMessage(String authType, String userId, String token, boolean success) {
        String status = success ? "SUCCESS" : "FAILURE";
        String maskedToken = maskToken(token);

        return String.format("Authentication [%s] for user [%s] with token [%s] - %s",
                authType, userId, maskedToken, status);
    }

    /**
     * Masks sensitive request parameters for logging.
     *
     * <p>This method helps safely log request parameters that may contain
     * sensitive information. It identifies common sensitive parameter names
     * and masks their values.
     *
     * <p>Sensitive parameter names (case-insensitive):
     * <ul>
     *   <li>token, access_token, refresh_token</li>
     *   <li>password, passwd, pwd</li>
     *   <li>secret, client_secret</li>
     *   <li>key, api_key, apikey</li>
     *   <li>authorization, auth</li>
     * </ul>
     *
     * @param paramName the parameter name
     * @param paramValue the parameter value
     * @return the original value if not sensitive, otherwise a masked value
     */
    public static String maskSensitiveParameter(String paramName, String paramValue) {
        if (paramName == null || paramValue == null) {
            return paramValue;
        }

        String lowerParamName = paramName.toLowerCase();

        // List of sensitive parameter names
        String[] sensitiveNames = {
            "token", "access_token", "refresh_token", "id_token",
            "password", "passwd", "pwd",
            "secret", "client_secret",
            "key", "api_key", "apikey",
            "authorization", "auth"
        };

        for (String sensitiveName : sensitiveNames) {
            if (lowerParamName.contains(sensitiveName)) {
                return maskToken(paramValue);
            }
        }

        return paramValue;
    }
}