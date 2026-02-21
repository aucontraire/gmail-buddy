package com.aucontraire.gmailbuddy.config;

import com.aucontraire.gmailbuddy.ratelimit.RateLimitInfo;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet Filter that adds standardized response headers to all HTTP responses.
 * Uses a Filter instead of HandlerInterceptor to ensure headers are added before response commit.
 *
 * Added Headers:
 * - X-Request-ID: UUID for request correlation (generated or from incoming request)
 * - X-Response-Time: Response time in milliseconds
 * - X-RateLimit-Limit: Maximum requests per window (RFC standard)
 * - X-RateLimit-Remaining: Remaining requests in current window (RFC standard)
 * - X-RateLimit-Reset: Unix timestamp when limit resets (RFC standard)
 * - X-Gmail-Quota-Used: Estimated Gmail API quota units consumed by this request
 * - X-Gmail-Quota-Remaining: Estimated remaining Gmail API quota (if trackable)
 *
 * Also stores request ID in SLF4J MDC for log correlation.
 */
@Component
@Order(1) // Run early in the filter chain
public class ResponseHeaderFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(ResponseHeaderFilter.class);
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String RESPONSE_TIME_HEADER = "X-Response-Time";
    private static final String MDC_REQUEST_ID_KEY = "requestId";

    // Rate limit headers (RFC standard)
    private static final String RATE_LIMIT_LIMIT_HEADER = "X-RateLimit-Limit";
    private static final String RATE_LIMIT_REMAINING_HEADER = "X-RateLimit-Remaining";
    private static final String RATE_LIMIT_RESET_HEADER = "X-RateLimit-Reset";

    // Gmail quota headers (application-specific)
    private static final String GMAIL_QUOTA_USED_HEADER = "X-Gmail-Quota-Used";
    private static final String GMAIL_QUOTA_REMAINING_HEADER = "X-Gmail-Quota-Remaining";

    // Request attribute keys
    public static final String ATTR_RATE_LIMIT_INFO = "rateLimitInfo";
    public static final String ATTR_GMAIL_QUOTA_USED = "gmailQuotaUsed";
    public static final String ATTR_GMAIL_QUOTA_REMAINING = "gmailQuotaRemaining";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Generate or use existing request ID
        String requestId = httpRequest.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }

        // Store in MDC for logging correlation
        MDC.put(MDC_REQUEST_ID_KEY, requestId);

        // Record start time
        final long startTime = System.currentTimeMillis();
        final String finalRequestId = requestId;

        logger.debug("ResponseHeaderFilter: Processing request {} with requestId: {}",
                    httpRequest.getRequestURI(), requestId);

        // Wrap the response to add headers before commit
        HttpServletResponseWrapper responseWrapper = new HttpServletResponseWrapper(httpResponse) {
            private boolean headersAdded = false;

            private void addHeadersIfNeeded() {
                if (!headersAdded) {
                    // Add request ID header
                    super.setHeader(REQUEST_ID_HEADER, finalRequestId);

                    // Calculate and add response time
                    long responseTime = System.currentTimeMillis() - startTime;
                    super.setHeader(RESPONSE_TIME_HEADER, String.valueOf(responseTime));

                    // Add rate limit headers if available
                    addRateLimitHeaders();

                    // Add Gmail quota headers if available
                    addGmailQuotaHeaders();

                    headersAdded = true;

                    logger.debug("ResponseHeaderFilter: Added headers - requestId: {}, responseTime: {}ms",
                                finalRequestId, responseTime);
                }
            }

            private void addRateLimitHeaders() {
                Object rateLimitInfoObj = httpRequest.getAttribute(ATTR_RATE_LIMIT_INFO);
                if (rateLimitInfoObj instanceof RateLimitInfo rateLimitInfo) {
                    super.setHeader(RATE_LIMIT_LIMIT_HEADER, String.valueOf(rateLimitInfo.getLimit()));
                    super.setHeader(RATE_LIMIT_REMAINING_HEADER, String.valueOf(rateLimitInfo.getRemaining()));
                    super.setHeader(RATE_LIMIT_RESET_HEADER, String.valueOf(rateLimitInfo.getResetTimestamp()));

                    logger.debug("ResponseHeaderFilter: Added rate limit headers - limit: {}, remaining: {}, reset: {}",
                                rateLimitInfo.getLimit(), rateLimitInfo.getRemaining(), rateLimitInfo.getResetTimestamp());
                }
            }

            private void addGmailQuotaHeaders() {
                Object quotaUsedObj = httpRequest.getAttribute(ATTR_GMAIL_QUOTA_USED);
                if (quotaUsedObj instanceof Integer quotaUsed) {
                    super.setHeader(GMAIL_QUOTA_USED_HEADER, String.valueOf(quotaUsed));

                    logger.debug("ResponseHeaderFilter: Added Gmail quota used header - quota: {}", quotaUsed);
                }

                Object quotaRemainingObj = httpRequest.getAttribute(ATTR_GMAIL_QUOTA_REMAINING);
                if (quotaRemainingObj instanceof Integer quotaRemaining) {
                    super.setHeader(GMAIL_QUOTA_REMAINING_HEADER, String.valueOf(quotaRemaining));

                    logger.debug("ResponseHeaderFilter: Added Gmail quota remaining header - remaining: {}", quotaRemaining);
                }
            }

            @Override
            public void sendError(int sc) throws IOException {
                addHeadersIfNeeded();
                super.sendError(sc);
            }

            @Override
            public void sendError(int sc, String msg) throws IOException {
                addHeadersIfNeeded();
                super.sendError(sc, msg);
            }

            @Override
            public void sendRedirect(String location) throws IOException {
                addHeadersIfNeeded();
                super.sendRedirect(location);
            }

            @Override
            public ServletOutputStream getOutputStream() throws IOException {
                addHeadersIfNeeded();
                return super.getOutputStream();
            }

            @Override
            public java.io.PrintWriter getWriter() throws IOException {
                addHeadersIfNeeded();
                return super.getWriter();
            }

            @Override
            public void flushBuffer() throws IOException {
                addHeadersIfNeeded();
                super.flushBuffer();
            }
        };

        try {
            // Execute the rest of the filter chain with wrapped response
            chain.doFilter(request, responseWrapper);

            logger.debug("ResponseHeaderFilter: Completed request {} - committed: {}",
                        httpRequest.getRequestURI(), responseWrapper.isCommitted());

        } finally {
            // Always clean up MDC to prevent memory leaks
            MDC.clear();
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("ResponseHeaderFilter initialized");
    }

    @Override
    public void destroy() {
        logger.info("ResponseHeaderFilter destroyed");
    }
}
