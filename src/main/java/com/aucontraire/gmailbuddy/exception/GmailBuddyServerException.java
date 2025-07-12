package com.aucontraire.gmailbuddy.exception;

/**
 * Base class for server-side errors (5xx HTTP status codes).
 * These exceptions indicate that the server encountered an error while processing the request.
 * 
 * @author Gmail Buddy Team
 * @since 1.0
 */
public abstract class GmailBuddyServerException extends GmailBuddyException {

    /**
     * Constructs a new server exception with the specified error code and message.
     * 
     * @param errorCode a unique error code for this exception type
     * @param message the detail message
     */
    protected GmailBuddyServerException(String errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * Constructs a new server exception with the specified error code, message and cause.
     * 
     * @param errorCode a unique error code for this exception type
     * @param message the detail message
     * @param cause the cause of this exception
     */
    protected GmailBuddyServerException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    /**
     * Constructs a new server exception with the specified error code, message and correlation ID.
     * 
     * @param errorCode a unique error code for this exception type
     * @param message the detail message
     * @param correlationId the correlation ID for request tracing
     */
    protected GmailBuddyServerException(String errorCode, String message, String correlationId) {
        super(errorCode, message, correlationId);
    }

    @Override
    public final boolean isClientError() {
        return false;
    }
}