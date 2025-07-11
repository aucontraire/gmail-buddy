package com.aucontraire.gmailbuddy.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when authorization fails.
 * This includes insufficient permissions and forbidden operations.
 * 
 * @author Gmail Buddy Team
 * @since 1.0
 */
public class AuthorizationException extends GmailBuddyClientException {

    private static final String ERROR_CODE = "AUTHORIZATION_ERROR";

    /**
     * Constructs a new authorization exception with the specified message.
     * 
     * @param message the detail message explaining the authorization failure
     */
    public AuthorizationException(String message) {
        super(ERROR_CODE, message);
    }

    /**
     * Constructs a new authorization exception with the specified message and cause.
     * 
     * @param message the detail message explaining the authorization failure
     * @param cause the underlying cause of the authorization failure
     */
    public AuthorizationException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }

    /**
     * Constructs a new authorization exception with the specified message and correlation ID.
     * 
     * @param message the detail message explaining the authorization failure
     * @param correlationId the correlation ID for request tracing
     */
    public AuthorizationException(String message, String correlationId) {
        super(ERROR_CODE, message, correlationId);
    }

    @Override
    public int getHttpStatus() {
        return HttpStatus.FORBIDDEN.value();
    }
}