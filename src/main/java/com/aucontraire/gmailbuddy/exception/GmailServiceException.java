package com.aucontraire.gmailbuddy.exception;

/**
 * Legacy exception for Gmail service errors.
 * @deprecated Use GmailApiException, InternalServerException, or other specific exceptions instead
 */
@Deprecated(since = "1.0", forRemoval = true)
public class GmailServiceException extends GmailApiException {
    public GmailServiceException(String message) {
        super(message);
    }

    public GmailServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
