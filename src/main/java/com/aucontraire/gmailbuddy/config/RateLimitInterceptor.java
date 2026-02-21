package com.aucontraire.gmailbuddy.config;

import com.aucontraire.gmailbuddy.ratelimit.GmailQuotaEstimator;
import com.aucontraire.gmailbuddy.ratelimit.RateLimitInfo;
import com.aucontraire.gmailbuddy.ratelimit.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor that tracks rate limits and Gmail quota usage for each request.
 * Sets request attributes that will be read by ResponseHeaderFilter to add appropriate headers.
 *
 * This interceptor:
 * 1. Records the request in RateLimitService and gets rate limit info
 * 2. Estimates Gmail API quota usage based on the endpoint
 * 3. Sets request attributes for the filter to read
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RateLimitService rateLimitService;
    private final GmailQuotaEstimator quotaEstimator;

    @Autowired
    public RateLimitInterceptor(RateLimitService rateLimitService, GmailQuotaEstimator quotaEstimator) {
        this.rateLimitService = rateLimitService;
        this.quotaEstimator = quotaEstimator;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                            @NonNull HttpServletResponse response,
                            @NonNull Object handler) {

        // Get user identifier from security context
        String userId = getUserIdentifier();

        // Record request and get rate limit info
        RateLimitInfo rateLimitInfo = rateLimitService.recordRequest(userId);
        request.setAttribute(ResponseHeaderFilter.ATTR_RATE_LIMIT_INFO, rateLimitInfo);

        logger.debug("RateLimitInterceptor: Recorded request for user: {} - {}", userId, rateLimitInfo);

        // Estimate Gmail quota usage based on endpoint
        estimateAndSetQuotaUsage(request);

        return true;
    }

    /**
     * Gets the user identifier from the security context.
     * Falls back to "anonymous" if no authenticated user.
     *
     * @return User identifier
     */
    private String getUserIdentifier() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() &&
            !"anonymousUser".equals(authentication.getPrincipal())) {
            return authentication.getName();
        }
        return "anonymous";
    }

    /**
     * Estimates Gmail API quota usage based on the request URI and method.
     * Sets the estimated quota as a request attribute.
     *
     * @param request The HTTP request
     */
    private void estimateAndSetQuotaUsage(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        int estimatedQuota = estimateQuotaForEndpoint(uri, method);

        if (estimatedQuota > 0) {
            request.setAttribute(ResponseHeaderFilter.ATTR_GMAIL_QUOTA_USED, estimatedQuota);
            logger.debug("RateLimitInterceptor: Estimated quota for {} {}: {} units", method, uri, estimatedQuota);
        }
    }

    /**
     * Estimates quota usage based on the endpoint URI and HTTP method.
     *
     * @param uri Request URI
     * @param method HTTP method
     * @return Estimated quota units
     */
    private int estimateQuotaForEndpoint(String uri, String method) {
        // Skip non-Gmail API endpoints
        if (!uri.startsWith("/api/v1/gmail")) {
            return 0;
        }

        // Estimate based on endpoint pattern
        // Check more specific patterns first (with path suffixes)
        if (uri.matches(".*/messages/[^/]+/body")) {
            // GET /messages/{id}/body - fetches message body
            return quotaEstimator.estimateGetMessageQuota();
        } else if (uri.matches(".*/messages/[^/]+/read") && "PUT".equals(method)) {
            // PUT /messages/{id}/read - modifies message
            return quotaEstimator.estimateModifyMessageQuota();
        } else if (uri.endsWith("/messages/filter/modifyLabels") && "POST".equals(method)) {
            // POST /messages/filter/modifyLabels - batch modify
            // Estimate for a moderate batch (10 batches of 50 messages each)
            return quotaEstimator.estimateBatchModifyQuota(500, 50);
        } else if (uri.endsWith("/messages/filter") && "POST".equals(method)) {
            // POST /messages/filter - searches messages
            return quotaEstimator.estimateFilterMessagesQuota(0); // Can't know count yet
        } else if (uri.endsWith("/messages/filter") && "DELETE".equals(method)) {
            // DELETE /messages/filter - batch delete
            // Estimate for a moderate batch (10 batches of 50 messages each)
            return quotaEstimator.estimateBatchDeleteQuota(500, 50);
        } else if (uri.endsWith("/messages") || uri.endsWith("/messages/latest")) {
            // GET /messages or /messages/latest - list messages
            return quotaEstimator.estimateListMessagesQuota(0); // Can't know count yet
        } else if (uri.matches(".*/messages/[^/]+") && "GET".equals(method)) {
            // GET /messages/{id} - gets single message
            return quotaEstimator.estimateGetMessageQuota();
        } else if (uri.matches(".*/messages/[^/]+") && "DELETE".equals(method)) {
            // DELETE /messages/{id} - deletes single message
            return quotaEstimator.estimateDeleteMessageQuota();
        }

        // Default for unknown endpoints
        return 0;
    }
}
