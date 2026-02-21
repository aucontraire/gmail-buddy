package com.aucontraire.gmailbuddy.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;

/**
 * Interceptor that adds standardized response headers to all API responses.
 * Implements RFC-compliant request ID tracking and response time measurement.
 *
 * NOTE: This interceptor approach doesn't work for @RestController because the response
 * is committed before postHandle runs. Use ResponseHeaderFilter instead.
 *
 * DEPRECATED: Replaced by ResponseHeaderFilter
 *
 * Added Headers:
 * - X-Request-ID: UUID for request correlation (generated or from incoming request)
 * - X-Response-Time: Response time in milliseconds
 *
 * Also stores request ID in SLF4J MDC for log correlation.
 */
// @Component - DISABLED: Using ResponseHeaderFilter instead
public class ResponseHeaderInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(ResponseHeaderInterceptor.class);
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String RESPONSE_TIME_HEADER = "X-Response-Time";
    private static final String REQUEST_START_TIME = "requestStartTime";
    private static final String MDC_REQUEST_ID_KEY = "requestId";

    /**
     * Called before the handler is executed.
     * Generates or uses existing request ID, stores it in MDC, and records start time.
     *
     * @param request current HTTP request
     * @param response current HTTP response
     * @param handler chosen handler to execute
     * @return true to continue execution, false to abort
     */
    @Override
    public boolean preHandle(HttpServletRequest request,
                            HttpServletResponse response,
                            Object handler) {
        // Generate or use existing request ID
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }

        // Store in MDC for logging correlation
        MDC.put(MDC_REQUEST_ID_KEY, requestId);

        // Store start time for response time calculation
        request.setAttribute(REQUEST_START_TIME, System.currentTimeMillis());

        logger.info("ResponseHeaderInterceptor.preHandle invoked for {} - requestId: {}",
                    request.getRequestURI(), requestId);

        return true;
    }

    /**
     * Called after the handler is executed but before the view is rendered.
     * This is the right place to add headers for REST APIs.
     *
     * @param request current HTTP request
     * @param response current HTTP response
     * @param handler the handler that was executed
     * @param modelAndView the ModelAndView (null for REST controllers)
     */
    @Override
    public void postHandle(HttpServletRequest request,
                          HttpServletResponse response,
                          Object handler,
                          org.springframework.web.servlet.ModelAndView modelAndView) {
        // Add request ID to response
        String requestId = MDC.get(MDC_REQUEST_ID_KEY);
        if (requestId != null) {
            response.setHeader(REQUEST_ID_HEADER, requestId);
            logger.info("Setting X-Request-ID header: {}", requestId);
        }

        // Calculate and add response time
        Long startTime = (Long) request.getAttribute(REQUEST_START_TIME);
        if (startTime != null) {
            long responseTime = System.currentTimeMillis() - startTime;
            response.setHeader(RESPONSE_TIME_HEADER, String.valueOf(responseTime));
            logger.info("Setting X-Response-Time header: {}ms", responseTime);
        }

        logger.info("ResponseHeaderInterceptor.postHandle invoked for {} - requestId: {}, responseTime: {}ms, committed: {}",
                    request.getRequestURI(), requestId,
                    (startTime != null ? (System.currentTimeMillis() - startTime) : "N/A"),
                    response.isCommitted());
    }

    /**
     * Called after request completion, after view rendering.
     * Cleans up MDC to prevent memory leaks.
     *
     * @param request current HTTP request
     * @param response current HTTP response
     * @param handler the handler that was executed
     * @param ex any exception thrown during handler execution (null if none)
     */
    @Override
    public void afterCompletion(HttpServletRequest request,
                               HttpServletResponse response,
                               Object handler,
                               Exception ex) {
        String requestId = MDC.get(MDC_REQUEST_ID_KEY);
        logger.info("ResponseHeaderInterceptor.afterCompletion invoked for {} - requestId: {}, committed: {}",
                    request.getRequestURI(), requestId, response.isCommitted());

        // Clean up MDC to prevent memory leaks
        MDC.clear();
    }
}