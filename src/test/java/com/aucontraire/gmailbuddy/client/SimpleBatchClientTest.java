package com.aucontraire.gmailbuddy.client;

import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import com.aucontraire.gmailbuddy.service.BulkOperationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Simple unit tests for GmailBatchClient functionality.
 * Focuses on testing the core logic without complex Gmail API mocking.
 */
@ExtendWith(MockitoExtension.class)
class SimpleBatchClientTest {

    @Mock
    private GmailBuddyProperties properties;

    private GmailBatchClient batchClient;

    @BeforeEach
    void setUp() {
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
        lenient().when(batchOps.delayBetweenBatchesMs()).thenReturn(0L);
        lenient().when(batchOps.microDelayBetweenOperationsMs()).thenReturn(10L);

        batchClient = new GmailBatchClient(properties);
    }

    @Test
    void getMaxBatchSize_ReturnsCorrectValue() {
        // When
        int maxBatchSize = batchClient.getMaxBatchSize();

        // Then
        assertEquals(100, maxBatchSize);
    }

    @Test
    void validateBatchResult_WithCompleteSuccess_DoesNotThrow() {
        // Given
        BulkOperationResult successResult = new BulkOperationResult("DELETE");
        successResult.addSuccess("msg1");
        successResult.addSuccess("msg2");
        successResult.markCompleted();

        // When & Then - Should not throw
        assertDoesNotThrow(() -> batchClient.validateBatchResult(successResult, true));
        assertDoesNotThrow(() -> batchClient.validateBatchResult(successResult, false));
    }

    @Test
    void validateBatchResult_WithCompleteFailure_ThrowsException() {
        // Given
        BulkOperationResult failureResult = new BulkOperationResult("DELETE");
        failureResult.addFailure("msg1", "Not found");
        failureResult.addFailure("msg2", "Permission denied");
        failureResult.markCompleted();

        // When & Then
        assertThrows(com.aucontraire.gmailbuddy.exception.BatchOperationException.class,
                    () -> batchClient.validateBatchResult(failureResult, true));
        assertThrows(com.aucontraire.gmailbuddy.exception.BatchOperationException.class,
                    () -> batchClient.validateBatchResult(failureResult, false));
    }

    @Test
    void areFailuresRetryable_WithRetryableErrors_ReturnsTrue() {
        // Given
        BulkOperationResult result = new BulkOperationResult("DELETE");
        result.addFailure("msg1", "Timeout occurred");
        result.addFailure("msg2", "Service unavailable");
        result.markCompleted();

        // When
        boolean retryable = batchClient.areFailuresRetryable(result);

        // Then
        assertTrue(retryable);
    }

    @Test
    void areFailuresRetryable_WithNonRetryableErrors_ReturnsFalse() {
        // Given
        BulkOperationResult result = new BulkOperationResult("DELETE");
        result.addFailure("msg1", "Message not found");
        result.addFailure("msg2", "Permission denied");
        result.markCompleted();

        // When
        boolean retryable = batchClient.areFailuresRetryable(result);

        // Then
        assertFalse(retryable);
    }

    @Test
    void getRetryableFailures_FiltersCorrectly() {
        // Given
        BulkOperationResult result = new BulkOperationResult("DELETE");
        result.addFailure("msg1", "Timeout occurred");  // Retryable
        result.addFailure("msg2", "Message not found"); // Not retryable
        result.addFailure("msg3", "Rate limit exceeded"); // Retryable
        result.markCompleted();

        // When
        List<String> retryableFailures = batchClient.getRetryableFailures(result);

        // Then
        assertEquals(2, retryableFailures.size());
        assertTrue(retryableFailures.contains("msg1"));
        assertTrue(retryableFailures.contains("msg3"));
        assertFalse(retryableFailures.contains("msg2"));
    }

    @Test
    void validateBatchResult_WithEmptyOperations_LogsWarning() {
        // Given
        BulkOperationResult emptyResult = new BulkOperationResult("DELETE");
        emptyResult.markCompleted();

        // When & Then - Should not throw for empty operations
        assertDoesNotThrow(() -> batchClient.validateBatchResult(emptyResult, true));
        assertDoesNotThrow(() -> batchClient.validateBatchResult(emptyResult, false));
    }

    @Test
    void validateBatchResult_WithPartialFailureAndLenientPolicy_DoesNotThrow() {
        // Given
        BulkOperationResult partialResult = new BulkOperationResult("DELETE");
        partialResult.addSuccess("msg1");
        partialResult.addSuccess("msg2");
        partialResult.addFailure("msg3", "Not found");
        partialResult.markCompleted();

        // When & Then
        assertDoesNotThrow(() -> batchClient.validateBatchResult(partialResult, false));
    }

    @Test
    void validateBatchResult_WithPartialFailureAndStrictPolicy_ThrowsException() {
        // Given
        BulkOperationResult partialResult = new BulkOperationResult("DELETE");
        partialResult.addSuccess("msg1");
        partialResult.addSuccess("msg2");
        partialResult.addFailure("msg3", "Not found");
        partialResult.markCompleted();

        // When & Then
        assertThrows(com.aucontraire.gmailbuddy.exception.BatchOperationException.class,
                    () -> batchClient.validateBatchResult(partialResult, true));
    }
}