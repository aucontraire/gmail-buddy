package com.aucontraire.gmailbuddy.dto.response;

import com.aucontraire.gmailbuddy.dto.common.OperationStatus;
import com.aucontraire.gmailbuddy.dto.common.ResponseMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DeleteOperationResult DTO.
 * Tests all factory methods and the withMetadata fluent API.
 */
@DisplayName("DeleteOperationResult Tests")
class DeleteOperationResultTest {

    @Test
    @DisplayName("success() factory method returns SUCCESS status with proper message")
    void success_ReturnsSuccessStatusWithMessage() {
        // Given
        String messageId = "msg-success-123";

        // When
        DeleteOperationResult result = DeleteOperationResult.success(messageId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getMessageId()).isEqualTo("msg-success-123");
        assertThat(result.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(result.getMessage()).isEqualTo("Message deleted successfully");
        assertThat(result.getMetadata()).isNull();
    }

    @Test
    @DisplayName("notFound() factory method returns FAILURE status with not found message")
    void notFound_ReturnsFailureStatusWithNotFoundMessage() {
        // Given
        String messageId = "msg-not-found-456";

        // When
        DeleteOperationResult result = DeleteOperationResult.notFound(messageId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getMessageId()).isEqualTo("msg-not-found-456");
        assertThat(result.getStatus()).isEqualTo(OperationStatus.FAILURE);
        assertThat(result.getMessage()).isEqualTo("Message not found");
        assertThat(result.getMetadata()).isNull();
    }

    @Test
    @DisplayName("failure() factory method returns FAILURE status with custom reason")
    void failure_ReturnsFailureStatusWithCustomReason() {
        // Given
        String messageId = "msg-failed-789";
        String reason = "Permission denied: insufficient privileges";

        // When
        DeleteOperationResult result = DeleteOperationResult.failure(messageId, reason);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getMessageId()).isEqualTo("msg-failed-789");
        assertThat(result.getStatus()).isEqualTo(OperationStatus.FAILURE);
        assertThat(result.getMessage()).isEqualTo("Permission denied: insufficient privileges");
        assertThat(result.getMetadata()).isNull();
    }

    @Test
    @DisplayName("failure() with null reason still creates result")
    void failure_WithNullReason_CreatesResultWithNullMessage() {
        // Given
        String messageId = "msg-null-reason";

        // When
        DeleteOperationResult result = DeleteOperationResult.failure(messageId, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getMessageId()).isEqualTo("msg-null-reason");
        assertThat(result.getStatus()).isEqualTo(OperationStatus.FAILURE);
        assertThat(result.getMessage()).isNull();
    }

    @Test
    @DisplayName("withMetadata() adds metadata to success result")
    void withMetadata_AddsMetadataToSuccessResult() {
        // Given
        DeleteOperationResult result = DeleteOperationResult.success("msg-meta-123");
        ResponseMetadata metadata = ResponseMetadata.builder()
                .durationMs(100L)
                .quotaUsed(5)
                .build();

        // When
        DeleteOperationResult resultWithMetadata = result.withMetadata(metadata);

        // Then
        assertThat(resultWithMetadata).isSameAs(result); // Returns same instance
        assertThat(resultWithMetadata.getMetadata()).isNotNull();
        assertThat(resultWithMetadata.getMetadata().getDurationMs()).isEqualTo(100L);
        assertThat(resultWithMetadata.getMetadata().getQuotaUsed()).isEqualTo(5);
        assertThat(resultWithMetadata.getMetadata().getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("withMetadata() adds metadata to notFound result")
    void withMetadata_AddsMetadataToNotFoundResult() {
        // Given
        DeleteOperationResult result = DeleteOperationResult.notFound("msg-not-found");
        ResponseMetadata metadata = ResponseMetadata.builder()
                .durationMs(50L)
                .quotaUsed(2)
                .build();

        // When
        DeleteOperationResult resultWithMetadata = result.withMetadata(metadata);

        // Then
        assertThat(resultWithMetadata).isSameAs(result);
        assertThat(resultWithMetadata.getMetadata()).isNotNull();
        assertThat(resultWithMetadata.getMetadata().getDurationMs()).isEqualTo(50L);
    }

    @Test
    @DisplayName("withMetadata() adds metadata to failure result")
    void withMetadata_AddsMetadataToFailureResult() {
        // Given
        DeleteOperationResult result = DeleteOperationResult.failure("msg-fail", "API error");
        ResponseMetadata metadata = ResponseMetadata.builder()
                .durationMs(200L)
                .quotaUsed(3)
                .build();

        // When
        DeleteOperationResult resultWithMetadata = result.withMetadata(metadata);

        // Then
        assertThat(resultWithMetadata).isSameAs(result);
        assertThat(resultWithMetadata.getMetadata()).isNotNull();
        assertThat(resultWithMetadata.getMetadata().getDurationMs()).isEqualTo(200L);
    }

    @Test
    @DisplayName("withMetadata() allows method chaining")
    void withMetadata_AllowsMethodChaining() {
        // Given
        ResponseMetadata metadata = ResponseMetadata.builder()
                .durationMs(150L)
                .quotaUsed(4)
                .build();

        // When
        DeleteOperationResult result = DeleteOperationResult.success("msg-chain")
                .withMetadata(metadata);

        // Then
        assertThat(result.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(result.getMessageId()).isEqualTo("msg-chain");
        assertThat(result.getMetadata()).isNotNull();
        assertThat(result.getMetadata().getDurationMs()).isEqualTo(150L);
    }

    @Test
    @DisplayName("getMessageId() returns the message ID")
    void getMessageId_ReturnsMessageId() {
        // Given
        DeleteOperationResult result = DeleteOperationResult.success("test-id");

        // When
        String messageId = result.getMessageId();

        // Then
        assertThat(messageId).isEqualTo("test-id");
    }

    @Test
    @DisplayName("getStatus() returns the operation status")
    void getStatus_ReturnsStatus() {
        // Given
        DeleteOperationResult result = DeleteOperationResult.notFound("test-id");

        // When
        OperationStatus status = result.getStatus();

        // Then
        assertThat(status).isEqualTo(OperationStatus.FAILURE);
    }

    @Test
    @DisplayName("getMessage() returns the status message")
    void getMessage_ReturnsMessage() {
        // Given
        DeleteOperationResult result = DeleteOperationResult.failure("test-id", "Custom error");

        // When
        String message = result.getMessage();

        // Then
        assertThat(message).isEqualTo("Custom error");
    }

    @Test
    @DisplayName("getMetadata() returns null when no metadata set")
    void getMetadata_ReturnsNullWhenNotSet() {
        // Given
        DeleteOperationResult result = DeleteOperationResult.success("test-id");

        // When
        ResponseMetadata metadata = result.getMetadata();

        // Then
        assertThat(metadata).isNull();
    }
}
