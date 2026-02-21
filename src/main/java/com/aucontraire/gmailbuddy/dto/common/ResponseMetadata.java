package com.aucontraire.gmailbuddy.dto.common;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Common metadata included in API responses.
 * Contains timing information and resource usage metrics.
 *
 * @since 1.0
 */
@Schema(description = "Response metadata with timing and quota information")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseMetadata {

    @Schema(description = "Timestamp when the response was generated", example = "2024-01-15T10:30:00.000Z")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant timestamp;

    @Schema(description = "Duration of the operation in milliseconds", example = "125")
    private Long durationMs;

    @Schema(description = "Estimated Gmail API quota units consumed", example = "5")
    private Integer quotaUsed;

    /**
     * Default constructor - sets timestamp to current time
     */
    public ResponseMetadata() {
        this.timestamp = Instant.now();
    }

    /**
     * Create a new builder for ResponseMetadata
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for ResponseMetadata following the builder pattern
     */
    public static class Builder {
        private final ResponseMetadata metadata = new ResponseMetadata();

        /**
         * Set the duration in milliseconds
         * @param durationMs duration in milliseconds
         * @return builder instance
         */
        public Builder durationMs(Long durationMs) {
            metadata.durationMs = durationMs;
            return this;
        }

        /**
         * Set the quota units used by this operation
         * @param quotaUsed quota units consumed
         * @return builder instance
         */
        public Builder quotaUsed(Integer quotaUsed) {
            metadata.quotaUsed = quotaUsed;
            return this;
        }

        /**
         * Build the ResponseMetadata instance
         * @return configured ResponseMetadata
         */
        public ResponseMetadata build() {
            return metadata;
        }
    }

    // Getters

    public Instant getTimestamp() {
        return timestamp;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public Integer getQuotaUsed() {
        return quotaUsed;
    }

    @Override
    public String toString() {
        return "ResponseMetadata{" +
                "timestamp=" + timestamp +
                ", durationMs=" + durationMs +
                ", quotaUsed=" + quotaUsed +
                '}';
    }
}