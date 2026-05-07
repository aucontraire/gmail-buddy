package com.aucontraire.gmailbuddy.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when Gmail's send/draft API rejects a message with {@code reason='messageTooLarge'} —
 * the assembled MIME exceeds the maximum payload Gmail will accept (35 MB raw, ~25 MB base64).
 *
 * <p>Distinguished from {@link ValidationException} (which covers Bean Validation failures on
 * input, including the local {@code @MaxBodySize} 10 MB pre-check) because this is a Gmail-side
 * rejection of the assembled MIME stream, not a structural rejection of input. The caller
 * supplied content that passed all local Bean Validation rules and was successfully encoded
 * into a MIME message, but Gmail's API layer rejected the payload as too large.</p>
 *
 * <p>Maps to HTTP {@code 413 Payload Too Large} and problem type
 * {@code /problems/message-too-large}.</p>
 *
 * @see ValidationException
 * @see com.aucontraire.gmailbuddy.constants.ProblemTypes#MESSAGE_TOO_LARGE
 */
public class MessageTooLargeException extends GmailBuddyClientException {

    private static final String ERROR_CODE = "MESSAGE_TOO_LARGE";

    /**
     * Constructs a new message-too-large exception with the specified message.
     *
     * @param message the detail message explaining the Gmail rejection
     */
    public MessageTooLargeException(String message) {
        super(ERROR_CODE, message);
    }

    /**
     * Constructs a new message-too-large exception with the specified message and cause.
     *
     * @param message the detail message explaining the Gmail rejection
     * @param cause   the underlying {@link com.google.api.client.googleapis.json.GoogleJsonResponseException}
     *                returned by the Gmail API client
     */
    public MessageTooLargeException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }

    /**
     * Constructs a new message-too-large exception with the specified message and correlation ID.
     *
     * @param message       the detail message explaining the Gmail rejection
     * @param correlationId the correlation ID for request tracing
     */
    public MessageTooLargeException(String message, String correlationId) {
        super(ERROR_CODE, message, correlationId);
    }

    /**
     * Returns {@code 413 Payload Too Large}.
     *
     * <p>The assembled MIME stream exceeded the maximum payload size that Gmail will
     * accept. This is a provider-side size limit, not a local Bean Validation failure —
     * the request was structurally valid but the resulting message was too large to
     * deliver.</p>
     *
     * @return {@link HttpStatus#PAYLOAD_TOO_LARGE} value (413)
     */
    @Override
    public int getHttpStatus() {
        return HttpStatus.PAYLOAD_TOO_LARGE.value();
    }
}
