package com.aucontraire.gmailbuddy.exception;

import java.util.UUID;

/**
 * Base exception class for all Gmail Buddy application exceptions.
 * Provides a consistent foundation for error handling with correlation ID support.
 * 
 * <p>This is a RuntimeException to avoid checked exception handling overhead
 * while providing structured error information for better debugging and monitoring.</p>
 * 
 * @author Gmail Buddy Team
 * @since 1.0
 */
public abstract class GmailBuddyException extends RuntimeException {

    private final String errorCode;
    private final String correlationId;

    /**
     * Constructs a new Gmail Buddy exception with the specified error code and message.
     * 
     * @param errorCode a unique error code for this exception type
     * @param message the detail message
     */
    protected GmailBuddyException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.correlationId = UUID.randomUUID().toString();
    }

    /**
     * Constructs a new Gmail Buddy exception with the specified error code, message and cause.
     * 
     * @param errorCode a unique error code for this exception type
     * @param message the detail message
     * @param cause the cause of this exception
     */
    protected GmailBuddyException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.correlationId = UUID.randomUUID().toString();
    }

    /**
     * Constructs a new Gmail Buddy exception with the specified error code, message and correlation ID.
     * This constructor is useful when you need to preserve correlation ID across exception transformations.
     * 
     * @param errorCode a unique error code for this exception type
     * @param message the detail message
     * @param correlationId the correlation ID for request tracing
     */
    protected GmailBuddyException(String errorCode, String message, String correlationId) {
        super(message);
        this.errorCode = errorCode;
        this.correlationId = correlationId;
    }

    /**
     * Gets the error code for this exception.
     * 
     * @return the error code
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Gets the correlation ID for this exception.
     * This can be used for request tracing and debugging.
     * 
     * @return the correlation ID
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * Indicates whether this exception represents a client error (4xx HTTP status).
     * 
     * @return true if this is a client error, false otherwise
     */
    public abstract boolean isClientError();

    /**
     * Gets the appropriate HTTP status code for this exception.
     * 
     * @return the HTTP status code
     */
    public abstract int getHttpStatus();
}