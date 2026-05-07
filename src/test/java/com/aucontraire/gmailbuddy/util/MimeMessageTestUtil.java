package com.aucontraire.gmailbuddy.util;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Properties;

/**
 * Test utility for decoding a base64url-encoded raw Gmail message payload back
 * into a {@link MimeMessage} for structural assertion in unit tests.
 *
 * <p>Gmail's API returns raw MIME content encoded as base64url in
 * {@code Message.getRaw()}. Tests that verify MimeMessage construction (e.g.,
 * {@code MimeMessageBuilderTest}, repository round-trip tests) need a way to
 * reconstruct the {@link MimeMessage} from that wire encoding so they can assert
 * on individual headers and the body rather than on the opaque encoded string.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Given a Gmail Message whose raw field was set by MimeMessageBuilder:
 * String raw = gmailMessage.getRaw(); // base64url-encoded MIME bytes
 * MimeMessage mime = MimeMessageTestUtil.fromBase64Url(raw);
 * assertThat(MimeMessageTestUtil.getHeader(mime, "Subject")).isEqualTo("Hello");
 * }</pre>
 *
 * <h2>Design notes</h2>
 * <ul>
 *   <li>Uses {@code Base64.getUrlDecoder()} (not the standard decoder) because
 *       Gmail's API uses base64url encoding as defined by RFC 4648 §5.</li>
 *   <li>Constructs a no-op {@link Session} (no transport properties needed) so
 *       there is no dependency on any mail server or SMTP configuration.</li>
 *   <li>This class is intentionally NOT a Spring {@code @Component}; it is a
 *       pure-static utility for test code only.</li>
 * </ul>
 *
 * <p>Used by: T031 ({@code MimeMessageBuilderTest}), T034
 * ({@code GmailRepositoryImplCreateDraftTest}), T037
 * ({@code CreateDraftIntegrationTest}), T038, T041, T044, T048.</p>
 */
public final class MimeMessageTestUtil {

    // Utility class — no instances.
    private MimeMessageTestUtil() {
        throw new AssertionError("MimeMessageTestUtil is a static utility class and must not be instantiated");
    }

    /**
     * Decodes a base64url-encoded raw Gmail message string into a
     * {@link MimeMessage}.
     *
     * <p>This is the inverse of the encoding step performed by
     * {@code MimeMessageBuilder}: serialise {@link MimeMessage} bytes →
     * base64url-encode → store in {@code Message.setRaw(...)}. This method
     * reverses that operation so tests can assert on individual MIME
     * headers and body parts.</p>
     *
     * @param base64UrlRaw the base64url-encoded raw MIME bytes as returned by
     *                     {@code com.google.api.services.gmail.model.Message#getRaw()}
     * @return a fully parsed {@link MimeMessage}
     * @throws MessagingException if the decoded bytes do not represent valid MIME
     * @throws IOException        if the byte-array stream cannot be read (should never
     *                            happen in practice but declared to keep callers honest)
     * @throws IllegalArgumentException if {@code base64UrlRaw} is {@code null} or blank
     */
    public static MimeMessage fromBase64Url(String base64UrlRaw)
            throws MessagingException, IOException {

        if (base64UrlRaw == null || base64UrlRaw.isBlank()) {
            throw new IllegalArgumentException(
                    "base64UrlRaw must not be null or blank — was the raw payload actually set?");
        }

        byte[] mimeBytes = Base64.getUrlDecoder().decode(base64UrlRaw);
        InputStream in = new ByteArrayInputStream(mimeBytes);

        // A no-properties Session is sufficient for parsing an already-encoded
        // MIME message; no SMTP transport is involved.
        Session noOpSession = Session.getInstance(new Properties());
        return new MimeMessage(noOpSession, in);
    }

    /**
     * Returns the first value of a named MIME header from a decoded
     * {@link MimeMessage}, or {@code null} if the header is absent.
     *
     * <p>Header names are case-insensitive per RFC 5322; the underlying
     * {@link MimeMessage#getHeader(String)} implementation handles this.</p>
     *
     * <p>Typical use: assert the {@code Subject} header value, or the
     * {@code Content-Type} of the message body:</p>
     *
     * <pre>{@code
     * assertThat(MimeMessageTestUtil.getHeader(mime, "Subject"))
     *     .isEqualTo("Hello, world");
     * assertThat(MimeMessageTestUtil.getHeader(mime, "Content-Type"))
     *     .startsWith("text/plain");
     * }</pre>
     *
     * @param message    the {@link MimeMessage} to query
     * @param headerName the RFC 5322 header name (e.g., {@code "Subject"},
     *                   {@code "To"}, {@code "Content-Type"})
     * @return the first value of the header, or {@code null} if not present
     * @throws MessagingException if the underlying MIME parsing fails
     * @throws IllegalArgumentException if {@code message} is {@code null}
     */
    public static String getHeader(MimeMessage message, String headerName)
            throws MessagingException {

        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }

        String[] values = message.getHeader(headerName);
        return (values != null && values.length > 0) ? values[0] : null;
    }
}
