package com.aucontraire.gmailbuddy.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a service is temporarily unavailable.
 * This includes Gmail API downtime, maintenance windows, or circuit breaker scenarios.
 * 
 * @author Gmail Buddy Team
 * @since 1.0
 */
public class ServiceUnavailableException extends GmailBuddyServerException {

    private static final String ERROR_CODE = "SERVICE_UNAVAILABLE";

    private final long retryAfterSeconds;

    /**
     * Constructs a new service unavailable exception with the specified message.
     * 
     * @param message the detail message explaining why the service is unavailable
     */
    public ServiceUnavailableException(String message) {
        super(ERROR_CODE, message);
        this.retryAfterSeconds = 300; // Default retry after 5 minutes
    }

    /**
     * Constructs a new service unavailable exception with the specified message and retry delay.
     * 
     * @param message the detail message explaining why the service is unavailable
     * @param retryAfterSeconds the number of seconds to wait before retrying
     */
    public ServiceUnavailableException(String message, long retryAfterSeconds) {
        super(ERROR_CODE, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * Constructs a new service unavailable exception with the specified message and cause.
     * 
     * @param message the detail message explaining why the service is unavailable
     * @param cause the underlying cause
     */
    public ServiceUnavailableException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
        this.retryAfterSeconds = 300;
    }

    /**
     * Constructs a new service unavailable exception with the specified message, cause and retry delay.
     * 
     * @param message the detail message explaining why the service is unavailable
     * @param cause the underlying cause
     * @param retryAfterSeconds the number of seconds to wait before retrying
     */
    public ServiceUnavailableException(String message, Throwable cause, long retryAfterSeconds) {
        super(ERROR_CODE, message, cause);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * Gets the number of seconds to wait before retrying the request.
     * 
     * @return the retry delay in seconds
     */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    @Override
    public int getHttpStatus() {
        return HttpStatus.SERVICE_UNAVAILABLE.value();
    }
}