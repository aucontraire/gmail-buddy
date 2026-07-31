package com.aucontraire.gmailbuddy.mapper;

import com.aucontraire.gmailbuddy.dto.common.ResponseMetadata;
import com.aucontraire.gmailbuddy.dto.response.BatchOperationResponse;
import com.aucontraire.gmailbuddy.dto.response.BulkDeleteResponse;
import com.aucontraire.gmailbuddy.dto.response.LabelModificationResponse;
import com.aucontraire.gmailbuddy.service.BulkOperationResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Maps service layer results to response DTOs.
 * Centralizes the conversion logic for consistent response formatting across all API endpoints.
 *
 * This mapper is responsible for:
 * - Converting BulkOperationResult objects to appropriate response DTOs
 * - Calculating response metadata (timing, quota usage)
 * - Ensuring consistent response structure across the API
 *
 * @since 1.0
 */
@Component
public class ResponseMapper {

    /**
     * Flat per-chunk quota cost of Gmail's native {@code users.messages.batchModify} and
     * {@code users.messages.batchDelete} endpoints (~50 units regardless of chunk size, up to
     * 1000 message IDs per chunk). Replaces the previous per-message {@code modify} loop's
     * {@code totalOperations * 5} estimate now that the feature-005/WI-1 batch-by-id endpoints
     * (batchTrash / batchUntrash / batchModifyLabels) issue one native batchModify call per
     * chunk instead of one call per message (WI-1, FR-008).
     */
    private static final int BATCH_MODIFY_QUOTA_PER_CHUNK = 50;

    /**
     * Converts a BulkOperationResult to a BulkDeleteResponse DTO.
     * This method transforms the internal service result into a structured API response
     * suitable for bulk delete operations.
     *
     * @param serviceResult the result from the service layer containing operation details
     * @return BulkDeleteResponse DTO with status, counts, and metadata
     */
    public BulkDeleteResponse toBulkDeleteResponse(BulkOperationResult serviceResult) {
        ResponseMetadata metadata = ResponseMetadata.builder()
                .durationMs(serviceResult.getDurationMs())
                .quotaUsed(estimateQuotaUsed(serviceResult))
                .build();

        return BulkDeleteResponse.builder()
                .status(serviceResult.getStatus())
                .totalOperations(serviceResult.getTotalOperations())
                .successCount(serviceResult.getSuccessCount())
                .failureCount(serviceResult.getFailureCount())
                .successfulOperations(new ArrayList<>(serviceResult.getSuccessfulOperations()))
                .failedOperations(new HashMap<>(serviceResult.getFailedOperations()))
                .metadata(metadata)
                .build();
    }

    /**
     * Converts a BulkOperationResult to a LabelModificationResponse DTO.
     * This method transforms the internal service result into a structured API response
     * suitable for label modification operations.
     *
     * @param serviceResult the result from the service layer containing operation details
     * @param labelsAdded the list of label IDs that were added to messages
     * @param labelsRemoved the list of label IDs that were removed from messages
     * @return LabelModificationResponse DTO with status, modification details, and metadata
     */
    public LabelModificationResponse toLabelModificationResponse(
            BulkOperationResult serviceResult, List<String> labelsAdded, List<String> labelsRemoved) {

        ResponseMetadata metadata = ResponseMetadata.builder()
                .durationMs(serviceResult.getDurationMs())
                .quotaUsed(estimateQuotaUsed(serviceResult))
                .build();

        return LabelModificationResponse.builder()
                .status(serviceResult.getStatus())
                .messagesModified(serviceResult.getSuccessCount())
                .labelsAdded(labelsAdded)
                .labelsRemoved(labelsRemoved)
                .affectedMessageIds(new ArrayList<>(serviceResult.getSuccessfulOperations()))
                .metadata(metadata)
                .build();
    }

    /**
     * Converts a BulkOperationResult to a BatchOperationResponse DTO for the
     * feature-005 batch-by-id endpoints (batchTrash, batchUntrash, batchModifyLabels).
     *
     * <p>{@code status} is taken directly from {@link BulkOperationResult#getStatus()}
     * (SUCCESS/PARTIAL_SUCCESS/FAILURE/NO_RESULTS from the counts), and per-id outcomes
     * are exposed via {@code successfulOperations}/{@code failedOperations}. As of WI-1, these
     * endpoints issue Gmail's native {@code batchModify} once per chunk, so quota is estimated
     * on that same flat per-chunk basis as {@code batchDelete} — {@code totalBatchesProcessed} ×
     * {@value #BATCH_MODIFY_QUOTA_PER_CHUNK} — rather than the previous per-message-modify
     * estimate (FR-008).</p>
     *
     * @param serviceResult the result from the service layer containing operation details
     * @return BatchOperationResponse DTO with status, per-id outcomes, and metadata
     */
    public BatchOperationResponse toBatchOperationResponse(BulkOperationResult serviceResult) {
        ResponseMetadata metadata = ResponseMetadata.builder()
                .durationMs(serviceResult.getDurationMs())
                .quotaUsed(estimateQuotaUsed(serviceResult))
                .build();

        return BatchOperationResponse.builder()
                .status(serviceResult.getStatus())
                .totalOperations(serviceResult.getTotalOperations())
                .successCount(serviceResult.getSuccessCount())
                .failureCount(serviceResult.getFailureCount())
                .successfulOperations(new ArrayList<>(serviceResult.getSuccessfulOperations()))
                .failedOperations(new HashMap<>(serviceResult.getFailedOperations()))
                .metadata(metadata)
                .build();
    }

    /**
     * Estimates Gmail API quota units used based on operation results.
     *
     * Gmail API quota estimation:
     * - batchDelete: approximately 50 units per batch
     * - batchModify: approximately 50 units per batch
     *
     * This is a conservative estimate as actual quota usage may vary based on:
     * - Batch size (max 1000 messages per batch)
     * - Network conditions and retries
     * - Additional API calls for error handling
     *
     * @param result the operation result containing batch processing details
     * @return estimated quota units consumed by the operation
     */
    private int estimateQuotaUsed(BulkOperationResult result) {
        // Gmail API batch operations (batchDelete, and batchModify as of WI-1) use
        // approximately 50 quota units per batch/chunk, regardless of chunk size.
        // This is based on Google's documented quota costs for batch operations.
        return result.getTotalBatchesProcessed() * BATCH_MODIFY_QUOTA_PER_CHUNK;
    }
}
