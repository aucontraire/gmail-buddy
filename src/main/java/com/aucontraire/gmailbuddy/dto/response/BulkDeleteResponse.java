package com.aucontraire.gmailbuddy.dto.response;

import com.aucontraire.gmailbuddy.dto.common.OperationStatus;
import com.aucontraire.gmailbuddy.dto.common.ResponseMetadata;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for bulk delete operations.
 * Contains status information and details about successful and failed operations.
 *
 * @since 1.0
 */
@Schema(description = "Result of a bulk delete operation")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BulkDeleteResponse {

    @Schema(description = "Overall operation status", example = "SUCCESS")
    private OperationStatus status;

    @Schema(description = "Total number of operations attempted", example = "10")
    private Integer totalOperations;

    @Schema(description = "Number of successful deletions", example = "9")
    private Integer successCount;

    @Schema(description = "Number of failed deletions", example = "1")
    private Integer failureCount;

    @Schema(description = "List of successfully deleted message IDs")
    private List<String> successfulOperations;

    @Schema(description = "Map of failed message IDs to error messages")
    private Map<String, String> failedOperations;

    @Schema(description = "Response metadata including timing information")
    private ResponseMetadata metadata;

    /**
     * Create a new builder for BulkDeleteResponse
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for BulkDeleteResponse following the builder pattern
     */
    public static class Builder {
        private final BulkDeleteResponse response = new BulkDeleteResponse();

        /**
         * Set the operation status
         * @param status the operation status
         * @return builder instance
         */
        public Builder status(OperationStatus status) {
            response.status = status;
            return this;
        }

        /**
         * Set the total number of operations attempted
         * @param total total operations count
         * @return builder instance
         */
        public Builder totalOperations(int total) {
            response.totalOperations = total;
            return this;
        }

        /**
         * Set the number of successful operations
         * @param count success count
         * @return builder instance
         */
        public Builder successCount(int count) {
            response.successCount = count;
            return this;
        }

        /**
         * Set the number of failed operations
         * @param count failure count
         * @return builder instance
         */
        public Builder failureCount(int count) {
            response.failureCount = count;
            return this;
        }

        /**
         * Set the list of successful operation identifiers
         * @param operations list of successful message IDs
         * @return builder instance
         */
        public Builder successfulOperations(List<String> operations) {
            response.successfulOperations = operations;
            return this;
        }

        /**
         * Set the map of failed operations with error messages
         * @param operations map of message ID to error message
         * @return builder instance
         */
        public Builder failedOperations(Map<String, String> operations) {
            response.failedOperations = operations;
            return this;
        }

        /**
         * Set the response metadata
         * @param metadata response metadata including timing and quota info
         * @return builder instance
         */
        public Builder metadata(ResponseMetadata metadata) {
            response.metadata = metadata;
            return this;
        }

        /**
         * Build the BulkDeleteResponse instance
         * @return configured BulkDeleteResponse
         */
        public BulkDeleteResponse build() {
            return response;
        }
    }

    // Getters

    public OperationStatus getStatus() {
        return status;
    }

    public Integer getTotalOperations() {
        return totalOperations;
    }

    public Integer getSuccessCount() {
        return successCount;
    }

    public Integer getFailureCount() {
        return failureCount;
    }

    public List<String> getSuccessfulOperations() {
        return successfulOperations;
    }

    public Map<String, String> getFailedOperations() {
        return failedOperations;
    }

    public ResponseMetadata getMetadata() {
        return metadata;
    }

    @Override
    public String toString() {
        return "BulkDeleteResponse{" +
                "status=" + status +
                ", totalOperations=" + totalOperations +
                ", successCount=" + successCount +
                ", failureCount=" + failureCount +
                ", metadata=" + metadata +
                '}';
    }
}
