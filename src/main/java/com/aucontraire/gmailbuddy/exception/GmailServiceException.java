package com.aucontraire.gmailbuddy.exception;

public class GmailServiceException extends Exception {
    public GmailServiceException(String message) {
        super(message);
    }

    public GmailServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
