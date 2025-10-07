package com.aucontraire.gmailbuddy.exception;

import com.aucontraire.gmailbuddy.service.BulkOperationResult;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a batch operation fails or has partial failures.
 * Contains detailed information about which operations succeeded and which failed.
 *
 * <p>This exception is thrown when batch operations like bulk delete or label modification
 * encounter errors. It provides access to the BulkOperationResult for detailed analysis
 * of the success/failure status of individual operations.</p>
 *
 * @author Gmail Buddy Team
 * @since 1.0
 */
public class BatchOperationException extends GmailBuddyServerException {

    private static final String ERROR_CODE = "BATCH_OPERATION_ERROR";

    private final BulkOperationResult operationResult;
    private final boolean partialFailure;

    /**
     * Constructs a new batch operation exception for complete failure.
     *
     * @param message the detail message explaining the batch operation failure
     * @param operationResult the result containing success/failure details
     */
    public BatchOperationException(String message, BulkOperationResult operationResult) {
        super(ERROR_CODE, message);
        this.operationResult = operationResult;
        this.partialFailure = operationResult.hasSuccesses();
    }

    /**
     * Constructs a new batch operation exception for complete failure with cause.
     *
     * @param message the detail message explaining the batch operation failure
     * @param operationResult the result containing success/failure details
     * @param cause the underlying cause of the batch operation failure
     */
    public BatchOperationException(String message, BulkOperationResult operationResult, Throwable cause) {
        super(ERROR_CODE, message, cause);
        this.operationResult = operationResult;
        this.partialFailure = operationResult.hasSuccesses();
    }

    /**
     * Creates a batch operation exception for partial failures.
     * Used when some operations in the batch succeeded but others failed.
     *
     * @param operationResult the result containing success/failure details
     * @return a new BatchOperationException for partial failure
     */
    public static BatchOperationException partialFailure(BulkOperationResult operationResult) {
        String message = String.format(
            "Batch %s operation partially failed: %d/%d operations succeeded (%.1f%% success rate)",
            operationResult.getOperationType(),
            operationResult.getSuccessCount(),
            operationResult.getTotalOperations(),
            operationResult.getSuccessRate()
        );

        BatchOperationException exception = new BatchOperationException(message, operationResult);
        return exception;
    }

    /**
     * Creates a batch operation exception for complete failures.
     * Used when all operations in the batch failed.
     *
     * @param operationResult the result containing success/failure details
     * @return a new BatchOperationException for complete failure
     */
    public static BatchOperationException completeFailure(BulkOperationResult operationResult) {
        String message = String.format(
            "Batch %s operation completely failed: 0/%d operations succeeded",
            operationResult.getOperationType(),
            operationResult.getTotalOperations()
        );

        return new BatchOperationException(message, operationResult);
    }

    /**
     * Creates a batch operation exception for complete failures with cause.
     *
     * @param operationResult the result containing success/failure details
     * @param cause the underlying cause of the failure
     * @return a new BatchOperationException for complete failure
     */
    public static BatchOperationException completeFailure(BulkOperationResult operationResult, Throwable cause) {
        String message = String.format(
            "Batch %s operation completely failed: 0/%d operations succeeded",
            operationResult.getOperationType(),
            operationResult.getTotalOperations()
        );

        return new BatchOperationException(message, operationResult, cause);
    }

    /**
     * Gets the detailed operation result.
     *
     * @return the BulkOperationResult containing success/failure details
     */
    public BulkOperationResult getOperationResult() {
        return operationResult;
    }

    /**
     * Indicates whether this was a partial failure (some operations succeeded).
     *
     * @return true if some operations succeeded, false if all failed
     */
    public boolean isPartialFailure() {
        return partialFailure;
    }

    /**
     * Indicates whether this was a complete failure (no operations succeeded).
     *
     * @return true if all operations failed, false if some succeeded
     */
    public boolean isCompleteFailure() {
        return !partialFailure;
    }

    /**
     * Gets the number of operations that succeeded.
     *
     * @return the success count
     */
    public int getSuccessCount() {
        return operationResult.getSuccessCount();
    }

    /**
     * Gets the number of operations that failed.
     *
     * @return the failure count
     */
    public int getFailureCount() {
        return operationResult.getFailureCount();
    }

    /**
     * Gets the total number of operations attempted.
     *
     * @return the total operation count
     */
    public int getTotalOperations() {
        return operationResult.getTotalOperations();
    }

    /**
     * Gets the success rate as a percentage.
     *
     * @return the success rate (0.0 to 100.0)
     */
    public double getSuccessRate() {
        return operationResult.getSuccessRate();
    }

    @Override
    public int getHttpStatus() {
        // Use 207 Multi-Status for partial failures, 502 Bad Gateway for complete failures
        return partialFailure ? HttpStatus.MULTI_STATUS.value() : HttpStatus.BAD_GATEWAY.value();
    }
}