package com.aucontraire.gmailbuddy.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that wraps incoming HTTP requests with a {@link CachedBodyHttpServletRequest}
 * so that the request body can be read more than once within the same request lifecycle.
 *
 * <p><strong>Why this filter exists (T036 implementation note)</strong></p>
 *
 * <p>The {@link RateLimitInterceptor} estimates Gmail API quota usage before the request
 * body is parsed by Spring's {@code RequestMappingHandlerAdapter}. For threading-aware
 * quota routing, the interceptor must inspect the body for the {@code inReplyToMessageId}
 * field to decide whether to report 105 quota units (threaded send: 5 lookup + 100 send)
 * or 100 units (plain send). The standard {@link jakarta.servlet.http.HttpServletRequest}
 * input stream is single-read: once the interceptor reads it, Spring cannot re-read it
 * for DTO binding.</p>
 *
 * <p><strong>Approach chosen: (a) {@code CachedBodyHttpServletRequest}</strong></p>
 *
 * <p>Alternative (b) — moving quota-header emission to the controller layer via
 * {@code @AfterReturning} advice — was considered but rejected: it would require an
 * {@code @Aspect} bean, two pointcut expressions, and access to both the parsed DTO
 * and the {@link HttpServletRequest} attribute inside advice code. That adds AOP
 * machinery that is harder to unit-test and harder to reason about than a single
 * filter. Approach (a) keeps the quota-routing concern entirely inside the interceptor,
 * which already owns quota estimation, and introduces only this thin filter as glue.</p>
 *
 * <p><strong>Why NOT {@code ContentCachingRequestWrapper} (T036 bug fix)</strong></p>
 *
 * <p>Spring's {@code ContentCachingRequestWrapper} was the original choice, but its
 * {@code getInputStream()} is not idempotent: it returns the same underlying
 * {@link jakarta.servlet.ServletInputStream} that is consumed on the first read.
 * When {@link RateLimitInterceptor#isThreadedRequest} reads the stream to inspect
 * the body, the stream position advances to EOF. Spring's {@code RequestMappingHandlerAdapter}
 * subsequently calls {@code getInputStream()} again for {@code @RequestBody} binding
 * and receives an empty stream, causing {@code HttpMessageNotReadableException}.
 * {@link CachedBodyHttpServletRequest} fixes this by returning a fresh
 * {@link java.io.ByteArrayInputStream} on every {@code getInputStream()} call.</p>
 *
 * <p><strong>Filter ordering</strong></p>
 *
 * <p>This filter runs at {@code @Order(2)}, after {@link ResponseHeaderFilter} ({@code @Order(1)})
 * which wraps the <em>response</em>. The Spring Security filter chain (including
 * {@link TokenAuthenticationFilter}) runs later and reads only the {@code Authorization}
 * header — never the request body — so wrapping the body here does not interfere with
 * authentication. Spring's argument resolver reads the body from the cached wrapper
 * after the interceptor has already peeked at it via {@link CachedBodyHttpServletRequest#getCachedBody()}.</p>
 *
 * <p>Only requests targeting {@code /api/v1/gmail/**} are wrapped; all other paths pass
 * through unwrapped to avoid unnecessary buffering overhead.</p>
 */
@Component
@Order(2)
public class RequestBodyCachingFilter extends OncePerRequestFilter {

    private static final String GMAIL_API_PATH_PREFIX = "/api/v1/gmail";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String uri = request.getRequestURI();
        String method = request.getMethod();

        // Only buffer POST request bodies to the two threading-capable endpoints.
        // Other endpoints do not need body caching for quota routing; wrapping
        // unconditionally would waste heap on large GET/DELETE request paths.
        if ("POST".equals(method) && uri != null && uri.startsWith(GMAIL_API_PATH_PREFIX)
                && (uri.endsWith("/messages") || uri.endsWith("/drafts"))) {
            CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
            filterChain.doFilter(wrappedRequest, response);
        } else {
            filterChain.doFilter(request, response);
        }
    }
}
