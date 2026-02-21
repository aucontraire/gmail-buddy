package com.aucontraire.gmailbuddy.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for estimating Gmail API quota usage based on operation types.
 *
 * Gmail API quota costs (as of 2025):
 * - messages.list: 5 quota units
 * - messages.get: 5 quota units
 * - messages.batchDelete: 50 quota units per batch (up to 1000 messages)
 * - messages.batchModify: 50 quota units per batch (up to 1000 messages)
 * - messages.delete: 10 quota units (individual delete)
 * - messages.modify: 5 quota units (individual modify)
 *
 * Gmail API has a default quota of 250 quota units per second per user,
 * and 1 billion quota units per day (shared across all users).
 */
@Service
public class GmailQuotaEstimator {

    private static final Logger logger = LoggerFactory.getLogger(GmailQuotaEstimator.class);

    // Quota costs for various Gmail API operations
    private static final int MESSAGES_LIST_QUOTA = 5;
    private static final int MESSAGES_GET_QUOTA = 5;
    private static final int MESSAGES_BATCH_DELETE_QUOTA = 50;
    private static final int MESSAGES_BATCH_MODIFY_QUOTA = 50;
    private static final int MESSAGES_DELETE_QUOTA = 10;
    private static final int MESSAGES_MODIFY_QUOTA = 5;

    /**
     * Estimates quota usage for listing messages.
     *
     * @param messageCount Number of messages in the result
     * @return Estimated quota units consumed
     */
    public int estimateListMessagesQuota(int messageCount) {
        // Base cost for the list operation
        int quota = MESSAGES_LIST_QUOTA;

        // Add cost for fetching full message details (if applicable)
        // For now, we assume the list operation is just the list call
        logger.debug("Estimated quota for listing {} messages: {} units", messageCount, quota);
        return quota;
    }

    /**
     * Estimates quota usage for getting a single message.
     *
     * @return Estimated quota units consumed
     */
    public int estimateGetMessageQuota() {
        logger.debug("Estimated quota for getting message: {} units", MESSAGES_GET_QUOTA);
        return MESSAGES_GET_QUOTA;
    }

    /**
     * Estimates quota usage for batch delete operations.
     *
     * @param messageCount Number of messages being deleted
     * @param batchSize Size of each batch
     * @return Estimated quota units consumed
     */
    public int estimateBatchDeleteQuota(int messageCount, int batchSize) {
        if (messageCount <= 0) {
            return 0;
        }

        // Calculate number of batches
        int numBatches = (int) Math.ceil((double) messageCount / batchSize);
        int quota = numBatches * MESSAGES_BATCH_DELETE_QUOTA;

        logger.debug("Estimated quota for batch deleting {} messages in {} batches: {} units",
                    messageCount, numBatches, quota);
        return quota;
    }

    /**
     * Estimates quota usage for batch modify operations.
     *
     * @param messageCount Number of messages being modified
     * @param batchSize Size of each batch
     * @return Estimated quota units consumed
     */
    public int estimateBatchModifyQuota(int messageCount, int batchSize) {
        if (messageCount <= 0) {
            return 0;
        }

        // Calculate number of batches
        int numBatches = (int) Math.ceil((double) messageCount / batchSize);
        int quota = numBatches * MESSAGES_BATCH_MODIFY_QUOTA;

        logger.debug("Estimated quota for batch modifying {} messages in {} batches: {} units",
                    messageCount, numBatches, quota);
        return quota;
    }

    /**
     * Estimates quota usage for a single message delete.
     *
     * @return Estimated quota units consumed
     */
    public int estimateDeleteMessageQuota() {
        logger.debug("Estimated quota for deleting single message: {} units", MESSAGES_DELETE_QUOTA);
        return MESSAGES_DELETE_QUOTA;
    }

    /**
     * Estimates quota usage for a single message modify.
     *
     * @return Estimated quota units consumed
     */
    public int estimateModifyMessageQuota() {
        logger.debug("Estimated quota for modifying single message: {} units", MESSAGES_MODIFY_QUOTA);
        return MESSAGES_MODIFY_QUOTA;
    }

    /**
     * Estimates quota usage for filtering messages.
     * This typically involves a list operation followed by fetching message details.
     *
     * @param messageCount Number of messages returned
     * @return Estimated quota units consumed
     */
    public int estimateFilterMessagesQuota(int messageCount) {
        // Base cost for the list/search operation
        int quota = MESSAGES_LIST_QUOTA;

        // Add cost for fetching full details of each message (if format=full is used)
        // For now, we estimate this as just the search cost
        logger.debug("Estimated quota for filtering {} messages: {} units", messageCount, quota);
        return quota;
    }
}
