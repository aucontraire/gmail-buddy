package com.aucontraire.gmailbuddy.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for tracking and managing application-level rate limits.
 *
 * This service provides a simple in-memory rate limiting mechanism
 * based on a fixed window approach. For production use with multiple
 * instances, consider using a distributed cache like Redis.
 *
 * Default configuration:
 * - Window size: 1 minute (60 seconds)
 * - Limit: 1000 requests per window
 */
@Service
public class RateLimitService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitService.class);

    private static final int DEFAULT_LIMIT = 1000;
    private static final long WINDOW_SIZE_SECONDS = 60;

    // Tracks requests per user per window
    private final ConcurrentHashMap<String, WindowData> userWindows = new ConcurrentHashMap<>();

    /**
     * Records a request for the given user and returns the current rate limit info.
     *
     * @param userId User identifier (typically OAuth2 principal name)
     * @return RateLimitInfo containing current limit, remaining, and reset time
     */
    public RateLimitInfo recordRequest(String userId) {
        long currentTime = Instant.now().getEpochSecond();

        WindowData windowData = userWindows.compute(userId, (key, existing) -> {
            if (existing == null || existing.isExpired(currentTime)) {
                // Start new window
                long resetTime = currentTime + WINDOW_SIZE_SECONDS;
                logger.debug("Starting new rate limit window for user: {}, reset at: {}", userId, resetTime);
                return new WindowData(resetTime);
            }
            // Increment counter in current window
            existing.incrementCount();
            return existing;
        });

        int remaining = Math.max(0, DEFAULT_LIMIT - windowData.getCount());

        RateLimitInfo info = new RateLimitInfo(DEFAULT_LIMIT, remaining, windowData.getResetTime());

        logger.debug("Rate limit for user {}: limit={}, remaining={}, reset={}",
                    userId, info.getLimit(), info.getRemaining(), info.getResetTimestamp());

        return info;
    }

    /**
     * Gets the current rate limit info for a user without recording a request.
     *
     * @param userId User identifier
     * @return RateLimitInfo containing current limit, remaining, and reset time
     */
    public RateLimitInfo getRateLimitInfo(String userId) {
        long currentTime = Instant.now().getEpochSecond();

        WindowData windowData = userWindows.get(userId);

        if (windowData == null || windowData.isExpired(currentTime)) {
            // No active window - return full limit available
            long resetTime = currentTime + WINDOW_SIZE_SECONDS;
            return new RateLimitInfo(DEFAULT_LIMIT, DEFAULT_LIMIT, resetTime);
        }

        int remaining = Math.max(0, DEFAULT_LIMIT - windowData.getCount());
        return new RateLimitInfo(DEFAULT_LIMIT, remaining, windowData.getResetTime());
    }

    /**
     * Cleans up expired windows to prevent memory leaks.
     * Should be called periodically by a scheduled task.
     */
    public void cleanupExpiredWindows() {
        long currentTime = Instant.now().getEpochSecond();
        int sizeBefore = userWindows.size();

        userWindows.entrySet().removeIf(entry -> entry.getValue().isExpired(currentTime));

        int removed = sizeBefore - userWindows.size();
        if (removed > 0) {
            logger.debug("Cleaned up {} expired rate limit windows", removed);
        }
    }

    /**
     * Internal class to track request count and window reset time.
     */
    private static class WindowData {
        private final long resetTime;
        private final AtomicInteger count;

        WindowData(long resetTime) {
            this.resetTime = resetTime;
            this.count = new AtomicInteger(1); // Start at 1 for the current request
        }

        void incrementCount() {
            count.incrementAndGet();
        }

        int getCount() {
            return count.get();
        }

        long getResetTime() {
            return resetTime;
        }

        boolean isExpired(long currentTime) {
            return currentTime >= resetTime;
        }
    }
}
