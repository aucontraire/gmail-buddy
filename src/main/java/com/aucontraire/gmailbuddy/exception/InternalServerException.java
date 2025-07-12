package com.aucontraire.gmailbuddy.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown for unexpected internal server errors.
 * This represents unhandled exceptions and programming errors that
 * should not be exposed to clients in detail.
 * 
 * @author Gmail Buddy Team
 * @since 1.0
 */
public class InternalServerException extends GmailBuddyServerException {

    private static final String ERROR_CODE = "INTERNAL_SERVER_ERROR";

    /**
     * Constructs a new internal server exception with a generic message.
     * The actual cause details are logged but not exposed to the client.
     */
    public InternalServerException() {
        super(ERROR_CODE, "An unexpected error occurred while processing your request");
    }

    /**
     * Constructs a new internal server exception with the specified cause.
     * The cause details are logged but a generic message is shown to the client.
     * 
     * @param cause the underlying cause of the internal error
     */
    public InternalServerException(Throwable cause) {
        super(ERROR_CODE, "An unexpected error occurred while processing your request", cause);
    }

    /**
     * Constructs a new internal server exception with the specified message and cause.
     * This constructor should be used when you want to provide a specific internal message.
     * 
     * @param message the internal error message (for logging)
     * @param cause the underlying cause of the internal error
     */
    public InternalServerException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }

    /**
     * Constructs a new internal server exception with the specified message and correlation ID.
     * 
     * @param message the internal error message
     * @param correlationId the correlation ID for request tracing
     */
    public InternalServerException(String message, String correlationId) {
        super(ERROR_CODE, message, correlationId);
    }

    @Override
    public int getHttpStatus() {
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }
}