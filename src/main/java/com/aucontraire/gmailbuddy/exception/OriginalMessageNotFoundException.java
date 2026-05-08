package com.aucontraire.gmailbuddy.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when the {@code inReplyToMessageId} supplied by the caller does not resolve
 * to an accessible message in the authenticated user's Gmail account.
 *
 * <p>This exception is raised by {@code GmailRepositoryImpl.getMessageHeaders(...)}
 * when the Gmail {@code users.messages.get} API returns HTTP 404 for the specified
 * message ID. It indicates that the prerequisite resource (the message being replied
 * to) does not exist or is not accessible under the authenticated user's account.</p>
 *
 * <p>Distinguished from {@link ResourceNotFoundException} (which covers "the resource
 * you requested, e.g. the draft you want to send, does not exist") because this is a
 * <em>semantic</em> failure on a <em>prerequisite</em> resource, not the primary
 * resource of the request. The request was well-formed and passed all Bean Validation;
 * the referenced original message simply cannot be found.</p>
 *
 * <p>Maps to HTTP {@code 422 Unprocessable Entity} and problem type
 * {@code /problems/original-message-not-found}. HTTP 422 is the appropriate status
 * for "well-formed but semantically unprocessable" — the caller can retry with a
 * different {@code inReplyToMessageId} or omit the threading field to send without
 * threading.</p>
 *
 * @see com.aucontraire.gmailbuddy.constants.ProblemTypes#ORIGINAL_MESSAGE_NOT_FOUND
 * @see InvalidRecipientException
 */
public class OriginalMessageNotFoundException extends GmailBuddyClientException {

    private static final String ERROR_CODE = "ORIGINAL_MESSAGE_NOT_FOUND";

    /**
     * Constructs a new exception with the specified message.
     *
     * @param message the detail message explaining which message ID could not be found
     *                and why threading cannot proceed
     */
    public OriginalMessageNotFoundException(String message) {
        super(ERROR_CODE, message);
    }

    /**
     * Constructs a new exception with the specified message and cause.
     *
     * @param message the detail message explaining which message ID could not be found
     * @param cause   the underlying {@link com.google.api.client.googleapis.json.GoogleJsonResponseException}
     *                returned by the Gmail API client (typically a 404 response)
     */
    public OriginalMessageNotFoundException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }

    /**
     * Returns {@code 422 Unprocessable Entity}.
     *
     * <p>The request was well-formed (passed Bean Validation) but could not be
     * processed because the original message referenced by {@code inReplyToMessageId}
     * does not exist or is not accessible in the authenticated user's Gmail account.</p>
     *
     * @return {@link HttpStatus#UNPROCESSABLE_ENTITY} value (422)
     */
    @Override
    public int getHttpStatus() {
        return HttpStatus.UNPROCESSABLE_ENTITY.value();
    }
}
