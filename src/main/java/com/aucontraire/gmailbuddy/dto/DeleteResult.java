package com.aucontraire.gmailbuddy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing the result of a single message delete operation.
 * Provides detailed information about the outcome of the delete request.
 *
 * @author Gmail Buddy Team
 * @since 1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeleteResult {

    private final String messageId;
    private final boolean success;
    private final String message;

    /**
     * Creates a DeleteResult for a successful delete operation.
     *
     * @param messageId the ID of the deleted message
     * @return a DeleteResult indicating success
     */
    public static DeleteResult success(String messageId) {
        return new DeleteResult(messageId, true, "Message deleted successfully");
    }

    /**
     * Creates a DeleteResult for a failed delete operation.
     *
     * @param messageId the ID of the message that failed to delete
     * @param errorMessage the error message describing the failure
     * @return a DeleteResult indicating failure
     */
    public static DeleteResult failure(String messageId, String errorMessage) {
        return new DeleteResult(messageId, false, errorMessage);
    }

    /**
     * Constructor for DeleteResult.
     *
     * @param messageId the ID of the message
     * @param success whether the operation succeeded
     * @param message a descriptive message about the operation result
     */
    public DeleteResult(String messageId, boolean success, String message) {
        this.messageId = messageId;
        this.success = success;
        this.message = message;
    }

    /**
     * Gets the message ID.
     *
     * @return the message ID
     */
    public String getMessageId() {
        return messageId;
    }

    /**
     * Checks if the delete operation was successful.
     *
     * @return true if successful, false otherwise
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Gets the descriptive message about the operation result.
     *
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return String.format("DeleteResult{messageId='%s', success=%s, message='%s'}",
            messageId, success, message);
    }
}