package com.aucontraire.gmailbuddy.exception;

/**
 * Base class for client-side errors (4xx HTTP status codes).
 * These exceptions indicate that the client made an error in the request.
 * 
 * @author Gmail Buddy Team
 * @since 1.0
 */
public abstract class GmailBuddyClientException extends GmailBuddyException {

    /**
     * Constructs a new client exception with the specified error code and message.
     * 
     * @param errorCode a unique error code for this exception type
     * @param message the detail message
     */
    protected GmailBuddyClientException(String errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * Constructs a new client exception with the specified error code, message and cause.
     * 
     * @param errorCode a unique error code for this exception type
     * @param message the detail message
     * @param cause the cause of this exception
     */
    protected GmailBuddyClientException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    /**
     * Constructs a new client exception with the specified error code, message and correlation ID.
     * 
     * @param errorCode a unique error code for this exception type
     * @param message the detail message
     * @param correlationId the correlation ID for request tracing
     */
    protected GmailBuddyClientException(String errorCode, String message, String correlationId) {
        super(errorCode, message, correlationId);
    }

    @Override
    public final boolean isClientError() {
        return true;
    }
}