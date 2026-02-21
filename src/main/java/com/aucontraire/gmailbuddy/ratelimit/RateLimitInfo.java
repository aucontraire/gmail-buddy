package com.aucontraire.gmailbuddy.ratelimit;

/**
 * Holds rate limit information for a request window.
 * Used to track and communicate rate limiting state via response headers.
 */
public class RateLimitInfo {

    private final int limit;
    private final int remaining;
    private final long resetTimestamp;

    /**
     * Creates a new RateLimitInfo instance.
     *
     * @param limit Maximum number of requests allowed in the current window
     * @param remaining Number of requests remaining in the current window
     * @param resetTimestamp Unix timestamp (seconds) when the rate limit window resets
     */
    public RateLimitInfo(int limit, int remaining, long resetTimestamp) {
        this.limit = limit;
        this.remaining = remaining;
        this.resetTimestamp = resetTimestamp;
    }

    /**
     * Gets the maximum number of requests allowed in the current window.
     *
     * @return the request limit
     */
    public int getLimit() {
        return limit;
    }

    /**
     * Gets the number of requests remaining in the current window.
     *
     * @return the remaining request count
     */
    public int getRemaining() {
        return remaining;
    }

    /**
     * Gets the Unix timestamp (seconds) when the rate limit window resets.
     *
     * @return the reset timestamp
     */
    public long getResetTimestamp() {
        return resetTimestamp;
    }

    @Override
    public String toString() {
        return "RateLimitInfo{" +
                "limit=" + limit +
                ", remaining=" + remaining +
                ", resetTimestamp=" + resetTimestamp +
                '}';
    }
}
