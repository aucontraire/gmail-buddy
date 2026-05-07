package com.aucontraire.gmailbuddy.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when the Gmail API rejects or fails to process a send or
 * draft-send request in a way that cannot be attributed to the caller's input.
 *
 * <p>Maps to HTTP {@code 502 Bad Gateway} because the failure originates
 * in the upstream Gmail API, not in the application itself nor in the
 * caller's request.</p>
 *
 * <p>For daily-send-limit exceeded errors use
 * {@link RateLimitException} with a {@code Retry-After} header of 86400 seconds.
 * For resource-not-found scenarios (draft was discarded before send-draft call)
 * use {@link ResourceNotFoundException}. This exception is the catch-all for
 * other unexpected Gmail send failures ({@code /problems/message-send-failed}).</p>
 *
 * @see GmailBuddyServerException
 */
public class MessageSendException extends GmailBuddyServerException {

    private static final String ERROR_CODE = "MESSAGE_SEND_FAILED";

    /**
     * Constructs a {@code MessageSendException} with the specified detail message.
     *
     * @param message a description of why the send failed (for logging; not
     *                exposed verbatim in the API response)
     */
    public MessageSendException(String message) {
        super(ERROR_CODE, message);
    }

    /**
     * Constructs a {@code MessageSendException} with the specified detail message
     * and the upstream cause.
     *
     * @param message a description of why the send failed
     * @param cause   the upstream exception from the Gmail API client
     */
    public MessageSendException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }

    /**
     * Constructs a {@code MessageSendException} with the specified detail message
     * and a correlation ID for cross-request tracing.
     *
     * @param message       a description of why the send failed
     * @param correlationId the correlation ID to associate with this failure
     */
    public MessageSendException(String message, String correlationId) {
        super(ERROR_CODE, message, correlationId);
    }

    /**
     * Returns {@code 502} (Bad Gateway) — the Gmail API is the upstream dependency
     * that produced the failure.
     *
     * @return {@link HttpStatus#BAD_GATEWAY} value
     */
    @Override
    public int getHttpStatus() {
        return HttpStatus.BAD_GATEWAY.value();
    }
}
