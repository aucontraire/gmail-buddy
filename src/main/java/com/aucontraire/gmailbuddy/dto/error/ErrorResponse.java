package com.aucontraire.gmailbuddy.dto.error;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * OpenAPI schema representation for error responses.
 * This class is used for documentation purposes to represent the
 * RFC 7807 ProblemDetail structure in OpenAPI/Swagger documentation.
 *
 * @see ProblemDetail
 * @since 1.0
 */
@Schema(description = "RFC 7807 Problem Details error response")
public class ErrorResponse {

    @Schema(description = "URI identifying the problem type", example = "https://gmailbuddy.example.com/problems/unauthorized")
    private String type;

    @Schema(description = "Short, human-readable summary of the problem", example = "Unauthorized")
    private String title;

    @Schema(description = "HTTP status code", example = "401")
    private Integer status;

    @Schema(description = "Human-readable explanation specific to this occurrence", example = "Invalid or expired access token")
    private String detail;

    @Schema(description = "URI reference to the specific occurrence", example = "/api/v1/gmail/messages")
    private String instance;

    @Schema(description = "Correlation ID for request tracking", example = "req-12345-abcde")
    private String requestId;

    @Schema(description = "When the error occurred", example = "2024-01-15T10:30:00Z")
    private String timestamp;

    @Schema(description = "Whether the operation can be retried", example = "true")
    private Boolean retryable;

    @Schema(description = "Error category", example = "CLIENT_ERROR")
    private String category;

    // Getters and setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public String getInstance() { return instance; }
    public void setInstance(String instance) { this.instance = instance; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public Boolean getRetryable() { return retryable; }
    public void setRetryable(Boolean retryable) { this.retryable = retryable; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
