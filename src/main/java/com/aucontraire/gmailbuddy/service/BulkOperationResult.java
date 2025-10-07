package com.aucontraire.gmailbuddy.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Result class for tracking the success and failure of bulk Gmail operations.
 * Provides detailed reporting on which operations succeeded and which failed,
 * along with error details for failed operations.
 *
 * This class is thread-safe and can be used across multiple batch operations.
 *
 * @author Gmail Buddy Team
 * @since 1.0
 */
public class BulkOperationResult {

    private final List<String> successfulOperations = new ArrayList<>();
    private final Map<String, String> failedOperations = new ConcurrentHashMap<>();
    private final Map<String, Integer> retryAttempts = new ConcurrentHashMap<>();
    private final String operationType;
    private final long startTime;
    private long endTime;
    private int totalBatchesProcessed = 0;
    private int totalBatchesRetried = 0;

    /**
     * Creates a new BulkOperationResult for tracking a specific operation type.
     *
     * @param operationType the type of operation being tracked (e.g., "DELETE", "MODIFY_LABELS")
     */
    public BulkOperationResult(String operationType) {
        this.operationType = operationType;
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Records a successful operation.
     *
     * @param identifier the identifier of the successful operation (e.g., message ID)
     */
    public synchronized void addSuccess(String identifier) {
        successfulOperations.add(identifier);
    }

    /**
     * Records a failed operation with error details.
     *
     * @param identifier the identifier of the failed operation (e.g., message ID)
     * @param errorMessage the error message describing why the operation failed
     */
    public void addFailure(String identifier, String errorMessage) {
        failedOperations.put(identifier, errorMessage);
    }

    /**
     * Marks the operation as completed and records the end time.
     */
    public void markCompleted() {
        this.endTime = System.currentTimeMillis();
    }

    /**
     * Gets the total number of operations attempted.
     *
     * @return the sum of successful and failed operations
     */
    public int getTotalOperations() {
        return successfulOperations.size() + failedOperations.size();
    }

    /**
     * Gets the number of successful operations.
     *
     * @return the count of successful operations
     */
    public int getSuccessCount() {
        return successfulOperations.size();
    }

    /**
     * Gets the number of failed operations.
     *
     * @return the count of failed operations
     */
    public int getFailureCount() {
        return failedOperations.size();
    }

    /**
     * Gets the list of identifiers for successful operations.
     *
     * @return a copy of the successful operations list
     */
    public List<String> getSuccessfulOperations() {
        return new ArrayList<>(successfulOperations);
    }

    /**
     * Gets the map of failed operations with their error messages.
     *
     * @return a copy of the failed operations map
     */
    public Map<String, String> getFailedOperations() {
        return Map.copyOf(failedOperations);
    }

    /**
     * Gets the operation type being tracked.
     *
     * @return the operation type
     */
    public String getOperationType() {
        return operationType;
    }

    /**
     * Gets the duration of the operation in milliseconds.
     *
     * @return the operation duration, or -1 if not yet completed
     */
    public long getDurationMs() {
        if (endTime == 0) {
            return -1; // Not yet completed
        }
        return endTime - startTime;
    }

    /**
     * Checks if all operations were successful.
     *
     * @return true if no operations failed, false otherwise
     */
    public boolean isCompleteSuccess() {
        return failedOperations.isEmpty() && !successfulOperations.isEmpty();
    }

    /**
     * Checks if any operations were successful.
     *
     * @return true if at least one operation succeeded, false otherwise
     */
    public boolean hasSuccesses() {
        return !successfulOperations.isEmpty();
    }

    /**
     * Checks if any operations failed.
     *
     * @return true if at least one operation failed, false otherwise
     */
    public boolean hasFailures() {
        return !failedOperations.isEmpty();
    }

    /**
     * Gets the success rate as a percentage.
     *
     * @return the success rate (0.0 to 100.0), or 0.0 if no operations were attempted
     */
    public double getSuccessRate() {
        int total = getTotalOperations();
        if (total == 0) {
            return 0.0;
        }
        return (double) getSuccessCount() / total * 100.0;
    }

    /**
     * Increments the retry attempt count for a specific operation.
     *
     * @param identifier the identifier of the operation being retried
     */
    public void incrementRetryAttempt(String identifier) {
        retryAttempts.put(identifier, retryAttempts.getOrDefault(identifier, 0) + 1);
    }

    /**
     * Gets the number of retry attempts for a specific operation.
     *
     * @param identifier the identifier of the operation
     * @return the number of retry attempts, or 0 if no retries
     */
    public int getRetryAttempts(String identifier) {
        return retryAttempts.getOrDefault(identifier, 0);
    }

    /**
     * Gets the total number of batches processed.
     *
     * @return the total batch count
     */
    public int getTotalBatchesProcessed() {
        return totalBatchesProcessed;
    }

    /**
     * Increments the total number of batches processed.
     */
    public void incrementBatchesProcessed() {
        this.totalBatchesProcessed++;
    }

    /**
     * Gets the total number of batches that were retried.
     *
     * @return the total retry batch count
     */
    public int getTotalBatchesRetried() {
        return totalBatchesRetried;
    }

    /**
     * Increments the total number of batches retried.
     */
    public void incrementBatchesRetried() {
        this.totalBatchesRetried++;
    }

    /**
     * Gets the total number of retry attempts across all operations.
     *
     * @return the sum of all retry attempts
     */
    public int getTotalRetryAttempts() {
        return retryAttempts.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Clears retry tracking for a specific operation (used when operation succeeds after retry).
     *
     * @param identifier the identifier of the operation
     */
    public void clearRetryAttempts(String identifier) {
        retryAttempts.remove(identifier);
    }

    @Override
    public String toString() {
        int total = getTotalOperations();
        long duration = getDurationMs();
        String durationStr = duration >= 0 ? duration + "ms" : "in progress";

        return String.format(
            "BulkOperationResult{type='%s', total=%d, success=%d, failed=%d, successRate=%.1f%%, " +
            "batches=%d, retriedBatches=%d, totalRetries=%d, duration=%s}",
            operationType, total, getSuccessCount(), getFailureCount(), getSuccessRate(),
            totalBatchesProcessed, totalBatchesRetried, getTotalRetryAttempts(), durationStr
        );
    }
}