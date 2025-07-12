package com.aucontraire.gmailbuddy.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a requested resource cannot be found.
 * This includes messages, labels, or other Gmail resources that don't exist.
 * 
 * @author Gmail Buddy Team
 * @since 1.0
 */
public class ResourceNotFoundException extends GmailBuddyClientException {

    private static final String ERROR_CODE = "RESOURCE_NOT_FOUND";

    /**
     * Constructs a new resource not found exception with the specified message.
     * 
     * @param message the detail message explaining what resource was not found
     */
    public ResourceNotFoundException(String message) {
        super(ERROR_CODE, message);
    }

    /**
     * Constructs a new resource not found exception with the specified message and cause.
     * 
     * @param message the detail message explaining what resource was not found
     * @param cause the underlying cause
     */
    public ResourceNotFoundException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }

    /**
     * Constructs a new resource not found exception with the specified message and correlation ID.
     * 
     * @param message the detail message explaining what resource was not found
     * @param correlationId the correlation ID for request tracing
     */
    public ResourceNotFoundException(String message, String correlationId) {
        super(ERROR_CODE, message, correlationId);
    }

    @Override
    public int getHttpStatus() {
        return HttpStatus.NOT_FOUND.value();
    }
}