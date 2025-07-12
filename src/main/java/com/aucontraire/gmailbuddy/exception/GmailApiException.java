package com.aucontraire.gmailbuddy.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when the Gmail API returns an error.
 * This represents failures in communication with the Gmail service
 * that are not client errors.
 * 
 * @author Gmail Buddy Team
 * @since 1.0
 */
public class GmailApiException extends GmailBuddyServerException {

    private static final String ERROR_CODE = "GMAIL_API_ERROR";

    private final boolean retryable;

    /**
     * Constructs a new Gmail API exception with the specified message.
     * 
     * @param message the detail message explaining the API failure
     */
    public GmailApiException(String message) {
        super(ERROR_CODE, message);
        this.retryable = false;
    }

    /**
     * Constructs a new Gmail API exception with the specified message and retry flag.
     * 
     * @param message the detail message explaining the API failure
     * @param retryable whether this operation can be retried
     */
    public GmailApiException(String message, boolean retryable) {
        super(ERROR_CODE, message);
        this.retryable = retryable;
    }

    /**
     * Constructs a new Gmail API exception with the specified message and cause.
     * 
     * @param message the detail message explaining the API failure
     * @param cause the underlying cause of the API failure
     */
    public GmailApiException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
        this.retryable = determineRetryability(cause);
    }

    /**
     * Constructs a new Gmail API exception with the specified message, cause and retry flag.
     * 
     * @param message the detail message explaining the API failure
     * @param cause the underlying cause of the API failure
     * @param retryable whether this operation can be retried
     */
    public GmailApiException(String message, Throwable cause, boolean retryable) {
        super(ERROR_CODE, message, cause);
        this.retryable = retryable;
    }

    /**
     * Determines if this exception represents a retryable operation.
     * 
     * @return true if the operation can be retried, false otherwise
     */
    public boolean isRetryable() {
        return retryable;
    }

    @Override
    public int getHttpStatus() {
        return HttpStatus.BAD_GATEWAY.value();
    }

    /**
     * Determines if an exception is retryable based on its type.
     * 
     * @param cause the underlying exception
     * @return true if retryable, false otherwise
     */
    private static boolean determineRetryability(Throwable cause) {
        if (cause == null) {
            return false;
        }
        
        String className = cause.getClass().getSimpleName();
        String message = cause.getMessage();
        
        // Network-related exceptions are typically retryable
        if (className.contains("IOException") || 
            className.contains("ConnectException") ||
            className.contains("SocketTimeoutException")) {
            return true;
        }
        
        // Temporary Gmail API errors are retryable
        if (message != null && (
            message.contains("temporary") ||
            message.contains("service unavailable") ||
            message.contains("timeout"))) {
            return true;
        }
        
        return false;
    }
}