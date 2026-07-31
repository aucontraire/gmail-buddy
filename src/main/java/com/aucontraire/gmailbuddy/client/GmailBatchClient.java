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
import com.google.api.services.gmail.model.BatchModifyMessagesRequest;
import com.google.api.services.gmail.model.ModifyMessageRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Client for executing Gmail operations in batches to improve performance.
 * Uses Gmail's native batchDelete() endpoint for deletions and native batchModify() endpoint for
 * label modifications (add/remove labels, trash/untrash) while respecting Gmail API limits.
 *
 * This component significantly reduces API calls, quota usage, and improves performance:
 * - Delete operations: Uses batchDelete() API (50 units flat fee for up to 1000 messages)
 * - Label modifications: Uses batchModify() API (~50 units flat fee per chunk of up to 1000
 *   messages). A non-transient whole-chunk failure (e.g. an invalid or inaccessible message ID)
 *   falls back to a per-message recovery pass so valid IDs are not lost to the native operation's
 *   all-or-nothing failure mode; a transient failure (rate limit / server error / transport) is
 *   retried on the native path instead (WI-1, FR-005/FR-006).
 *
 * @author Gmail Buddy Team
 * @since 1.0
 */
@Component
public class GmailBatchClient {

    private static final Logger logger = LoggerFactory.getLogger(GmailBatchClient.class);

    /**
     * Maximum number of operations allowed per batch request by Gmail API.
     * For batchDelete: Gmail allows up to 1000 message IDs per request.
     * For batch modify: Conservative limit of 100 operations to prevent rate limiting.
     */
    private static final int DEFAULT_MAX_BATCH_SIZE = 100;

    /**
     * Maximum number of message IDs allowed in a single batchDelete request.
     * Gmail API limit for the batchDelete endpoint.
     */
    private static final int BATCH_DELETE_MAX_SIZE = 1000;

    /**
     * Default micro-delay between operations within a batch to reduce concurrent pressure.
     * This is used as fallback if configuration is not available.
     */
    private static final long DEFAULT_MICRO_DELAY_BETWEEN_OPERATIONS_MS = 10;

    /**
     * Circuit breaker threshold - number of consecutive failures before cooling off.
     */
    private static final int CIRCUIT_BREAKER_THRESHOLD = 3;

    /**
     * Cooling off period when circuit breaker is triggered (in milliseconds).
     */
    private static final long COOLING_OFF_PERIOD_MS = 30000; // 30 seconds

    private final GmailBuddyProperties properties;

    // Circuit breaker state
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicInteger adaptiveBatchSize = new AtomicInteger(15);

    @Autowired
    public GmailBatchClient(GmailBuddyProperties properties) {
        this.properties = properties;
    }

    /**
     * Executes bulk delete operations using Gmail's native batchDelete() API endpoint.
     * This method uses the efficient batchDelete endpoint which costs only 50 quota units
     * for up to 1000 messages, compared to ~10 units per message with individual delete calls.
     *
     * Performance comparison for 510 messages:
     * - Old approach: 510 individual delete calls = 5,100 quota units, ~210 seconds
     * - New approach: 1 batchDelete call = 50 quota units, ~5 seconds
     * - Savings: 99% quota reduction, 95% time reduction
     *
     * Includes circuit breaker pattern and rate limiting for Gmail API protection.
     *
     * @param gmail the authenticated Gmail service instance
     * @param userId the user ID (typically "me")
     * @param messageIds list of message IDs to delete (up to 1000 per batch)
     * @return BulkOperationResult with detailed success/failure information
     * @throws IOException if there's an error executing the batch delete requests
     */
    public BulkOperationResult batchDeleteMessages(Gmail gmail, String userId, List<String> messageIds)
            throws IOException {
        BulkOperationResult result = new BulkOperationResult("BATCH_DELETE");

        if (messageIds == null || messageIds.isEmpty()) {
            logger.info("No messages to delete");
            result.markCompleted();
            return result;
        }

        logger.info(
                "Starting native batchDelete operation for {} messages (max {} per batch)",
                messageIds.size(),
                BATCH_DELETE_MAX_SIZE);

        // Check circuit breaker state
        if (isCircuitBreakerOpen()) {
            long coolingOffRemaining = getCoolingOffRemainingMs();
            logger.warn("Circuit breaker is open. Cooling off for {} more ms", coolingOffRemaining);

            try {
                Thread.sleep(Math.min(coolingOffRemaining, 5000)); // Max 5 second delay per check
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Operation interrupted during circuit breaker cooling off", e);
            }
        }

        // Split messageIds into chunks of 1000 (Gmail's batchDelete limit)
        List<List<String>> chunks = createBatches(messageIds, BATCH_DELETE_MAX_SIZE);
        logger.info("Split {} messages into {} chunks for batchDelete", messageIds.size(), chunks.size());

        for (int i = 0; i < chunks.size(); i++) {
            List<String> chunk = chunks.get(i);
            logger.debug("Processing chunk {} of {} with {} messages", i + 1, chunks.size(), chunk.size());

            // Track success count before this chunk
            int previousSuccessCount = result.getSuccessCount();

            // Execute batchDelete with retry logic
            executeBatchDeleteWithRetry(gmail, userId, chunk, result);

            // Determine if this chunk succeeded (all messages in chunk were successfully deleted)
            int successfulInChunk = result.getSuccessCount() - previousSuccessCount;
            boolean chunkSuccess = (successfulInChunk == chunk.size());

            // Update adaptive rate limiting based on chunk result
            updateAdaptiveRateLimit(chunkSuccess, chunk.size());

            // Increment batch counter
            result.incrementBatchesProcessed();

            // Add delay between chunks to respect rate limits (except for the last chunk)
            if (i < chunks.size() - 1) {
                addDelayBetweenBatches();
            }
        }

        result.markCompleted();
        logger.info(
                "Native batchDelete operation completed: {} successful, {} failed, {} total (duration: {}ms)",
                result.getSuccessCount(),
                result.getFailureCount(),
                result.getTotalOperations(),
                result.getDurationMs());
        return result;
    }

    /**
     * Executes bulk label modification operations using Gmail's native {@code users.messages.batchModify}
     * endpoint (one call per chunk, ~50 flat quota units) as the primary path (WI-1, FR-001/FR-004).
     *
     * <p>On a non-transient whole-chunk failure (e.g. an invalid or inaccessible message ID), the
     * chunk is recovered via the per-message fallback ({@link #executeBatchModifyLabelsPerMessage})
     * so valid IDs are still reported as successful (FR-005). On a transient failure (rate limit,
     * server error, or transport-level {@link IOException}), the native call is retried with bounded
     * backoff via {@link #executeBatchWithRetry} rather than falling back per-message (FR-006).
     *
     * <p>{@code batchTrash} / {@code batchUntrash} / {@code batchModifyLabels} (the three feature-005
     * batch-by-id endpoints) all share this one primitive via a {@code TRASH} label add/remove, so all
     * three benefit identically (FR-010).
     *
     * <p>Includes circuit breaker pattern and adaptive rate limiting for Gmail API protection.
     *
     * @param gmail the authenticated Gmail service instance
     * @param userId the user ID (typically "me")
     * @param messageIds list of message IDs to modify
     * @param modifyRequest the label modification request
     * @return BulkOperationResult with detailed success/failure information
     * @throws IOException if there's an error executing the batch requests
     */
    public BulkOperationResult batchModifyLabels(
            Gmail gmail, String userId, List<String> messageIds, ModifyMessageRequest modifyRequest)
            throws IOException {
        BulkOperationResult result = new BulkOperationResult("MODIFY_LABELS");
        logger.info(
                "Starting batch modify labels operation for {} messages with adaptive rate limiting",
                messageIds.size());

        // Check circuit breaker state
        if (isCircuitBreakerOpen()) {
            long coolingOffRemaining = getCoolingOffRemainingMs();
            logger.warn("Circuit breaker is open. Cooling off for {} more ms", coolingOffRemaining);

            // Add extended cooling off delay
            try {
                Thread.sleep(Math.min(coolingOffRemaining, 5000)); // Max 5 second delay per check
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Operation interrupted during circuit breaker cooling off", e);
            }
        }

        // Use adaptive batch size for better rate limiting
        int batchSize = getAdaptiveBatchSize();

        // Split messageIds into batches
        List<List<String>> batches = createBatches(messageIds, batchSize);
        logger.info("Split {} messages into {} batches (batch size: {})", messageIds.size(), batches.size(), batchSize);

        for (int i = 0; i < batches.size(); i++) {
            List<String> batch = batches.get(i);
            logger.debug("Processing batch {} of {} with {} messages", i + 1, batches.size(), batch.size());

            // Track success count before this batch
            int previousSuccessCount = result.getSuccessCount();

            // Execute batch with retry logic, using the native batchModify primitive as the
            // primary path (WI-1). executeBatchModifyLabelsNative recovers non-transient
            // whole-chunk failures per-message in place; only transient failures are rethrown
            // here for bounded native retry.
            executeBatchWithRetry(
                    gmail, userId, batch, result, (g, u, b, r) -> executeBatchModifyLabelsNative(g, u, b, modifyRequest, r));

            // Determine if this batch succeeded (all messages in batch were successfully modified)
            int successfulInBatch = result.getSuccessCount() - previousSuccessCount;
            boolean batchSuccess = (successfulInBatch == batch.size());

            // Update adaptive rate limiting based on batch result
            updateAdaptiveRateLimit(batchSuccess, batch.size());

            // Increment batch counter
            result.incrementBatchesProcessed();

            // Add delay between batches to respect rate limits (except for the last batch)
            if (i < batches.size() - 1) {
                addDelayBetweenBatches();
            }
        }

        result.markCompleted();
        logger.info("Batch modify labels operation completed: {}", result);
        return result;
    }

    /**
     * Creates sublists of the specified batch size from the input list.
     *
     * @param items the list to split into batches
     * @param batchSize the maximum size of each batch
     * @param <T> the type of items in the list
     * @return list of batches, each containing at most batchSize items
     */
    private <T> List<List<T>> createBatches(List<T> items, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < items.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, items.size());
            batches.add(new ArrayList<>(items.subList(i, endIndex)));
        }
        return batches;
    }

    /**
     * Executes a single batchDelete operation using Gmail's native batchDelete endpoint.
     * This method handles a chunk of up to 1000 message IDs and uses Gmail's efficient
     * batchDelete API which costs only 50 quota units regardless of the number of messages.
     *
     * Important: Gmail's batchDelete is an all-or-nothing operation. If it fails, all
     * message IDs in the chunk are marked as failed. This is different from batch requests
     * which can have partial successes.
     *
     * @param gmail the authenticated Gmail service instance
     * @param userId the user ID
     * @param messageIds the message IDs to delete (up to 1000)
     * @param result the result tracker for recording successes and failures
     * @throws IOException if there's an error executing the batchDelete request
     */
    private void executeNativeBatchDelete(
            Gmail gmail, String userId, List<String> messageIds, BulkOperationResult result) throws IOException {
        if (messageIds.isEmpty()) {
            logger.debug("No messages to delete in this chunk");
            return;
        }

        logger.debug("Executing native batchDelete for {} messages (50 quota units)", messageIds.size());

        try {
            // Create the batchDelete request
            BatchDeleteMessagesRequest batchRequest = new BatchDeleteMessagesRequest().setIds(messageIds);

            // Execute the batchDelete - this is a single API call that costs 50 quota units
            gmail.users().messages().batchDelete(userId, batchRequest).execute();

            // Success - all messages in this chunk were deleted
            messageIds.forEach(result::addSuccess);
            logger.info("Successfully deleted {} messages using batchDelete (50 quota units)", messageIds.size());

            // Reset circuit breaker on success
            resetCircuitBreaker();

        } catch (GoogleJsonResponseException e) {
            // Handle specific Gmail API errors
            GoogleJsonError error = e.getDetails();
            String errorMessage = error != null ? error.getMessage() : e.getMessage();
            int statusCode = e.getStatusCode();

            logger.error(
                    "Gmail batchDelete failed for chunk of {} messages. Status: {}, Error: {}",
                    messageIds.size(),
                    statusCode,
                    errorMessage,
                    e);

            // Mark all messages in this chunk as failed (batchDelete is all-or-nothing)
            messageIds.forEach(id -> result.addFailure(id, errorMessage));

            // Record failure for circuit breaker
            recordFailure();

            // Re-throw for retry logic to handle
            throw new IOException("Gmail batchDelete failed: " + errorMessage, e);

        } catch (IOException e) {
            logger.error(
                    "IOException during batchDelete for chunk of {} messages: {}",
                    messageIds.size(),
                    e.getMessage(),
                    e);

            // Mark all messages in this chunk as failed
            messageIds.forEach(id -> result.addFailure(id, e.getMessage()));

            // Record failure for circuit breaker
            recordFailure();

            // Re-throw for retry logic to handle
            throw e;
        }
    }

    /**
     * Executes a single native {@code users.messages.batchModify} call for a chunk of message IDs
     * (up to 1000), the primary path for label modifications (WI-1, R1). The call costs a flat ~50
     * quota units regardless of chunk size and returns no body (HTTP 204) on success.
     *
     * <p>Failure classification (FR-006, research R2): on {@link GoogleJsonResponseException}, a
     * rate-limit or server error (HTTP 429/5xx) is treated as <b>transient</b> and rethrown as an
     * {@link IOException} so {@link #executeBatchWithRetry} performs bounded native retry with
     * backoff; a non-429 4xx (e.g. an invalid or inaccessible message ID) is treated as
     * <b>non-transient</b> and recovered in place via {@link #executeBatchModifyLabelsPerMessage} so
     * the caller receives truthful per-ID outcomes instead of losing every valid ID to the native
     * operation's all-or-nothing failure mode (FR-005). A plain (non-JSON) transport {@link
     * IOException} is likewise treated as transient. The per-message recovery is idempotent, so it
     * is safe to re-apply even if the native call partially applied before failing (FR-012).
     *
     * @param gmail the authenticated Gmail service instance
     * @param userId the user ID
     * @param messageIds the message IDs to modify in this chunk (up to 1000)
     * @param modifyRequest the label modification request (add/remove label IDs)
     * @param result the result tracker for recording successes and failures
     * @throws IOException if the native call fails transiently and should be retried by the caller
     */
    private void executeBatchModifyLabelsNative(
            Gmail gmail,
            String userId,
            List<String> messageIds,
            ModifyMessageRequest modifyRequest,
            BulkOperationResult result)
            throws IOException {
        if (messageIds.isEmpty()) {
            logger.debug("No messages to modify in this chunk");
            return;
        }

        logger.debug("Executing native batchModify for {} messages (~50 quota units)", messageIds.size());

        try {
            BatchModifyMessagesRequest nativeRequest = new BatchModifyMessagesRequest()
                    .setIds(messageIds)
                    .setAddLabelIds(modifyRequest.getAddLabelIds())
                    .setRemoveLabelIds(modifyRequest.getRemoveLabelIds());

            // Execute the native batchModify - a single API call that costs ~50 quota units
            // regardless of chunk size, and returns no body (HTTP 204) on success.
            gmail.users().messages().batchModify(userId, nativeRequest).execute();

            // Success - every message in this chunk was modified.
            messageIds.forEach(result::addSuccess);
            logger.info("Successfully modified labels for {} messages using native batchModify (~50 quota units)", messageIds.size());

            resetCircuitBreaker();

        } catch (GoogleJsonResponseException e) {
            int statusCode = e.getStatusCode();
            GoogleJsonError error = e.getDetails();
            String errorMessage = error != null ? error.getMessage() : e.getMessage();

            recordFailure();

            if (isTransientStatusCode(statusCode)) {
                logger.warn(
                        "Native batchModify transient failure for chunk of {} messages. Status: {}, Error: {}",
                        messageIds.size(),
                        statusCode,
                        errorMessage,
                        e);
                throw new IOException("Gmail batchModify failed transiently: " + errorMessage, e);
            }

            logger.warn(
                    "Native batchModify non-transient failure for chunk of {} messages (status {}); "
                            + "recovering per-message: {}",
                    messageIds.size(),
                    statusCode,
                    errorMessage,
                    e);
            executeBatchModifyLabelsPerMessage(gmail, userId, messageIds, modifyRequest, result);

        } catch (IOException e) {
            // Transport-level failure carrying no HTTP status - treated as transient (FR-006).
            logger.warn(
                    "IOException during native batchModify for chunk of {} messages: {}",
                    messageIds.size(),
                    e.getMessage(),
                    e);
            recordFailure();
            throw e;
        }
    }

    /**
     * Determines whether a Gmail API HTTP status code represents a transient condition (rate
     * limiting or a server-side error) that should be retried on the native batch path, as opposed
     * to a non-transient client error (e.g. an invalid or inaccessible message ID) that should be
     * recovered per-message (FR-006, research R2).
     *
     * @param statusCode the HTTP status code from a {@link GoogleJsonResponseException}
     * @return true if the status code (429 or 5xx) indicates a transient condition
     */
    private boolean isTransientStatusCode(int statusCode) {
        return statusCode == 429 || (statusCode >= 500 && statusCode < 600);
    }

    /**
     * Executes a single batch of label modification operations. Serves as the per-message recovery
     * path (WI-1): invoked directly by {@link #executeBatchModifyLabelsNative} when a native
     * batchModify call fails non-transiently, to reconstruct truthful per-ID success/failure
     * outcomes for the chunk.
     *
     * @param gmail the authenticated Gmail service instance
     * @param userId the user ID
     * @param messageIds the message IDs to modify in this batch
     * @param modifyRequest the label modification request
     * @param result the result tracker for recording successes and failures
     * @throws IOException if there's an error creating or executing the batch request
     */
    private void executeBatchModifyLabelsPerMessage(
            Gmail gmail,
            String userId,
            List<String> messageIds,
            ModifyMessageRequest modifyRequest,
            BulkOperationResult result)
            throws IOException {
        BatchRequest batch = gmail.batch();

        logger.debug("Queuing {} modify label operations with micro-delays", messageIds.size());

        for (int i = 0; i < messageIds.size(); i++) {
            String messageId = messageIds.get(i);

            gmail.users()
                    .messages()
                    .modify(userId, messageId, modifyRequest)
                    .queue(batch, new JsonBatchCallback<com.google.api.services.gmail.model.Message>() {
                        @Override
                        public void onSuccess(
                                com.google.api.services.gmail.model.Message message, HttpHeaders responseHeaders) {
                            result.addSuccess(messageId);
                            logger.debug("Successfully modified labels for message: {}", messageId);
                        }

                        @Override
                        public void onFailure(GoogleJsonError error, HttpHeaders responseHeaders) {
                            String errorMessage = error != null ? error.getMessage() : "Unknown error";
                            result.addFailure(messageId, errorMessage);
                            logger.warn("Failed to modify labels for message {}: {}", messageId, errorMessage);
                        }
                    });

            // Add micro-delay between queuing operations to reduce concurrent pressure
            if (i < messageIds.size() - 1) {
                addMicroDelay();
            }
        }

        logger.debug("Executing batch with {} queued operations", messageIds.size());
        batch.execute();
    }

    /**
     * Gets the maximum batch size supported by this client.
     * Uses the configured value, but respects Gmail API limits.
     *
     * @return the maximum number of operations per batch
     */
    public int getMaxBatchSize() {
        return Math.min(properties.gmailApi().rateLimit().batchOperations().maxBatchSize(), DEFAULT_MAX_BATCH_SIZE);
    }

    /**
     * Validates batch operation results and throws appropriate exceptions for failures.
     * This method provides configurable failure handling strategies.
     *
     * @param result the batch operation result to validate
     * @param failOnPartialFailure whether to throw exception on partial failures
     * @throws BatchOperationException if the operation failed according to the specified policy
     */
    public void validateBatchResult(BulkOperationResult result, boolean failOnPartialFailure) {
        if (result.getTotalOperations() == 0) {
            logger.warn("Batch operation completed with no operations attempted");
            return;
        }

        if (result.isCompleteSuccess()) {
            logger.info("Batch operation completed successfully: {}", result);
            return;
        }

        if (!result.hasSuccesses()) {
            // Complete failure - always throw exception
            logger.error("Batch operation completely failed: {}", result);
            throw BatchOperationException.completeFailure(result);
        }

        if (failOnPartialFailure && result.hasFailures()) {
            // Partial failure with strict failure policy
            logger.error("Batch operation partially failed: {}", result);
            throw BatchOperationException.partialFailure(result);
        }

        // Partial failure with lenient policy - just log warning
        logger.warn("Batch operation partially failed but continuing: {}", result);
    }

    /**
     * Analyzes batch operation errors and determines if they are retryable.
     *
     * @param result the batch operation result to analyze
     * @return true if the failed operations are likely retryable, false otherwise
     */
    public boolean areFailuresRetryable(BulkOperationResult result) {
        if (!result.hasFailures()) {
            return false;
        }

        // Analyze error messages to determine retryability
        return result.getFailedOperations().values().stream().anyMatch(this::isRetryableError);
    }

    /**
     * Checks if an error message indicates a retryable condition.
     *
     * @param errorMessage the error message to analyze
     * @return true if the error appears retryable, false otherwise
     */
    private boolean isRetryableError(String errorMessage) {
        if (errorMessage == null) {
            return false;
        }

        String lowerMessage = errorMessage.toLowerCase();

        // Network and temporary errors are typically retryable
        return lowerMessage.contains("timeout")
                || lowerMessage.contains("temporary")
                || lowerMessage.contains("service unavailable")
                || lowerMessage.contains("rate limit")
                || lowerMessage.contains("quota exceeded")
                || lowerMessage.contains("internal error")
                || lowerMessage.contains("backend error")
                || lowerMessage.contains("too many concurrent requests")
                || lowerMessage.contains("user rate limit exceeded");
    }

    /**
     * Functional interface for batch execution operations.
     */
    @FunctionalInterface
    private interface BatchExecutor {
        void execute(Gmail gmail, String userId, List<String> batch, BulkOperationResult result) throws IOException;
    }

    /**
     * Executes a native batchDelete operation with retry logic and exponential backoff.
     * This method wraps the executeNativeBatchDelete call with intelligent retry handling.
     *
     * @param gmail the authenticated Gmail service instance
     * @param userId the user ID
     * @param messageIds the message IDs to delete in this chunk
     * @param result the result tracker
     */
    private void executeBatchDeleteWithRetry(
            Gmail gmail, String userId, List<String> messageIds, BulkOperationResult result) {
        int maxRetries = properties.gmailApi().rateLimit().batchOperations().maxRetryAttempts();
        long initialBackoffMs =
                properties.gmailApi().rateLimit().batchOperations().initialBackoffMs();
        double backoffMultiplier =
                properties.gmailApi().rateLimit().batchOperations().backoffMultiplier();
        long maxBackoffMs = properties.gmailApi().rateLimit().batchOperations().maxBackoffMs();

        int attempt = 0;
        long backoffMs = initialBackoffMs;

        while (attempt <= maxRetries) {
            try {
                executeNativeBatchDelete(gmail, userId, messageIds, result);

                // Success - log if this was a retry
                if (attempt > 0) {
                    logger.info("Native batchDelete succeeded after {} retry attempts", attempt);
                    result.incrementBatchesRetried();
                }
                return;

            } catch (IOException e) {
                attempt++;
                String errorMessage = e.getMessage();

                logger.warn("Native batchDelete attempt {} failed: {}", attempt, errorMessage);

                // Check if this is the last attempt or if the error is not retryable
                if (attempt > maxRetries || !isRetryableError(errorMessage)) {
                    logger.error("Native batchDelete failed after {} attempts: {}", attempt, errorMessage);

                    // Messages are already marked as failed in executeNativeBatchDelete
                    if (attempt > 1) {
                        result.incrementBatchesRetried();
                    }
                    return;
                }

                // Calculate exponential backoff delay
                long delayMs = Math.min(backoffMs, maxBackoffMs);
                logger.info(
                        "Retrying native batchDelete in {}ms (attempt {} of {})", delayMs, attempt + 1, maxRetries + 1);

                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.error("Retry delay interrupted");
                    return;
                }

                // Increase backoff for next attempt
                backoffMs = Math.min((long) (backoffMs * backoffMultiplier), maxBackoffMs);
            }
        }
    }

    /**
     * Executes a batch operation with retry logic and exponential backoff.
     *
     * @param gmail the authenticated Gmail service instance
     * @param userId the user ID
     * @param batch the batch of message IDs to process
     * @param result the result tracker
     * @param executor the batch execution function
     */
    private void executeBatchWithRetry(
            Gmail gmail, String userId, List<String> batch, BulkOperationResult result, BatchExecutor executor) {
        int maxRetries = properties.gmailApi().rateLimit().batchOperations().maxRetryAttempts();
        long initialBackoffMs =
                properties.gmailApi().rateLimit().batchOperations().initialBackoffMs();
        double backoffMultiplier =
                properties.gmailApi().rateLimit().batchOperations().backoffMultiplier();
        long maxBackoffMs = properties.gmailApi().rateLimit().batchOperations().maxBackoffMs();

        int attempt = 0;
        long backoffMs = initialBackoffMs;

        while (attempt <= maxRetries) {
            try {
                executor.execute(gmail, userId, batch, result);

                // Success - if this was a retry, log it
                if (attempt > 0) {
                    logger.info("Batch succeeded after {} retry attempts", attempt);
                    result.incrementBatchesRetried();
                }
                return;

            } catch (IOException e) {
                attempt++;
                String errorMessage = e.getMessage();
                boolean retryable = isRetryableFailure(e);

                logger.warn("Batch execution attempt {} failed: {}", attempt, errorMessage);

                // Check if this is the last attempt or if the error is not retryable
                if (attempt > maxRetries || !retryable) {
                    logger.error("Batch execution failed after {} attempts: {}", attempt, errorMessage);

                    // Mark all messages in this batch as failed with a truthful, PII-free reason
                    // that distinguishes a retry-exhausted transient failure from a non-retryable
                    // one (FR-013). Native batchModify's own non-transient (bad-ID) failures never
                    // reach this branch - they are recovered per-message before rethrowing.
                    String reason = retryable
                            ? "Retryable (rate-limited or transient server error) after " + attempt
                                    + " attempts: " + errorMessage
                            : "Batch execution failed after " + attempt + " attempts: " + errorMessage;
                    for (String messageId : batch) {
                        result.addFailure(messageId, reason);
                    }

                    if (attempt > 1) {
                        result.incrementBatchesRetried();
                    }
                    return;
                }

                // Calculate exponential backoff delay
                long delayMs = Math.min(backoffMs, maxBackoffMs);
                logger.info("Retrying batch in {}ms (attempt {} of {})", delayMs, attempt + 1, maxRetries + 1);

                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.error("Retry delay interrupted");
                    return;
                }

                // Increase backoff for next attempt
                backoffMs = Math.min((long) (backoffMs * backoffMultiplier), maxBackoffMs);
            }
        }
    }

    /**
     * Determines whether a failure caught by {@link #executeBatchWithRetry} should be retried.
     * If the failure wraps a {@link GoogleJsonResponseException} (as {@link
     * #executeBatchModifyLabelsNative} does for transient statuses), retryability is decided by
     * the HTTP status code (429/5xx, FR-006) rather than error-message text. Otherwise (a plain
     * transport-level {@link IOException}), it is treated as retryable, consistent with FR-006
     * classifying transport failures as transient.
     *
     * @param e the exception thrown by the batch executor
     * @return true if the failure should be retried
     */
    private boolean isRetryableFailure(IOException e) {
        Throwable cause = e.getCause();
        if (cause instanceof GoogleJsonResponseException) {
            return isTransientStatusCode(((GoogleJsonResponseException) cause).getStatusCode());
        }
        return true;
    }

    /**
     * Adds a configurable delay between batch executions to respect Gmail API rate limits.
     */
    private void addDelayBetweenBatches() {
        long delayMs = properties.gmailApi().rateLimit().batchOperations().delayBetweenBatchesMs();

        if (delayMs > 0) {
            logger.debug("Adding {}ms delay between batches", delayMs);
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Batch delay interrupted");
            }
        }
    }

    /**
     * Adds a small micro-delay between operations within a batch to reduce concurrent pressure.
     */
    private void addMicroDelay() {
        long microDelayMs = getMicroDelayMs();
        if (microDelayMs > 0) {
            try {
                Thread.sleep(microDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.debug("Micro-delay interrupted");
            }
        }
    }

    /**
     * Gets the configured micro-delay value or uses the default.
     *
     * @return micro-delay in milliseconds
     */
    private long getMicroDelayMs() {
        try {
            return properties.gmailApi().rateLimit().batchOperations().microDelayBetweenOperationsMs();
        } catch (Exception e) {
            // Fallback to default if configuration is not available
            return DEFAULT_MICRO_DELAY_BETWEEN_OPERATIONS_MS;
        }
    }

    /**
     * Extracts failed message IDs from a batch operation result for retry attempts.
     *
     * @param result the batch operation result
     * @return list of message IDs that failed and could be retried
     */
    public List<String> getRetryableFailures(BulkOperationResult result) {
        return result.getFailedOperations().entrySet().stream()
                .filter(entry -> isRetryableError(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Checks if the circuit breaker is currently open (cooling off period active).
     *
     * @return true if circuit breaker is open, false otherwise
     */
    private boolean isCircuitBreakerOpen() {
        if (consecutiveFailures.get() < CIRCUIT_BREAKER_THRESHOLD) {
            return false;
        }

        long timeSinceLastFailure = System.currentTimeMillis() - lastFailureTime.get();
        return timeSinceLastFailure < COOLING_OFF_PERIOD_MS;
    }

    /**
     * Gets the remaining cooling off time in milliseconds.
     *
     * @return remaining cooling off time, or 0 if circuit breaker is closed
     */
    private long getCoolingOffRemainingMs() {
        if (consecutiveFailures.get() < CIRCUIT_BREAKER_THRESHOLD) {
            return 0;
        }

        long timeSinceLastFailure = System.currentTimeMillis() - lastFailureTime.get();
        return Math.max(0, COOLING_OFF_PERIOD_MS - timeSinceLastFailure);
    }

    /**
     * Records a failure for circuit breaker tracking.
     */
    private void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());

        if (failures >= CIRCUIT_BREAKER_THRESHOLD) {
            logger.warn(
                    "Circuit breaker triggered after {} consecutive failures. Cooling off for {}ms",
                    failures,
                    COOLING_OFF_PERIOD_MS);
        }
    }

    /**
     * Resets the circuit breaker state after a successful operation.
     */
    private void resetCircuitBreaker() {
        int previousFailures = consecutiveFailures.getAndSet(0);
        if (previousFailures > 0) {
            logger.info("Circuit breaker reset after successful operation (was {} failures)", previousFailures);
        }
    }

    /**
     * Gets the current adaptive batch size based on recent success rates.
     *
     * @return the adaptive batch size to use for the next batch
     */
    private int getAdaptiveBatchSize() {
        int configuredMax = properties.gmailApi().rateLimit().batchOperations().maxBatchSize();
        int currentAdaptive = adaptiveBatchSize.get();

        // Use the minimum of configured max and current adaptive size
        int effectiveSize = Math.min(configuredMax, currentAdaptive);

        // Never go below 5 operations per batch
        return Math.max(5, effectiveSize);
    }

    /**
     * Updates the adaptive rate limiting based on batch operation results.
     *
     * @param batchSuccess whether the batch operation succeeded
     * @param batchSize the size of the batch that was executed
     */
    private void updateAdaptiveRateLimit(boolean batchSuccess, int batchSize) {
        int currentSize = adaptiveBatchSize.get();

        if (batchSuccess) {
            // Gradually increase batch size on success, but stay conservative
            int newSize = Math.min(
                    currentSize + 1,
                    properties.gmailApi().rateLimit().batchOperations().maxBatchSize());
            if (newSize != currentSize) {
                adaptiveBatchSize.set(newSize);
                logger.debug("Adaptive batch size increased to {} after successful batch", newSize);
            }
        } else {
            // Aggressively reduce batch size on failure
            int reduction = Math.max(2, currentSize / 4); // Reduce by 25% or at least 2
            int newSize = Math.max(5, currentSize - reduction); // Never go below 5

            if (newSize != currentSize) {
                adaptiveBatchSize.set(newSize);
                logger.warn("Adaptive batch size reduced to {} after failed batch (was {})", newSize, currentSize);
            }
        }
    }

    /**
     * Gets current circuit breaker statistics for monitoring.
     *
     * @return a map containing circuit breaker metrics
     */
    public Map<String, Object> getCircuitBreakerStats() {
        return Map.of(
                "consecutiveFailures", consecutiveFailures.get(),
                "isOpen", isCircuitBreakerOpen(),
                "coolingOffRemainingMs", getCoolingOffRemainingMs(),
                "adaptiveBatchSize", adaptiveBatchSize.get(),
                "lastFailureTime", lastFailureTime.get());
    }
}
