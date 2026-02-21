package com.aucontraire.gmailbuddy.ratelimit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitInfoTest {

    @Test
    void testConstructorAndGetters() {
        // Arrange
        int limit = 1000;
        int remaining = 750;
        long resetTimestamp = 1234567890L;

        // Act
        RateLimitInfo info = new RateLimitInfo(limit, remaining, resetTimestamp);

        // Assert
        assertEquals(limit, info.getLimit(), "Limit should match constructor value");
        assertEquals(remaining, info.getRemaining(), "Remaining should match constructor value");
        assertEquals(resetTimestamp, info.getResetTimestamp(), "Reset timestamp should match constructor value");
    }

    @Test
    void testToString() {
        // Arrange
        RateLimitInfo info = new RateLimitInfo(1000, 750, 1234567890L);

        // Act
        String result = info.toString();

        // Assert
        assertTrue(result.contains("limit=1000"), "toString should contain limit");
        assertTrue(result.contains("remaining=750"), "toString should contain remaining");
        assertTrue(result.contains("resetTimestamp=1234567890"), "toString should contain resetTimestamp");
    }

    @Test
    void testWithZeroRemaining() {
        // Arrange & Act
        RateLimitInfo info = new RateLimitInfo(1000, 0, 1234567890L);

        // Assert
        assertEquals(0, info.getRemaining(), "Remaining should be zero");
        assertEquals(1000, info.getLimit(), "Limit should still be set");
    }

    @Test
    void testWithMaxValues() {
        // Arrange & Act
        RateLimitInfo info = new RateLimitInfo(Integer.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE);

        // Assert
        assertEquals(Integer.MAX_VALUE, info.getLimit(), "Should handle max integer limit");
        assertEquals(Integer.MAX_VALUE, info.getRemaining(), "Should handle max integer remaining");
        assertEquals(Long.MAX_VALUE, info.getResetTimestamp(), "Should handle max long timestamp");
    }
}
