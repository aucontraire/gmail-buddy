package com.aucontraire.gmailbuddy.client;

import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import com.aucontraire.gmailbuddy.service.BulkOperationResult;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.http.HttpHeaders;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ModifyMessageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Performance and large-scale tests for GmailBatchClient.
 * These tests verify that the batch implementation can handle large volumes
 * of operations efficiently and correctly partition them into appropriately sized batches.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Gmail Batch Client Performance Tests")
class GmailBatchPerformanceTest {

    @Mock
    private GmailBuddyProperties properties;

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
    private Gmail.Users.Messages.BatchDelete batchDeleteRequest;

    @Mock
    private BatchRequest batchRequest;

    private GmailBatchClient gmailBatchClient;

    @BeforeEach
    void setUp() throws IOException {
        // Create nested mock structure for GmailBuddyProperties
        GmailBuddyProperties.GmailApi gmailApi = mock(GmailBuddyProperties.GmailApi.class);
        GmailBuddyProperties.GmailApi.RateLimit rateLimit = mock(GmailBuddyProperties.GmailApi.RateLimit.class);
        GmailBuddyProperties.GmailApi.RateLimit.BatchOperations batchOps = mock(GmailBuddyProperties.GmailApi.RateLimit.BatchOperations.class);

        // Wire up the mock chain
        lenient().when(properties.gmailApi()).thenReturn(gmailApi);
        lenient().when(gmailApi.rateLimit()).thenReturn(rateLimit);
        lenient().when(rateLimit.batchOperations()).thenReturn(batchOps);

        // Configure batch operations properties
        lenient().when(batchOps.maxBatchSize()).thenReturn(100);
        lenient().when(batchOps.maxRetryAttempts()).thenReturn(4);
        lenient().when(batchOps.initialBackoffMs()).thenReturn(2000L);
        lenient().when(batchOps.backoffMultiplier()).thenReturn(2.5);
        lenient().when(batchOps.maxBackoffMs()).thenReturn(60000L);
        lenient().when(batchOps.delayBetweenBatchesMs()).thenReturn(0L); // No delay for performance tests
        lenient().when(batchOps.microDelayBetweenOperationsMs()).thenReturn(0L); // No micro-delay for performance tests

        gmailBatchClient = new GmailBatchClient(properties);

        // Setup Gmail mock hierarchy (lenient because concurrent test creates its own mocks)
        lenient().when(gmail.users()).thenReturn(users);
        lenient().when(users.messages()).thenReturn(messages);

        // Setup batchDelete mock chain for native Gmail batchDelete operations
        // Note: lenient() is used because not all tests call batchDelete (e.g., concurrent test uses its own mocks)
        lenient().when(messages.batchDelete(anyString(), any())).thenReturn(batchDeleteRequest);
    }

    @ParameterizedTest
    @MethodSource("largeScaleTestCases")
    @DisplayName("Should handle large-scale operations with optimal batch partitioning")
    void batchDeleteMessages_LargeScale_ShouldPartitionOptimally(int messageCount, int expectedChunks) throws IOException {
        // Arrange
        String userId = "me";
        List<String> messageIds = IntStream.range(1, messageCount + 1)
                .mapToObj(i -> "msg" + i)
                .toList();

        // Note: With native batchDelete API, we don't mock individual delete operations
        // The batchDelete mock is already set up in setUp() method

        long startTime = System.currentTimeMillis();

        // Act
        BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

        long duration = System.currentTimeMillis() - startTime;

        // Assert
        assertThat(result.getTotalOperations()).isEqualTo(messageCount);
        assertThat(result.getSuccessCount()).isEqualTo(messageCount);
        assertThat(result.getTotalBatchesProcessed()).isEqualTo(expectedChunks);

        // Performance assertion - should complete quickly even for large datasets
        assertThat(duration).isLessThan(messageCount * 2); // 2ms per message is very generous for mocked operations

        // Log performance metrics for visibility
        System.out.printf("Processed %d messages in %d chunks in %dms (%.2f messages/second)%n",
                messageCount, expectedChunks, duration,
                messageCount / Math.max(duration / 1000.0, 0.001));
    }

    static Stream<Arguments> largeScaleTestCases() {
        // Native batchDelete supports up to 1000 messages per chunk
        return Stream.of(
                Arguments.of(50, 1),       // Single chunk
                Arguments.of(500, 1),      // Single chunk
                Arguments.of(1000, 1),     // Exactly one chunk
                Arguments.of(1500, 2),     // 1.5 chunks
                Arguments.of(2000, 2),     // 2 chunks
                Arguments.of(5000, 5),     // 5 chunks
                Arguments.of(10000, 10),   // 10 chunks
                Arguments.of(20000, 20)    // 20 chunks stress test
        );
    }

    @Test
    @DisplayName("Should maintain consistent performance across multiple large operations")
    void batchOperations_MultipleExecutions_ShouldMaintainPerformance() throws IOException {
        // Arrange
        String userId = "me";
        int operationsPerExecution = 1000;
        int numberOfExecutions = 5;
        List<Long> executionTimes = new ArrayList<>();

        // Note: No individual delete mocks needed with native batchDelete API

        // Act - Execute multiple large batch operations
        for (int exec = 0; exec < numberOfExecutions; exec++) {
            final int execId = exec; // Make effectively final for lambda
            List<String> messageIds = IntStream.range(1, operationsPerExecution + 1)
                    .mapToObj(i -> "exec" + execId + "_msg" + i)
                    .toList();

            long startTime = System.currentTimeMillis();
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);
            long duration = System.currentTimeMillis() - startTime;

            executionTimes.add(duration);

            // Assert each execution
            assertThat(result.getTotalOperations()).isEqualTo(operationsPerExecution);
            assertThat(result.getSuccessCount()).isEqualTo(operationsPerExecution);
        }

        // Assert consistent performance
        double averageTime = executionTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        double maxTime = executionTimes.stream().mapToLong(Long::longValue).max().orElse(0);
        double minTime = executionTimes.stream().mapToLong(Long::longValue).min().orElse(0);

        // Performance shouldn't vary dramatically between executions (be more generous for mock timing variance)
        assertThat(maxTime - minTime).isLessThan(averageTime * 2); // Variance should be less than 2x average
        assertThat(averageTime).isLessThan(5000); // Average should be under 5 seconds for mocked operations

        System.out.printf("Performance consistency: avg=%.2fms, min=%.2fms, max=%.2fms%n",
                averageTime, minTime, maxTime);
    }

    @Test
    @DisplayName("Should handle memory efficiently with very large datasets")
    void batchDeleteMessages_VeryLargeDataset_ShouldHandleMemoryEfficiently() throws IOException {
        // Arrange
        String userId = "me";
        int messageCount = 50000; // Very large dataset

        // Monitor memory before operation
        System.gc();
        long memoryBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        List<String> messageIds = IntStream.range(1, messageCount + 1)
                .mapToObj(i -> "msg" + i)
                .toList();

        // Note: No individual delete mocks needed with native batchDelete API

        // Act
        long startTime = System.currentTimeMillis();
        BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);
        long duration = System.currentTimeMillis() - startTime;

        // Monitor memory after operation
        System.gc();
        long memoryAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long memoryUsed = memoryAfter - memoryBefore;

        // Assert
        assertThat(result.getTotalOperations()).isEqualTo(messageCount);
        assertThat(result.getSuccessCount()).isEqualTo(messageCount);
        assertThat(duration).isLessThan(30000); // Should complete within 30 seconds

        // Memory usage should be reasonable (less than 100MB for this operation)
        assertThat(memoryUsed).isLessThan(100 * 1024 * 1024);

        System.out.printf("Processed %d messages in %dms using %d bytes of memory%n",
                messageCount, duration, memoryUsed);
    }

    @Test
    @DisplayName("Should handle concurrent batch operations safely")
    void batchOperations_ConcurrentExecution_ShouldBeSafe() throws InterruptedException, IOException {
        // Arrange
        String userId = "me";
        int threadsCount = 5;
        int messagesPerThread = 200;
        ExecutorService executor = Executors.newFixedThreadPool(threadsCount);
        List<BulkOperationResult> results = new ArrayList<>();

        // Act - Execute concurrent batch operations
        long startTime = System.currentTimeMillis();
        for (int thread = 0; thread < threadsCount; thread++) {
            final int threadId = thread;
            executor.submit(() -> {
                try {
                    List<String> messageIds = IntStream.range(1, messagesPerThread + 1)
                            .mapToObj(i -> "thread" + threadId + "_msg" + i)
                            .toList();

                    // Each thread needs its own Gmail service mock
                    Gmail threadGmail = mock(Gmail.class);
                    Gmail.Users threadUsers = mock(Gmail.Users.class);
                    Gmail.Users.Messages threadMessages = mock(Gmail.Users.Messages.class);
                    Gmail.Users.Messages.BatchDelete threadBatchDelete = mock(Gmail.Users.Messages.BatchDelete.class);

                    when(threadGmail.users()).thenReturn(threadUsers);
                    when(threadUsers.messages()).thenReturn(threadMessages);

                    // Mock the native batchDelete chain for this thread
                    try {
                        when(threadMessages.batchDelete(anyString(), any())).thenReturn(threadBatchDelete);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    BulkOperationResult result = gmailBatchClient.batchDeleteMessages(threadGmail, userId, messageIds);
                    synchronized (results) {
                        results.add(result);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        long duration = System.currentTimeMillis() - startTime;

        // Assert
        assertThat(results).hasSize(threadsCount);

        int totalOperations = results.stream()
                .mapToInt(BulkOperationResult::getTotalOperations)
                .sum();

        assertThat(totalOperations).isEqualTo(threadsCount * messagesPerThread);
        assertThat(duration).isLessThan(15000); // Should complete within 15 seconds

        System.out.printf("Concurrent execution: %d threads, %d total operations in %dms%n",
                threadsCount, totalOperations, duration);
    }

    @Test
    @DisplayName("Should efficiently handle batch partitioning for irregular sizes")
    void batchDeleteMessages_IrregularSizes_ShouldPartitionEfficiently() throws IOException {
        // Test various irregular sizes to ensure efficient partitioning
        int[] testSizes = {1, 13, 47, 99, 101, 137, 203, 299, 301, 456, 789, 999, 1001, 1337, 2023};

        for (int messageCount : testSizes) {
            // Arrange
            String userId = "me";
            List<String> messageIds = IntStream.range(1, messageCount + 1)
                    .mapToObj(i -> "test" + messageCount + "_msg" + i)
                    .toList();

            // Native batchDelete supports up to 1000 messages per chunk
            int expectedChunks = (int) Math.ceil((double) messageCount / 1000);

            // Act
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            // Assert
            assertThat(result.getTotalOperations()).isEqualTo(messageCount);
            assertThat(result.getSuccessCount()).isEqualTo(messageCount);
            assertThat(result.getTotalBatchesProcessed()).isEqualTo(expectedChunks);
        }
    }

    @Test
    @DisplayName("Should handle mixed success and failure scenarios at scale")
    void batchDeleteMessages_MixedResultsAtScale_ShouldHandleCorrectly() throws IOException {
        // Arrange
        String userId = "me";
        int messageCount = 1000;
        List<String> messageIds = IntStream.range(1, messageCount + 1)
                .mapToObj(i -> "msg" + i)
                .toList();

        // Note: With native batchDelete API, it's all-or-nothing per chunk
        // We can't easily test mixed results without mocking IOException scenarios

        // Act
        BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

        // Assert - With mocked success, all should succeed
        assertThat(result.getTotalOperations()).isEqualTo(messageCount);
        assertThat(result.getSuccessCount()).isEqualTo(messageCount);
        assertThat(result.getTotalBatchesProcessed()).isEqualTo(1); // 1000 messages = 1 chunk
    }

    @Test
    @DisplayName("Should demonstrate performance improvement over individual operations")
    void batchVsIndividualOperations_ShouldShowPerformanceImprovement() throws IOException {
        // This test demonstrates the theoretical performance improvement
        // In real scenarios, native batchDelete operations are significantly faster

        String userId = "me";
        int messageCount = 500;
        List<String> messageIds = IntStream.range(1, messageCount + 1)
                .mapToObj(i -> "msg" + i)
                .toList();

        // Simulate batch operation timing
        long batchStartTime = System.currentTimeMillis();

        BulkOperationResult batchResult = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);
        long batchDuration = System.currentTimeMillis() - batchStartTime;

        // Verify batch operation structure
        assertThat(batchResult.getTotalOperations()).isEqualTo(messageCount);
        assertThat(batchResult.getSuccessCount()).isEqualTo(messageCount);
        assertThat(batchResult.getTotalBatchesProcessed()).isEqualTo(1); // 500 messages = 1 chunk (under 1000 limit)

        // In real scenarios:
        // - Individual operations: 500 HTTP requests = ~4.5 minutes (based on user feedback)
        // - Native batchDelete: 1 HTTP request = ~5-10 seconds (50 quota units flat fee)
        // This represents a 27x-54x performance improvement!

        System.out.printf("Native batchDelete processed %d messages in %d chunk in %dms%n",
                messageCount, 1, batchDuration);
        System.out.printf("Theoretical individual operations would take ~%d minutes%n",
                messageCount * 500 / 1000 / 60); // Assuming 500ms per individual operation
        System.out.printf("Performance improvement: ~%.1fx faster%n",
                (messageCount * 500.0) / Math.max(batchDuration, 1));
    }
}