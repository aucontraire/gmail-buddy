package com.aucontraire.gmailbuddy.dto.response;

import com.aucontraire.gmailbuddy.dto.common.OperationStatus;
import com.aucontraire.gmailbuddy.dto.common.ResponseMetadata;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response DTO for label modification operations.
 * Contains status information and details about the labels that were modified.
 *
 * @since 1.0
 */
@Schema(description = "Result of a label modification operation")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LabelModificationResponse {

    @Schema(description = "Operation status", example = "SUCCESS")
    private OperationStatus status;

    @Schema(description = "Number of messages modified", example = "5")
    private Integer messagesModified;

    @Schema(description = "Labels that were added", example = "[\"IMPORTANT\", \"STARRED\"]")
    private List<String> labelsAdded;

    @Schema(description = "Labels that were removed", example = "[\"UNREAD\"]")
    private List<String> labelsRemoved;

    @Schema(description = "IDs of messages that were modified")
    private List<String> affectedMessageIds;

    @Schema(description = "Response metadata including timing information")
    private ResponseMetadata metadata;

    /**
     * Create a new builder for LabelModificationResponse
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for LabelModificationResponse following the builder pattern
     */
    public static class Builder {
        private final LabelModificationResponse response = new LabelModificationResponse();

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
         * Set the number of messages modified
         * @param count number of messages modified
         * @return builder instance
         */
        public Builder messagesModified(int count) {
            response.messagesModified = count;
            return this;
        }

        /**
         * Set the list of labels that were added
         * @param labels list of label IDs that were added
         * @return builder instance
         */
        public Builder labelsAdded(List<String> labels) {
            response.labelsAdded = labels;
            return this;
        }

        /**
         * Set the list of labels that were removed
         * @param labels list of label IDs that were removed
         * @return builder instance
         */
        public Builder labelsRemoved(List<String> labels) {
            response.labelsRemoved = labels;
            return this;
        }

        /**
         * Set the list of message IDs that were affected
         * @param ids list of message IDs
         * @return builder instance
         */
        public Builder affectedMessageIds(List<String> ids) {
            response.affectedMessageIds = ids;
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
         * Build the LabelModificationResponse instance
         * @return configured LabelModificationResponse
         */
        public LabelModificationResponse build() {
            return response;
        }
    }

    // Getters

    public OperationStatus getStatus() {
        return status;
    }

    public Integer getMessagesModified() {
        return messagesModified;
    }

    public List<String> getLabelsAdded() {
        return labelsAdded;
    }

    public List<String> getLabelsRemoved() {
        return labelsRemoved;
    }

    public List<String> getAffectedMessageIds() {
        return affectedMessageIds;
    }

    public ResponseMetadata getMetadata() {
        return metadata;
    }

    @Override
    public String toString() {
        return "LabelModificationResponse{" +
                "status=" + status +
                ", messagesModified=" + messagesModified +
                ", labelsAdded=" + labelsAdded +
                ", labelsRemoved=" + labelsRemoved +
                ", metadata=" + metadata +
                '}';
    }
}
