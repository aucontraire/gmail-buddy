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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for LabelModificationResponse DTO.
 * Tests builder pattern, getters, toString(), and JSON serialization behavior.
 */
@DisplayName("LabelModificationResponse Tests")
class LabelModificationResponseTest {

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
        LabelModificationResponse.Builder builder = LabelModificationResponse.builder();

        // Assert
        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Builder should build LabelModificationResponse with all fields")
    void builder_WithAllFields_ShouldBuildResponse() {
        // Given
        List<String> labelsAdded = Arrays.asList("INBOX", "IMPORTANT");
        List<String> labelsRemoved = Arrays.asList("UNREAD", "SPAM");
        List<String> affectedIds = Arrays.asList("msg1", "msg2", "msg3");

        ResponseMetadata metadata = ResponseMetadata.builder()
                .durationMs(300L)
                .quotaUsed(3)
                .build();

        // When
        LabelModificationResponse response = LabelModificationResponse.builder()
                .status(OperationStatus.SUCCESS)
                .messagesModified(3)
                .labelsAdded(labelsAdded)
                .labelsRemoved(labelsRemoved)
                .affectedMessageIds(affectedIds)
                .metadata(metadata)
                .build();

        // Then
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getMessagesModified()).isEqualTo(3);
        assertThat(response.getLabelsAdded()).hasSize(2);
        assertThat(response.getLabelsRemoved()).hasSize(2);
        assertThat(response.getAffectedMessageIds()).hasSize(3);
        assertThat(response.getMetadata()).isNotNull();
    }

    @Test
    @DisplayName("Builder should support method chaining")
    void builder_ShouldSupportMethodChaining() {
        // When
        LabelModificationResponse response = LabelModificationResponse.builder()
                .status(OperationStatus.SUCCESS)
                .messagesModified(5)
                .labelsAdded(Arrays.asList("INBOX"))
                .labelsRemoved(Collections.emptyList())
                .affectedMessageIds(Arrays.asList("msg1"))
                .metadata(null)
                .build();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getMessagesModified()).isEqualTo(5);
    }

    @Test
    @DisplayName("Builder should build response with minimal fields")
    void builder_WithMinimalFields_ShouldBuildResponse() {
        // When
        LabelModificationResponse response = LabelModificationResponse.builder()
                .status(OperationStatus.SUCCESS)
                .messagesModified(0)
                .build();

        // Then
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getMessagesModified()).isEqualTo(0);
        assertThat(response.getLabelsAdded()).isNull();
        assertThat(response.getLabelsRemoved()).isNull();
        assertThat(response.getAffectedMessageIds()).isNull();
        assertThat(response.getMetadata()).isNull();
    }

    @Test
    @DisplayName("getStatus() should return correct status")
    void getStatus_ShouldReturnCorrectStatus() {
        // Given
        LabelModificationResponse response = LabelModificationResponse.builder()
                .status(OperationStatus.PARTIAL_SUCCESS)
                .build();

        // When & Then
        assertThat(response.getStatus()).isEqualTo(OperationStatus.PARTIAL_SUCCESS);
    }

    @Test
    @DisplayName("getMessagesModified() should return correct count")
    void getMessagesModified_ShouldReturnCorrectCount() {
        // Given
        LabelModificationResponse response = LabelModificationResponse.builder()
                .messagesModified(42)
                .build();

        // When & Then
        assertThat(response.getMessagesModified()).isEqualTo(42);
    }

    @Test
    @DisplayName("getLabelsAdded() should return correct list")
    void getLabelsAdded_ShouldReturnCorrectList() {
        // Given
        List<String> labels = Arrays.asList("INBOX", "STARRED", "IMPORTANT");
        LabelModificationResponse response = LabelModificationResponse.builder()
                .labelsAdded(labels)
                .build();

        // When & Then
        assertThat(response.getLabelsAdded()).containsExactly("INBOX", "STARRED", "IMPORTANT");
    }

    @Test
    @DisplayName("getLabelsRemoved() should return correct list")
    void getLabelsRemoved_ShouldReturnCorrectList() {
        // Given
        List<String> labels = Arrays.asList("UNREAD", "SPAM");
        LabelModificationResponse response = LabelModificationResponse.builder()
                .labelsRemoved(labels)
                .build();

        // When & Then
        assertThat(response.getLabelsRemoved()).containsExactly("UNREAD", "SPAM");
    }

    @Test
    @DisplayName("getAffectedMessageIds() should return correct list")
    void getAffectedMessageIds_ShouldReturnCorrectList() {
        // Given
        List<String> messageIds = Arrays.asList("msg1", "msg2", "msg3");
        LabelModificationResponse response = LabelModificationResponse.builder()
                .affectedMessageIds(messageIds)
                .build();

        // When & Then
        assertThat(response.getAffectedMessageIds()).containsExactly("msg1", "msg2", "msg3");
    }

    @Test
    @DisplayName("getMetadata() should return correct metadata")
    void getMetadata_ShouldReturnCorrectMetadata() {
        // Given
        ResponseMetadata metadata = ResponseMetadata.builder()
                .durationMs(450L)
                .quotaUsed(15)
                .build();

        LabelModificationResponse response = LabelModificationResponse.builder()
                .metadata(metadata)
                .build();

        // When & Then
        assertThat(response.getMetadata()).isEqualTo(metadata);
        assertThat(response.getMetadata().getDurationMs()).isEqualTo(450L);
    }

    @Test
    @DisplayName("toString() should include all relevant information")
    void toString_ShouldIncludeAllRelevantInformation() {
        // Given
        ResponseMetadata metadata = ResponseMetadata.builder()
                .durationMs(200L)
                .build();

        LabelModificationResponse response = LabelModificationResponse.builder()
                .status(OperationStatus.SUCCESS)
                .messagesModified(5)
                .labelsAdded(Arrays.asList("INBOX"))
                .labelsRemoved(Arrays.asList("SPAM"))
                .metadata(metadata)
                .build();

        // When
        String result = response.toString();

        // Then
        assertThat(result).contains("LabelModificationResponse");
        assertThat(result).contains("status=SUCCESS");
        assertThat(result).contains("messagesModified=5");
        assertThat(result).contains("labelsAdded=[INBOX]");
        assertThat(result).contains("labelsRemoved=[SPAM]");
        assertThat(result).contains("metadata=");
    }

    @Test
    @DisplayName("toString() should handle null fields gracefully")
    void toString_WithNullFields_ShouldHandleGracefully() {
        // Given
        LabelModificationResponse response = LabelModificationResponse.builder()
                .status(OperationStatus.NO_RESULTS)
                .messagesModified(0)
                .build();

        // When
        String result = response.toString();

        // Then
        assertThat(result).contains("LabelModificationResponse");
        assertThat(result).contains("status=NO_RESULTS");
        assertThat(result).contains("messagesModified=0");
        assertThat(result).contains("labelsAdded=null");
        assertThat(result).contains("labelsRemoved=null");
        assertThat(result).contains("metadata=null");
    }

    @Test
    @DisplayName("JSON serialization should exclude null fields with @JsonInclude(NON_NULL)")
    void jsonSerialization_WithNullFields_ShouldExcludeNulls() throws JsonProcessingException {
        // Given
        LabelModificationResponse response = LabelModificationResponse.builder()
                .status(OperationStatus.SUCCESS)
                .messagesModified(3)
                .build();

        // When
        String json = objectMapper.writeValueAsString(response);

        // Then
        assertThat(json).contains("\"status\":\"SUCCESS\"");
        assertThat(json).contains("\"messagesModified\":3");
        assertThat(json).doesNotContain("labelsAdded");
        assertThat(json).doesNotContain("labelsRemoved");
        assertThat(json).doesNotContain("affectedMessageIds");
        assertThat(json).doesNotContain("metadata");
    }

    @Test
    @DisplayName("JSON serialization should include all non-null fields")
    void jsonSerialization_WithAllFields_ShouldIncludeAll() throws JsonProcessingException {
        // Given
        List<String> labelsAdded = Arrays.asList("INBOX", "STARRED");
        List<String> labelsRemoved = Arrays.asList("UNREAD");
        List<String> affectedIds = Arrays.asList("msg1", "msg2");

        ResponseMetadata metadata = ResponseMetadata.builder()
                .durationMs(350L)
                .quotaUsed(2)
                .build();

        LabelModificationResponse response = LabelModificationResponse.builder()
                .status(OperationStatus.SUCCESS)
                .messagesModified(2)
                .labelsAdded(labelsAdded)
                .labelsRemoved(labelsRemoved)
                .affectedMessageIds(affectedIds)
                .metadata(metadata)
                .build();

        // When
        String json = objectMapper.writeValueAsString(response);

        // Then
        assertThat(json).contains("\"status\":\"SUCCESS\"");
        assertThat(json).contains("\"messagesModified\":2");
        assertThat(json).contains("\"labelsAdded\"");
        assertThat(json).contains("\"labelsRemoved\"");
        assertThat(json).contains("\"affectedMessageIds\"");
        assertThat(json).contains("\"metadata\"");
    }

    @Test
    @DisplayName("JSON deserialization should reconstruct LabelModificationResponse correctly")
    void jsonDeserialization_ShouldReconstructObject() throws JsonProcessingException {
        // Given
        String json = "{\"status\":\"SUCCESS\",\"messagesModified\":3," +
                "\"labelsAdded\":[\"INBOX\"],\"labelsRemoved\":[\"SPAM\"]," +
                "\"affectedMessageIds\":[\"msg1\",\"msg2\",\"msg3\"]}";

        // When
        LabelModificationResponse response = objectMapper.readValue(json, LabelModificationResponse.class);

        // Then
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getMessagesModified()).isEqualTo(3);
        assertThat(response.getLabelsAdded()).containsExactly("INBOX");
        assertThat(response.getLabelsRemoved()).containsExactly("SPAM");
        assertThat(response.getAffectedMessageIds()).containsExactly("msg1", "msg2", "msg3");
    }

    @Test
    @DisplayName("Builder should handle empty label lists")
    void builder_WithEmptyLabelLists_ShouldHandleGracefully() {
        // When
        LabelModificationResponse response = LabelModificationResponse.builder()
                .status(OperationStatus.SUCCESS)
                .messagesModified(0)
                .labelsAdded(Collections.emptyList())
                .labelsRemoved(Collections.emptyList())
                .affectedMessageIds(Collections.emptyList())
                .build();

        // Then
        assertThat(response.getLabelsAdded()).isEmpty();
        assertThat(response.getLabelsRemoved()).isEmpty();
        assertThat(response.getAffectedMessageIds()).isEmpty();
    }

    @Test
    @DisplayName("Builder should handle only adding labels")
    void builder_WithOnlyAddedLabels_ShouldHandleCorrectly() {
        // When
        LabelModificationResponse response = LabelModificationResponse.builder()
                .status(OperationStatus.SUCCESS)
                .messagesModified(5)
                .labelsAdded(Arrays.asList("INBOX", "STARRED"))
                .affectedMessageIds(Arrays.asList("msg1", "msg2", "msg3", "msg4", "msg5"))
                .build();

        // Then
        assertThat(response.getLabelsAdded()).hasSize(2);
        assertThat(response.getLabelsRemoved()).isNull();
        assertThat(response.getAffectedMessageIds()).hasSize(5);
    }

    @Test
    @DisplayName("Builder should handle only removing labels")
    void builder_WithOnlyRemovedLabels_ShouldHandleCorrectly() {
        // When
        LabelModificationResponse response = LabelModificationResponse.builder()
                .status(OperationStatus.SUCCESS)
                .messagesModified(3)
                .labelsRemoved(Arrays.asList("SPAM", "TRASH"))
                .affectedMessageIds(Arrays.asList("msg1", "msg2", "msg3"))
                .build();

        // Then
        assertThat(response.getLabelsAdded()).isNull();
        assertThat(response.getLabelsRemoved()).hasSize(2);
        assertThat(response.getAffectedMessageIds()).hasSize(3);
    }

    @Test
    @DisplayName("Builder should handle large number of affected messages")
    void builder_WithLargeNumberOfAffectedMessages_ShouldHandleCorrectly() {
        // Given
        List<String> largeIdList = Arrays.asList(
                "msg1", "msg2", "msg3", "msg4", "msg5",
                "msg6", "msg7", "msg8", "msg9", "msg10"
        );

        // When
        LabelModificationResponse response = LabelModificationResponse.builder()
                .status(OperationStatus.SUCCESS)
                .messagesModified(10)
                .labelsAdded(Arrays.asList("IMPORTANT"))
                .affectedMessageIds(largeIdList)
                .build();

        // Then
        assertThat(response.getMessagesModified()).isEqualTo(10);
        assertThat(response.getAffectedMessageIds()).hasSize(10);
    }

    @Test
    @DisplayName("toString() should format all operation statuses correctly")
    void toString_WithDifferentStatuses_ShouldFormatCorrectly() {
        // Test SUCCESS
        LabelModificationResponse successResponse = LabelModificationResponse.builder()
                .status(OperationStatus.SUCCESS)
                .messagesModified(5)
                .build();

        assertThat(successResponse.toString()).contains("status=SUCCESS");

        // Test FAILURE
        LabelModificationResponse failureResponse = LabelModificationResponse.builder()
                .status(OperationStatus.FAILURE)
                .messagesModified(0)
                .build();

        assertThat(failureResponse.toString()).contains("status=FAILURE");

        // Test PARTIAL_SUCCESS
        LabelModificationResponse partialResponse = LabelModificationResponse.builder()
                .status(OperationStatus.PARTIAL_SUCCESS)
                .messagesModified(3)
                .build();

        assertThat(partialResponse.toString()).contains("status=PARTIAL_SUCCESS");
    }

    @Test
    @DisplayName("Builder should handle custom Gmail labels")
    void builder_WithCustomLabels_ShouldHandleCorrectly() {
        // Given
        List<String> customLabels = Arrays.asList("Label_1", "Label_2", "CustomCategory");

        // When
        LabelModificationResponse response = LabelModificationResponse.builder()
                .status(OperationStatus.SUCCESS)
                .messagesModified(1)
                .labelsAdded(customLabels)
                .affectedMessageIds(Arrays.asList("msg123"))
                .build();

        // Then
        assertThat(response.getLabelsAdded()).containsExactly("Label_1", "Label_2", "CustomCategory");
    }
}
