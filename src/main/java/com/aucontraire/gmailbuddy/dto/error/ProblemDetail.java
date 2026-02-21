package com.aucontraire.gmailbuddy.dto.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * RFC 7807 - Problem Details for HTTP APIs
 * https://tools.ietf.org/html/rfc7807
 *
 * This class represents a standardized error response format that provides
 * machine-readable details about HTTP API errors. It follows the RFC 7807
 * specification while adding Gmail Buddy specific extensions.
 *
 * Standard RFC 7807 fields:
 * - type: URI identifying the problem type
 * - title: Short, human-readable summary
 * - status: HTTP status code
 * - detail: Human-readable explanation specific to this occurrence
 * - instance: URI reference to the specific occurrence
 *
 * Gmail Buddy extensions:
 * - requestId: Correlation ID for request tracking
 * - timestamp: When the error occurred
 * - retryable: Whether the operation can be retried
 * - category: Error category (CLIENT_ERROR, SERVER_ERROR, etc.)
 * - extensions: Additional problem-specific data
 *
 * @author Gmail Buddy Team
 * @since 1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemDetail {

    // RFC 7807 standard fields
    @JsonProperty("type")
    private URI type;

    @JsonProperty("title")
    private String title;

    @JsonProperty("status")
    private Integer status;

    @JsonProperty("detail")
    private String detail;

    @JsonProperty("instance")
    private URI instance;

    // Gmail Buddy extensions
    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("timestamp")
    private Instant timestamp;

    @JsonProperty("retryable")
    private Boolean retryable;

    @JsonProperty("category")
    private String category;

    @JsonProperty("extensions")
    private Map<String, Object> extensions;

    /**
     * Private constructor to enforce builder usage.
     */
    private ProblemDetail() {
        this.timestamp = Instant.now();
    }

    /**
     * Creates a new builder for constructing ProblemDetail instances.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for creating ProblemDetail instances with validation.
     */
    public static class Builder {
        private final ProblemDetail problem = new ProblemDetail();

        /**
         * Sets the problem type URI.
         *
         * @param type the URI identifying the problem type (required)
         * @return this builder
         */
        public Builder type(String type) {
            problem.type = URI.create(type);
            return this;
        }

        /**
         * Sets the problem type URI.
         *
         * @param type the URI identifying the problem type (required)
         * @return this builder
         */
        public Builder type(URI type) {
            problem.type = type;
            return this;
        }

        /**
         * Sets the problem title.
         *
         * @param title short, human-readable summary (required)
         * @return this builder
         */
        public Builder title(String title) {
            problem.title = title;
            return this;
        }

        /**
         * Sets the HTTP status code.
         *
         * @param status the HTTP status code (required)
         * @return this builder
         */
        public Builder status(int status) {
            problem.status = status;
            return this;
        }

        /**
         * Sets the detailed explanation.
         *
         * @param detail human-readable explanation specific to this occurrence
         * @return this builder
         */
        public Builder detail(String detail) {
            problem.detail = detail;
            return this;
        }

        /**
         * Sets the instance URI.
         *
         * @param instance URI reference to the specific occurrence
         * @return this builder
         */
        public Builder instance(String instance) {
            problem.instance = URI.create(instance);
            return this;
        }

        /**
         * Sets the instance URI.
         *
         * @param instance URI reference to the specific occurrence
         * @return this builder
         */
        public Builder instance(URI instance) {
            problem.instance = instance;
            return this;
        }

        /**
         * Sets the request correlation ID.
         *
         * @param requestId correlation ID for request tracking
         * @return this builder
         */
        public Builder requestId(String requestId) {
            problem.requestId = requestId;
            return this;
        }

        /**
         * Sets whether the operation is retryable.
         *
         * @param retryable true if the operation can be retried
         * @return this builder
         */
        public Builder retryable(boolean retryable) {
            problem.retryable = retryable;
            return this;
        }

        /**
         * Sets the error category.
         *
         * @param category error category (e.g., CLIENT_ERROR, SERVER_ERROR)
         * @return this builder
         */
        public Builder category(String category) {
            problem.category = category;
            return this;
        }

        /**
         * Sets the timestamp.
         *
         * @param timestamp when the error occurred
         * @return this builder
         */
        public Builder timestamp(Instant timestamp) {
            problem.timestamp = timestamp;
            return this;
        }

        /**
         * Adds an extension property.
         *
         * @param key the extension property key
         * @param value the extension property value
         * @return this builder
         */
        public Builder extension(String key, Object value) {
            if (problem.extensions == null) {
                problem.extensions = new HashMap<>();
            }
            problem.extensions.put(key, value);
            return this;
        }

        /**
         * Sets all extension properties at once.
         *
         * @param extensions map of extension properties
         * @return this builder
         */
        public Builder extensions(Map<String, Object> extensions) {
            problem.extensions = extensions != null ? new HashMap<>(extensions) : null;
            return this;
        }

        /**
         * Builds the ProblemDetail instance with validation.
         *
         * @return the constructed ProblemDetail
         * @throws IllegalStateException if required fields are missing
         */
        public ProblemDetail build() {
            // Validate required RFC 7807 fields
            if (problem.type == null) {
                throw new IllegalStateException("type is required for RFC 7807 ProblemDetail");
            }
            if (problem.title == null || problem.title.trim().isEmpty()) {
                throw new IllegalStateException("title is required for RFC 7807 ProblemDetail");
            }
            if (problem.status == null) {
                throw new IllegalStateException("status is required for RFC 7807 ProblemDetail");
            }

            return problem;
        }
    }

    // Getters

    public URI getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public Integer getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }

    public URI getInstance() {
        return instance;
    }

    public String getRequestId() {
        return requestId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Boolean getRetryable() {
        return retryable;
    }

    public String getCategory() {
        return category;
    }

    public Map<String, Object> getExtensions() {
        return extensions;
    }

    @Override
    public String toString() {
        return "ProblemDetail{" +
                "type=" + type +
                ", title='" + title + '\'' +
                ", status=" + status +
                ", detail='" + detail + '\'' +
                ", instance=" + instance +
                ", requestId='" + requestId + '\'' +
                ", timestamp=" + timestamp +
                ", retryable=" + retryable +
                ", category='" + category + '\'' +
                ", extensions=" + extensions +
                '}';
    }
}
