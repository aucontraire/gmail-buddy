package com.aucontraire.gmailbuddy.mapper;

import com.aucontraire.gmailbuddy.dto.common.OperationStatus;
import com.aucontraire.gmailbuddy.dto.common.ResponseMetadata;
import com.aucontraire.gmailbuddy.dto.response.BulkDeleteResponse;
import com.aucontraire.gmailbuddy.dto.response.LabelModificationResponse;
import com.aucontraire.gmailbuddy.service.BulkOperationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive unit tests for ResponseMapper.
 * Tests the mapping logic from BulkOperationResult to various response DTOs.
 *
 * @author Gmail Buddy Testing Team
 */
@DisplayName("ResponseMapper Unit Tests")
class ResponseMapperTest {

    private ResponseMapper responseMapper;

    @BeforeEach
    void setUp() {
        responseMapper = new ResponseMapper();
    }

    @Nested
    @DisplayName("toBulkDeleteResponse Tests")
    class ToBulkDeleteResponseTests {

        @Test
        @DisplayName("Should map all fields correctly from BulkOperationResult")
        void shouldMapAllFieldsCorrectly() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_DELETE);
            serviceResult.addSuccess("msg-001");
            serviceResult.addSuccess("msg-002");
            serviceResult.addSuccess("msg-003");
            serviceResult.addFailure("msg-004", "Permission denied");
            serviceResult.addFailure("msg-005", "Message not found");
            serviceResult.incrementBatchesProcessed();
            serviceResult.incrementBatchesProcessed();
            serviceResult.markCompleted();

            // Act
            BulkDeleteResponse response = responseMapper.toBulkDeleteResponse(serviceResult);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(OperationStatus.PARTIAL_SUCCESS);
            assertThat(response.getTotalOperations()).isEqualTo(5);
            assertThat(response.getSuccessCount()).isEqualTo(3);
            assertThat(response.getFailureCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should copy successfulOperations list correctly")
        void shouldCopySuccessfulOperationsList() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_DELETE);
            serviceResult.addSuccess("msg-001");
            serviceResult.addSuccess("msg-002");
            serviceResult.addSuccess("msg-003");
            serviceResult.markCompleted();

            // Act
            BulkDeleteResponse response = responseMapper.toBulkDeleteResponse(serviceResult);

            // Assert
            assertThat(response.getSuccessfulOperations())
                .isNotNull()
                .hasSize(3)
                .containsExactlyInAnyOrder("msg-001", "msg-002", "msg-003");
        }

        @Test
        @DisplayName("Should copy failedOperations map correctly")
        void shouldCopyFailedOperationsMap() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_DELETE);
            serviceResult.addSuccess("msg-001");
            serviceResult.addFailure("msg-002", "Permission denied");
            serviceResult.addFailure("msg-003", "Message not found");
            serviceResult.addFailure("msg-004", "Invalid request");
            serviceResult.markCompleted();

            // Act
            BulkDeleteResponse response = responseMapper.toBulkDeleteResponse(serviceResult);

            // Assert
            assertThat(response.getFailedOperations())
                .isNotNull()
                .hasSize(3)
                .containsEntry("msg-002", "Permission denied")
                .containsEntry("msg-003", "Message not found")
                .containsEntry("msg-004", "Invalid request");
        }

        @Test
        @DisplayName("Should create ResponseMetadata with correct durationMs")
        void shouldCreateResponseMetadataWithDuration() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_DELETE);
            serviceResult.addSuccess("msg-001");
            serviceResult.incrementBatchesProcessed();

            // Simulate some processing time
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            serviceResult.markCompleted();

            long actualDuration = serviceResult.getDurationMs();

            // Act
            BulkDeleteResponse response = responseMapper.toBulkDeleteResponse(serviceResult);

            // Assert
            assertThat(response.getMetadata()).isNotNull();
            assertThat(response.getMetadata().getDurationMs()).isEqualTo(actualDuration);
            assertThat(response.getMetadata().getDurationMs()).isGreaterThanOrEqualTo(0L);
        }

        @Test
        @DisplayName("Should calculate quotaUsed correctly - single batch")
        void shouldCalculateQuotaUsedForSingleBatch() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_DELETE);
            serviceResult.addSuccess("msg-001");
            serviceResult.addSuccess("msg-002");
            serviceResult.incrementBatchesProcessed(); // 1 batch
            serviceResult.markCompleted();

            // Act
            BulkDeleteResponse response = responseMapper.toBulkDeleteResponse(serviceResult);

            // Assert
            assertThat(response.getMetadata().getQuotaUsed()).isEqualTo(50); // 1 batch * 50
        }

        @Test
        @DisplayName("Should calculate quotaUsed correctly - multiple batches")
        void shouldCalculateQuotaUsedForMultipleBatches() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_DELETE);
            for (int i = 1; i <= 10; i++) {
                serviceResult.addSuccess("msg-" + String.format("%03d", i));
            }
            serviceResult.incrementBatchesProcessed(); // Batch 1
            serviceResult.incrementBatchesProcessed(); // Batch 2
            serviceResult.incrementBatchesProcessed(); // Batch 3
            serviceResult.markCompleted();

            // Act
            BulkDeleteResponse response = responseMapper.toBulkDeleteResponse(serviceResult);

            // Assert
            assertThat(response.getMetadata().getQuotaUsed()).isEqualTo(150); // 3 batches * 50
        }

        @Test
        @DisplayName("Should calculate quotaUsed as zero when no batches processed")
        void shouldCalculateQuotaUsedAsZeroForNoBatches() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_DELETE);
            // No batches processed
            serviceResult.markCompleted();

            // Act
            BulkDeleteResponse response = responseMapper.toBulkDeleteResponse(serviceResult);

            // Assert
            assertThat(response.getMetadata().getQuotaUsed()).isEqualTo(0); // 0 batches * 50
        }

        @Test
        @DisplayName("Should handle empty successful operations list")
        void shouldHandleEmptySuccessfulOperations() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_DELETE);
            serviceResult.addFailure("msg-001", "Failed operation");
            serviceResult.incrementBatchesProcessed();
            serviceResult.markCompleted();

            // Act
            BulkDeleteResponse response = responseMapper.toBulkDeleteResponse(serviceResult);

            // Assert
            assertThat(response.getSuccessfulOperations())
                .isNotNull()
                .isEmpty();
            assertThat(response.getSuccessCount()).isEqualTo(0);
            assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILURE);
        }

        @Test
        @DisplayName("Should handle empty failed operations map")
        void shouldHandleEmptyFailedOperations() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_DELETE);
            serviceResult.addSuccess("msg-001");
            serviceResult.addSuccess("msg-002");
            serviceResult.incrementBatchesProcessed();
            serviceResult.markCompleted();

            // Act
            BulkDeleteResponse response = responseMapper.toBulkDeleteResponse(serviceResult);

            // Assert
            assertThat(response.getFailedOperations())
                .isNotNull()
                .isEmpty();
            assertThat(response.getFailureCount()).isEqualTo(0);
            assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        }

        @Test
        @DisplayName("Should handle partial success scenario correctly")
        void shouldHandlePartialSuccessScenario() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_DELETE);
            // Add 7 successes and 3 failures
            for (int i = 1; i <= 7; i++) {
                serviceResult.addSuccess("success-" + i);
            }
            for (int i = 1; i <= 3; i++) {
                serviceResult.addFailure("failed-" + i, "Error " + i);
            }
            serviceResult.incrementBatchesProcessed();
            serviceResult.markCompleted();

            // Act
            BulkDeleteResponse response = responseMapper.toBulkDeleteResponse(serviceResult);

            // Assert
            assertThat(response.getStatus()).isEqualTo(OperationStatus.PARTIAL_SUCCESS);
            assertThat(response.getTotalOperations()).isEqualTo(10);
            assertThat(response.getSuccessCount()).isEqualTo(7);
            assertThat(response.getFailureCount()).isEqualTo(3);
            assertThat(response.getSuccessfulOperations()).hasSize(7);
            assertThat(response.getFailedOperations()).hasSize(3);
        }

        @Test
        @DisplayName("Should handle complete success scenario")
        void shouldHandleCompleteSuccessScenario() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_DELETE);
            serviceResult.addSuccess("msg-001");
            serviceResult.addSuccess("msg-002");
            serviceResult.addSuccess("msg-003");
            serviceResult.incrementBatchesProcessed();
            serviceResult.markCompleted();

            // Act
            BulkDeleteResponse response = responseMapper.toBulkDeleteResponse(serviceResult);

            // Assert
            assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
            assertThat(response.getTotalOperations()).isEqualTo(3);
            assertThat(response.getSuccessCount()).isEqualTo(3);
            assertThat(response.getFailureCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should handle complete failure scenario")
        void shouldHandleCompleteFailureScenario() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_DELETE);
            serviceResult.addFailure("msg-001", "Error 1");
            serviceResult.addFailure("msg-002", "Error 2");
            serviceResult.addFailure("msg-003", "Error 3");
            serviceResult.incrementBatchesProcessed();
            serviceResult.markCompleted();

            // Act
            BulkDeleteResponse response = responseMapper.toBulkDeleteResponse(serviceResult);

            // Assert
            assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILURE);
            assertThat(response.getTotalOperations()).isEqualTo(3);
            assertThat(response.getSuccessCount()).isEqualTo(0);
            assertThat(response.getFailureCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("Should handle no results scenario")
        void shouldHandleNoResultsScenario() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_DELETE);
            // No operations added
            serviceResult.markCompleted();

            // Act
            BulkDeleteResponse response = responseMapper.toBulkDeleteResponse(serviceResult);

            // Assert
            assertThat(response.getStatus()).isEqualTo(OperationStatus.NO_RESULTS);
            assertThat(response.getTotalOperations()).isEqualTo(0);
            assertThat(response.getSuccessCount()).isEqualTo(0);
            assertThat(response.getFailureCount()).isEqualTo(0);
            assertThat(response.getSuccessfulOperations()).isEmpty();
            assertThat(response.getFailedOperations()).isEmpty();
        }

        @Test
        @DisplayName("Should create independent copies of collections")
        void shouldCreateIndependentCopiesOfCollections() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_DELETE);
            serviceResult.addSuccess("msg-001");
            serviceResult.addFailure("msg-002", "Error");
            serviceResult.markCompleted();

            // Act
            BulkDeleteResponse response = responseMapper.toBulkDeleteResponse(serviceResult);

            // Modify service result after mapping
            serviceResult.addSuccess("msg-003");
            serviceResult.addFailure("msg-004", "Another error");

            // Assert - response should not be affected by subsequent changes
            assertThat(response.getSuccessfulOperations()).hasSize(1);
            assertThat(response.getFailedOperations()).hasSize(1);
            assertThat(serviceResult.getSuccessCount()).isEqualTo(2); // Original changed
            assertThat(serviceResult.getFailureCount()).isEqualTo(2); // Original changed
        }
    }

    @Nested
    @DisplayName("toLabelModificationResponse Tests")
    class ToLabelModificationResponseTests {

        @Test
        @DisplayName("Should map status correctly from BulkOperationResult")
        void shouldMapStatusCorrectly() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_MODIFY);
            serviceResult.addSuccess("msg-001");
            serviceResult.addSuccess("msg-002");
            serviceResult.incrementBatchesProcessed();
            serviceResult.markCompleted();

            List<String> labelsAdded = Arrays.asList("INBOX", "IMPORTANT");
            List<String> labelsRemoved = Collections.emptyList();

            // Act
            LabelModificationResponse response = responseMapper.toLabelModificationResponse(
                serviceResult, labelsAdded, labelsRemoved);

            // Assert
            assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        }

        @Test
        @DisplayName("Should set messagesModified from successCount")
        void shouldSetMessagesModifiedFromSuccessCount() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_MODIFY);
            serviceResult.addSuccess("msg-001");
            serviceResult.addSuccess("msg-002");
            serviceResult.addSuccess("msg-003");
            serviceResult.addSuccess("msg-004");
            serviceResult.addSuccess("msg-005");
            serviceResult.addFailure("msg-006", "Error");
            serviceResult.incrementBatchesProcessed();
            serviceResult.markCompleted();

            List<String> labelsAdded = Arrays.asList("STARRED");
            List<String> labelsRemoved = Arrays.asList("INBOX");

            // Act
            LabelModificationResponse response = responseMapper.toLabelModificationResponse(
                serviceResult, labelsAdded, labelsRemoved);

            // Assert
            assertThat(response.getMessagesModified()).isEqualTo(5);
        }

        @Test
        @DisplayName("Should set labelsAdded correctly")
        void shouldSetLabelsAddedCorrectly() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_MODIFY);
            serviceResult.addSuccess("msg-001");
            serviceResult.incrementBatchesProcessed();
            serviceResult.markCompleted();

            List<String> labelsAdded = Arrays.asList("INBOX", "IMPORTANT", "CATEGORY_PERSONAL");
            List<String> labelsRemoved = Collections.emptyList();

            // Act
            LabelModificationResponse response = responseMapper.toLabelModificationResponse(
                serviceResult, labelsAdded, labelsRemoved);

            // Assert
            assertThat(response.getLabelsAdded())
                .isNotNull()
                .hasSize(3)
                .containsExactly("INBOX", "IMPORTANT", "CATEGORY_PERSONAL");
        }

        @Test
        @DisplayName("Should set labelsRemoved correctly")
        void shouldSetLabelsRemovedCorrectly() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_MODIFY);
            serviceResult.addSuccess("msg-001");
            serviceResult.incrementBatchesProcessed();
            serviceResult.markCompleted();

            List<String> labelsAdded = Collections.emptyList();
            List<String> labelsRemoved = Arrays.asList("SPAM", "TRASH");

            // Act
            LabelModificationResponse response = responseMapper.toLabelModificationResponse(
                serviceResult, labelsAdded, labelsRemoved);

            // Assert
            assertThat(response.getLabelsRemoved())
                .isNotNull()
                .hasSize(2)
                .containsExactly("SPAM", "TRASH");
        }

        @Test
        @DisplayName("Should set affectedMessageIds from successfulOperations")
        void shouldSetAffectedMessageIdsFromSuccessfulOperations() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_MODIFY);
            serviceResult.addSuccess("msg-001");
            serviceResult.addSuccess("msg-002");
            serviceResult.addSuccess("msg-003");
            serviceResult.incrementBatchesProcessed();
            serviceResult.markCompleted();

            List<String> labelsAdded = Arrays.asList("STARRED");
            List<String> labelsRemoved = Collections.emptyList();

            // Act
            LabelModificationResponse response = responseMapper.toLabelModificationResponse(
                serviceResult, labelsAdded, labelsRemoved);

            // Assert
            assertThat(response.getAffectedMessageIds())
                .isNotNull()
                .hasSize(3)
                .containsExactlyInAnyOrder("msg-001", "msg-002", "msg-003");
        }

        @Test
        @DisplayName("Should create ResponseMetadata with correct durationMs")
        void shouldCreateResponseMetadataWithDuration() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_MODIFY);
            serviceResult.addSuccess("msg-001");
            serviceResult.incrementBatchesProcessed();

            // Simulate some processing time
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            serviceResult.markCompleted();

            long actualDuration = serviceResult.getDurationMs();
            List<String> labelsAdded = Arrays.asList("STARRED");
            List<String> labelsRemoved = Collections.emptyList();

            // Act
            LabelModificationResponse response = responseMapper.toLabelModificationResponse(
                serviceResult, labelsAdded, labelsRemoved);

            // Assert
            assertThat(response.getMetadata()).isNotNull();
            assertThat(response.getMetadata().getDurationMs()).isEqualTo(actualDuration);
            assertThat(response.getMetadata().getDurationMs()).isGreaterThanOrEqualTo(0L);
        }

        @Test
        @DisplayName("Should calculate quotaUsed correctly - single batch")
        void shouldCalculateQuotaUsedForSingleBatch() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_MODIFY);
            serviceResult.addSuccess("msg-001");
            serviceResult.incrementBatchesProcessed(); // 1 batch
            serviceResult.markCompleted();

            List<String> labelsAdded = Arrays.asList("STARRED");
            List<String> labelsRemoved = Collections.emptyList();

            // Act
            LabelModificationResponse response = responseMapper.toLabelModificationResponse(
                serviceResult, labelsAdded, labelsRemoved);

            // Assert
            assertThat(response.getMetadata().getQuotaUsed()).isEqualTo(50); // 1 batch * 50
        }

        @Test
        @DisplayName("Should calculate quotaUsed correctly - multiple batches")
        void shouldCalculateQuotaUsedForMultipleBatches() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_MODIFY);
            for (int i = 1; i <= 15; i++) {
                serviceResult.addSuccess("msg-" + String.format("%03d", i));
            }
            serviceResult.incrementBatchesProcessed(); // Batch 1
            serviceResult.incrementBatchesProcessed(); // Batch 2
            serviceResult.incrementBatchesProcessed(); // Batch 3
            serviceResult.incrementBatchesProcessed(); // Batch 4
            serviceResult.incrementBatchesProcessed(); // Batch 5
            serviceResult.markCompleted();

            List<String> labelsAdded = Arrays.asList("INBOX");
            List<String> labelsRemoved = Arrays.asList("SPAM");

            // Act
            LabelModificationResponse response = responseMapper.toLabelModificationResponse(
                serviceResult, labelsAdded, labelsRemoved);

            // Assert
            assertThat(response.getMetadata().getQuotaUsed()).isEqualTo(250); // 5 batches * 50
        }

        @Test
        @DisplayName("Should handle empty labelsAdded list")
        void shouldHandleEmptyLabelsAddedList() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_MODIFY);
            serviceResult.addSuccess("msg-001");
            serviceResult.incrementBatchesProcessed();
            serviceResult.markCompleted();

            List<String> labelsAdded = Collections.emptyList();
            List<String> labelsRemoved = Arrays.asList("SPAM");

            // Act
            LabelModificationResponse response = responseMapper.toLabelModificationResponse(
                serviceResult, labelsAdded, labelsRemoved);

            // Assert
            assertThat(response.getLabelsAdded())
                .isNotNull()
                .isEmpty();
        }

        @Test
        @DisplayName("Should handle empty labelsRemoved list")
        void shouldHandleEmptyLabelsRemovedList() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_MODIFY);
            serviceResult.addSuccess("msg-001");
            serviceResult.incrementBatchesProcessed();
            serviceResult.markCompleted();

            List<String> labelsAdded = Arrays.asList("INBOX");
            List<String> labelsRemoved = Collections.emptyList();

            // Act
            LabelModificationResponse response = responseMapper.toLabelModificationResponse(
                serviceResult, labelsAdded, labelsRemoved);

            // Assert
            assertThat(response.getLabelsRemoved())
                .isNotNull()
                .isEmpty();
        }

        @Test
        @DisplayName("Should handle null labelsAdded list")
        void shouldHandleNullLabelsAddedList() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_MODIFY);
            serviceResult.addSuccess("msg-001");
            serviceResult.incrementBatchesProcessed();
            serviceResult.markCompleted();

            List<String> labelsAdded = null;
            List<String> labelsRemoved = Arrays.asList("SPAM");

            // Act
            LabelModificationResponse response = responseMapper.toLabelModificationResponse(
                serviceResult, labelsAdded, labelsRemoved);

            // Assert
            assertThat(response.getLabelsAdded()).isNull();
        }

        @Test
        @DisplayName("Should handle null labelsRemoved list")
        void shouldHandleNullLabelsRemovedList() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_MODIFY);
            serviceResult.addSuccess("msg-001");
            serviceResult.incrementBatchesProcessed();
            serviceResult.markCompleted();

            List<String> labelsAdded = Arrays.asList("INBOX");
            List<String> labelsRemoved = null;

            // Act
            LabelModificationResponse response = responseMapper.toLabelModificationResponse(
                serviceResult, labelsAdded, labelsRemoved);

            // Assert
            assertThat(response.getLabelsRemoved()).isNull();
        }

        @Test
        @DisplayName("Should handle both label lists being null")
        void shouldHandleBothLabelListsBeingNull() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_MODIFY);
            serviceResult.addSuccess("msg-001");
            serviceResult.incrementBatchesProcessed();
            serviceResult.markCompleted();

            // Act
            LabelModificationResponse response = responseMapper.toLabelModificationResponse(
                serviceResult, null, null);

            // Assert
            assertThat(response.getLabelsAdded()).isNull();
            assertThat(response.getLabelsRemoved()).isNull();
            assertThat(response.getMessagesModified()).isEqualTo(1);
            assertThat(response.getAffectedMessageIds()).hasSize(1);
        }

        @Test
        @DisplayName("Should handle both label lists being empty")
        void shouldHandleBothLabelListsBeingEmpty() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_MODIFY);
            serviceResult.addSuccess("msg-001");
            serviceResult.incrementBatchesProcessed();
            serviceResult.markCompleted();

            List<String> labelsAdded = Collections.emptyList();
            List<String> labelsRemoved = Collections.emptyList();

            // Act
            LabelModificationResponse response = responseMapper.toLabelModificationResponse(
                serviceResult, labelsAdded, labelsRemoved);

            // Assert
            assertThat(response.getLabelsAdded()).isEmpty();
            assertThat(response.getLabelsRemoved()).isEmpty();
            assertThat(response.getMessagesModified()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should handle partial success with label modifications")
        void shouldHandlePartialSuccessWithLabelModifications() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_MODIFY);
            serviceResult.addSuccess("msg-001");
            serviceResult.addSuccess("msg-002");
            serviceResult.addSuccess("msg-003");
            serviceResult.addFailure("msg-004", "Message not found");
            serviceResult.addFailure("msg-005", "Permission denied");
            serviceResult.incrementBatchesProcessed();
            serviceResult.markCompleted();

            List<String> labelsAdded = Arrays.asList("IMPORTANT", "STARRED");
            List<String> labelsRemoved = Arrays.asList("INBOX");

            // Act
            LabelModificationResponse response = responseMapper.toLabelModificationResponse(
                serviceResult, labelsAdded, labelsRemoved);

            // Assert
            assertThat(response.getStatus()).isEqualTo(OperationStatus.PARTIAL_SUCCESS);
            assertThat(response.getMessagesModified()).isEqualTo(3);
            assertThat(response.getLabelsAdded()).hasSize(2);
            assertThat(response.getLabelsRemoved()).hasSize(1);
            assertThat(response.getAffectedMessageIds()).hasSize(3);
        }

        @Test
        @DisplayName("Should handle complete failure scenario")
        void shouldHandleCompleteFailureScenario() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_MODIFY);
            serviceResult.addFailure("msg-001", "Error 1");
            serviceResult.addFailure("msg-002", "Error 2");
            serviceResult.incrementBatchesProcessed();
            serviceResult.markCompleted();

            List<String> labelsAdded = Arrays.asList("STARRED");
            List<String> labelsRemoved = Collections.emptyList();

            // Act
            LabelModificationResponse response = responseMapper.toLabelModificationResponse(
                serviceResult, labelsAdded, labelsRemoved);

            // Assert
            assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILURE);
            assertThat(response.getMessagesModified()).isEqualTo(0);
            assertThat(response.getAffectedMessageIds()).isEmpty();
        }

        @Test
        @DisplayName("Should create independent copy of affectedMessageIds")
        void shouldCreateIndependentCopyOfAffectedMessageIds() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_MODIFY);
            serviceResult.addSuccess("msg-001");
            serviceResult.addSuccess("msg-002");
            serviceResult.incrementBatchesProcessed();
            serviceResult.markCompleted();

            List<String> labelsAdded = Arrays.asList("STARRED");
            List<String> labelsRemoved = Collections.emptyList();

            // Act
            LabelModificationResponse response = responseMapper.toLabelModificationResponse(
                serviceResult, labelsAdded, labelsRemoved);

            // Modify service result after mapping
            serviceResult.addSuccess("msg-003");

            // Assert - response should not be affected by subsequent changes
            assertThat(response.getAffectedMessageIds()).hasSize(2);
            assertThat(serviceResult.getSuccessCount()).isEqualTo(3); // Original changed
        }

        @Test
        @DisplayName("Should handle large number of labels being modified")
        void shouldHandleLargeNumberOfLabelsBeingModified() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_MODIFY);
            for (int i = 1; i <= 100; i++) {
                serviceResult.addSuccess("msg-" + String.format("%03d", i));
            }
            serviceResult.incrementBatchesProcessed();
            serviceResult.markCompleted();

            List<String> labelsAdded = Arrays.asList(
                "INBOX", "IMPORTANT", "STARRED", "CATEGORY_PERSONAL", "CATEGORY_SOCIAL");
            List<String> labelsRemoved = Arrays.asList(
                "SPAM", "TRASH", "UNREAD");

            // Act
            LabelModificationResponse response = responseMapper.toLabelModificationResponse(
                serviceResult, labelsAdded, labelsRemoved);

            // Assert
            assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
            assertThat(response.getMessagesModified()).isEqualTo(100);
            assertThat(response.getLabelsAdded()).hasSize(5);
            assertThat(response.getLabelsRemoved()).hasSize(3);
            assertThat(response.getAffectedMessageIds()).hasSize(100);
        }
    }

    @Nested
    @DisplayName("Quota Calculation Tests")
    class QuotaCalculationTests {

        @ParameterizedTest(name = "{0} batches should use {1} quota units")
        @MethodSource("quotaCalculationTestCases")
        @DisplayName("Should calculate quota correctly for various batch counts")
        void shouldCalculateQuotaCorrectly(int batchCount, int expectedQuota) {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_DELETE);
            serviceResult.addSuccess("msg-001");
            for (int i = 0; i < batchCount; i++) {
                serviceResult.incrementBatchesProcessed();
            }
            serviceResult.markCompleted();

            // Act
            BulkDeleteResponse response = responseMapper.toBulkDeleteResponse(serviceResult);

            // Assert
            assertThat(response.getMetadata().getQuotaUsed()).isEqualTo(expectedQuota);
        }

        static Stream<Arguments> quotaCalculationTestCases() {
            return Stream.of(
                Arguments.of(0, 0),     // 0 batches * 50 = 0
                Arguments.of(1, 50),    // 1 batch * 50 = 50
                Arguments.of(2, 100),   // 2 batches * 50 = 100
                Arguments.of(5, 250),   // 5 batches * 50 = 250
                Arguments.of(10, 500),  // 10 batches * 50 = 500
                Arguments.of(20, 1000), // 20 batches * 50 = 1000
                Arguments.of(100, 5000) // 100 batches * 50 = 5000
            );
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle BulkOperationResult that is not yet completed")
        void shouldHandleNotYetCompletedResult() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_DELETE);
            serviceResult.addSuccess("msg-001");
            serviceResult.incrementBatchesProcessed();
            // Note: NOT calling markCompleted()

            // Act
            BulkDeleteResponse response = responseMapper.toBulkDeleteResponse(serviceResult);

            // Assert
            assertThat(response.getMetadata().getDurationMs()).isEqualTo(-1L);
        }

        @Test
        @DisplayName("Should handle very large operation counts")
        void shouldHandleVeryLargeOperationCounts() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_DELETE);
            for (int i = 1; i <= 1000; i++) {
                serviceResult.addSuccess("msg-" + String.format("%04d", i));
            }
            for (int i = 1; i <= 10; i++) {
                serviceResult.incrementBatchesProcessed();
            }
            serviceResult.markCompleted();

            // Act
            BulkDeleteResponse response = responseMapper.toBulkDeleteResponse(serviceResult);

            // Assert
            assertThat(response.getSuccessfulOperations()).hasSize(1000);
            assertThat(response.getTotalOperations()).isEqualTo(1000);
            assertThat(response.getMetadata().getQuotaUsed()).isEqualTo(500);
        }

        @Test
        @DisplayName("Should preserve metadata timestamp")
        void shouldPreserveMetadataTimestamp() {
            // Arrange
            BulkOperationResult serviceResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_DELETE);
            serviceResult.addSuccess("msg-001");
            serviceResult.incrementBatchesProcessed();
            serviceResult.markCompleted();

            // Act
            BulkDeleteResponse response = responseMapper.toBulkDeleteResponse(serviceResult);

            // Assert
            assertThat(response.getMetadata().getTimestamp()).isNotNull();
        }
    }
}