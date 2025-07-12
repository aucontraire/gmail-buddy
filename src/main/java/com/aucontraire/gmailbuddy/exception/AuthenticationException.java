package com.aucontraire.gmailbuddy.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when authentication fails.
 * This includes OAuth2 token expiry, invalid tokens, and missing authentication.
 * 
 * @author Gmail Buddy Team
 * @since 1.0
 */
public class AuthenticationException extends GmailBuddyClientException {

    private static final String ERROR_CODE = "AUTHENTICATION_ERROR";

    /**
     * Constructs a new authentication exception with the specified message.
     * 
     * @param message the detail message explaining the authentication failure
     */
    public AuthenticationException(String message) {
        super(ERROR_CODE, message);
    }

    /**
     * Constructs a new authentication exception with the specified message and cause.
     * 
     * @param message the detail message explaining the authentication failure
     * @param cause the underlying cause of the authentication failure
     */
    public AuthenticationException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }

    /**
     * Constructs a new authentication exception with the specified message and correlation ID.
     * 
     * @param message the detail message explaining the authentication failure
     * @param correlationId the correlation ID for request tracing
     */
    public AuthenticationException(String message, String correlationId) {
        super(ERROR_CODE, message, correlationId);
    }

    @Override
    public int getHttpStatus() {
        return HttpStatus.UNAUTHORIZED.value();
    }
}