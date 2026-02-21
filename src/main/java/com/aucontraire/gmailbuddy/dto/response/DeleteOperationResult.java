package com.aucontraire.gmailbuddy.dto.response;

import com.aucontraire.gmailbuddy.dto.common.OperationStatus;
import com.aucontraire.gmailbuddy.dto.common.ResponseMetadata;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for single delete operations.
 * Provides detailed result information including status and message.
 *
 * @since 1.0
 */
@Schema(description = "Result of a single delete operation")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeleteOperationResult {

    @Schema(description = "ID of the deleted message", example = "18d1a2b3c4d5e6f7")
    private String messageId;

    @Schema(description = "Operation status", example = "SUCCESS")
    private OperationStatus status;

    @Schema(description = "Human-readable result message", example = "Message deleted successfully")
    private String message;

    @Schema(description = "Response metadata including timing information")
    private ResponseMetadata metadata;

    /**
     * Factory method for successful delete operation.
     *
     * @param messageId the ID of the deleted message
     * @return DeleteOperationResult indicating success
     */
    public static DeleteOperationResult success(String messageId) {
        DeleteOperationResult result = new DeleteOperationResult();
        result.messageId = messageId;
        result.status = OperationStatus.SUCCESS;
        result.message = "Message deleted successfully";
        return result;
    }

    /**
     * Factory method for not found delete operation.
     *
     * @param messageId the ID of the message that was not found
     * @return DeleteOperationResult indicating not found
     */
    public static DeleteOperationResult notFound(String messageId) {
        DeleteOperationResult result = new DeleteOperationResult();
        result.messageId = messageId;
        result.status = OperationStatus.FAILURE;
        result.message = "Message not found";
        return result;
    }

    /**
     * Factory method for failed delete operation.
     *
     * @param messageId the ID of the message that failed to delete
     * @param reason the reason for the failure
     * @return DeleteOperationResult indicating failure
     */
    public static DeleteOperationResult failure(String messageId, String reason) {
        DeleteOperationResult result = new DeleteOperationResult();
        result.messageId = messageId;
        result.status = OperationStatus.FAILURE;
        result.message = reason;
        return result;
    }

    /**
     * Add metadata to this result.
     *
     * @param metadata the response metadata
     * @return this instance with metadata set
     */
    public DeleteOperationResult withMetadata(ResponseMetadata metadata) {
        this.metadata = metadata;
        return this;
    }

    // Getters
    public String getMessageId() {
        return messageId;
    }

    public OperationStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public ResponseMetadata getMetadata() {
        return metadata;
    }
}
