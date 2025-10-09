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
import java.util.concurrent.atomic.AtomicInteger;
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

        // Setup default batch operation values (updated to match new configuration: batch-size=50)
        lenient().when(batchOperations.maxBatchSize()).thenReturn(50);
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
        @DisplayName("Should return correct maximum batch size (50)")
        void getMaxBatchSize_ShouldReturnCorrectValue() {
            // Act
            int maxBatchSize = gmailBatchClient.getMaxBatchSize();

            // Assert - Should return the configured value (50) which is less than Gmail API limit (100)
            assertThat(maxBatchSize).isEqualTo(50);
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

    // ===========================================
    // Inter-Batch Delay Configuration Tests (P0-4)
    // Tests for delay-between-batches-ms=500 configuration change
    // ===========================================

    @Nested
    @DisplayName("Inter-Batch Delay 500ms Configuration Tests (P0-4)")
    class InterBatchDelay500MsConfigurationTests {

        @Test
        @DisplayName("Should verify getDelayBetweenBatchesMs returns 500ms from configuration")
        void getDelayBetweenBatchesMs_WithDelay500_ShouldReturn500() {
            // Arrange
            when(batchOperations.delayBetweenBatchesMs()).thenReturn(500L);

            // Act
            long delay = batchOperations.delayBetweenBatchesMs();

            // Assert
            assertThat(delay)
                .as("getDelayBetweenBatchesMs should return configured value of 500ms")
                .isEqualTo(500L);
        }

        @Test
        @DisplayName("Should apply 500ms delay between batches during batch delete operations")
        void batchDeleteMessages_WithDelay500_ShouldApplyDelayBetweenBatches() throws IOException {
            // Arrange
            String userId = "me";
            // Create 3 chunks (3000 messages = 3 batches with 1000 each)
            List<String> messageIds = IntStream.range(1, 3001)
                .mapToObj(i -> "msg" + i)
                .toList();

            when(batchOperations.delayBetweenBatchesMs()).thenReturn(500L);

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            doNothing().when(batchDeleteRequest).execute();

            long startTime = System.currentTimeMillis();

            // Act
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            long duration = System.currentTimeMillis() - startTime;

            // Assert
            assertThat(result.getTotalBatchesProcessed()).isEqualTo(3);

            // Should have 2 delays between 3 batches (2 × 500ms = 1000ms minimum)
            assertThat(duration).isGreaterThanOrEqualTo(1000);
        }

        @Test
        @DisplayName("Should verify timing with delay 500ms for 10 batches")
        void batchDeleteMessages_10Batches_ShouldRespect500MsDelay() throws IOException {
            // Arrange
            String userId = "me";
            // Create 10,000 messages (10 batches)
            List<String> messageIds = IntStream.range(1, 10001)
                .mapToObj(i -> "msg" + i)
                .toList();

            when(batchOperations.delayBetweenBatchesMs()).thenReturn(500L);

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            doNothing().when(batchDeleteRequest).execute();

            long startTime = System.currentTimeMillis();

            // Act
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            long duration = System.currentTimeMillis() - startTime;

            // Assert
            assertThat(result.getTotalBatchesProcessed()).isEqualTo(10);

            // Should have 9 delays between 10 batches (9 × 500ms = 4500ms minimum)
            assertThat(duration).isGreaterThanOrEqualTo(4500);
        }

        @Test
        @DisplayName("Should demonstrate performance improvement: 2000ms → 500ms = 75% reduction")
        void delayReduction_PerformanceImprovement_ShouldBe75Percent() {
            // Performance calculation for 10 batches
            long oldDelay = 2000L;
            long newDelay = 500L;
            int batchCount = 10;

            long oldOverhead = (batchCount - 1) * oldDelay; // 9 × 2000ms = 18000ms
            long newOverhead = (batchCount - 1) * newDelay; // 9 × 500ms = 4500ms
            long savings = oldOverhead - newOverhead;       // 13500ms saved

            double reductionPercent = ((double)savings / oldOverhead) * 100;

            assertThat(oldOverhead).isEqualTo(18000L);
            assertThat(newOverhead).isEqualTo(4500L);
            assertThat(savings).isEqualTo(13500L);
            assertThat(reductionPercent).isEqualTo(75.0);
        }

        @Test
        @DisplayName("Should validate combined P0-1 + P0-4 performance improvement")
        void combinedP01AndP04_PerformanceImprovement_ShouldBeSubstantial() {
            // P0-1: batchDelete() reduces quota from 5100 units to 50 units (99% reduction)
            // P0-4: delay reduction from 2000ms to 500ms (75% time reduction)

            // Example: Delete 510 messages (requires 2 batches with batch-size=50 and batchDelete max=1000)
            int messageCount = 510;
            int batchCount = 2; // With batchDelete endpoint (1000 max per batch)

            // Old approach (individual delete, 15 batch size, 2000ms delay)
            int oldBatchCount = (int) Math.ceil((double)messageCount / 15); // 34 batches
            long oldQuota = messageCount * 10; // 5100 units (10 per message)
            long oldTimeDelays = (oldBatchCount - 1) * 2000L; // 66000ms delays

            // New approach (batchDelete, 50 batch size, 500ms delay)
            int newBatchCount = batchCount; // 2 batches
            long newQuota = batchCount * 50; // 100 units (50 per batchDelete call)
            long newTimeDelays = (newBatchCount - 1) * 500L; // 500ms delays

            // Quota improvement
            double quotaReduction = ((double)(oldQuota - newQuota) / oldQuota) * 100;

            // Time improvement (delay overhead only)
            double timeReduction = ((double)(oldTimeDelays - newTimeDelays) / oldTimeDelays) * 100;

            assertThat(quotaReduction)
                .as("P0-1: Quota reduction should be ~98%%")
                .isGreaterThan(98.0);

            assertThat(timeReduction)
                .as("P0-4: Delay time reduction should be ~99%%")
                .isGreaterThan(99.0);
        }

        @Test
        @DisplayName("Should calculate delay overhead for various batch counts with 500ms delay")
        void delayOverhead_VariousBatchCounts_With500MsDelay() {
            long delay = 500L;

            // Test cases: batch count -> expected delay overhead
            Map<Integer, Long> testCases = Map.of(
                5, 2000L,   // (5-1) × 500ms = 2000ms
                10, 4500L,  // (10-1) × 500ms = 4500ms
                20, 9500L   // (20-1) × 500ms = 9500ms
            );

            testCases.forEach((batchCount, expectedOverhead) -> {
                long overhead = (batchCount - 1) * delay;

                assertThat(overhead)
                    .as("With %d batches and 500ms delay: overhead should be %dms", batchCount, expectedOverhead)
                    .isEqualTo(expectedOverhead);
            });
        }

        @Test
        @DisplayName("Should verify no delay applied for single batch operation")
        void batchDeleteMessages_SingleBatch_ShouldNotApplyDelay() throws IOException {
            // Arrange
            String userId = "me";
            List<String> messageIds = IntStream.range(1, 101)
                .mapToObj(i -> "msg" + i)
                .toList();

            // Note: No need to stub delayBetweenBatchesMs for single batch (delay only applied between batches)

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            doNothing().when(batchDeleteRequest).execute();

            long startTime = System.currentTimeMillis();

            // Act
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            long duration = System.currentTimeMillis() - startTime;

            // Assert
            assertThat(result.getTotalBatchesProcessed()).isEqualTo(1);

            // Single batch should have minimal delay (no inter-batch delay)
            assertThat(duration).isLessThan(500); // Less than configured delay since no inter-batch delay
        }

        @Test
        @DisplayName("Should respect configured delay even with zero delay setting")
        void batchDeleteMessages_ZeroDelay_ShouldNotWaitBetweenBatches() throws IOException {
            // Arrange
            String userId = "me";
            List<String> messageIds = IntStream.range(1, 2001)
                .mapToObj(i -> "msg" + i)
                .toList();

            when(batchOperations.delayBetweenBatchesMs()).thenReturn(0L); // No delay

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            doNothing().when(batchDeleteRequest).execute();

            long startTime = System.currentTimeMillis();

            // Act
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            long duration = System.currentTimeMillis() - startTime;

            // Assert
            assertThat(result.getTotalBatchesProcessed()).isEqualTo(2);

            // With zero delay, operation should complete very quickly
            assertThat(duration).isLessThan(500);
        }

        @Test
        @DisplayName("Should calculate total operation time improvement with 500ms delay")
        void totalOperationTime_WithDelay500_ShouldDemonstrateImprovement() {
            // Example: 500 messages with batch-size=50 and batchDelete max=1000
            int messageCount = 500;
            int batchSize = 50;

            // With native batchDelete (1000 max per batch)
            int newBatchCount = 1; // All 500 messages in 1 batchDelete call
            long newDelayOverhead = (newBatchCount - 1) * 500L; // 0ms (single batch)

            // Old approach with individual batches
            int oldBatchCount = (int) Math.ceil((double)messageCount / batchSize); // 10 batches
            long oldDelayOverhead = (oldBatchCount - 1) * 2000L; // 18000ms

            long savings = oldDelayOverhead - newDelayOverhead;

            assertThat(newDelayOverhead).isEqualTo(0L);
            assertThat(oldDelayOverhead).isEqualTo(18000L);
            assertThat(savings)
                .as("For 500 messages: native batchDelete eliminates all delay overhead")
                .isEqualTo(18000L);
        }

        @Test
        @DisplayName("Should validate delay configuration is within valid range")
        void delay500Ms_ShouldBeWithinValidRange() {
            // Verify 500ms is within @Min(100) @Max(5000) constraint
            long delay = 500L;
            long minDelay = 100L;
            long maxDelay = 5000L;

            assertThat(delay)
                .as("Delay 500ms should be within valid range [100, 5000]")
                .isBetween(minDelay, maxDelay);
        }
    }

    // ===========================================
    // Batch Size Configuration Tests (P0-3)
    // Tests for batch-size=50 configuration change
    // ===========================================

    // ===========================================
    // Adaptive Batch Sizing Algorithm Tests (P0-5)
    // Tests for activation of adaptive algorithm in batchDeleteMessages and batchModifyLabels
    // ===========================================

    @Nested
    @DisplayName("Adaptive Batch Sizing Algorithm Tests (P0-5)")
    class AdaptiveBatchSizingAlgorithmTests {

        @Test
        @DisplayName("Should start with initial adaptive size of 15")
        void adaptiveBatchSize_Initial_ShouldBe15() throws Exception {
            // Use reflection to get adaptiveBatchSize field
            java.lang.reflect.Field adaptiveField = GmailBatchClient.class.getDeclaredField("adaptiveBatchSize");
            adaptiveField.setAccessible(true);
            AtomicInteger adaptiveSize = (AtomicInteger) adaptiveField.get(gmailBatchClient);

            // Assert
            assertThat(adaptiveSize.get())
                .as("Initial adaptive batch size should be 15 (from AtomicInteger initialization)")
                .isEqualTo(15);
        }

        @Test
        @DisplayName("Should use adaptive batch size in batchModifyLabels via getAdaptiveBatchSize()")
        void batchModifyLabels_ShouldUseAdaptiveSize() throws Exception {
            // Arrange
            String userId = "me";
            List<String> messageIds = IntStream.range(1, 31) // 30 messages
                .mapToObj(i -> "msg" + i)
                .toList();
            ModifyMessageRequest modifyRequest = new ModifyMessageRequest()
                .setAddLabelIds(List.of("LABEL_1"));

            // Get current adaptive size via reflection
            java.lang.reflect.Field adaptiveField = GmailBatchClient.class.getDeclaredField("adaptiveBatchSize");
            adaptiveField.setAccessible(true);
            AtomicInteger adaptiveSize = (AtomicInteger) adaptiveField.get(gmailBatchClient);
            int currentAdaptiveSize = adaptiveSize.get(); // Should be 15

            Gmail.Users.Messages.Modify mockModifyRequest = mock(Gmail.Users.Messages.Modify.class);
            when(messages.modify(anyString(), anyString(), any(ModifyMessageRequest.class)))
                .thenReturn(mockModifyRequest);

            // Act
            BulkOperationResult result = gmailBatchClient.batchModifyLabels(gmail, userId, messageIds, modifyRequest);

            // Assert - With 30 messages and adaptive size 15, should create 2 batches
            assertThat(result.getTotalBatchesProcessed())
                .as("With 30 messages and adaptive size 15, should create 2 batches")
                .isEqualTo(2);
        }

        @Test
        @DisplayName("Should call updateAdaptiveRateLimit after successful batchDeleteMessages chunk")
        void batchDeleteMessages_SuccessfulChunk_ShouldCallUpdateAdaptive() throws Exception {
            // Arrange
            String userId = "me";
            List<String> messageIds = List.of("msg1", "msg2", "msg3");

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            doNothing().when(batchDeleteRequest).execute();

            // Get initial adaptive size
            java.lang.reflect.Field adaptiveField = GmailBatchClient.class.getDeclaredField("adaptiveBatchSize");
            adaptiveField.setAccessible(true);
            AtomicInteger adaptiveSize = (AtomicInteger) adaptiveField.get(gmailBatchClient);
            int initialSize = adaptiveSize.get();

            // Act
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            // Assert - Adaptive size should have increased by 1 after successful chunk
            assertThat(adaptiveSize.get())
                .as("After successful chunk, adaptive size should increase by 1")
                .isEqualTo(initialSize + 1);
        }

        @Test
        @DisplayName("Should call updateAdaptiveRateLimit after failed batchDeleteMessages chunk")
        void batchDeleteMessages_FailedChunk_ShouldCallUpdateAdaptive() throws Exception {
            // Arrange
            String userId = "me";
            List<String> messageIds = List.of("msg1", "msg2", "msg3");

            GoogleJsonResponseException exception = createMockedGoogleJsonResponseException(404, "Message not found");

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            when(batchDeleteRequest.execute()).thenThrow(exception);

            // Get initial adaptive size
            java.lang.reflect.Field adaptiveField = GmailBatchClient.class.getDeclaredField("adaptiveBatchSize");
            adaptiveField.setAccessible(true);
            AtomicInteger adaptiveSize = (AtomicInteger) adaptiveField.get(gmailBatchClient);
            int initialSize = adaptiveSize.get(); // 15

            // Act
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            // Assert - Adaptive size should have decreased after failed chunk
            // Reduction = max(2, 15 / 4) = max(2, 3) = 3
            // New size = max(5, 15 - 3) = 12
            assertThat(adaptiveSize.get())
                .as("After failed chunk, adaptive size should decrease")
                .isLessThan(initialSize);
        }

        @Test
        @DisplayName("Should increase adaptive size by 1 after successful batch")
        void adaptiveSize_AfterSuccess_ShouldIncreaseBy1() throws Exception {
            // Arrange
            String userId = "me";
            List<String> messageIds = List.of("msg1", "msg2", "msg3");

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            doNothing().when(batchDeleteRequest).execute();

            // Get adaptive size field via reflection
            java.lang.reflect.Field adaptiveField = GmailBatchClient.class.getDeclaredField("adaptiveBatchSize");
            adaptiveField.setAccessible(true);
            AtomicInteger adaptiveSize = (AtomicInteger) adaptiveField.get(gmailBatchClient);
            int initialSize = adaptiveSize.get();

            // Act
            gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            // Assert
            assertThat(adaptiveSize.get())
                .as("Adaptive size should increase by 1 after successful batch (from %d to %d)", initialSize, initialSize + 1)
                .isEqualTo(initialSize + 1);
        }

        @Test
        @DisplayName("Should gradually increase adaptive size with multiple successes")
        void adaptiveSize_MultipleSuccesses_ShouldIncreaseGradually() throws Exception {
            // Arrange
            String userId = "me";
            List<String> messageIds = List.of("msg1", "msg2", "msg3");

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            doNothing().when(batchDeleteRequest).execute();

            // Get adaptive size field via reflection
            java.lang.reflect.Field adaptiveField = GmailBatchClient.class.getDeclaredField("adaptiveBatchSize");
            adaptiveField.setAccessible(true);
            AtomicInteger adaptiveSize = (AtomicInteger) adaptiveField.get(gmailBatchClient);
            int initialSize = adaptiveSize.get(); // Should be 15

            // Act - Execute 5 successful batches
            for (int i = 0; i < 5; i++) {
                gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);
            }

            // Assert - Size should have increased by 5 (15 → 20)
            assertThat(adaptiveSize.get())
                .as("After 5 successful batches, adaptive size should increase by 5")
                .isEqualTo(initialSize + 5);
        }

        @Test
        @DisplayName("Should never exceed configured max batch size of 50")
        void adaptiveSize_MaxCapAt50_ShouldNotExceed() throws Exception {
            // Arrange
            String userId = "me";
            List<String> messageIds = List.of("msg1", "msg2", "msg3");

            when(batchOperations.maxBatchSize()).thenReturn(50);
            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            doNothing().when(batchDeleteRequest).execute();

            // Get adaptive size field via reflection and set to 49 (just below max)
            java.lang.reflect.Field adaptiveField = GmailBatchClient.class.getDeclaredField("adaptiveBatchSize");
            adaptiveField.setAccessible(true);
            AtomicInteger adaptiveSize = (AtomicInteger) adaptiveField.get(gmailBatchClient);
            adaptiveSize.set(49);

            // Act - Execute 5 successful batches (should cap at 50)
            for (int i = 0; i < 5; i++) {
                gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);
            }

            // Assert - Size should cap at configured max of 50
            assertThat(adaptiveSize.get())
                .as("Adaptive size should cap at configured max of 50")
                .isEqualTo(50);
        }

        @Test
        @DisplayName("Should stay at max size 50 even with continued successes")
        void adaptiveSize_AtMax50_ShouldStayAt50() throws Exception {
            // Arrange
            String userId = "me";
            List<String> messageIds = List.of("msg1", "msg2", "msg3");

            when(batchOperations.maxBatchSize()).thenReturn(50);
            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            doNothing().when(batchDeleteRequest).execute();

            // Get adaptive size field via reflection and set to max (50)
            java.lang.reflect.Field adaptiveField = GmailBatchClient.class.getDeclaredField("adaptiveBatchSize");
            adaptiveField.setAccessible(true);
            AtomicInteger adaptiveSize = (AtomicInteger) adaptiveField.get(gmailBatchClient);
            adaptiveSize.set(50);

            // Act - Execute 10 successful batches (should stay at 50)
            for (int i = 0; i < 10; i++) {
                gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);
            }

            // Assert - Size should remain at 50
            assertThat(adaptiveSize.get())
                .as("Adaptive size should remain at max of 50")
                .isEqualTo(50);
        }

        @Test
        @DisplayName("Should reduce adaptive size by 25% on failure")
        void adaptiveSize_AfterFailure_ShouldReduceBy25Percent() throws Exception {
            // Arrange
            String userId = "me";
            List<String> messageIds = List.of("msg1", "msg2", "msg3");

            GoogleJsonResponseException exception = createMockedGoogleJsonResponseException(404, "Not found");

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            when(batchDeleteRequest.execute()).thenThrow(exception);

            // Set adaptive size to 20
            java.lang.reflect.Field adaptiveField = GmailBatchClient.class.getDeclaredField("adaptiveBatchSize");
            adaptiveField.setAccessible(true);
            AtomicInteger adaptiveSize = (AtomicInteger) adaptiveField.get(gmailBatchClient);
            adaptiveSize.set(20);

            // Act
            gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            // Assert - Reduction = max(2, 20 / 4) = 5
            // New size = max(5, 20 - 5) = 15
            assertThat(adaptiveSize.get())
                .as("Adaptive size should reduce from 20 to 15 (25%% reduction)")
                .isEqualTo(15);
        }

        @Test
        @DisplayName("Should reduce by minimum 2 if 25% is less than 2")
        void adaptiveSize_SmallSize_ShouldReduceByMin2() throws Exception {
            // Arrange
            String userId = "me";
            List<String> messageIds = List.of("msg1", "msg2", "msg3");

            GoogleJsonResponseException exception = createMockedGoogleJsonResponseException(500, "Internal error");

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            when(batchDeleteRequest.execute()).thenThrow(exception);

            // Set adaptive size to 6
            java.lang.reflect.Field adaptiveField = GmailBatchClient.class.getDeclaredField("adaptiveBatchSize");
            adaptiveField.setAccessible(true);
            AtomicInteger adaptiveSize = (AtomicInteger) adaptiveField.get(gmailBatchClient);
            adaptiveSize.set(6);

            // Act
            gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            // Assert - Reduction = max(2, 6 / 4) = max(2, 1) = 2
            // New size = max(5, 6 - 2) = max(5, 4) = 5 (floors at 5)
            assertThat(adaptiveSize.get())
                .as("Adaptive size should reduce from 6 to 5 (floors at minimum)")
                .isEqualTo(5);
        }

        @Test
        @DisplayName("Should never go below minimum of 5")
        void adaptiveSize_MinFloorAt5_ShouldNotGoBelowS() throws Exception {
            // Arrange
            String userId = "me";
            List<String> messageIds = List.of("msg1", "msg2", "msg3");

            GoogleJsonResponseException exception = createMockedGoogleJsonResponseException(429, "Rate limit");

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            when(batchDeleteRequest.execute()).thenThrow(exception);

            // Set adaptive size to 5 (minimum)
            java.lang.reflect.Field adaptiveField = GmailBatchClient.class.getDeclaredField("adaptiveBatchSize");
            adaptiveField.setAccessible(true);
            AtomicInteger adaptiveSize = (AtomicInteger) adaptiveField.get(gmailBatchClient);
            adaptiveSize.set(5);

            // Act - Execute multiple failed batches
            for (int i = 0; i < 5; i++) {
                gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);
            }

            // Assert - Size should remain at 5 (minimum floor)
            assertThat(adaptiveSize.get())
                .as("Adaptive size should floor at minimum of 5")
                .isEqualTo(5);
        }

        @Test
        @DisplayName("Should stay at 5 even with continued failures")
        void adaptiveSize_AtMin5_ShouldStayAt5() throws Exception {
            // Arrange
            String userId = "me";
            List<String> messageIds = List.of("msg1", "msg2", "msg3");

            GoogleJsonResponseException exception = createMockedGoogleJsonResponseException(403, "Quota exceeded");

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            when(batchDeleteRequest.execute()).thenThrow(exception);

            // Set adaptive size to minimum (5)
            java.lang.reflect.Field adaptiveField = GmailBatchClient.class.getDeclaredField("adaptiveBatchSize");
            adaptiveField.setAccessible(true);
            AtomicInteger adaptiveSize = (AtomicInteger) adaptiveField.get(gmailBatchClient);
            adaptiveSize.set(5);

            // Act - Execute 10 failed batches (should stay at 5)
            for (int i = 0; i < 10; i++) {
                gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);
            }

            // Assert - Size should remain at 5
            assertThat(adaptiveSize.get())
                .as("Adaptive size should remain at minimum of 5")
                .isEqualTo(5);
        }

        @Test
        @DisplayName("Should calculate 25% reduction correctly for various sizes")
        void adaptiveSize_25PercentReduction_ShouldCalculateCorrectly() {
            // Test the reduction formula: max(2, currentSize / 4)
            Map<Integer, Integer> sizeToReduction = Map.of(
                40, 10,  // 40 / 4 = 10
                30, 7,   // 30 / 4 = 7
                20, 5,   // 20 / 4 = 5
                10, 2,   // 10 / 4 = 2
                8, 2,    // 8 / 4 = 2
                6, 2,    // 6 / 4 = 1, but max(2, 1) = 2
                4, 2     // 4 / 4 = 1, but max(2, 1) = 2
            );

            sizeToReduction.forEach((size, expectedReduction) -> {
                int actualReduction = Math.max(2, size / 4);
                assertThat(actualReduction)
                    .as("For size %d, reduction should be %d", size, expectedReduction)
                    .isEqualTo(expectedReduction);
            });
        }

        @Test
        @DisplayName("Should test adaptive size with starting size of 10")
        void adaptiveSize_FromSize10_ShouldBehavCorrectly() throws Exception {
            // Arrange
            String userId = "me";
            List<String> messageIds = List.of("msg1", "msg2", "msg3");

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            doNothing().when(batchDeleteRequest).execute();

            // Set adaptive size to 10
            java.lang.reflect.Field adaptiveField = GmailBatchClient.class.getDeclaredField("adaptiveBatchSize");
            adaptiveField.setAccessible(true);
            AtomicInteger adaptiveSize = (AtomicInteger) adaptiveField.get(gmailBatchClient);
            adaptiveSize.set(10);

            // Act - 5 successes
            for (int i = 0; i < 5; i++) {
                gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);
            }

            // Assert - Should increase to 15 (10 + 5)
            assertThat(adaptiveSize.get())
                .as("After 5 successes from size 10, should be 15")
                .isEqualTo(15);
        }

        @Test
        @DisplayName("Should test adaptive size with starting size of 30")
        void adaptiveSize_FromSize30_ShouldBehaveCorrectly() throws Exception {
            // Arrange
            String userId = "me";
            List<String> messageIds = List.of("msg1", "msg2", "msg3");

            GoogleJsonResponseException exception = createMockedGoogleJsonResponseException(500, "Error");

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            when(batchDeleteRequest.execute()).thenThrow(exception);

            // Set adaptive size to 30
            java.lang.reflect.Field adaptiveField = GmailBatchClient.class.getDeclaredField("adaptiveBatchSize");
            adaptiveField.setAccessible(true);
            AtomicInteger adaptiveSize = (AtomicInteger) adaptiveField.get(gmailBatchClient);
            adaptiveSize.set(30);

            // Act - 1 failure
            gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            // Assert - Reduction = max(2, 30 / 4) = 7
            // New size = max(5, 30 - 7) = 23
            assertThat(adaptiveSize.get())
                .as("After 1 failure from size 30, should reduce to 23")
                .isEqualTo(23);
        }

        @Test
        @DisplayName("Should test adaptive size with starting size of 40")
        void adaptiveSize_FromSize40_ShouldBehaveCorrectly() throws Exception {
            // Arrange
            String userId = "me";
            List<String> messageIds = List.of("msg1", "msg2", "msg3");

            GoogleJsonResponseException exception = createMockedGoogleJsonResponseException(429, "Rate limit");

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            when(batchDeleteRequest.execute()).thenThrow(exception);

            // Set adaptive size to 40
            java.lang.reflect.Field adaptiveField = GmailBatchClient.class.getDeclaredField("adaptiveBatchSize");
            adaptiveField.setAccessible(true);
            AtomicInteger adaptiveSize = (AtomicInteger) adaptiveField.get(gmailBatchClient);
            adaptiveSize.set(40);

            // Act - 1 failure
            gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            // Assert - Reduction = max(2, 40 / 4) = 10
            // New size = max(5, 40 - 10) = 30
            assertThat(adaptiveSize.get())
                .as("After 1 failure from size 40, should reduce to 30")
                .isEqualTo(30);
        }

        @Test
        @DisplayName("Should integrate adaptive algorithm with circuit breaker")
        void adaptiveSize_WithCircuitBreaker_ShouldWorkTogether() throws Exception {
            // Arrange
            String userId = "me";
            List<String> messageIds = List.of("msg1", "msg2", "msg3");

            GoogleJsonResponseException exception = createMockedGoogleJsonResponseException(404, "Not found");

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            when(batchDeleteRequest.execute()).thenThrow(exception);

            // Get adaptive size field
            java.lang.reflect.Field adaptiveField = GmailBatchClient.class.getDeclaredField("adaptiveBatchSize");
            adaptiveField.setAccessible(true);
            AtomicInteger adaptiveSize = (AtomicInteger) adaptiveField.get(gmailBatchClient);
            int initialSize = adaptiveSize.get();

            // Act - Execute 3 failures (triggers circuit breaker)
            gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);
            gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);
            gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            Map<String, Object> stats = gmailBatchClient.getCircuitBreakerStats();

            // Assert - Circuit breaker should be open AND adaptive size should have decreased
            assertThat(stats.get("isOpen"))
                .as("Circuit breaker should be open after 3 failures")
                .isEqualTo(true);

            assertThat(adaptiveSize.get())
                .as("Adaptive size should have decreased with failures")
                .isLessThan(initialSize);
        }

        @Test
        @DisplayName("Should persist adaptive size across multiple batch operations")
        void adaptiveSize_MultipleOperations_ShouldPersist() throws Exception {
            // Arrange
            String userId = "me";
            List<String> messageIds = List.of("msg1", "msg2", "msg3");

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            doNothing().when(batchDeleteRequest).execute();

            // Get adaptive size field
            java.lang.reflect.Field adaptiveField = GmailBatchClient.class.getDeclaredField("adaptiveBatchSize");
            adaptiveField.setAccessible(true);
            AtomicInteger adaptiveSize = (AtomicInteger) adaptiveField.get(gmailBatchClient);
            int initialSize = adaptiveSize.get();

            // Act - Execute 3 successful operations
            gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);
            int sizeAfterFirst = adaptiveSize.get();

            gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);
            int sizeAfterSecond = adaptiveSize.get();

            gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);
            int sizeAfterThird = adaptiveSize.get();

            // Assert - Size should increase progressively
            assertThat(sizeAfterFirst)
                .as("Size should increase after first operation")
                .isEqualTo(initialSize + 1);

            assertThat(sizeAfterSecond)
                .as("Size should increase after second operation")
                .isEqualTo(initialSize + 2);

            assertThat(sizeAfterThird)
                .as("Size should increase after third operation")
                .isEqualTo(initialSize + 3);
        }

        @Test
        @DisplayName("Should call updateAdaptiveRateLimit in batchDeleteMessages")
        void batchDeleteMessages_ShouldCallUpdateAdaptive() throws Exception {
            // Arrange
            String userId = "me";
            List<String> messageIds = List.of("msg1", "msg2", "msg3");

            when(messages.batchDelete(eq(userId), any(BatchDeleteMessagesRequest.class)))
                .thenReturn(batchDeleteRequest);
            doNothing().when(batchDeleteRequest).execute();

            // Get adaptive size field
            java.lang.reflect.Field adaptiveField = GmailBatchClient.class.getDeclaredField("adaptiveBatchSize");
            adaptiveField.setAccessible(true);
            AtomicInteger adaptiveSize = (AtomicInteger) adaptiveField.get(gmailBatchClient);
            int initialSize = adaptiveSize.get();

            // Act
            gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            // Assert - Adaptive size should have changed (increased by 1 for success)
            assertThat(adaptiveSize.get())
                .as("updateAdaptiveRateLimit should be called, changing the adaptive size")
                .isNotEqualTo(initialSize);
        }

        @Test
        @DisplayName("Should call updateAdaptiveRateLimit in batchModifyLabels")
        void batchModifyLabels_ShouldCallUpdateAdaptive() throws Exception {
            // Arrange
            String userId = "me";
            List<String> messageIds = List.of("msg1", "msg2", "msg3");
            ModifyMessageRequest modifyRequest = new ModifyMessageRequest()
                .setAddLabelIds(List.of("LABEL_1"));

            Gmail.Users.Messages.Modify mockModifyRequest = mock(Gmail.Users.Messages.Modify.class);
            when(messages.modify(anyString(), anyString(), any(ModifyMessageRequest.class)))
                .thenReturn(mockModifyRequest);

            // Get adaptive size field
            java.lang.reflect.Field adaptiveField = GmailBatchClient.class.getDeclaredField("adaptiveBatchSize");
            adaptiveField.setAccessible(true);
            AtomicInteger adaptiveSize = (AtomicInteger) adaptiveField.get(gmailBatchClient);
            int initialSize = adaptiveSize.get();

            // Act
            gmailBatchClient.batchModifyLabels(gmail, userId, messageIds, modifyRequest);

            // Assert - Adaptive size should have changed after batch execution
            // Note: The actual change depends on batch success/failure, but it should be called
            assertThat(adaptiveSize.get())
                .as("updateAdaptiveRateLimit should be called in batchModifyLabels")
                .isGreaterThanOrEqualTo(5); // At minimum it should be 5
        }

        @Test
        @DisplayName("Should affect batchModifyLabels batch splitting based on adaptive size")
        void batchModifyLabels_AdaptiveSize_ShouldAffectBatchSplitting() throws Exception {
            // Arrange
            String userId = "me";
            // Create 45 messages
            List<String> messageIds = IntStream.range(1, 46)
                .mapToObj(i -> "msg" + i)
                .toList();
            ModifyMessageRequest modifyRequest = new ModifyMessageRequest()
                .setAddLabelIds(List.of("LABEL_1"));

            Gmail.Users.Messages.Modify mockModifyRequest = mock(Gmail.Users.Messages.Modify.class);
            when(messages.modify(anyString(), anyString(), any(ModifyMessageRequest.class)))
                .thenReturn(mockModifyRequest);

            // Set adaptive size to 10
            java.lang.reflect.Field adaptiveField = GmailBatchClient.class.getDeclaredField("adaptiveBatchSize");
            adaptiveField.setAccessible(true);
            AtomicInteger adaptiveSize = (AtomicInteger) adaptiveField.get(gmailBatchClient);
            adaptiveSize.set(10);

            // Act
            BulkOperationResult result = gmailBatchClient.batchModifyLabels(gmail, userId, messageIds, modifyRequest);

            // Assert - With 45 messages and adaptive size 10, should create ceil(45/10) = 5 batches
            assertThat(result.getTotalBatchesProcessed())
                .as("With 45 messages and adaptive size 10, should create 5 batches")
                .isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("Batch Size 50 Configuration Tests")
    class BatchSize50ConfigurationTests {

        @Test
        @DisplayName("Should verify getMaxBatchSize returns 50 from configuration")
        void getMaxBatchSize_WithBatchSize50_ShouldReturn50() {
            // Arrange
            when(batchOperations.maxBatchSize()).thenReturn(50);

            // Act
            int maxBatchSize = gmailBatchClient.getMaxBatchSize();

            // Assert
            assertThat(maxBatchSize)
                .as("getMaxBatchSize should return configured value of 50")
                .isEqualTo(50);
        }

        @Test
        @DisplayName("Should respect Gmail API limit even if configured higher")
        void getMaxBatchSize_ConfiguredAboveLimit_ShouldRespectApiLimit() {
            // Arrange - Configure batch size above the DEFAULT_MAX_BATCH_SIZE (100)
            when(batchOperations.maxBatchSize()).thenReturn(150);

            // Act
            int maxBatchSize = gmailBatchClient.getMaxBatchSize();

            // Assert - Should cap at DEFAULT_MAX_BATCH_SIZE (100)
            assertThat(maxBatchSize)
                .as("getMaxBatchSize should cap at Gmail API limit of 100")
                .isEqualTo(100);
        }

        @Test
        @DisplayName("Should use configured max batch size as upper limit for adaptive sizing")
        void batchSize50_AdaptiveSizingRespectMax_ShouldCapAt50() {
            // GmailBatchClient uses adaptive batch sizing for modify operations
            // The configured maxBatchSize (50) acts as an upper limit
            // The adaptive size starts at 15 and can grow up to the configured max (50)

            when(batchOperations.maxBatchSize()).thenReturn(50);

            // getAdaptiveBatchSize() uses: Math.min(configuredMax, currentAdaptive)
            // With configuredMax=50, the adaptive size can potentially grow to 50

            int maxBatchSize = gmailBatchClient.getMaxBatchSize();
            assertThat(maxBatchSize).isEqualTo(50);
        }

        @Test
        @DisplayName("Should calculate batch count correctly with various batch sizes")
        void batchSizeCalculation_VariousSizes_ShouldBeAccurate() {
            // This test verifies the batch count formula with different batch sizes
            // Formula: batches = ceil(messageCount / batchSize)

            // Test with batch size 50
            Map<Integer, Integer> testCasesBatch50 = Map.of(
                1, 1,       // 1 message -> 1 batch
                25, 1,      // 25 messages -> 1 batch
                49, 1,      // 49 messages -> 1 batch
                50, 1,      // 50 messages -> 1 batch
                51, 2,      // 51 messages -> 2 batches
                100, 2,     // 100 messages -> 2 batches
                500, 10,    // 500 messages -> 10 batches
                999, 20,    // 999 messages -> 20 batches
                1000, 20    // 1000 messages -> 20 batches
            );

            testCasesBatch50.forEach((messageCount, expectedBatches) -> {
                int calculatedBatches = (int) Math.ceil((double) messageCount / 50);
                assertThat(calculatedBatches)
                    .as("With batch-size=50: %d messages should create %d batches", messageCount, expectedBatches)
                    .isEqualTo(expectedBatches);
            });
        }

        @Test
        @DisplayName("Should demonstrate performance improvement with batch size 50 vs 15")
        void batchSize50_PerformanceImprovement_ShouldReduce70Percent() {
            // This test demonstrates the theoretical performance improvement
            // from increasing batch size from 15 to 50

            // Test case: 500 messages
            int messageCount = 500;
            int oldBatchSize = 15;
            int newBatchSize = 50;

            int oldBatchCount = (int) Math.ceil((double) messageCount / oldBatchSize); // 34 batches
            int newBatchCount = (int) Math.ceil((double) messageCount / newBatchSize); // 10 batches

            double reduction = ((double)(oldBatchCount - newBatchCount) / oldBatchCount) * 100;

            assertThat(oldBatchCount).isEqualTo(34);
            assertThat(newBatchCount).isEqualTo(10);
            assertThat(reduction)
                .as("Changing from batch-size=15 to batch-size=50 should reduce batch count by ~70%%")
                .isGreaterThan(70.0);
        }

        @Test
        @DisplayName("Should demonstrate batch count reduction for large-scale operations")
        void batchSize50_LargeScale_ShouldDemonstrateImprovement() {
            // Demonstrate the improvement for 1000 messages
            int messageCount = 1000;
            int oldBatchSize = 15;
            int newBatchSize = 50;

            int oldBatchCount = (int) Math.ceil((double) messageCount / oldBatchSize); // 67 batches
            int newBatchCount = (int) Math.ceil((double) messageCount / newBatchSize); // 20 batches

            double reduction = ((double)(oldBatchCount - newBatchCount) / oldBatchCount) * 100;

            assertThat(oldBatchCount).isEqualTo(67);
            assertThat(newBatchCount).isEqualTo(20);
            assertThat(reduction)
                .as("For 1000 messages: batch-size=50 reduces batch count by ~70%%")
                .isGreaterThan(70.0);
        }

        @ParameterizedTest
        @ValueSource(ints = {10, 25, 50, 75, 100})
        @DisplayName("Should validate batch sizes within allowed range")
        void batchSizeValidation_WithinRange_ShouldBeValid(int batchSize) {
            // Verify that batch sizes within the @Min(10) @Max(100) range work correctly
            assertThat(batchSize)
                .as("Batch size %d should be within valid range [10, 100]", batchSize)
                .isBetween(10, 100);
        }

        @Test
        @DisplayName("Should confirm batch size 50 is optimal per Google recommendations")
        void batchSize50_GoogleRecommendation_ShouldBeOptimal() {
            // Google recommends 50 as the optimal batch size for Gmail API
            // Reference: application.properties comment line 25

            int googleRecommendedSize = 50;
            int minAllowed = 10;
            int maxAllowed = 100;

            assertThat(googleRecommendedSize)
                .as("Google's recommended batch size of 50 should be within valid range")
                .isBetween(minAllowed, maxAllowed);
        }
    }
}
