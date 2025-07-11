package com.aucontraire.gmailbuddy.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when request validation fails.
 * This includes both bean validation failures and business rule validation.
 * 
 * @author Gmail Buddy Team
 * @since 1.0
 */
public class ValidationException extends GmailBuddyClientException {

    private static final String ERROR_CODE = "VALIDATION_ERROR";

    /**
     * Constructs a new validation exception with the specified message.
     * 
     * @param message the detail message explaining the validation failure
     */
    public ValidationException(String message) {
        super(ERROR_CODE, message);
    }

    /**
     * Constructs a new validation exception with the specified message and cause.
     * 
     * @param message the detail message explaining the validation failure
     * @param cause the underlying cause of the validation failure
     */
    public ValidationException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }

    /**
     * Constructs a new validation exception with the specified message and correlation ID.
     * 
     * @param message the detail message explaining the validation failure
     * @param correlationId the correlation ID for request tracing
     */
    public ValidationException(String message, String correlationId) {
        super(ERROR_CODE, message, correlationId);
    }

    @Override
    public int getHttpStatus() {
        return HttpStatus.BAD_REQUEST.value();
    }
}