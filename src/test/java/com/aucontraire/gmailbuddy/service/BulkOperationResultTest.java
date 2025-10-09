package com.aucontraire.gmailbuddy.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BulkOperationResult class.
 * Tests all the functionality for tracking batch operation success/failure.
 */
class BulkOperationResultTest {

    @Test
    void constructor_SetsOperationTypeAndStartTime() {
        // When
        BulkOperationResult result = new BulkOperationResult("DELETE");

        // Then
        assertEquals("DELETE", result.getOperationType());
        assertEquals(0, result.getTotalOperations());
        assertEquals(0, result.getSuccessCount());
        assertEquals(0, result.getFailureCount());
        assertEquals(-1, result.getDurationMs()); // Not completed yet
    }

    @Test
    void addSuccess_IncrementsSuccessCount() {
        // Given
        BulkOperationResult result = new BulkOperationResult("DELETE");

        // When
        result.addSuccess("msg1");
        result.addSuccess("msg2");

        // Then
        assertEquals(2, result.getSuccessCount());
        assertEquals(0, result.getFailureCount());
        assertEquals(2, result.getTotalOperations());
        assertTrue(result.hasSuccesses());
        assertFalse(result.hasFailures());
    }

    @Test
    void addFailure_IncrementsFailureCount() {
        // Given
        BulkOperationResult result = new BulkOperationResult("DELETE");

        // When
        result.addFailure("msg1", "Not found");
        result.addFailure("msg2", "Permission denied");

        // Then
        assertEquals(0, result.getSuccessCount());
        assertEquals(2, result.getFailureCount());
        assertEquals(2, result.getTotalOperations());
        assertFalse(result.hasSuccesses());
        assertTrue(result.hasFailures());
    }

    @Test
    void markCompleted_SetsDurationAndEndTime() {
        // Given
        BulkOperationResult result = new BulkOperationResult("DELETE");
        result.addSuccess("msg1");

        // When
        result.markCompleted();

        // Then
        assertTrue(result.getDurationMs() >= 0);
    }

    @Test
    void isCompleteSuccess_WithAllSuccesses_ReturnsTrue() {
        // Given
        BulkOperationResult result = new BulkOperationResult("DELETE");
        result.addSuccess("msg1");
        result.addSuccess("msg2");

        // When & Then
        assertTrue(result.isCompleteSuccess());
    }

    @Test
    void isCompleteSuccess_WithFailures_ReturnsFalse() {
        // Given
        BulkOperationResult result = new BulkOperationResult("DELETE");
        result.addSuccess("msg1");
        result.addFailure("msg2", "Error");

        // When & Then
        assertFalse(result.isCompleteSuccess());
    }

    @Test
    void isCompleteSuccess_WithNoOperations_ReturnsFalse() {
        // Given
        BulkOperationResult result = new BulkOperationResult("DELETE");

        // When & Then
        assertFalse(result.isCompleteSuccess());
    }

    @Test
    void getSuccessRate_CalculatesCorrectly() {
        // Given
        BulkOperationResult result = new BulkOperationResult("DELETE");
        result.addSuccess("msg1");
        result.addSuccess("msg2");
        result.addSuccess("msg3");
        result.addFailure("msg4", "Error");
        result.addFailure("msg5", "Error");

        // When
        double successRate = result.getSuccessRate();

        // Then
        assertEquals(60.0, successRate, 0.1);
    }

    @Test
    void getSuccessRate_WithNoOperations_ReturnsZero() {
        // Given
        BulkOperationResult result = new BulkOperationResult("DELETE");

        // When
        double successRate = result.getSuccessRate();

        // Then
        assertEquals(0.0, successRate);
    }

    @Test
    void getSuccessfulOperations_ReturnsImmutableCopy() {
        // Given
        BulkOperationResult result = new BulkOperationResult("DELETE");
        result.addSuccess("msg1");
        result.addSuccess("msg2");

        // When
        var successList = result.getSuccessfulOperations();

        // Then
        assertEquals(2, successList.size());
        assertTrue(successList.contains("msg1"));
        assertTrue(successList.contains("msg2"));

        // Verify it's a copy - modifying it shouldn't affect the original
        successList.add("msg3");
        assertEquals(2, result.getSuccessCount()); // Should still be 2
    }

    @Test
    void getFailedOperations_ReturnsImmutableCopy() {
        // Given
        BulkOperationResult result = new BulkOperationResult("DELETE");
        result.addFailure("msg1", "Error 1");
        result.addFailure("msg2", "Error 2");

        // When
        var failureMap = result.getFailedOperations();

        // Then
        assertEquals(2, failureMap.size());
        assertEquals("Error 1", failureMap.get("msg1"));
        assertEquals("Error 2", failureMap.get("msg2"));

        // Verify it's immutable
        assertThrows(UnsupportedOperationException.class, () -> failureMap.put("msg3", "Error 3"));
    }

    @Test
    void toString_IncludesAllRelevantInformation() {
        // Given
        BulkOperationResult result = new BulkOperationResult("MODIFY_LABELS");
        result.addSuccess("msg1");
        result.addSuccess("msg2");
        result.addFailure("msg3", "Error");
        result.markCompleted();

        // When
        String resultString = result.toString();

        // Then
        assertTrue(resultString.contains("MODIFY_LABELS"));
        assertTrue(resultString.contains("total=3"));
        assertTrue(resultString.contains("success=2"));
        assertTrue(resultString.contains("failed=1"));
        assertTrue(resultString.contains("successRate=66.7%"));
        assertTrue(resultString.contains("ms"));
    }

    @Test
    void toString_WithInProgressOperation_ShowsInProgress() {
        // Given
        BulkOperationResult result = new BulkOperationResult("DELETE");
        result.addSuccess("msg1");
        // Don't mark as completed

        // When
        String resultString = result.toString();

        // Then
        assertTrue(resultString.contains("in progress"));
    }

    @Test
    void concurrentAccess_ThreadSafe() throws InterruptedException {
        // Given
        BulkOperationResult result = new BulkOperationResult("DELETE");

        // When - Simulate concurrent access
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 50; i++) {
                result.addSuccess("success" + i);
            }
        });

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 50; i++) {
                result.addFailure("failure" + i, "Error " + i);
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        // Then
        assertEquals(50, result.getSuccessCount());
        assertEquals(50, result.getFailureCount());
        assertEquals(100, result.getTotalOperations());
    }

    @Test
    @DisplayName("Should handle high-concurrency thread safety with executor service")
    void highConcurrencyThreadSafety_ShouldMaintainDataIntegrity() throws InterruptedException {
        // Arrange
        BulkOperationResult result = new BulkOperationResult("DELETE");
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(10);
        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger failureCounter = new AtomicInteger(0);

        // Act - Submit 10 threads each adding 100 operations
        for (int threadId = 0; threadId < 10; threadId++) {
            final int currentThreadId = threadId;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < 100; i++) {
                        if (i % 2 == 0) {
                            result.addSuccess("thread" + currentThreadId + "_success" + i);
                            successCounter.incrementAndGet();
                        } else {
                            result.addFailure("thread" + currentThreadId + "_failure" + i, "Error " + i);
                            failureCounter.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all threads to complete
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        // Assert
        assertThat(result.getSuccessCount()).isEqualTo(successCounter.get());
        assertThat(result.getFailureCount()).isEqualTo(failureCounter.get());
        assertThat(result.getTotalOperations()).isEqualTo(1000);
        assertThat(result.getSuccessCount()).isEqualTo(500);
        assertThat(result.getFailureCount()).isEqualTo(500);
        assertThat(result.getSuccessRate()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("Should handle duplicate success IDs correctly")
    void addSuccess_DuplicateIds_ShouldAddBothEntries() {
        // Arrange
        BulkOperationResult result = new BulkOperationResult("DELETE");

        // Act
        result.addSuccess("msg1");
        result.addSuccess("msg1"); // Duplicate ID

        // Assert
        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getSuccessfulOperations()).hasSize(2);
        assertThat(result.getSuccessfulOperations()).containsExactly("msg1", "msg1");
    }

    @Test
    @DisplayName("Should handle duplicate failure IDs by overwriting")
    void addFailure_DuplicateIds_ShouldOverwritePreviousEntry() {
        // Arrange
        BulkOperationResult result = new BulkOperationResult("DELETE");

        // Act
        result.addFailure("msg1", "First error");
        result.addFailure("msg1", "Second error"); // Same ID, different error

        // Assert
        assertThat(result.getFailureCount()).isEqualTo(1);
        assertThat(result.getFailedOperations()).hasSize(1);
        assertThat(result.getFailedOperations().get("msg1")).isEqualTo("Second error");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100, 1000, 10000})
    @DisplayName("Should handle large volumes of operations efficiently")
    void handleLargeVolumes_ShouldMaintainPerformance(int operationCount) {
        // Arrange
        BulkOperationResult result = new BulkOperationResult("DELETE");
        long startTime = System.currentTimeMillis();

        // Act
        for (int i = 0; i < operationCount; i++) {
            if (i % 2 == 0) {
                result.addSuccess("msg" + i);
            } else {
                result.addFailure("msg" + i, "Error " + i);
            }
        }
        result.markCompleted();

        long endTime = System.currentTimeMillis();

        // Assert
        assertThat(result.getTotalOperations()).isEqualTo(operationCount);
        assertThat(result.getSuccessCount()).isEqualTo(operationCount / 2);
        assertThat(result.getFailureCount()).isEqualTo(operationCount - operationCount / 2);
        assertThat(endTime - startTime).isLessThan(1000); // Should complete within 1 second
    }

    @Test
    @DisplayName("Should handle null error messages in failures")
    void addFailure_NullErrorMessage_ShouldHandleGracefully() {
        // Arrange
        BulkOperationResult result = new BulkOperationResult("DELETE");

        // Act
        result.addFailure("msg1", null);
        result.addFailure("msg2", "");

        // Assert
        assertThat(result.getFailureCount()).isEqualTo(2);
        assertThat(result.getFailedOperations().get("msg1")).isNull();
        assertThat(result.getFailedOperations().get("msg2")).isEmpty();
    }

    @Test
    @DisplayName("Should calculate duration accurately")
    void getDurationMs_ShouldCalculateAccurately() throws InterruptedException {
        // Arrange
        BulkOperationResult result = new BulkOperationResult("DELETE");
        result.addSuccess("msg1");

        // Wait a bit to ensure measurable duration
        Thread.sleep(10);

        // Act
        result.markCompleted();

        // Assert
        assertThat(result.getDurationMs()).isGreaterThanOrEqualTo(10);
        assertThat(result.getDurationMs()).isLessThan(1000); // Reasonable upper bound
    }

    @Test
    @DisplayName("Should provide thread-safe access to collections")
    void getCollections_ConcurrentAccess_ShouldBeThreadSafe() throws InterruptedException {
        // Arrange
        BulkOperationResult result = new BulkOperationResult("DELETE");
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(5);

        // Act - Add operations while reading collections concurrently
        for (int i = 0; i < 5; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 20; j++) {
                        result.addSuccess("thread" + threadId + "_msg" + j);
                        result.addFailure("thread" + threadId + "_err" + j, "Error " + j);

                        // Concurrent reads
                        var successes = result.getSuccessfulOperations();
                        var failures = result.getFailedOperations();

                        // These shouldn't throw exceptions
                        assertThat(successes).isNotNull();
                        assertThat(failures).isNotNull();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for completion
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        // Assert final state
        assertThat(result.getTotalOperations()).isEqualTo(200);
        assertThat(result.getSuccessCount()).isEqualTo(100);
        assertThat(result.getFailureCount()).isEqualTo(100);
    }

    @Test
    @DisplayName("Should provide meaningful toString output for edge cases")
    void toString_EdgeCases_ShouldProveMeaningfulOutput() {
        // Test with zero operations
        BulkOperationResult emptyResult = new BulkOperationResult("EMPTY_TEST");
        emptyResult.markCompleted();

        String emptyString = emptyResult.toString();
        assertThat(emptyString)
                .contains("EMPTY_TEST")
                .contains("total=0")
                .contains("success=0")
                .contains("failed=0")
                .contains("successRate=0.0%");

        // Test with only failures
        BulkOperationResult failureOnlyResult = new BulkOperationResult("FAILURE_TEST");
        failureOnlyResult.addFailure("msg1", "Error");
        failureOnlyResult.markCompleted();

        String failureString = failureOnlyResult.toString();
        assertThat(failureString)
                .contains("FAILURE_TEST")
                .contains("total=1")
                .contains("success=0")
                .contains("failed=1")
                .contains("successRate=0.0%");
    }
}