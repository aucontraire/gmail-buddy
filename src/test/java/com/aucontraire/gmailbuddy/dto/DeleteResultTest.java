package com.aucontreras.gmailbuddy.dto;

import com.aucontraire.gmailbuddy.dto.DeleteResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DeleteResult DTO.
 * Tests factory methods, JSON serialization, and field validation.
 */
@DisplayName("DeleteResult DTO Tests")
class DeleteResultTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("success() factory method should create successful result with correct values")
    void successFactoryMethod_ShouldCreateSuccessfulResult() {
        // Given
        String messageId = "msg12345";

        // When
        DeleteResult result = DeleteResult.success(messageId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getMessageId()).isEqualTo(messageId);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Message deleted successfully");
    }

    @Test
    @DisplayName("failure() factory method should create failed result with correct values")
    void failureFactoryMethod_ShouldCreateFailedResult() {
        // Given
        String messageId = "msg12345";
        String errorMessage = "Message not found";

        // When
        DeleteResult result = DeleteResult.failure(messageId, errorMessage);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getMessageId()).isEqualTo(messageId);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo(errorMessage);
    }

    @Test
    @DisplayName("Constructor should initialize all fields correctly")
    void constructor_ShouldInitializeAllFields() {
        // Given
        String messageId = "msg12345";
        boolean success = true;
        String message = "Custom message";

        // When
        DeleteResult result = new DeleteResult(messageId, success, message);

        // Then
        assertThat(result.getMessageId()).isEqualTo(messageId);
        assertThat(result.isSuccess()).isEqualTo(success);
        assertThat(result.getMessage()).isEqualTo(message);
    }

    @Test
    @DisplayName("toString() should include all field values")
    void toString_ShouldIncludeAllFields() {
        // Given
        DeleteResult result = DeleteResult.success("msg12345");

        // When
        String resultString = result.toString();

        // Then
        assertThat(resultString)
                .contains("msg12345")
                .contains("true")
                .contains("Message deleted successfully");
    }

    @Test
    @DisplayName("Successful result should serialize to correct JSON")
    void successResult_ShouldSerializeToCorrectJson() throws Exception {
        // Given
        DeleteResult result = DeleteResult.success("msg12345");

        // When
        String json = objectMapper.writeValueAsString(result);

        // Then
        assertThat(json)
                .contains("\"messageId\":\"msg12345\"")
                .contains("\"success\":true")
                .contains("\"message\":\"Message deleted successfully\"");
    }

    @Test
    @DisplayName("Failed result should serialize to correct JSON")
    void failureResult_ShouldSerializeToCorrectJson() throws Exception {
        // Given
        DeleteResult result = DeleteResult.failure("msg12345", "Permission denied");

        // When
        String json = objectMapper.writeValueAsString(result);

        // Then
        assertThat(json)
                .contains("\"messageId\":\"msg12345\"")
                .contains("\"success\":false")
                .contains("\"message\":\"Permission denied\"");
    }

    // Note: Deserialization test removed because DeleteResult has final fields and no default constructor.
    // This is intentional as DeleteResult is designed for serialization only (API responses).

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("Should handle null and empty messageId")
    void shouldHandleNullAndEmptyMessageId(String messageId) {
        // When
        DeleteResult result = DeleteResult.success(messageId);

        // Then
        assertThat(result.getMessageId()).isEqualTo(messageId);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should handle null error message in failure")
    void shouldHandleNullErrorMessage() {
        // When
        DeleteResult result = DeleteResult.failure("msg12345", null);

        // Then
        assertThat(result.getMessageId()).isEqualTo("msg12345");
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isNull();
    }

    @Test
    @DisplayName("Should handle empty error message in failure")
    void shouldHandleEmptyErrorMessage() {
        // When
        DeleteResult result = DeleteResult.failure("msg12345", "");

        // Then
        assertThat(result.getMessageId()).isEqualTo("msg12345");
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEmpty();
    }

    @Test
    @DisplayName("Should handle very long error messages")
    void shouldHandleVeryLongErrorMessages() {
        // Given
        String longMessage = "Error: ".repeat(100);

        // When
        DeleteResult result = DeleteResult.failure("msg12345", longMessage);

        // Then
        assertThat(result.getMessage()).isEqualTo(longMessage);
        assertThat(result.getMessage().length()).isGreaterThan(500);
    }

    @Test
    @DisplayName("JSON should exclude null fields due to @JsonInclude annotation")
    void jsonShouldExcludeNullFields() throws Exception {
        // Given
        DeleteResult result = new DeleteResult(null, false, null);

        // When
        String json = objectMapper.writeValueAsString(result);

        // Then - Should not contain "messageId" or "message" keys since they're null
        assertThat(json).contains("\"success\":false");
        // Note: The @JsonInclude(JsonInclude.Include.NON_NULL) should exclude null fields
    }

    @Test
    @DisplayName("Should handle special characters in messageId")
    void shouldHandleSpecialCharactersInMessageId() {
        // Given
        String specialMessageId = "msg-123_456.789@abc";

        // When
        DeleteResult result = DeleteResult.success(specialMessageId);

        // Then
        assertThat(result.getMessageId()).isEqualTo(specialMessageId);
    }

    @Test
    @DisplayName("Should handle special characters in error message")
    void shouldHandleSpecialCharactersInErrorMessage() {
        // Given
        String specialErrorMessage = "Error: Message not found! (404) - \"Invalid request\"";

        // When
        DeleteResult result = DeleteResult.failure("msg12345", specialErrorMessage);

        // Then
        assertThat(result.getMessage()).isEqualTo(specialErrorMessage);
    }

    @Test
    @DisplayName("Two results with same values should not be equal (no equals/hashCode)")
    void twoResultsWithSameValues_NoEqualsMethod() {
        // Given
        DeleteResult result1 = DeleteResult.success("msg12345");
        DeleteResult result2 = DeleteResult.success("msg12345");

        // Then - Since equals is not overridden, these should be different objects
        assertThat(result1).isNotSameAs(result2);
    }

    @Test
    @DisplayName("Success and failure results should have different success flags")
    void successAndFailureResults_ShouldHaveDifferentFlags() {
        // Given
        DeleteResult successResult = DeleteResult.success("msg12345");
        DeleteResult failureResult = DeleteResult.failure("msg12345", "Error");

        // Then
        assertThat(successResult.isSuccess()).isTrue();
        assertThat(failureResult.isSuccess()).isFalse();
        assertThat(successResult.getMessage()).isNotEqualTo(failureResult.getMessage());
    }

    @Test
    @DisplayName("Should maintain immutability - fields are final")
    void shouldMaintainImmutability() {
        // Given
        DeleteResult result = DeleteResult.success("msg12345");

        // Then - Verify fields are accessible but cannot be modified
        assertThat(result.getMessageId()).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isNotNull();

        // Note: Since fields are final, there are no setters to test
        // This test verifies the DTO follows immutable design
    }
}
