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
    private static final int MESSAGES_SEND_QUOTA = 100;
    private static final int DRAFTS_CREATE_QUOTA = 10;
    private static final int DRAFTS_SEND_QUOTA = 100;
    private static final int DRAFTS_LIST_QUOTA = 1;
    private static final int DRAFTS_GET_QUOTA = 5;
    private static final int DRAFTS_DELETE_QUOTA = 10;
    private static final int DRAFTS_UPDATE_QUOTA = 15;

    // New read-API quota constants (feature 004 — US1)
    private static final int THREADS_LIST_QUOTA = 10;
    private static final int THREADS_GET_QUOTA = 10;
    private static final int MESSAGES_GET_FULL_QUOTA = 10;
    private static final int MESSAGES_GET_METADATA_QUOTA = 5;
    private static final int LABELS_LIST_QUOTA = 1;
    private static final int LABELS_GET_QUOTA = 1;
    private static final int ATTACHMENTS_LIST_QUOTA = 5;
    private static final int ATTACHMENTS_GET_QUOTA = 5;

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

    /**
     * Estimates quota usage for sending a message directly (users.messages.send).
     *
     * @return Estimated quota units consumed
     */
    public int estimateSendMessageQuota() {
        logger.debug("Estimated quota for sending message: {} units", MESSAGES_SEND_QUOTA);
        return MESSAGES_SEND_QUOTA;
    }

    /**
     * Estimates quota usage for creating a draft (users.drafts.create).
     *
     * @return Estimated quota units consumed
     */
    public int estimateCreateDraftQuota() {
        logger.debug("Estimated quota for creating draft: {} units", DRAFTS_CREATE_QUOTA);
        return DRAFTS_CREATE_QUOTA;
    }

    /**
     * Estimates quota usage for sending an existing draft (users.drafts.send).
     *
     * @return Estimated quota units consumed
     */
    public int estimateSendDraftQuota() {
        logger.debug("Estimated quota for sending draft: {} units", DRAFTS_SEND_QUOTA);
        return DRAFTS_SEND_QUOTA;
    }

    /**
     * Estimates quota usage for a threaded message send (users.messages.send with
     * a prior users.messages.get metadata lookup).
     * ~5 (lookup) + ~100 (send) = ~105 units.
     *
     * @return Estimated quota units consumed
     */
    public int estimateThreadedSendMessageQuota() {
        int quota = MESSAGES_GET_QUOTA + MESSAGES_SEND_QUOTA;
        logger.debug("Estimated quota for threaded send message: {} units", quota);
        return quota;
    }

    /**
     * Estimates quota usage for a threaded draft creation (users.drafts.create with
     * a prior users.messages.get metadata lookup).
     * ~5 (lookup) + ~10 (draft create) = ~15 units.
     *
     * @return Estimated quota units consumed
     */
    public int estimateThreadedCreateDraftQuota() {
        int quota = MESSAGES_GET_QUOTA + DRAFTS_CREATE_QUOTA;
        logger.debug("Estimated quota for threaded create draft: {} units", quota);
        return quota;
    }

    /**
     * Estimates quota for a list-drafts page call.
     * Actual cost: 1 (list call) + itemCount × 5 (per-item get calls).
     *
     * @param itemCount number of items actually returned on this page
     * @return estimated quota units for this page
     */
    public int estimateListDraftsQuota(int itemCount) {
        int quota = DRAFTS_LIST_QUOTA + (itemCount * DRAFTS_GET_QUOTA);
        logger.debug("Estimated quota for listing {} drafts: {} units", itemCount, quota);
        return quota;
    }

    /**
     * Estimates quota for a single-draft get (users.drafts.get, format=FULL).
     * ~5 units.
     *
     * @return Estimated quota units consumed
     */
    public int estimateGetDraftQuota() {
        logger.debug("Estimated quota for getting draft: {} units", DRAFTS_GET_QUOTA);
        return DRAFTS_GET_QUOTA;
    }

    /**
     * Estimates quota for a draft delete (users.drafts.delete).
     * ~10 units.
     *
     * @return Estimated quota units consumed
     */
    public int estimateDeleteDraftQuota() {
        logger.debug("Estimated quota for deleting draft: {} units", DRAFTS_DELETE_QUOTA);
        return DRAFTS_DELETE_QUOTA;
    }

    /**
     * Estimates quota for a draft update (users.drafts.update).
     * ~15 units (5 internal read + 10 write).
     *
     * @return Estimated quota units consumed
     */
    public int estimateUpdateDraftQuota() {
        logger.debug("Estimated quota for updating draft: {} units", DRAFTS_UPDATE_QUOTA);
        return DRAFTS_UPDATE_QUOTA;
    }

    // -------------------------------------------------------------------------
    // Feature 004 — Read API: threads, message detail, labels, attachments
    // -------------------------------------------------------------------------

    /**
     * Estimates quota for listing threads (users.threads.list).
     * Flat 10 units regardless of page size — no per-item enrichment (Clarifications Q1).
     *
     * @return Estimated quota units consumed
     */
    public int estimateListThreadsQuota() {
        logger.debug("Estimated quota for listing threads: {} units", THREADS_LIST_QUOTA);
        return THREADS_LIST_QUOTA;
    }

    /**
     * Estimates quota for getting a thread (users.threads.get, format=FULL).
     * 10 units.
     *
     * @return Estimated quota units consumed
     */
    public int estimateGetThreadQuota() {
        logger.debug("Estimated quota for getting thread: {} units", THREADS_GET_QUOTA);
        return THREADS_GET_QUOTA;
    }

    /**
     * Estimates quota for getting message detail.
     * 10 units for format=full; 5 units for format=metadata.
     *
     * @param format the requested format ("full" or "metadata")
     * @return Estimated quota units consumed
     */
    public int estimateGetMessageDetailQuota(String format) {
        int quota = "metadata".equals(format) ? MESSAGES_GET_METADATA_QUOTA : MESSAGES_GET_FULL_QUOTA;
        logger.debug("Estimated quota for getting message detail (format={}): {} units", format, quota);
        return quota;
    }

    /**
     * Estimates quota for listing labels (users.labels.list).
     * 1 unit.
     *
     * @return Estimated quota units consumed
     */
    public int estimateListLabelsQuota() {
        logger.debug("Estimated quota for listing labels: {} units", LABELS_LIST_QUOTA);
        return LABELS_LIST_QUOTA;
    }

    /**
     * Estimates quota for getting a label (users.labels.get).
     * 1 unit.
     *
     * @return Estimated quota units consumed
     */
    public int estimateGetLabelQuota() {
        logger.debug("Estimated quota for getting label: {} units", LABELS_GET_QUOTA);
        return LABELS_GET_QUOTA;
    }

    /**
     * Estimates quota for listing attachments on a message (users.messages.get, format=FULL).
     * 5 units.
     *
     * @return Estimated quota units consumed
     */
    public int estimateListAttachmentsQuota() {
        logger.debug("Estimated quota for listing attachments: {} units", ATTACHMENTS_LIST_QUOTA);
        return ATTACHMENTS_LIST_QUOTA;
    }

    /**
     * Estimates quota for downloading a single attachment (users.messages.attachments.get).
     * 5 units.
     *
     * @return Estimated quota units consumed
     */
    public int estimateGetAttachmentQuota() {
        logger.debug("Estimated quota for getting attachment: {} units", ATTACHMENTS_GET_QUOTA);
        return ATTACHMENTS_GET_QUOTA;
    }
}
