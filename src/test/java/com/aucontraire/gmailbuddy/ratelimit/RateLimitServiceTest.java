package com.aucontraire.gmailbuddy.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitServiceTest {

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService();
    }

    @Test
    void testRecordRequest_firstRequest() {
        // Act
        RateLimitInfo info = rateLimitService.recordRequest("user1");

        // Assert
        assertNotNull(info, "RateLimitInfo should not be null");
        assertEquals(1000, info.getLimit(), "Default limit should be 1000");
        assertEquals(999, info.getRemaining(), "First request should leave 999 remaining");
        assertTrue(info.getResetTimestamp() > 0, "Reset timestamp should be set");
    }

    @Test
    void testRecordRequest_multipleRequests() {
        // Act
        RateLimitInfo info1 = rateLimitService.recordRequest("user1");
        RateLimitInfo info2 = rateLimitService.recordRequest("user1");
        RateLimitInfo info3 = rateLimitService.recordRequest("user1");

        // Assert
        assertEquals(999, info1.getRemaining(), "First request: 999 remaining");
        assertEquals(998, info2.getRemaining(), "Second request: 998 remaining");
        assertEquals(997, info3.getRemaining(), "Third request: 997 remaining");

        // All should have same reset timestamp (same window)
        assertEquals(info1.getResetTimestamp(), info2.getResetTimestamp(),
                    "Reset timestamp should be consistent within window");
        assertEquals(info2.getResetTimestamp(), info3.getResetTimestamp(),
                    "Reset timestamp should be consistent within window");
    }

    @Test
    void testRecordRequest_multipleUsers() {
        // Act
        RateLimitInfo user1Info = rateLimitService.recordRequest("user1");
        RateLimitInfo user2Info = rateLimitService.recordRequest("user2");

        // Assert
        assertEquals(999, user1Info.getRemaining(), "User1 should have 999 remaining");
        assertEquals(999, user2Info.getRemaining(), "User2 should have 999 remaining (separate window)");
    }

    @Test
    void testGetRateLimitInfo_withoutRecording() {
        // Act
        RateLimitInfo info = rateLimitService.getRateLimitInfo("user1");

        // Assert
        assertEquals(1000, info.getLimit(), "Should return default limit");
        assertEquals(1000, info.getRemaining(), "Should return full limit when no requests recorded");
    }

    @Test
    void testGetRateLimitInfo_afterRecording() {
        // Arrange
        rateLimitService.recordRequest("user1");
        rateLimitService.recordRequest("user1");

        // Act
        RateLimitInfo info = rateLimitService.getRateLimitInfo("user1");

        // Assert
        assertEquals(998, info.getRemaining(), "Should reflect recorded requests without adding new one");
    }

    @Test
    void testRecordRequest_exhaustLimit() {
        // Arrange - record 1000 requests
        String userId = "user1";
        for (int i = 0; i < 1000; i++) {
            rateLimitService.recordRequest(userId);
        }

        // Act - one more request
        RateLimitInfo info = rateLimitService.recordRequest(userId);

        // Assert
        assertEquals(0, info.getRemaining(), "Remaining should be 0 when limit exhausted");
        assertEquals(1000, info.getLimit(), "Limit should still be 1000");
    }

    @Test
    void testRecordRequest_beyondLimit() {
        // Arrange - record 1001 requests
        String userId = "user1";
        for (int i = 0; i < 1001; i++) {
            rateLimitService.recordRequest(userId);
        }

        // Act
        RateLimitInfo info = rateLimitService.getRateLimitInfo(userId);

        // Assert
        assertEquals(0, info.getRemaining(), "Remaining should not go negative (clamped to 0)");
    }

    @Test
    void testCleanupExpiredWindows() {
        // Arrange - create some windows
        rateLimitService.recordRequest("user1");
        rateLimitService.recordRequest("user2");
        rateLimitService.recordRequest("user3");

        // Act - cleanup (windows won't be expired yet)
        rateLimitService.cleanupExpiredWindows();

        // Assert - windows should still exist
        RateLimitInfo info1 = rateLimitService.getRateLimitInfo("user1");
        assertEquals(999, info1.getRemaining(), "Window should not be cleaned up yet");
    }

    @Test
    void testRecordRequest_anonymousUser() {
        // Act
        RateLimitInfo info = rateLimitService.recordRequest("anonymous");

        // Assert
        assertEquals(1000, info.getLimit(), "Anonymous user should have default limit");
        assertEquals(999, info.getRemaining(), "Anonymous user should be tracked");
    }

    @Test
    void testRecordRequest_concurrentUsers() {
        // Act - simulate concurrent requests from different users
        RateLimitInfo user1_req1 = rateLimitService.recordRequest("user1");
        RateLimitInfo user2_req1 = rateLimitService.recordRequest("user2");
        RateLimitInfo user1_req2 = rateLimitService.recordRequest("user1");
        RateLimitInfo user2_req2 = rateLimitService.recordRequest("user2");

        // Assert
        assertEquals(999, user1_req1.getRemaining(), "User1 first request: 999 remaining");
        assertEquals(999, user2_req1.getRemaining(), "User2 first request: 999 remaining");
        assertEquals(998, user1_req2.getRemaining(), "User1 second request: 998 remaining");
        assertEquals(998, user2_req2.getRemaining(), "User2 second request: 998 remaining");
    }

    @Test
    void testGetRateLimitInfo_noWindowExists() {
        // Act
        RateLimitInfo info = rateLimitService.getRateLimitInfo("nonexistent");

        // Assert
        assertEquals(1000, info.getLimit(), "Should return default limit for new user");
        assertEquals(1000, info.getRemaining(), "Should return full remaining for new user");
        assertTrue(info.getResetTimestamp() > 0, "Should calculate reset timestamp");
    }
}
