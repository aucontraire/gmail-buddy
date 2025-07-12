package com.aucontraire.gmailbuddy.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when API rate limits are exceeded.
 * This includes Gmail API quota limits and request throttling.
 * 
 * @author Gmail Buddy Team
 * @since 1.0
 */
public class RateLimitException extends GmailBuddyClientException {

    private static final String ERROR_CODE = "RATE_LIMIT_EXCEEDED";

    private final long retryAfterSeconds;

    /**
     * Constructs a new rate limit exception with the specified message.
     * 
     * @param message the detail message explaining the rate limit
     */
    public RateLimitException(String message) {
        super(ERROR_CODE, message);
        this.retryAfterSeconds = 60; // Default retry after 1 minute
    }

    /**
     * Constructs a new rate limit exception with the specified message and retry delay.
     * 
     * @param message the detail message explaining the rate limit
     * @param retryAfterSeconds the number of seconds to wait before retrying
     */
    public RateLimitException(String message, long retryAfterSeconds) {
        super(ERROR_CODE, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * Constructs a new rate limit exception with the specified message, cause and retry delay.
     * 
     * @param message the detail message explaining the rate limit
     * @param cause the underlying cause
     * @param retryAfterSeconds the number of seconds to wait before retrying
     */
    public RateLimitException(String message, Throwable cause, long retryAfterSeconds) {
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
        return HttpStatus.TOO_MANY_REQUESTS.value();
    }
}