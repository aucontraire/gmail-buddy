package com.aucontraire.gmailbuddy.dto.response;

import com.aucontraire.gmailbuddy.dto.common.ResponseMetadata;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response DTO for list/search endpoints with pagination support.
 * Contains message summaries and pagination metadata.
 *
 * @since 1.0
 */
@Schema(description = "Paginated list of Gmail messages")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageListResponse {

    @Schema(description = "List of message summaries")
    private List<MessageSummary> messages;

    @Schema(description = "Total count of messages (estimated)", example = "150")
    private Integer totalCount;

    @Schema(description = "Whether more results are available", example = "true")
    private Boolean hasMore;

    @Schema(description = "Token for fetching the next page of results", example = "eyJwYWdl...")
    private String nextPageToken;

    @Schema(description = "Response metadata including timing information")
    private ResponseMetadata metadata;

    /**
     * Create a new builder for MessageListResponse.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for MessageListResponse following the builder pattern.
     */
    public static class Builder {
        private final MessageListResponse response = new MessageListResponse();

        /**
         * Set the list of message summaries.
         *
         * @param messages list of message summaries
         * @return builder instance
         */
        public Builder messages(List<MessageSummary> messages) {
            response.messages = messages;
            return this;
        }

        /**
         * Set the total count of messages.
         *
         * @param totalCount total number of messages
         * @return builder instance
         */
        public Builder totalCount(Integer totalCount) {
            response.totalCount = totalCount;
            return this;
        }

        /**
         * Set whether there are more results available.
         *
         * @param hasMore true if more results are available
         * @return builder instance
         */
        public Builder hasMore(Boolean hasMore) {
            response.hasMore = hasMore;
            return this;
        }

        /**
         * Set the next page token for pagination.
         *
         * @param nextPageToken token for fetching next page
         * @return builder instance
         */
        public Builder nextPageToken(String nextPageToken) {
            response.nextPageToken = nextPageToken;
            return this;
        }

        /**
         * Set the response metadata.
         *
         * @param metadata response metadata
         * @return builder instance
         */
        public Builder metadata(ResponseMetadata metadata) {
            response.metadata = metadata;
            return this;
        }

        /**
         * Build the MessageListResponse instance.
         *
         * @return configured MessageListResponse
         */
        public MessageListResponse build() {
            return response;
        }
    }

    // Getters
    public List<MessageSummary> getMessages() {
        return messages;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public Boolean getHasMore() {
        return hasMore;
    }

    public String getNextPageToken() {
        return nextPageToken;
    }

    public ResponseMetadata getMetadata() {
        return metadata;
    }
}
