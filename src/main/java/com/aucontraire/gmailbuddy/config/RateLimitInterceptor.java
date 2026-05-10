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

import java.nio.charset.StandardCharsets;

/**
 * Interceptor that tracks rate limits and Gmail quota usage for each request.
 * Sets request attributes that will be read by ResponseHeaderFilter to add appropriate headers.
 *
 * <p>This interceptor:</p>
 * <ol>
 *   <li>Records the request in RateLimitService and gets rate limit info</li>
 *   <li>Estimates Gmail API quota usage based on the endpoint, including threading-aware
 *       routing for {@code POST /api/v1/gmail/messages} and {@code POST /api/v1/gmail/drafts}</li>
 *   <li>Sets request attributes for the filter to read</li>
 * </ol>
 *
 * <p><strong>Threading-aware quota routing (T036)</strong></p>
 *
 * <p>When a {@code POST /messages} or {@code POST /drafts} request includes
 * {@code inReplyToMessageId}, the actual Gmail API cost is higher because the service
 * performs a preliminary {@code users.messages.get} metadata lookup (~5 quota units)
 * before the send or draft-create call. This interceptor detects the presence of
 * {@code inReplyToMessageId} in the request body and routes to the appropriate
 * estimator method ({@link GmailQuotaEstimator#estimateThreadedSendMessageQuota()} → 105,
 * {@link GmailQuotaEstimator#estimateThreadedCreateDraftQuota()} → 15) so that the
 * {@code X-Gmail-Quota-Used} response header accurately reflects the real cost (FR-008b).</p>
 *
 * <p>Body inspection is possible here — before Spring's {@code RequestMappingHandlerAdapter}
 * binds the DTO — because {@link RequestBodyCachingFilter} wraps the request with a
 * {@link CachedBodyHttpServletRequest} at filter order 2, buffering the body for re-read.
 * A lightweight regex check on the cached byte content is used; no full JSON parsing
 * or DTO binding is performed in this interceptor.</p>
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitInterceptor.class);

    /** Default page size for pre-execution quota estimate on GET /drafts. */
    static final int DEFAULT_DRAFT_LIST_LIMIT = 25;

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

        int estimatedQuota = estimateQuotaForEndpoint(request, uri, method);

        if (estimatedQuota > 0) {
            request.setAttribute(ResponseHeaderFilter.ATTR_GMAIL_QUOTA_USED, estimatedQuota);
            logger.debug("RateLimitInterceptor: Estimated quota for {} {}: {} units", method, uri, estimatedQuota);
        }
    }

    /**
     * Estimates quota usage based on the endpoint URI, HTTP method, and — for the two
     * threading-capable {@code POST} endpoints — the request body.
     *
     * <p><strong>Threading-aware routing (T036, FR-008b)</strong></p>
     *
     * <p>For {@code POST /api/v1/gmail/messages} and {@code POST /api/v1/gmail/drafts},
     * the presence of {@code "inReplyToMessageId"} in the request body signals that the
     * service will perform an extra {@code users.messages.get} metadata lookup before
     * sending. The quota cost is therefore higher:</p>
     * <ul>
     *   <li>Threaded send: {@link GmailQuotaEstimator#estimateThreadedSendMessageQuota()} → 105 units</li>
     *   <li>Threaded draft: {@link GmailQuotaEstimator#estimateThreadedCreateDraftQuota()} → 15 units</li>
     *   <li>Non-threaded paths use the existing estimator methods unchanged.</li>
     * </ul>
     *
     * <p>Body inspection relies on {@link RequestBodyCachingFilter} having wrapped the
     * request with a {@link CachedBodyHttpServletRequest} before this interceptor runs.
     * If the wrapper is absent (e.g., in tests that bypass the filter chain), the
     * interceptor falls back to the non-threaded estimate — a safe degradation that
     * slightly under-reports quota but never causes a request failure.</p>
     *
     * @param request The HTTP request (may be a {@link CachedBodyHttpServletRequest})
     * @param uri     Request URI
     * @param method  HTTP method
     * @return Estimated quota units
     */
    int estimateQuotaForEndpoint(HttpServletRequest request, String uri, String method) {
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
        } else if (uri.endsWith("/messages") && "POST".equals(method)) {
            // POST /messages - direct send; threaded if body contains inReplyToMessageId
            if (isThreadedRequest(request)) {
                return quotaEstimator.estimateThreadedSendMessageQuota(); // 105 (5 lookup + 100 send)
            }
            return quotaEstimator.estimateSendMessageQuota(); // 100
        } else if (uri.endsWith("/drafts") && "POST".equals(method)) {
            // POST /drafts - create draft; threaded if body contains inReplyToMessageId
            if (isThreadedRequest(request)) {
                return quotaEstimator.estimateThreadedCreateDraftQuota(); // 15 (5 lookup + 10 draft create)
            }
            return quotaEstimator.estimateCreateDraftQuota(); // 10
        } else if (uri.matches(".*/drafts/[^/]+/send") && "POST".equals(method)) {
            // POST /drafts/{draftId}/send - send existing draft
            // Threading headers are baked into the draft at creation time; this call
            // always costs the same regardless of whether the draft is threaded (Decision 13).
            return quotaEstimator.estimateSendDraftQuota();
        } else if (uri.matches(".*/drafts/[^/]+") && "GET".equals(method)) {
            // GET /drafts/{id} — get draft detail
            return quotaEstimator.estimateGetDraftQuota();
        } else if (uri.matches(".*/drafts/[^/]+") && "DELETE".equals(method)) {
            // DELETE /drafts/{id}
            return quotaEstimator.estimateDeleteDraftQuota();
        } else if (uri.matches(".*/drafts/[^/]+") && "PUT".equals(method)) {
            // PUT /drafts/{id} — update draft
            return quotaEstimator.estimateUpdateDraftQuota();
        } else if (uri.endsWith("/drafts") && "GET".equals(method)) {
            // GET /drafts — list; pre-execution estimate using DEFAULT_DRAFT_LIST_LIMIT
            // Controller updates the request attribute post-execution with the actual item count
            return quotaEstimator.estimateListDraftsQuota(DEFAULT_DRAFT_LIST_LIMIT);
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

    /**
     * Detects whether a {@code POST /messages} or {@code POST /drafts} request is a
     * threaded request by scanning the cached body for the JSON key
     * {@code "inReplyToMessageId"} with a non-null value.
     *
     * <p>This method reads the body from the {@link CachedBodyHttpServletRequest} buffer
     * populated by {@link RequestBodyCachingFilter} via {@link CachedBodyHttpServletRequest#getCachedBody()}.
     * It does NOT parse the full JSON document; instead it uses a targeted regex that matches
     * the key followed by a non-null JSON value. This is deliberately lightweight — the
     * interceptor does not bind the DTO; it only needs a boolean signal.</p>
     *
     * <p>Regex details: {@code "inReplyToMessageId"\s*:\s*"[^"]+"}
     * matches the field name (with optional whitespace around the colon) followed by
     * a non-empty quoted string value. A literal {@code null} JSON value (e.g.,
     * {@code "inReplyToMessageId": null}) does not match and correctly falls back to
     * the non-threaded estimate.</p>
     *
     * <p>If the request is not a {@link CachedBodyHttpServletRequest} (e.g., in
     * integration tests that bypass the filter chain), this method returns {@code false}
     * so the non-threaded quota estimate is used — a safe degradation that slightly
     * under-reports quota for threaded requests but never causes a request failure.</p>
     *
     * @param request The HTTP request
     * @return {@code true} if the request body contains a non-null {@code inReplyToMessageId}
     */
    private boolean isThreadedRequest(HttpServletRequest request) {
        if (!(request instanceof CachedBodyHttpServletRequest wrapper)) {
            // Filter was bypassed (e.g., test context without full filter chain).
            // Fall back to non-threaded estimate — safe degradation.
            logger.debug("RateLimitInterceptor: request is not wrapped; cannot inspect body for threading; defaulting to non-threaded quota");
            return false;
        }

        // getCachedBody() returns a defensive copy of the pre-buffered bytes.
        // No stream I/O is performed here; the body remains fully readable by
        // Spring's @RequestBody argument resolver via getInputStream().
        byte[] bodyBytes = wrapper.getCachedBody();

        if (bodyBytes.length == 0) {
            return false;
        }

        String bodySnippet = new String(bodyBytes, StandardCharsets.UTF_8);
        // Match "inReplyToMessageId" key with a non-null, non-empty quoted string value.
        // Allows optional whitespace around the colon per JSON spec.
        boolean threaded = bodySnippet.matches("(?s).*\"inReplyToMessageId\"\\s*:\\s*\"[^\"]+\".*");
        if (threaded) {
            logger.debug("RateLimitInterceptor: detected inReplyToMessageId in request body — routing to threaded quota estimate");
        }
        return threaded;
    }
}
