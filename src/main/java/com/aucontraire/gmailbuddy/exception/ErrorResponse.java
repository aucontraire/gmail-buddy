package com.aucontraire.gmailbuddy.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standardized error response DTO for API error handling.
 * Provides a consistent structure for all error responses across the application
 * with support for correlation IDs, retry information, and error categorization.
 * 
 * @author Gmail Buddy Team
 * @since 1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private String code;
    private String message;
    private Map<String, String> details;
    private LocalDateTime timestamp;
    private String correlationId;
    private Boolean retryable;
    private Long retryAfterSeconds;
    private String category;

    public ErrorResponse() {
    }

    public ErrorResponse(String code, String message, Map<String, String> details, LocalDateTime timestamp) {
        this.code = code;
        this.message = message;
        this.details = details;
        this.timestamp = timestamp;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, String> getDetails() {
        return details;
    }

    public void setDetails(Map<String, String> details) {
        this.details = details;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public Boolean getRetryable() {
        return retryable;
    }

    public void setRetryable(Boolean retryable) {
        this.retryable = retryable;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public void setRetryAfterSeconds(Long retryAfterSeconds) {
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    /**
     * Builder class for creating ErrorResponse instances.
     */
    public static class Builder {
        private String code;
        private String message;
        private Map<String, String> details;
        private LocalDateTime timestamp = LocalDateTime.now();
        private String correlationId;
        private Boolean retryable;
        private Long retryAfterSeconds;
        private String category;

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder details(Map<String, String> details) {
            this.details = details;
            return this;
        }

        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder retryable(Boolean retryable) {
            this.retryable = retryable;
            return this;
        }

        public Builder retryAfterSeconds(Long retryAfterSeconds) {
            this.retryAfterSeconds = retryAfterSeconds;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public ErrorResponse build() {
            ErrorResponse response = new ErrorResponse();
            response.setCode(code);
            response.setMessage(message);
            response.setDetails(details);
            response.setTimestamp(timestamp);
            response.setCorrelationId(correlationId);
            response.setRetryable(retryable);
            response.setRetryAfterSeconds(retryAfterSeconds);
            response.setCategory(category);
            return response;
        }
    }

    /**
     * Creates a new builder for ErrorResponse.
     * 
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
}