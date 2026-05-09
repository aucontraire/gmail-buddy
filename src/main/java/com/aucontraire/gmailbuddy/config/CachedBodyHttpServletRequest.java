package com.aucontraire.gmailbuddy.config;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.util.StreamUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * An {@link HttpServletRequestWrapper} that reads the entire request body into an in-memory
 * byte array on construction and then vends a fresh {@link ServletInputStream} over those
 * cached bytes on every call to {@link #getInputStream()}.
 *
 * <p><strong>Why this class exists (T036 bug fix)</strong></p>
 *
 * <p>Spring's {@link org.springframework.web.util.ContentCachingRequestWrapper} does cache
 * the body bytes internally, but its {@link #getInputStream()} is NOT idempotent: it returns
 * the same underlying {@link ServletInputStream} instance that was already consumed on the
 * first read. A second caller (e.g., Spring's {@code RequestMappingHandlerAdapter} doing
 * {@code @RequestBody} binding) sees a stream positioned at EOF and receives an empty body,
 * causing {@code HttpMessageNotReadableException}.</p>
 *
 * <p>This wrapper fixes the defect by storing the body bytes in {@link #cachedBody} at
 * construction time and returning a brand-new {@link ByteArrayInputStream} on each
 * {@link #getInputStream()} call. The stream is always readable regardless of how many
 * times it has been read before.</p>
 *
 * <p><strong>Usage</strong></p>
 *
 * <p>{@link RequestBodyCachingFilter} wraps qualifying POST requests with this class.
 * {@link RateLimitInterceptor} calls {@link #getCachedBody()} to inspect the body for
 * {@code inReplyToMessageId} without touching the stream. Spring's argument resolver
 * subsequently calls {@link #getInputStream()} and gets the full body content.</p>
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    /**
     * Constructs a {@code CachedBodyHttpServletRequest} by draining {@code request}'s
     * input stream into {@link #cachedBody}.
     *
     * @param request The original {@link HttpServletRequest}
     * @throws IOException if reading the input stream fails
     */
    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
    }

    /**
     * Returns a fresh {@link ServletInputStream} backed by the cached body bytes.
     * Each invocation returns a new stream positioned at the beginning, making
     * this method safe for multiple sequential readers.
     *
     * @return A readable {@link ServletInputStream} over the cached body
     */
    @Override
    public ServletInputStream getInputStream() {
        return new CachedBodyServletInputStream(this.cachedBody);
    }

    /**
     * Returns a fresh {@link BufferedReader} over the cached body bytes.
     *
     * @return A readable {@link BufferedReader} over the cached body
     */
    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream()));
    }

    /**
     * Returns a defensive copy of the cached body bytes.
     * Prefer this over {@link #getInputStream()} when you only need to inspect the
     * body without consuming a stream (e.g., in the rate-limit interceptor).
     *
     * @return A copy of the buffered request body bytes
     */
    public byte[] getCachedBody() {
        return cachedBody.clone();
    }

    /**
     * A {@link ServletInputStream} backed by a {@link ByteArrayInputStream}.
     * Every instance starts at position 0 of the supplied byte array, so repeated
     * calls to {@link CachedBodyHttpServletRequest#getInputStream()} are independently
     * readable from the beginning.
     */
    private static class CachedBodyServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream input;

        CachedBodyServletInputStream(byte[] cachedBody) {
            this.input = new ByteArrayInputStream(cachedBody);
        }

        @Override
        public int read() {
            return input.read();
        }

        @Override
        public boolean isFinished() {
            return input.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            // No-op: this wrapper is used in the synchronous (blocking) servlet API
            // path only. Async/NIO listeners are not applicable.
        }
    }
}
