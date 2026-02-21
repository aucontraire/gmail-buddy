package com.aucontraire.gmailbuddy.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GmailQuotaEstimatorTest {

    private GmailQuotaEstimator estimator;

    @BeforeEach
    void setUp() {
        estimator = new GmailQuotaEstimator();
    }

    @Test
    void testEstimateListMessagesQuota() {
        // Act
        int quota = estimator.estimateListMessagesQuota(10);

        // Assert
        assertEquals(5, quota, "List messages operation should cost 5 quota units");
    }

    @Test
    void testEstimateGetMessageQuota() {
        // Act
        int quota = estimator.estimateGetMessageQuota();

        // Assert
        assertEquals(5, quota, "Get message operation should cost 5 quota units");
    }

    @Test
    void testEstimateBatchDeleteQuota_singleBatch() {
        // Act
        int quota = estimator.estimateBatchDeleteQuota(50, 50);

        // Assert
        assertEquals(50, quota, "Single batch delete should cost 50 quota units");
    }

    @Test
    void testEstimateBatchDeleteQuota_multipleBatches() {
        // Act - 5 batches of 50 messages
        int quota = estimator.estimateBatchDeleteQuota(250, 50);

        // Assert
        assertEquals(250, quota, "5 batches should cost 250 quota units (5 * 50)");
    }

    @Test
    void testEstimateBatchDeleteQuota_partialBatch() {
        // Act - 75 messages in batches of 50 = 2 batches
        int quota = estimator.estimateBatchDeleteQuota(75, 50);

        // Assert
        assertEquals(100, quota, "Partial batch should round up to 2 batches (100 quota units)");
    }

    @Test
    void testEstimateBatchDeleteQuota_zeroMessages() {
        // Act
        int quota = estimator.estimateBatchDeleteQuota(0, 50);

        // Assert
        assertEquals(0, quota, "Zero messages should cost 0 quota units");
    }

    @Test
    void testEstimateBatchModifyQuota_singleBatch() {
        // Act
        int quota = estimator.estimateBatchModifyQuota(50, 50);

        // Assert
        assertEquals(50, quota, "Single batch modify should cost 50 quota units");
    }

    @Test
    void testEstimateBatchModifyQuota_multipleBatches() {
        // Act - 10 batches of 50 messages
        int quota = estimator.estimateBatchModifyQuota(500, 50);

        // Assert
        assertEquals(500, quota, "10 batches should cost 500 quota units (10 * 50)");
    }

    @Test
    void testEstimateBatchModifyQuota_partialBatch() {
        // Act - 125 messages in batches of 50 = 3 batches
        int quota = estimator.estimateBatchModifyQuota(125, 50);

        // Assert
        assertEquals(150, quota, "Partial batch should round up to 3 batches (150 quota units)");
    }

    @Test
    void testEstimateBatchModifyQuota_zeroMessages() {
        // Act
        int quota = estimator.estimateBatchModifyQuota(0, 50);

        // Assert
        assertEquals(0, quota, "Zero messages should cost 0 quota units");
    }

    @Test
    void testEstimateDeleteMessageQuota() {
        // Act
        int quota = estimator.estimateDeleteMessageQuota();

        // Assert
        assertEquals(10, quota, "Delete single message operation should cost 10 quota units");
    }

    @Test
    void testEstimateModifyMessageQuota() {
        // Act
        int quota = estimator.estimateModifyMessageQuota();

        // Assert
        assertEquals(5, quota, "Modify single message operation should cost 5 quota units");
    }

    @Test
    void testEstimateFilterMessagesQuota() {
        // Act
        int quota = estimator.estimateFilterMessagesQuota(20);

        // Assert
        assertEquals(5, quota, "Filter messages operation should cost 5 quota units (base search cost)");
    }

    @Test
    void testEstimateBatchDeleteQuota_largeBatchSize() {
        // Act - 100 messages in batches of 100
        int quota = estimator.estimateBatchDeleteQuota(100, 100);

        // Assert
        assertEquals(50, quota, "Single batch of 100 messages should cost 50 quota units");
    }

    @Test
    void testEstimateBatchModifyQuota_smallBatchSize() {
        // Act - 100 messages in batches of 10 = 10 batches
        int quota = estimator.estimateBatchModifyQuota(100, 10);

        // Assert
        assertEquals(500, quota, "10 batches should cost 500 quota units");
    }
}
