package com.aucontraire.gmailbuddy.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when Gmail's send/draft API rejects a recipient address or message field
 * with {@code reason='invalidArgument'}.
 *
 * <p>Distinguished from {@link ValidationException} (which covers Bean Validation
 * failures on input) because this is a <em>semantic</em> rejection by the mailbox
 * provider, not a structural rejection of input. The caller supplied a
 * syntactically plausible address that passed all local Bean Validation rules, but
 * Gmail's upstream delivery layer determined the address cannot receive mail
 * (e.g., the mailbox does not exist, is over quota, or the domain rejects the
 * message).</p>
 *
 * <p>Maps to HTTP {@code 422 Unprocessable Entity} and problem type
 * {@code /problems/invalid-recipient}.</p>
 *
 * @see ValidationException
 * @see com.aucontraire.gmailbuddy.constants.ProblemTypes#INVALID_RECIPIENT
 */
public class InvalidRecipientException extends GmailBuddyClientException {

    private static final String ERROR_CODE = "INVALID_RECIPIENT";

    /**
     * Constructs a new invalid-recipient exception with the specified message.
     *
     * @param message the detail message explaining the Gmail rejection
     */
    public InvalidRecipientException(String message) {
        super(ERROR_CODE, message);
    }

    /**
     * Constructs a new invalid-recipient exception with the specified message and cause.
     *
     * @param message the detail message explaining the Gmail rejection
     * @param cause   the underlying {@link com.google.api.client.googleapis.json.GoogleJsonResponseException}
     *                returned by the Gmail API client
     */
    public InvalidRecipientException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }

    /**
     * Constructs a new invalid-recipient exception with the specified message and correlation ID.
     *
     * @param message       the detail message explaining the Gmail rejection
     * @param correlationId the correlation ID for request tracing
     */
    public InvalidRecipientException(String message, String correlationId) {
        super(ERROR_CODE, message, correlationId);
    }

    /**
     * Returns {@code 422 Unprocessable Entity}.
     *
     * <p>The request was well-formed (passed Bean Validation) but could not be
     * processed by Gmail because a recipient address or message field was semantically
     * invalid from the mailbox provider's perspective.</p>
     *
     * @return {@link HttpStatus#UNPROCESSABLE_ENTITY} value (422)
     */
    @Override
    public int getHttpStatus() {
        return HttpStatus.UNPROCESSABLE_ENTITY.value();
    }
}
