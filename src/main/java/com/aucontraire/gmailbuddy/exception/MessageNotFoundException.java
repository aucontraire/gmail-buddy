package com.aucontraire.gmailbuddy.exception;

/**
 * Legacy exception for message not found scenarios.
 * @deprecated Use ResourceNotFoundException instead for new code
 */
@Deprecated(since = "1.0", forRemoval = true)
public class MessageNotFoundException extends ResourceNotFoundException {
    public MessageNotFoundException(String message) {
        super(message);
    }

    public MessageNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
