package com.aucontraire.gmailbuddy.dto.response;

import com.aucontraire.gmailbuddy.dto.common.OperationStatus;
import com.aucontraire.gmailbuddy.dto.common.ResponseMetadata;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for BulkDeleteResponse DTO.
 * Tests builder pattern, getters, toString(), and JSON serialization behavior.
 */
@DisplayName("BulkDeleteResponse Tests")
class BulkDeleteResponseTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    @Test
    @DisplayName("builder() should create new Builder instance")
    void builder_ShouldCreateNewBuilder() {
        // Act
        BulkDeleteResponse.Builder builder = BulkDeleteResponse.builder();

        // Assert
        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Builder should build BulkDeleteResponse with all fields")
    void builder_WithAllFields_ShouldBuildResponse() {
        // Arrange
        List<String> successfulOps = Arrays.asList("msg1", "msg2", "msg3");
        Map<String, String> failedOps = new HashMap<>();
        failedOps.put("msg4", "Not found");
        failedOps.put("msg5", "Permission denied");

        ResponseMetadata metadata = ResponseMetadata.builder()
                .durationMs(500L)
                .quotaUsed(5)
                .build();

        // When
        BulkDeleteResponse response = BulkDeleteResponse.builder()
                .status(OperationStatus.PARTIAL_SUCCESS)
                .totalOperations(5)
                .successCount(3)
                .failureCount(2)
                .successfulOperations(successfulOps)
                .failedOperations(failedOps)
                .metadata(metadata)
                .build();

        // Then
        assertThat(response.getStatus()).isEqualTo(OperationStatus.PARTIAL_SUCCESS);
        assertThat(response.getTotalOperations()).isEqualTo(5);
        assertThat(response.getSuccessCount()).isEqualTo(3);
        assertThat(response.getFailureCount()).isEqualTo(2);
        assertThat(response.getSuccessfulOperations()).hasSize(3);
        assertThat(response.getFailedOperations()).hasSize(2);
        assertThat(response.getMetadata()).isNotNull();
    }

    @Test
    @DisplayName("Builder should support method chaining")
    void builder_ShouldSupportMethodChaining() {
        // When
        BulkDeleteResponse response = BulkDeleteResponse.builder()
                .status(OperationStatus.SUCCESS)
                .totalOperations(10)
                .successCount(10)
                .failureCount(0)
                .successfulOperations(Collections.emptyList())
                .failedOperations(Collections.emptyMap())
                .metadata(null)
                .build();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getTotalOperations()).isEqualTo(10);
    }

    @Test
    @DisplayName("Builder should build response with minimal fields")
    void builder_WithMinimalFields_ShouldBuildResponse() {
        // When
        BulkDeleteResponse response = BulkDeleteResponse.builder()
                .status(OperationStatus.SUCCESS)
                .totalOperations(0)
                .successCount(0)
                .failureCount(0)
                .build();

        // Then
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getSuccessfulOperations()).isNull();
        assertThat(response.getFailedOperations()).isNull();
        assertThat(response.getMetadata()).isNull();
    }

    @Test
    @DisplayName("getStatus() should return correct status")
    void getStatus_ShouldReturnCorrectStatus() {
        // Given
        BulkDeleteResponse response = BulkDeleteResponse.builder()
                .status(OperationStatus.FAILURE)
                .build();

        // When & Then
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILURE);
    }

    @Test
    @DisplayName("getTotalOperations() should return correct count")
    void getTotalOperations_ShouldReturnCorrectCount() {
        // Given
        BulkDeleteResponse response = BulkDeleteResponse.builder()
                .totalOperations(42)
                .build();

        // When & Then
        assertThat(response.getTotalOperations()).isEqualTo(42);
    }

    @Test
    @DisplayName("getSuccessCount() should return correct count")
    void getSuccessCount_ShouldReturnCorrectCount() {
        // Given
        BulkDeleteResponse response = BulkDeleteResponse.builder()
                .successCount(15)
                .build();

        // When & Then
        assertThat(response.getSuccessCount()).isEqualTo(15);
    }

    @Test
    @DisplayName("getFailureCount() should return correct count")
    void getFailureCount_ShouldReturnCorrectCount() {
        // Given
        BulkDeleteResponse response = BulkDeleteResponse.builder()
                .failureCount(7)
                .build();

        // When & Then
        assertThat(response.getFailureCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("getSuccessfulOperations() should return correct list")
    void getSuccessfulOperations_ShouldReturnCorrectList() {
        // Given
        List<String> successList = Arrays.asList("msg1", "msg2", "msg3");
        BulkDeleteResponse response = BulkDeleteResponse.builder()
                .successfulOperations(successList)
                .build();

        // When & Then
        assertThat(response.getSuccessfulOperations()).containsExactly("msg1", "msg2", "msg3");
    }

    @Test
    @DisplayName("getFailedOperations() should return correct map")
    void getFailedOperations_ShouldReturnCorrectMap() {
        // Given
        Map<String, String> failedMap = new HashMap<>();
        failedMap.put("msg1", "Error 1");
        failedMap.put("msg2", "Error 2");

        BulkDeleteResponse response = BulkDeleteResponse.builder()
                .failedOperations(failedMap)
                .build();

        // When & Then
        assertThat(response.getFailedOperations()).hasSize(2);
        assertThat(response.getFailedOperations().get("msg1")).isEqualTo("Error 1");
        assertThat(response.getFailedOperations().get("msg2")).isEqualTo("Error 2");
    }

    @Test
    @DisplayName("getMetadata() should return correct metadata")
    void getMetadata_ShouldReturnCorrectMetadata() {
        // Given
        ResponseMetadata metadata = ResponseMetadata.builder()
                .durationMs(300L)
                .quotaUsed(20)
                .build();

        BulkDeleteResponse response = BulkDeleteResponse.builder()
                .metadata(metadata)
                .build();

        // When & Then
        assertThat(response.getMetadata()).isEqualTo(metadata);
        assertThat(response.getMetadata().getDurationMs()).isEqualTo(300L);
    }

    @Test
    @DisplayName("toString() should include all relevant information")
    void toString_ShouldIncludeAllRelevantInformation() {
        // Given
        ResponseMetadata metadata = ResponseMetadata.builder()
                .durationMs(250L)
                .build();

        BulkDeleteResponse response = BulkDeleteResponse.builder()
                .status(OperationStatus.PARTIAL_SUCCESS)
                .totalOperations(10)
                .successCount(7)
                .failureCount(3)
                .metadata(metadata)
                .build();

        // When
        String result = response.toString();

        // Then
        assertThat(result).contains("BulkDeleteResponse");
        assertThat(result).contains("status=PARTIAL_SUCCESS");
        assertThat(result).contains("totalOperations=10");
        assertThat(result).contains("successCount=7");
        assertThat(result).contains("failureCount=3");
        assertThat(result).contains("metadata=");
    }

    @Test
    @DisplayName("toString() should handle null metadata")
    void toString_WithNullMetadata_ShouldHandleGracefully() {
        // Given
        BulkDeleteResponse response = BulkDeleteResponse.builder()
                .status(OperationStatus.SUCCESS)
                .totalOperations(5)
                .successCount(5)
                .failureCount(0)
                .build();

        // When
        String result = response.toString();

        // Then
        assertThat(result).contains("BulkDeleteResponse");
        assertThat(result).contains("metadata=null");
    }

    @Test
    @DisplayName("JSON serialization should exclude null fields with @JsonInclude(NON_NULL)")
    void jsonSerialization_WithNullFields_ShouldExcludeNulls() throws JsonProcessingException {
        // Given
        BulkDeleteResponse response = BulkDeleteResponse.builder()
                .status(OperationStatus.SUCCESS)
                .totalOperations(5)
                .successCount(5)
                .failureCount(0)
                .build();

        // When
        String json = objectMapper.writeValueAsString(response);

        // Then
        assertThat(json).contains("\"status\":\"SUCCESS\"");
        assertThat(json).contains("\"totalOperations\":5");
        assertThat(json).contains("\"successCount\":5");
        assertThat(json).contains("\"failureCount\":0");
        assertThat(json).doesNotContain("successfulOperations");
        assertThat(json).doesNotContain("failedOperations");
        assertThat(json).doesNotContain("metadata");
    }

    @Test
    @DisplayName("JSON serialization should include all non-null fields")
    void jsonSerialization_WithAllFields_ShouldIncludeAll() throws JsonProcessingException {
        // Given
        List<String> successList = Arrays.asList("msg1", "msg2");
        Map<String, String> failedMap = new HashMap<>();
        failedMap.put("msg3", "Error");

        ResponseMetadata metadata = ResponseMetadata.builder()
                .durationMs(400L)
                .quotaUsed(3)
                .build();

        BulkDeleteResponse response = BulkDeleteResponse.builder()
                .status(OperationStatus.PARTIAL_SUCCESS)
                .totalOperations(3)
                .successCount(2)
                .failureCount(1)
                .successfulOperations(successList)
                .failedOperations(failedMap)
                .metadata(metadata)
                .build();

        // When
        String json = objectMapper.writeValueAsString(response);

        // Then
        assertThat(json).contains("\"status\":\"PARTIAL_SUCCESS\"");
        assertThat(json).contains("\"totalOperations\":3");
        assertThat(json).contains("\"successCount\":2");
        assertThat(json).contains("\"failureCount\":1");
        assertThat(json).contains("\"successfulOperations\"");
        assertThat(json).contains("\"failedOperations\"");
        assertThat(json).contains("\"metadata\"");
    }

    @Test
    @DisplayName("JSON deserialization should reconstruct BulkDeleteResponse correctly")
    void jsonDeserialization_ShouldReconstructObject() throws JsonProcessingException {
        // Given
        String json = "{\"status\":\"SUCCESS\",\"totalOperations\":5," +
                "\"successCount\":5,\"failureCount\":0," +
                "\"successfulOperations\":[\"msg1\",\"msg2\"]}";

        // When
        BulkDeleteResponse response = objectMapper.readValue(json, BulkDeleteResponse.class);

        // Then
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getTotalOperations()).isEqualTo(5);
        assertThat(response.getSuccessCount()).isEqualTo(5);
        assertThat(response.getFailureCount()).isEqualTo(0);
        assertThat(response.getSuccessfulOperations()).containsExactly("msg1", "msg2");
    }

    @Test
    @DisplayName("Builder should handle empty successful operations list")
    void builder_WithEmptySuccessfulOperations_ShouldHandleGracefully() {
        // When
        BulkDeleteResponse response = BulkDeleteResponse.builder()
                .status(OperationStatus.FAILURE)
                .totalOperations(2)
                .successCount(0)
                .failureCount(2)
                .successfulOperations(Collections.emptyList())
                .build();

        // Then
        assertThat(response.getSuccessfulOperations()).isEmpty();
    }

    @Test
    @DisplayName("Builder should handle empty failed operations map")
    void builder_WithEmptyFailedOperations_ShouldHandleGracefully() {
        // When
        BulkDeleteResponse response = BulkDeleteResponse.builder()
                .status(OperationStatus.SUCCESS)
                .totalOperations(3)
                .successCount(3)
                .failureCount(0)
                .failedOperations(Collections.emptyMap())
                .build();

        // Then
        assertThat(response.getFailedOperations()).isEmpty();
    }

    @Test
    @DisplayName("Builder should handle large volume of operations")
    void builder_WithLargeVolume_ShouldHandleCorrectly() {
        // Given
        List<String> largeSuccessList = Arrays.asList(
                "msg1", "msg2", "msg3", "msg4", "msg5",
                "msg6", "msg7", "msg8", "msg9", "msg10"
        );

        // When
        BulkDeleteResponse response = BulkDeleteResponse.builder()
                .status(OperationStatus.SUCCESS)
                .totalOperations(10)
                .successCount(10)
                .failureCount(0)
                .successfulOperations(largeSuccessList)
                .build();

        // Then
        assertThat(response.getSuccessfulOperations()).hasSize(10);
        assertThat(response.getTotalOperations()).isEqualTo(10);
    }

    @Test
    @DisplayName("toString() should format all operation statuses correctly")
    void toString_WithDifferentStatuses_ShouldFormatCorrectly() {
        // Test SUCCESS
        BulkDeleteResponse successResponse = BulkDeleteResponse.builder()
                .status(OperationStatus.SUCCESS)
                .totalOperations(5)
                .successCount(5)
                .failureCount(0)
                .build();

        assertThat(successResponse.toString()).contains("status=SUCCESS");

        // Test FAILURE
        BulkDeleteResponse failureResponse = BulkDeleteResponse.builder()
                .status(OperationStatus.FAILURE)
                .totalOperations(5)
                .successCount(0)
                .failureCount(5)
                .build();

        assertThat(failureResponse.toString()).contains("status=FAILURE");

        // Test NO_RESULTS
        BulkDeleteResponse noResultsResponse = BulkDeleteResponse.builder()
                .status(OperationStatus.NO_RESULTS)
                .totalOperations(0)
                .successCount(0)
                .failureCount(0)
                .build();

        assertThat(noResultsResponse.toString()).contains("status=NO_RESULTS");
    }
}
