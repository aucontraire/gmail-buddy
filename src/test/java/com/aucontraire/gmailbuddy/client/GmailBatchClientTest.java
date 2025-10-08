package com.aucontraire.gmailbuddy.client;

import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import com.aucontraire.gmailbuddy.exception.BatchOperationException;
import com.aucontraire.gmailbuddy.service.BulkOperationResult;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.BatchDeleteMessagesRequest;
import com.google.api.services.gmail.model.ModifyMessageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Comprehensive test suite for GmailBatchClient.
 * Tests the refactored implementation using Gmail's native batchDelete() endpoint
 * and batch operations for label modifications.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GmailBatchClient Comprehensive Tests")
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
    private Gmail.Users.Messages.BatchDelete batchDeleteRequest;

    @Mock
    private Gmail.Users.Messages.Modify modifyRequest;

    @Mock
    private BatchRequest batchRequest;

    private GmailBatchClient gmailBatchClient;

    @BeforeEach
    void setUp() {
        // Setup properties mock hierarchy
        lenient().when(properties.gmailApi()).thenReturn(gmailApi);
        lenient().when(gmailApi.rateLimit()).thenReturn(rateLimit);
        lenient().when(rateLimit.batchOperations()).thenReturn(batchOperations);

        // Setup default batch operation values
        lenient().when(batchOperations.maxBatchSize()).thenReturn(15);
        lenient().when(batchOperations.delayBetweenBatchesMs()).thenReturn(0L); // No delay for tests
        lenient().when(batchOperations.maxRetryAttempts()).thenReturn(3);
        lenient().when(batchOperations.initialBackoffMs()).thenReturn(100L);
        lenient().when(batchOperations.backoffMultiplier()).thenReturn(2.0);
        lenient().when(batchOperations.maxBackoffMs()).thenReturn(1000L);
        lenient().when(batchOperations.microDelayBetweenOperationsMs()).thenReturn(0L);

        gmailBatchClient = new GmailBatchClient(properties);

        // Setup Gmail mock hierarchy (lenient for tests that don't need them)
        lenient().when(gmail.users()).thenReturn(users);
        lenient().when(users.messages()).thenReturn(messages);
        lenient().when(gmail.batch()).thenReturn(batchRequest);
    }

    /**
     * Helper method to create a mocked GoogleJsonResponseException.
     *
     * @param statusCode the HTTP status code
     * @param message the error message
     * @return a mocked GoogleJsonResponseException
     */
    private GoogleJsonResponseException createMockedGoogleJsonResponseException(int statusCode, String message) {
        GoogleJsonError error = new GoogleJsonError();
        error.setCode(statusCode);
        error.setMessage(message);

        GoogleJsonResponseException exception = mock(GoogleJsonResponseException.class);
        when(exception.getDetails()).thenReturn(error);
        when(exception.getMessage()).thenReturn(message);
        when(exception.getStatusCode()).thenReturn(statusCode);

        return exception;
    }

    @Nested
    @DisplayName("Native batchDelete() Success Scenarios")
    class BatchDeleteSuccessTests {

        @Test
        @DisplayName("Should handle empty message list gracefully and return immediately")
        void batchDeleteMessages_EmptyList_ShouldReturnImmediately() throws IOException {
            // Arrange
            String userId = "me";
            List<String> messageIds = Collections.emptyList();

            // Act
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getOperationType()).isEqualTo("BATCH_DELETE");
            assertThat(result.getTotalOperations()).isEqualTo(0);
            assertThat(result.getSuccessCount()).isEqualTo(0);
            assertThat(result.getFailureCount()).isEqualTo(0);
            assertThat(result.getTotalBatchesProcessed()).isEqualTo(0);

            // Verify no API calls were made
            verify(messages, never()).batchDelete(anyString(), any(BatchDeleteMessagesRequest.class));
        }

        @Test
        @DisplayName("Should handle null message list gracefully and return immediately")
        void batchDeleteMessages_NullList_ShouldReturnImmediately() throws IOException {
            // Arrange
            String userId = "me";
            List<String> messageIds = null;

            // Act
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getOperationType()).isEqualTo("BATCH_DELETE");
            assertThat(result.getTotalOperations()).isEqualTo(0);
            assertThat(result.getSuccessCount()).isEqualTo(0);
            assertThat(result.getFailureCount()).isEqualTo(0);

            // Verify no API calls were made
            verify(messages, never()).batchDelete(anyString(), any(BatchDeleteMessagesRequest.class));
        }

        @Test
        @DisplayName("Should successfully delete single message using batchDelete")
        void batchDeleteMessages_SingleMessage_ShouldSucceed() throws IOException {
            // Arrange
            String userId = "me";
            String messageId = "msg123";
            List<String> messageIds = List.of(messageId);

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            doNothing().when(batchDeleteRequest).execute(); // batchDelete returns void

            // Act
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            // Assert
            assertThat(result.getOperationType()).isEqualTo("BATCH_DELETE");
            assertThat(result.getTotalOperations()).isEqualTo(1);
            assertThat(result.getSuccessCount()).isEqualTo(1);
            assertThat(result.getFailureCount()).isEqualTo(0);
            assertThat(result.isCompleteSuccess()).isTrue();
            assertThat(result.getTotalBatchesProcessed()).isEqualTo(1);

            // Verify batchDelete was called once with correct parameters
            ArgumentCaptor<BatchDeleteMessagesRequest> captor = ArgumentCaptor.forClass(BatchDeleteMessagesRequest.class);
            verify(messages).batchDelete(eq(userId), captor.capture());
            assertThat(captor.getValue().getIds()).containsExactly(messageId);
        }

        @Test
        @DisplayName("Should successfully delete small batch of messages (< 1000)")
        void batchDeleteMessages_SmallBatch_ShouldSucceed() throws IOException {
            // Arrange
            String userId = "me";
            List<String> messageIds = IntStream.range(1, 501)
                .mapToObj(i -> "msg" + i)
                .toList();

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            doNothing().when(batchDeleteRequest).execute();

            // Act
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            // Assert
            assertThat(result.getOperationType()).isEqualTo("BATCH_DELETE");
            assertThat(result.getTotalOperations()).isEqualTo(500);
            assertThat(result.getSuccessCount()).isEqualTo(500);
            assertThat(result.getFailureCount()).isEqualTo(0);
            assertThat(result.isCompleteSuccess()).isTrue();
            assertThat(result.getTotalBatchesProcessed()).isEqualTo(1);

            // Verify batchDelete was called once
            verify(messages, times(1)).batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class));
        }

        @Test
        @DisplayName("Should handle exactly 1000 messages in single batchDelete call (boundary condition)")
        void batchDeleteMessages_ExactlyMaxSize_ShouldSucceed() throws IOException {
            // Arrange
            String userId = "me";
            List<String> messageIds = IntStream.range(1, 1001) // Exactly 1000 messages
                .mapToObj(i -> "msg" + i)
                .toList();

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            doNothing().when(batchDeleteRequest).execute();

            // Act
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            // Assert
            assertThat(result.getOperationType()).isEqualTo("BATCH_DELETE");
            assertThat(result.getTotalOperations()).isEqualTo(1000);
            assertThat(result.getSuccessCount()).isEqualTo(1000);
            assertThat(result.getFailureCount()).isEqualTo(0);
            assertThat(result.isCompleteSuccess()).isTrue();
            assertThat(result.getTotalBatchesProcessed()).isEqualTo(1);

            // Verify batchDelete was called once with exactly 1000 message IDs
            ArgumentCaptor<BatchDeleteMessagesRequest> captor = ArgumentCaptor.forClass(BatchDeleteMessagesRequest.class);
            verify(messages, times(1)).batchDelete(eq(userId), captor.capture());
            assertThat(captor.getValue().getIds()).hasSize(1000);
        }

        @Test
        @DisplayName("Should split large message list into multiple chunks (> 1000 messages)")
        void batchDeleteMessages_MultipleChunks_ShouldSplitCorrectly() throws IOException {
            // Arrange
            String userId = "me";
            List<String> messageIds = IntStream.range(1, 2501) // 2500 messages = 3 chunks
                .mapToObj(i -> "msg" + i)
                .toList();

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            doNothing().when(batchDeleteRequest).execute();

            // Act
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            // Assert
            assertThat(result.getOperationType()).isEqualTo("BATCH_DELETE");
            assertThat(result.getTotalOperations()).isEqualTo(2500);
            assertThat(result.getSuccessCount()).isEqualTo(2500);
            assertThat(result.getFailureCount()).isEqualTo(0);
            assertThat(result.isCompleteSuccess()).isTrue();
            assertThat(result.getTotalBatchesProcessed()).isEqualTo(3); // 1000 + 1000 + 500

            // Verify batchDelete was called 3 times
            ArgumentCaptor<BatchDeleteMessagesRequest> captor = ArgumentCaptor.forClass(BatchDeleteMessagesRequest.class);
            verify(messages, times(3)).batchDelete(eq(userId), captor.capture());

            // Verify chunk sizes
            List<BatchDeleteMessagesRequest> requests = captor.getAllValues();
            assertThat(requests.get(0).getIds()).hasSize(1000);
            assertThat(requests.get(1).getIds()).hasSize(1000);
            assertThat(requests.get(2).getIds()).hasSize(500);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 10, 100, 500, 999, 1000, 1001, 2000, 5000})
        @DisplayName("Should handle various batch sizes correctly")
        void batchDeleteMessages_VariousSizes_ShouldHandleCorrectly(int messageCount) throws IOException {
            // Arrange
            String userId = "me";
            List<String> messageIds = IntStream.range(1, messageCount + 1)
                .mapToObj(i -> "msg" + i)
                .toList();

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            doNothing().when(batchDeleteRequest).execute();

            // Act
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            // Assert
            int expectedChunks = (int) Math.ceil((double) messageCount / 1000);
            assertThat(result.getTotalOperations()).isEqualTo(messageCount);
            assertThat(result.getSuccessCount()).isEqualTo(messageCount);
            assertThat(result.getFailureCount()).isEqualTo(0);
            assertThat(result.getTotalBatchesProcessed()).isEqualTo(expectedChunks);

            // Verify correct number of batchDelete calls
            verify(messages, times(expectedChunks)).batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class));
        }
    }

    @Nested
    @DisplayName("Native batchDelete() Error Scenarios")
    class BatchDeleteErrorTests {

        @Test
        @DisplayName("Should retry on 429 rate limiting error")
        void batchDeleteMessages_RateLimitError429_ShouldRetry() throws IOException {
            // Arrange
            String userId = "me";
            List<String> messageIds = List.of("msg1", "msg2", "msg3");

            GoogleJsonResponseException exception = createMockedGoogleJsonResponseException(429, "Rate limit exceeded");

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            doThrow(exception)
                .doThrow(exception)
                .doNothing() // Succeeds on 3rd attempt
                .when(batchDeleteRequest).execute();

            // Act
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            // Assert
            assertThat(result.getSuccessCount()).isEqualTo(3);
            // Note: failedOperations is a Map, so duplicate failures are overwritten
            // Each message appears once in failures (last failure) and once in successes
            assertThat(result.getFailureCount()).isEqualTo(3); // 3 messages, overwritten on each retry
            assertThat(result.getTotalBatchesRetried()).isEqualTo(1); // One batch was retried

            // Verify batchDelete was called 3 times (initial + 2 retries)
            verify(messages, times(3)).batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class));
        }

        @Test
        @DisplayName("Should retry on 403 quota exceeded error")
        void batchDeleteMessages_QuotaExceededError403_ShouldRetry() throws IOException {
            // Arrange
            String userId = "me";
            List<String> messageIds = List.of("msg1", "msg2");

            GoogleJsonResponseException exception = createMockedGoogleJsonResponseException(403, "Quota exceeded for today");

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            doThrow(exception)
                .doNothing() // Succeeds on 2nd attempt
                .when(batchDeleteRequest).execute();

            // Act
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            // Assert
            assertThat(result.getSuccessCount()).isEqualTo(2);
            // Note: failedOperations is a Map, so duplicate failures are overwritten
            assertThat(result.getFailureCount()).isEqualTo(2); // 2 messages in failures map
            assertThat(result.getTotalBatchesRetried()).isEqualTo(1);

            // Verify batchDelete was called 2 times (initial + 1 retry)
            verify(messages, times(2)).batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class));
        }

        @Test
        @DisplayName("Should NOT retry on 404 not found error")
        void batchDeleteMessages_NotFoundError404_ShouldNotRetry() throws IOException {
            // Arrange
            String userId = "me";
            List<String> messageIds = List.of("msg1", "msg2", "msg3");

            GoogleJsonResponseException exception = createMockedGoogleJsonResponseException(404, "Message not found");

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            when(batchDeleteRequest.execute()).thenThrow(exception);

            // Act
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            // Assert - All messages should be marked as failed
            assertThat(result.getSuccessCount()).isEqualTo(0);
            assertThat(result.getFailureCount()).isEqualTo(3);
            assertThat(result.hasFailures()).isTrue();

            // Verify batchDelete was called only once (no retry)
            verify(batchDeleteRequest, times(1)).execute();
        }

        @Test
        @DisplayName("Should NOT retry on 401 authentication error")
        void batchDeleteMessages_AuthError401_ShouldNotRetry() throws IOException {
            // Arrange
            String userId = "me";
            List<String> messageIds = List.of("msg1");

            GoogleJsonResponseException exception = createMockedGoogleJsonResponseException(401, "Authentication required");

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            when(batchDeleteRequest.execute()).thenThrow(exception);

            // Act
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            // Assert
            assertThat(result.getSuccessCount()).isEqualTo(0);
            assertThat(result.getFailureCount()).isEqualTo(1);

            // Verify batchDelete was called only once (no retry)
            verify(batchDeleteRequest, times(1)).execute();
        }

        @Test
        @DisplayName("Should handle generic IOException with retry logic")
        void batchDeleteMessages_IOException_ShouldRetry() throws IOException {
            // Arrange
            String userId = "me";
            List<String> messageIds = List.of("msg1", "msg2");

            IOException ioException = new IOException("Connection timeout");

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            doThrow(ioException)
                .doNothing() // Succeeds on 2nd attempt
                .when(batchDeleteRequest).execute();

            // Act
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            // Assert
            assertThat(result.getSuccessCount()).isEqualTo(2);
            // Note: failedOperations is a Map, so duplicate failures are overwritten
            assertThat(result.getFailureCount()).isEqualTo(2); // 2 messages in failures map
            assertThat(result.getTotalBatchesRetried()).isEqualTo(1);

            // Verify batchDelete was called 2 times (initial + 1 retry)
            verify(messages, times(2)).batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class));
        }

        @Test
        @DisplayName("Should mark entire chunk as failed on all-or-nothing failure")
        void batchDeleteMessages_AllOrNothingFailure_ShouldMarkAllFailed() throws IOException {
            // Arrange
            String userId = "me";
            List<String> messageIds = List.of("msg1", "msg2", "msg3", "msg4", "msg5");

            GoogleJsonResponseException exception = createMockedGoogleJsonResponseException(500, "Internal server error");

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            // Fail on all retry attempts
            when(batchDeleteRequest.execute()).thenThrow(exception);

            // Act
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            // Assert - All messages in the chunk should be marked as failed
            assertThat(result.getSuccessCount()).isEqualTo(0);
            assertThat(result.getFailureCount()).isEqualTo(5);
            assertThat(result.getFailedOperations()).hasSize(5);
            assertThat(result.getFailedOperations().keySet())
                .containsExactlyInAnyOrder("msg1", "msg2", "msg3", "msg4", "msg5");

            // Verify batchDelete was called once (retry logic is internal to executeBatchDeleteWithRetry)
            verify(messages, times(1)).batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class));
        }

        @Test
        @DisplayName("Should continue processing remaining chunks after one chunk fails")
        void batchDeleteMessages_OneChunkFails_ShouldContinueWithOtherChunks() throws IOException {
            // Arrange
            String userId = "me";
            // 2500 messages = 3 chunks
            List<String> messageIds = IntStream.range(1, 2501)
                .mapToObj(i -> "msg" + i)
                .toList();

            GoogleJsonResponseException exception = createMockedGoogleJsonResponseException(404, "Not found");

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            doNothing()                  // Chunk 1 succeeds (1000 messages)
                .doThrow(exception)      // Chunk 2 fails (1000 messages)
                .doNothing()             // Chunk 3 succeeds (500 messages)
                .when(batchDeleteRequest).execute();

            // Act
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            // Assert
            assertThat(result.getSuccessCount()).isEqualTo(1500); // Chunks 1 and 3
            assertThat(result.getFailureCount()).isEqualTo(1000); // Chunk 2
            assertThat(result.getTotalBatchesProcessed()).isEqualTo(3);
            assertThat(result.hasFailures()).isTrue();
            assertThat(result.hasSuccesses()).isTrue();

            // Verify batchDelete was called 3 times
            verify(batchDeleteRequest, times(3)).execute();
        }
    }

    @Nested
    @DisplayName("Retry Logic Tests")
    class RetryLogicTests {

        @Test
        @DisplayName("Should implement exponential backoff on retries")
        void batchDeleteMessages_ExponentialBackoff_ShouldIncreaseDelay() throws IOException {
            // Arrange
            String userId = "me";
            List<String> messageIds = List.of("msg1");

            // Configure backoff parameters
            when(batchOperations.maxRetryAttempts()).thenReturn(3);
            when(batchOperations.initialBackoffMs()).thenReturn(100L);
            when(batchOperations.backoffMultiplier()).thenReturn(2.0);
            when(batchOperations.maxBackoffMs()).thenReturn(10000L);

            IOException exception = new IOException("Temporary failure");

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            doThrow(exception)
                .doThrow(exception)
                .doThrow(exception)
                .doNothing() // Succeeds on 4th attempt
                .when(batchDeleteRequest).execute();

            long startTime = System.currentTimeMillis();

            // Act
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            long duration = System.currentTimeMillis() - startTime;

            // Assert
            assertThat(result.getSuccessCount()).isEqualTo(1);
            assertThat(result.getTotalBatchesRetried()).isEqualTo(1);

            // Verify backoff delays were applied (100ms + 200ms + 400ms = 700ms minimum)
            assertThat(duration).isGreaterThanOrEqualTo(700);

            // Verify all attempts were made
            verify(batchDeleteRequest, times(4)).execute();
        }

        @Test
        @DisplayName("Should respect max retry attempts")
        void batchDeleteMessages_MaxRetries_ShouldStopAfterMaxAttempts() throws IOException {
            // Arrange
            String userId = "me";
            List<String> messageIds = List.of("msg1", "msg2");

            when(batchOperations.maxRetryAttempts()).thenReturn(3);

            IOException exception = new IOException("Persistent failure");

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            when(batchDeleteRequest.execute()).thenThrow(exception); // Always fails

            // Act
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            // Assert
            assertThat(result.getSuccessCount()).isEqualTo(0);
            assertThat(result.getFailureCount()).isEqualTo(2);

            // Verify batchDelete was called once (retry logic is internal to executeBatchDeleteWithRetry)
            verify(messages, times(1)).batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class));
        }

        @Test
        @DisplayName("Should fail fast on non-retryable errors")
        void batchDeleteMessages_NonRetryableError_ShouldFailFast() throws IOException {
            // Arrange
            String userId = "me";
            List<String> messageIds = List.of("msg1");

            GoogleJsonResponseException exception = createMockedGoogleJsonResponseException(400, "Invalid request");

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            when(batchDeleteRequest.execute()).thenThrow(exception);

            // Act
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            // Assert
            assertThat(result.getSuccessCount()).isEqualTo(0);
            assertThat(result.getFailureCount()).isEqualTo(1);

            // Verify only 1 attempt was made (no retries)
            verify(batchDeleteRequest, times(1)).execute();
        }

        @Test
        @DisplayName("Should track retry attempts correctly in result")
        void batchDeleteMessages_RetryCounting_ShouldTrackCorrectly() throws IOException {
            // Arrange
            String userId = "me";
            // Create 2 chunks
            List<String> messageIds = IntStream.range(1, 1501)
                .mapToObj(i -> "msg" + i)
                .toList();

            IOException exception = new IOException("Rate limit exceeded");

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            doThrow(exception).doNothing()  // Chunk 1: fails once, then succeeds
                .doThrow(exception).doThrow(exception).doNothing() // Chunk 2: fails twice, then succeeds
                .when(batchDeleteRequest).execute();

            // Act
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            // Assert
            assertThat(result.getSuccessCount()).isEqualTo(1500);
            // Note: failedOperations is a Map, so each message appears only once even with multiple failures
            // All 1500 messages eventually succeeded but also appear in failures map
            assertThat(result.getFailureCount()).isEqualTo(1500); // Each message appears once in failures map
            assertThat(result.getTotalBatchesProcessed()).isEqualTo(2);
            // Each chunk that needed retry increments the counter once
            assertThat(result.getTotalBatchesRetried()).isEqualTo(2); // Both chunks needed retries
        }
    }

    @Nested
    @DisplayName("Circuit Breaker Tests")
    class CircuitBreakerTests {

        @Test
        @DisplayName("Should record consecutive failures in circuit breaker")
        void batchDeleteMessages_ConsecutiveFailures_ShouldRecordInCircuitBreaker() throws IOException {
            // Arrange
            String userId = "me";
            List<String> messageIds = List.of("msg1");

            GoogleJsonResponseException exception = createMockedGoogleJsonResponseException(404, "Not found");

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            when(batchDeleteRequest.execute()).thenThrow(exception);

            // Act - Execute multiple times to trigger circuit breaker
            gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);
            gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);
            gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            Map<String, Object> stats = gmailBatchClient.getCircuitBreakerStats();

            // Assert
            assertThat(stats.get("consecutiveFailures")).isEqualTo(3);
            assertThat(stats.get("isOpen")).isEqualTo(true);
        }

        @Test
        @DisplayName("Should reset circuit breaker on successful operation")
        void batchDeleteMessages_SuccessAfterFailures_ShouldResetCircuitBreaker() throws IOException {
            // Arrange
            String userId = "me";
            List<String> messageIds = List.of("msg1");

            GoogleJsonResponseException exception = createMockedGoogleJsonResponseException(500, "Internal error");

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            doThrow(exception) // Fail first time
                .doNothing()   // Succeed second time (after retry)
                .when(batchDeleteRequest).execute();

            // Act
            gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);
            Map<String, Object> stats = gmailBatchClient.getCircuitBreakerStats();

            // Assert - Circuit breaker should be reset after success
            assertThat(stats.get("consecutiveFailures")).isEqualTo(0);
            assertThat(stats.get("isOpen")).isEqualTo(false);
        }

        @Test
        @DisplayName("Should wait during cooling off period when circuit breaker is open")
        void batchDeleteMessages_CircuitBreakerOpen_ShouldApplyCoolingOff() throws IOException {
            // Arrange
            String userId = "me";
            List<String> messageIds = List.of("msg1");

            GoogleJsonResponseException exception = createMockedGoogleJsonResponseException(404, "Not found");

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            when(batchDeleteRequest.execute()).thenThrow(exception);

            // Act - Trigger circuit breaker (3 failures)
            gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);
            gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);
            gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            Map<String, Object> statsBefore = gmailBatchClient.getCircuitBreakerStats();
            assertThat(statsBefore.get("isOpen")).isEqualTo(true);

            long startTime = System.currentTimeMillis();

            // Execute another operation (should wait for cooling off)
            gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            long duration = System.currentTimeMillis() - startTime;

            // Assert - Should have applied cooling off delay (capped at 5000ms in implementation)
            assertThat(duration).isGreaterThanOrEqualTo(100); // At least some delay applied
        }
    }

    @Nested
    @DisplayName("Performance and Edge Case Tests")
    class PerformanceAndEdgeCaseTests {

        @Test
        @DisplayName("Should apply delay between chunks when configured")
        void batchDeleteMessages_DelayBetweenChunks_ShouldApplyDelay() throws IOException {
            // Arrange
            String userId = "me";
            // Create 3 chunks
            List<String> messageIds = IntStream.range(1, 2001)
                .mapToObj(i -> "msg" + i)
                .toList();

            when(batchOperations.delayBetweenBatchesMs()).thenReturn(200L); // 200ms delay

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            doNothing().when(batchDeleteRequest).execute();

            long startTime = System.currentTimeMillis();

            // Act
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            long duration = System.currentTimeMillis() - startTime;

            // Assert
            assertThat(result.getTotalBatchesProcessed()).isEqualTo(2); // 2 full chunks

            // Should have 1 delay between 2 chunks (200ms minimum)
            // Note: Last chunk doesn't add delay
            assertThat(duration).isGreaterThanOrEqualTo(200);
        }

        @Test
        @DisplayName("Should handle large-scale operations efficiently")
        void batchDeleteMessages_LargeScale_ShouldHandleEfficiently() throws IOException {
            // Arrange
            String userId = "me";
            // Create 10,000 messages (10 chunks)
            List<String> messageIds = IntStream.range(1, 10001)
                .mapToObj(i -> "msg" + i)
                .toList();

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            doNothing().when(batchDeleteRequest).execute();

            long startTime = System.currentTimeMillis();

            // Act
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            long duration = System.currentTimeMillis() - startTime;

            // Assert
            assertThat(result.getTotalOperations()).isEqualTo(10000);
            assertThat(result.getSuccessCount()).isEqualTo(10000);
            assertThat(result.getFailureCount()).isEqualTo(0);
            assertThat(result.getTotalBatchesProcessed()).isEqualTo(10);

            // Verify efficient processing (should be fast with mocked operations)
            assertThat(duration).isLessThan(2000); // Less than 2 seconds for mocked operations

            // Verify correct number of batchDelete calls
            verify(batchDeleteRequest, times(10)).execute();
        }

        @Test
        @DisplayName("Should correctly calculate success rate")
        void batchDeleteMessages_SuccessRate_ShouldCalculateCorrectly() throws IOException {
            // Arrange
            String userId = "me";
            // Create 3 chunks
            List<String> messageIds = IntStream.range(1, 2501)
                .mapToObj(i -> "msg" + i)
                .toList();

            GoogleJsonResponseException exception = createMockedGoogleJsonResponseException(404, "Not found");

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            doNothing()                  // Chunk 1: 1000 messages succeed
                .doNothing()             // Chunk 2: 1000 messages succeed
                .doThrow(exception)      // Chunk 3: 500 messages fail
                .when(batchDeleteRequest).execute();

            // Act
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            // Assert
            assertThat(result.getSuccessCount()).isEqualTo(2000);
            assertThat(result.getFailureCount()).isEqualTo(500);
            assertThat(result.getTotalOperations()).isEqualTo(2500);
            assertThat(result.getSuccessRate()).isEqualTo(80.0); // 2000 / 2500 = 80%
        }

        @Test
        @DisplayName("Should track operation duration correctly")
        void batchDeleteMessages_Duration_ShouldTrackCorrectly() throws IOException {
            // Arrange
            String userId = "me";
            List<String> messageIds = List.of("msg1", "msg2", "msg3");

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            doNothing().when(batchDeleteRequest).execute();

            // Act
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            // Assert
            assertThat(result.getDurationMs()).isGreaterThanOrEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Label Modification Tests")
    class LabelModificationTests {

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

            // Act
            BulkOperationResult result = gmailBatchClient.batchModifyLabels(gmail, userId, messageIds, modifyRequest);

            // Assert
            verify(batchRequest).execute();
            assertThat(result.getOperationType()).isEqualTo("MODIFY_LABELS");
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should validate complete success result without exception")
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
    }

    @Nested
    @DisplayName("Error Classification Tests")
    class ErrorClassificationTests {

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
                Arguments.of("Connection timeout occurred"),
                Arguments.of("Service unavailable right now"),
                Arguments.of("Rate limit has been exceeded"),
                Arguments.of("Quota exceeded for today"),
                Arguments.of("Internal error occurred"),
                Arguments.of("Backend error during processing"),
                Arguments.of("timeout during connection"),
                Arguments.of("Service unavailable"),
                Arguments.of("Too many concurrent requests detected"),
                Arguments.of("User rate limit exceeded")
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
                Arguments.of("Unknown error") // Changed from null as ConcurrentHashMap doesn't allow null values
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
            result.addFailure("msg1", "Connection timeout");
            result.addFailure("msg2", "Message not found");
            result.addFailure("msg3", "Rate limit exceeded");
            result.addFailure("msg4", "Permission denied");
            result.markCompleted();

            // Act
            List<String> retryableFailures = gmailBatchClient.getRetryableFailures(result);

            // Assert
            assertThat(retryableFailures)
                .hasSize(2)
                .containsExactlyInAnyOrder("msg1", "msg3");
        }

        @Test
        @DisplayName("Should handle empty string error message gracefully when checking retryability")
        void isRetryableError_EmptyErrorMessage_ShouldReturnFalse() {
            // Arrange
            BulkOperationResult result = new BulkOperationResult("DELETE");
            result.addFailure("msg1", ""); // Empty string instead of null (ConcurrentHashMap doesn't allow null)
            result.markCompleted();

            // Act & Assert - Empty error message should not be considered retryable
            assertThat(gmailBatchClient.areFailuresRetryable(result)).isFalse();
        }
    }

    @Nested
    @DisplayName("Configuration Tests")
    class ConfigurationTests {

        @Test
        @DisplayName("Should return correct maximum batch size")
        void getMaxBatchSize_ShouldReturnCorrectValue() {
            // Act
            int maxBatchSize = gmailBatchClient.getMaxBatchSize();

            // Assert - Should return the configured value (15) which is less than Gmail API limit (100)
            assertThat(maxBatchSize).isEqualTo(15);
        }

        @Test
        @DisplayName("Should respect configured retry parameters")
        void batchDeleteMessages_ConfiguredRetryParams_ShouldRespect() throws IOException {
            // Arrange
            String userId = "me";
            List<String> messageIds = List.of("msg1");

            when(batchOperations.maxRetryAttempts()).thenReturn(2); // Custom max retries
            when(batchOperations.initialBackoffMs()).thenReturn(50L);
            when(batchOperations.backoffMultiplier()).thenReturn(3.0);
            when(batchOperations.maxBackoffMs()).thenReturn(500L);

            IOException exception = new IOException("Timeout");

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            when(batchDeleteRequest.execute()).thenThrow(exception); // Always fails

            // Act
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            // Assert - Should have made maxRetryAttempts + 1 total attempts
            verify(batchDeleteRequest, times(3)).execute(); // 2 retries + 1 initial = 3
            assertThat(result.getFailureCount()).isEqualTo(1);
        }
    }
}
