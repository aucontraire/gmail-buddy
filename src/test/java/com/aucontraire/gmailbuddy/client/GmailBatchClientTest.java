package com.aucontraire.gmailbuddy.client;

import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import com.aucontraire.gmailbuddy.exception.BatchOperationException;
import com.aucontraire.gmailbuddy.service.BulkOperationResult;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.HttpHeaders;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.ModifyMessageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GmailBatchClient Tests")
class GmailBatchClientTest {

    @Mock
    private GmailBuddyProperties properties;

    @Mock
    private GmailBuddyProperties.GmailApi gmailApi;

    @Mock
    private GmailBuddyProperties.GmailApi.RateLimit rateLimit;

    @Mock
    private GmailBuddyProperties.GmailApi.RateLimit.BatchOperations batchOperations;

    @Mock
    private Gmail gmail;

    @Mock
    private Gmail.Users users;

    @Mock
    private Gmail.Users.Messages messages;

    @Mock
    private Gmail.Users.Messages.Delete deleteRequest;

    @Mock
    private Gmail.Users.Messages.Modify modifyRequest;

    @Mock
    private BatchRequest batchRequest;

    private GmailBatchClient gmailBatchClient;

    @BeforeEach
    void setUp() {
        // Setup properties mock hierarchy
        when(properties.gmailApi()).thenReturn(gmailApi);
        when(gmailApi.rateLimit()).thenReturn(rateLimit);
        when(rateLimit.batchOperations()).thenReturn(batchOperations);

        // Setup default batch operation values
        when(batchOperations.maxBatchSize()).thenReturn(15);
        when(batchOperations.delayBetweenBatchesMs()).thenReturn(0L); // No delay for tests
        when(batchOperations.maxRetryAttempts()).thenReturn(4);
        when(batchOperations.initialBackoffMs()).thenReturn(100L); // Shorter for tests
        when(batchOperations.backoffMultiplier()).thenReturn(2.5);
        when(batchOperations.maxBackoffMs()).thenReturn(1000L); // Shorter for tests
        when(batchOperations.microDelayBetweenOperationsMs()).thenReturn(0L); // No micro-delay for tests

        gmailBatchClient = new GmailBatchClient(properties);

        // Setup Gmail mock hierarchy
        when(gmail.users()).thenReturn(users);
        when(users.messages()).thenReturn(messages);
        when(gmail.batch()).thenReturn(batchRequest);
    }

    @Test
    @DisplayName("Should return correct maximum batch size")
    void getMaxBatchSize_ShouldReturnCorrectValue() {
        // Act
        int maxBatchSize = gmailBatchClient.getMaxBatchSize();

        // Assert - Should return the configured value (15) which is less than Gmail API limit (100)
        assertThat(maxBatchSize).isEqualTo(15);
    }

    @Test
    @DisplayName("Should successfully batch delete single message")
    void batchDeleteMessages_SingleMessage_ShouldSucceed() throws IOException {
        // Arrange
        String userId = "me";
        String messageId = "msg123";
        List<String> messageIds = List.of(messageId);

        when(messages.delete(userId, messageId)).thenReturn(deleteRequest);
        // queue() method returns void, no need to mock its return value

        // Act
        BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

        // Assert
        verify(batchRequest).execute();
        assertThat(result.getOperationType()).isEqualTo("DELETE");
        assertThat(result.getTotalOperations()).isEqualTo(0); // No callbacks simulated yet
    }

    @Test
    @DisplayName("Should split large message list into multiple batches")
    void batchDeleteMessages_LargeList_ShouldSplitIntoBatches() throws IOException {
        // Arrange
        String userId = "me";
        List<String> messageIds = IntStream.range(1, 251) // 250 messages
                .mapToObj(i -> "msg" + i)
                .toList();

        // Mock delete requests for all messages
        for (String messageId : messageIds) {
            Gmail.Users.Messages.Delete mockDelete = mock(Gmail.Users.Messages.Delete.class);
            when(messages.delete(userId, messageId)).thenReturn(mockDelete);
            // queue() method returns void, no need to mock its return value
        }

        // Act
        BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

        // Assert
        // Should have been split into 3 batches (100 + 100 + 50)
        verify(batchRequest, times(3)).execute();
        assertThat(result.getOperationType()).isEqualTo("DELETE");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 50, 100, 101, 200, 500, 1000})
    @DisplayName("Should handle various batch sizes correctly")
    void batchDeleteMessages_VariousSizes_ShouldHandleCorrectly(int messageCount) throws IOException {
        // Arrange
        String userId = "me";
        List<String> messageIds = IntStream.range(1, messageCount + 1)
                .mapToObj(i -> "msg" + i)
                .toList();

        // Mock delete requests
        for (String messageId : messageIds) {
            Gmail.Users.Messages.Delete mockDelete = mock(Gmail.Users.Messages.Delete.class);
            when(messages.delete(userId, messageId)).thenReturn(mockDelete);
            // queue() method returns void, no need to mock its return value
        }

        // Act
        BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

        // Assert
        int expectedBatches = messageCount == 0 ? 0 : (int) Math.ceil((double) messageCount / 100);
        verify(batchRequest, times(expectedBatches)).execute();
    }

    @Test
    @DisplayName("Should handle empty message list gracefully")
    void batchDeleteMessages_EmptyList_ShouldHandleGracefully() throws IOException {
        // Arrange
        String userId = "me";
        List<String> messageIds = Collections.emptyList();

        // Act
        BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

        // Assert
        verify(batchRequest, never()).execute();
        assertThat(result.getTotalOperations()).isEqualTo(0);
        assertThat(result.getSuccessCount()).isEqualTo(0);
        assertThat(result.getFailureCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should continue processing batches after one batch fails")
    void batchDeleteMessages_OneBatchFails_ShouldContinueProcessing() throws IOException {
        // Arrange
        String userId = "me";
        List<String> messageIds = IntStream.range(1, 201) // 200 messages (2 batches)
                .mapToObj(i -> "msg" + i)
                .toList();

        // Mock first batch to throw exception
        for (int i = 0; i < 100; i++) {
            String messageId = "msg" + (i + 1);
            Gmail.Users.Messages.Delete mockDelete = mock(Gmail.Users.Messages.Delete.class);
            when(messages.delete(userId, messageId)).thenReturn(mockDelete);
            // queue() method returns void, no need to mock its return value
        }

        // Mock second batch normally
        for (int i = 100; i < 200; i++) {
            String messageId = "msg" + (i + 1);
            Gmail.Users.Messages.Delete mockDelete = mock(Gmail.Users.Messages.Delete.class);
            when(messages.delete(userId, messageId)).thenReturn(mockDelete);
            // queue() method returns void, no need to mock its return value
        }

        // Make first batch execution fail
        doThrow(new IOException("Network error"))
                .doNothing() // Second call succeeds
                .when(batchRequest).execute();

        // Act
        BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

        // Assert
        verify(batchRequest, times(2)).execute();
        assertThat(result.getFailureCount()).isEqualTo(100); // First batch failed
        assertThat(result.hasFailures()).isTrue();
    }

    @Test
    @DisplayName("Should successfully batch modify labels for single message")
    void batchModifyLabels_SingleMessage_ShouldSucceed() throws IOException {
        // Arrange
        String userId = "me";
        String messageId = "msg123";
        List<String> messageIds = List.of(messageId);
        ModifyMessageRequest modifyRequest = new ModifyMessageRequest()
                .setAddLabelIds(List.of("INBOX"))
                .setRemoveLabelIds(List.of("UNREAD"));

        Gmail.Users.Messages.Modify mockModifyRequest = mock(Gmail.Users.Messages.Modify.class);
        when(messages.modify(userId, messageId, modifyRequest)).thenReturn(mockModifyRequest);
        // queue() method returns void, no need to mock its return value

        // Act
        BulkOperationResult result = gmailBatchClient.batchModifyLabels(gmail, userId, messageIds, modifyRequest);

        // Assert
        verify(batchRequest).execute();
        assertThat(result.getOperationType()).isEqualTo("MODIFY_LABELS");
    }

    @Test
    @DisplayName("Should validate complete success result")
    void validateBatchResult_CompleteSuccess_ShouldNotThrowException() {
        // Arrange
        BulkOperationResult result = new BulkOperationResult("DELETE");
        result.addSuccess("msg1");
        result.addSuccess("msg2");
        result.markCompleted();

        // Act & Assert
        assertThatNoException().isThrownBy(() ->
            gmailBatchClient.validateBatchResult(result, true));
        assertThatNoException().isThrownBy(() ->
            gmailBatchClient.validateBatchResult(result, false));
    }

    @Test
    @DisplayName("Should validate empty operation result without exception")
    void validateBatchResult_EmptyOperations_ShouldNotThrowException() {
        // Arrange
        BulkOperationResult result = new BulkOperationResult("DELETE");
        result.markCompleted();

        // Act & Assert
        assertThatNoException().isThrownBy(() ->
            gmailBatchClient.validateBatchResult(result, true));
        assertThatNoException().isThrownBy(() ->
            gmailBatchClient.validateBatchResult(result, false));
    }

    @Test
    @DisplayName("Should throw exception for complete failure")
    void validateBatchResult_CompleteFailure_ShouldThrowException() {
        // Arrange
        BulkOperationResult result = new BulkOperationResult("DELETE");
        result.addFailure("msg1", "Error 1");
        result.addFailure("msg2", "Error 2");
        result.markCompleted();

        // Act & Assert
        assertThatThrownBy(() -> gmailBatchClient.validateBatchResult(result, true))
                .isInstanceOf(BatchOperationException.class)
                .hasMessageContaining("completely failed");

        assertThatThrownBy(() -> gmailBatchClient.validateBatchResult(result, false))
                .isInstanceOf(BatchOperationException.class)
                .hasMessageContaining("completely failed");
    }

    @Test
    @DisplayName("Should throw exception for partial failure when failOnPartialFailure is true")
    void validateBatchResult_PartialFailureStrict_ShouldThrowException() {
        // Arrange
        BulkOperationResult result = new BulkOperationResult("DELETE");
        result.addSuccess("msg1");
        result.addFailure("msg2", "Error 2");
        result.markCompleted();

        // Act & Assert
        assertThatThrownBy(() -> gmailBatchClient.validateBatchResult(result, true))
                .isInstanceOf(BatchOperationException.class)
                .hasMessageContaining("partially failed");
    }

    @Test
    @DisplayName("Should not throw exception for partial failure when failOnPartialFailure is false")
    void validateBatchResult_PartialFailureLenient_ShouldNotThrowException() {
        // Arrange
        BulkOperationResult result = new BulkOperationResult("DELETE");
        result.addSuccess("msg1");
        result.addFailure("msg2", "Error 2");
        result.markCompleted();

        // Act & Assert
        assertThatNoException().isThrownBy(() ->
            gmailBatchClient.validateBatchResult(result, false));
    }

    @ParameterizedTest
    @MethodSource("retryableErrorMessages")
    @DisplayName("Should identify retryable errors correctly")
    void areFailuresRetryable_RetryableErrors_ShouldReturnTrue(String errorMessage) {
        // Arrange
        BulkOperationResult result = new BulkOperationResult("DELETE");
        result.addFailure("msg1", errorMessage);
        result.markCompleted();

        // Act & Assert
        assertThat(gmailBatchClient.areFailuresRetryable(result)).isTrue();
    }

    static Stream<Arguments> retryableErrorMessages() {
        return Stream.of(
                Arguments.of("Connection timeout"),
                Arguments.of("Temporary service unavailable"),
                Arguments.of("Rate limit exceeded"),
                Arguments.of("Quota exceeded for today"),
                Arguments.of("Internal server error"),
                Arguments.of("Backend error occurred"),
                Arguments.of("TIMEOUT error in processing"),
                Arguments.of("Service temporarily unavailable")
        );
    }

    @ParameterizedTest
    @MethodSource("nonRetryableErrorMessages")
    @DisplayName("Should identify non-retryable errors correctly")
    void areFailuresRetryable_NonRetryableErrors_ShouldReturnFalse(String errorMessage) {
        // Arrange
        BulkOperationResult result = new BulkOperationResult("DELETE");
        result.addFailure("msg1", errorMessage);
        result.markCompleted();

        // Act & Assert
        assertThat(gmailBatchClient.areFailuresRetryable(result)).isFalse();
    }

    static Stream<Arguments> nonRetryableErrorMessages() {
        return Stream.of(
                Arguments.of("Message not found"),
                Arguments.of("Invalid message ID"),
                Arguments.of("Permission denied"),
                Arguments.of("Invalid label"),
                Arguments.of("Authentication failed"),
                Arguments.of((String) null) // null error message
        );
    }

    @Test
    @DisplayName("Should return empty list for retryable failures when no failures exist")
    void getRetryableFailures_NoFailures_ShouldReturnEmptyList() {
        // Arrange
        BulkOperationResult result = new BulkOperationResult("DELETE");
        result.addSuccess("msg1");
        result.markCompleted();

        // Act
        List<String> retryableFailures = gmailBatchClient.getRetryableFailures(result);

        // Assert
        assertThat(retryableFailures).isEmpty();
    }

    @Test
    @DisplayName("Should return only retryable failures from mixed failure types")
    void getRetryableFailures_MixedFailures_ShouldReturnOnlyRetryable() {
        // Arrange
        BulkOperationResult result = new BulkOperationResult("DELETE");
        result.addFailure("msg1", "Connection timeout"); // retryable
        result.addFailure("msg2", "Message not found"); // non-retryable
        result.addFailure("msg3", "Rate limit exceeded"); // retryable
        result.addFailure("msg4", "Permission denied"); // non-retryable
        result.markCompleted();

        // Act
        List<String> retryableFailures = gmailBatchClient.getRetryableFailures(result);

        // Assert
        assertThat(retryableFailures)
                .hasSize(2)
                .containsExactlyInAnyOrder("msg1", "msg3");
    }

    @Test
    @DisplayName("Should handle null error message gracefully when checking retryability")
    void isRetryableError_NullErrorMessage_ShouldReturnFalse() {
        // Arrange
        BulkOperationResult result = new BulkOperationResult("DELETE");
        result.addFailure("msg1", null);
        result.markCompleted();

        // Act & Assert
        assertThat(gmailBatchClient.areFailuresRetryable(result)).isFalse();
    }

    @Test
    @DisplayName("Should handle large scale operations efficiently")
    void batchDeleteMessages_LargeScale_ShouldHandleEfficiently() throws IOException {
        // Arrange
        String userId = "me";
        // Create 1000 message IDs
        List<String> messageIds = IntStream.range(1, 1001)
                .mapToObj(i -> "msg" + i)
                .toList();

        // Mock delete requests
        for (String messageId : messageIds) {
            Gmail.Users.Messages.Delete mockDelete = mock(Gmail.Users.Messages.Delete.class);
            when(messages.delete(userId, messageId)).thenReturn(mockDelete);
            // queue() method returns void, no need to mock its return value
        }

        long startTime = System.currentTimeMillis();

        // Act
        BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

        long duration = System.currentTimeMillis() - startTime;

        // Assert
        // Should have been split into 10 batches (1000 / 100)
        verify(batchRequest, times(10)).execute();
        assertThat(duration).isLessThan(5000); // Should complete within 5 seconds for mocked operations
    }
}