package com.aucontraire.gmailbuddy.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a label-create request ({@code POST /api/v1/gmail/labels}) targets a
 * name that already exists in the authenticated user's Gmail account.
 *
 * <p>Gmail's {@code users.labels.create} call rejects duplicate label names with an
 * HTTP 409 response ({@code GoogleJsonResponseException} with status 409).
 * {@code GmailRepositoryImpl} translates that Gmail-side conflict into this exception
 * so the service and controller layers work with the project's own exception
 * hierarchy rather than the Gmail SDK's checked exception type (Constitution II).</p>
 *
 * <p>Per FR-010 (feature 005 US3), no mutation occurs when this exception is thrown —
 * create-label is create-only; there is no upsert path.</p>
 *
 * <p><b>PII note (FR-015):</b> the exception message deliberately never embeds the
 * requested label name — {@code GlobalExceptionHandler} logs {@code ex.getMessage()}
 * verbatim, so keeping the message generic ensures the label name never reaches the
 * application logs via this path.</p>
 *
 * @see com.aucontraire.gmailbuddy.constants.ProblemTypes#RESOURCE_CONFLICT
 */
public class LabelAlreadyExistsException extends GmailBuddyClientException {

    private static final String ERROR_CODE = "LABEL_ALREADY_EXISTS";

    /**
     * Constructs a new exception with a generic, name-free detail message.
     *
     * @param message the detail message; callers MUST NOT embed the requested label
     *                name (FR-015)
     */
    public LabelAlreadyExistsException(String message) {
        super(ERROR_CODE, message);
    }

    /**
     * Constructs a new exception with a generic, name-free detail message and cause.
     *
     * @param message the detail message; callers MUST NOT embed the requested label
     *                name (FR-015)
     * @param cause   the underlying {@link com.google.api.client.googleapis.json.GoogleJsonResponseException}
     *                returned by the Gmail API client (typically a 409 response)
     */
    public LabelAlreadyExistsException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }

    /**
     * Returns {@code 409 Conflict}.
     *
     * @return {@link HttpStatus#CONFLICT} value (409)
     */
    @Override
    public int getHttpStatus() {
        return HttpStatus.CONFLICT.value();
    }
}
