package com.aucontraire.gmailbuddy.config;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CachedBodyHttpServletRequest}.
 *
 * <p>Covers T057 coverage gap: the {@link CachedBodyHttpServletRequest#getInputStream()},
 * {@link CachedBodyHttpServletRequest#getReader()}, {@link CachedBodyHttpServletRequest#getCachedBody()},
 * and the inner {@code CachedBodyServletInputStream} methods
 * ({@code isFinished()}, {@code isReady()}, {@code setReadListener()}).</p>
 *
 * <p>Uses a real {@link jakarta.servlet.http.HttpServletRequest} mock backed by a
 * {@link java.io.ByteArrayInputStream} to simulate the original request body.</p>
 */
@DisplayName("CachedBodyHttpServletRequest — coverage gaps (T057)")
class CachedBodyHttpServletRequestTest {

    private static final String BODY_JSON = "{\"to\":\"test@example.com\",\"subject\":\"Hello\",\"body\":\"Hi\"}";

    /**
     * Builds a minimal mock {@link HttpServletRequest} whose {@code getInputStream()} returns
     * the supplied body bytes as a {@link jakarta.servlet.ServletInputStream}.
     */
    private HttpServletRequest mockRequestWithBody(byte[] bodyBytes) throws IOException {
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        jakarta.servlet.ServletInputStream servletInputStream = new jakarta.servlet.ServletInputStream() {
            private final java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(bodyBytes);

            @Override public int read() { return bais.read(); }
            @Override public boolean isFinished() { return bais.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(jakarta.servlet.ReadListener listener) {}
        };
        when(mockRequest.getInputStream()).thenReturn(servletInputStream);
        return mockRequest;
    }

    // -------------------------------------------------------------------------
    // getInputStream() — T057 coverage gap line 65
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getInputStream returns a readable stream yielding the cached body bytes")
    void getInputStream_returnsCachedBodyAsReadableStream() throws IOException {
        // Arrange
        byte[] bodyBytes = BODY_JSON.getBytes(StandardCharsets.UTF_8);
        CachedBodyHttpServletRequest wrapper = new CachedBodyHttpServletRequest(mockRequestWithBody(bodyBytes));

        // Act: read via getInputStream()
        ServletInputStream inputStream = wrapper.getInputStream();
        byte[] read = inputStream.readAllBytes();

        // Assert: full body is readable
        assertThat(read).isEqualTo(bodyBytes);
    }

    @Test
    @DisplayName("getInputStream is idempotent — successive calls each return a full stream")
    void getInputStream_multipleCallsEachReturnFullStream() throws IOException {
        // Arrange
        byte[] bodyBytes = BODY_JSON.getBytes(StandardCharsets.UTF_8);
        CachedBodyHttpServletRequest wrapper = new CachedBodyHttpServletRequest(mockRequestWithBody(bodyBytes));

        // Act: read twice
        byte[] firstRead = wrapper.getInputStream().readAllBytes();
        byte[] secondRead = wrapper.getInputStream().readAllBytes();

        // Assert: both reads return identical content
        assertThat(firstRead).isEqualTo(bodyBytes);
        assertThat(secondRead).isEqualTo(bodyBytes);
    }

    // -------------------------------------------------------------------------
    // CachedBodyServletInputStream.isFinished() — T057 coverage gap line 110
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("isFinished returns false before reading and true after all bytes consumed")
    void servletInputStream_isFinished_falseBeforeRead_trueAfterRead() throws IOException {
        // Arrange
        byte[] bodyBytes = "hello".getBytes(StandardCharsets.UTF_8);
        CachedBodyHttpServletRequest wrapper = new CachedBodyHttpServletRequest(mockRequestWithBody(bodyBytes));
        ServletInputStream stream = wrapper.getInputStream();

        // Assert: not finished before any read
        assertThat(stream.isFinished()).isFalse();

        // Act: consume all bytes
        stream.readAllBytes();

        // Assert: finished after full read
        assertThat(stream.isFinished()).isTrue();
    }

    @Test
    @DisplayName("isFinished returns true immediately for empty body")
    void servletInputStream_isFinished_trueForEmptyBody() throws IOException {
        // Arrange
        CachedBodyHttpServletRequest wrapper = new CachedBodyHttpServletRequest(mockRequestWithBody(new byte[0]));
        ServletInputStream stream = wrapper.getInputStream();

        // Assert: empty body is already finished
        assertThat(stream.isFinished()).isTrue();
    }

    // -------------------------------------------------------------------------
    // CachedBodyServletInputStream.isReady() — T057 coverage gap line 115
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("isReady always returns true (synchronous blocking servlet path)")
    void servletInputStream_isReady_alwaysTrue() throws IOException {
        // Arrange
        byte[] bodyBytes = BODY_JSON.getBytes(StandardCharsets.UTF_8);
        CachedBodyHttpServletRequest wrapper = new CachedBodyHttpServletRequest(mockRequestWithBody(bodyBytes));
        ServletInputStream stream = wrapper.getInputStream();

        // Assert: always ready before reading
        assertThat(stream.isReady()).isTrue();

        // Act: consume all bytes
        stream.readAllBytes();

        // Assert: still ready after reading (synchronous path, no async I/O)
        assertThat(stream.isReady()).isTrue();
    }

    // -------------------------------------------------------------------------
    // setReadListener — no-op (not a coverage gap but verifies no exception)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("setReadListener is a no-op and does not throw")
    void servletInputStream_setReadListener_noOp() throws IOException {
        // Arrange
        CachedBodyHttpServletRequest wrapper = new CachedBodyHttpServletRequest(
                mockRequestWithBody(BODY_JSON.getBytes(StandardCharsets.UTF_8)));
        ServletInputStream stream = wrapper.getInputStream();

        // Act & Assert: setReadListener must not throw
        stream.setReadListener(null);
    }

    // -------------------------------------------------------------------------
    // getReader() and getCachedBody() — additional coverage
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getReader returns a BufferedReader over the cached body")
    void getReader_returnsCachedBodyViaReader() throws IOException {
        // Arrange
        String line = "test line content";
        CachedBodyHttpServletRequest wrapper = new CachedBodyHttpServletRequest(
                mockRequestWithBody(line.getBytes(StandardCharsets.UTF_8)));

        // Act
        String readLine = wrapper.getReader().readLine();

        // Assert
        assertThat(readLine).isEqualTo(line);
    }

    @Test
    @DisplayName("getCachedBody returns a defensive copy of the body bytes")
    void getCachedBody_returnsDefensiveCopy() throws IOException {
        // Arrange
        byte[] original = BODY_JSON.getBytes(StandardCharsets.UTF_8);
        CachedBodyHttpServletRequest wrapper = new CachedBodyHttpServletRequest(mockRequestWithBody(original));

        // Act
        byte[] copy1 = wrapper.getCachedBody();
        byte[] copy2 = wrapper.getCachedBody();

        // Assert: content matches original and copies are independent references
        assertThat(copy1).isEqualTo(original);
        assertThat(copy2).isEqualTo(original);
        assertThat(copy1).isNotSameAs(copy2); // defensive copies, not the same array
    }

    @Test
    @DisplayName("getCachedBody returns empty array for a request with no body")
    void getCachedBody_emptyBody_returnsEmptyArray() throws IOException {
        // Arrange
        CachedBodyHttpServletRequest wrapper = new CachedBodyHttpServletRequest(mockRequestWithBody(new byte[0]));

        // Act & Assert
        assertThat(wrapper.getCachedBody()).isEmpty();
    }
}
