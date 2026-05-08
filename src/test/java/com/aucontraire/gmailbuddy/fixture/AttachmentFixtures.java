package com.aucontraire.gmailbuddy.fixture;

import com.aucontraire.gmailbuddy.dto.Attachment;

import java.util.List;

/**
 * Static factory methods that produce {@link Attachment} instances for use in unit and
 * integration tests throughout the threading-and-attachments feature (Phase 2 Wave 2,
 * T019).
 *
 * <h2>Design</h2>
 * <p>All methods are static. This class carries no Spring context
 * ({@code @Component} is intentionally absent) so it can be used from any test layer —
 * plain JUnit, {@code @WebMvcTest}, {@code @SpringBootTest} — without needing a running
 * application context.</p>
 *
 * <h2>Naming conventions</h2>
 * <p>Method names follow the pattern {@code valid*()} for attachments that should pass
 * Bean Validation and {@code invalid*()} for attachments expected to fail at least one
 * constraint. This mirrors the convention used in {@link SendMessageRequestFixtures}.</p>
 *
 * <h2>Base64 note</h2>
 * <p>{@code JVBERi0xLjQK} is the standard Base64 encoding of {@code %PDF-1.4\n} — a
 * minimal valid PDF header prefix. It is a real, decodable Base64 string that the JDK's
 * {@code Base64.getDecoder()} accepts without throwing {@link IllegalArgumentException}.
 * It is short enough to keep fixture methods readable without materialising large heap
 * allocations in test source.</p>
 */
public final class AttachmentFixtures {

    // Reusable constants — kept package-accessible so companion tests can reference them.
    static final String VALID_PDF_BASE64 = "JVBERi0xLjQK";   // %PDF-1.4\n
    static final String VALID_PNG_BASE64 = "iVBORw0KGgo=";   // PNG IDAT header bytes
    static final String VALID_TXT_BASE64 = "SGVsbG8gV29ybGQ="; // "Hello World"

    // Utility class — no instances.
    private AttachmentFixtures() {
        throw new AssertionError(
                "AttachmentFixtures is a static factory class and must not be instantiated");
    }

    // -------------------------------------------------------------------------
    // Valid fixtures — expected to pass all Bean Validation constraints
    // -------------------------------------------------------------------------

    /**
     * Returns a single, fully valid {@link Attachment} representing a PDF file.
     *
     * <p>All three fields carry values that satisfy every constraint on
     * {@link Attachment}: {@code @NotBlank}, {@code @Size(max=255)},
     * {@code @SafeFilename}, {@code @ValidMimeType}, and {@code @ValidBase64}.</p>
     *
     * @return a valid PDF attachment
     */
    public static Attachment validSinglePdf() {
        return new Attachment(
                "resume.pdf",
                "application/pdf",
                VALID_PDF_BASE64
        );
    }

    /**
     * Returns a list of two valid {@link Attachment} instances covering different MIME
     * types (PDF and plain-text), suitable for multipart attachment tests.
     *
     * @return an immutable list of two valid attachments
     */
    public static List<Attachment> validMultiAttachmentList() {
        return List.of(
                new Attachment("resume.pdf", "application/pdf", VALID_PDF_BASE64),
                new Attachment("cover-letter.txt", "text/plain", VALID_TXT_BASE64)
        );
    }

    // -------------------------------------------------------------------------
    // Invalid fixtures — expected to fail at least one Bean Validation constraint
    // -------------------------------------------------------------------------

    /**
     * Returns an {@link Attachment} whose {@code filename} contains the path-traversal
     * sequence {@code ../../etc/passwd}.
     *
     * <p>The {@code @SafeFilename} constraint must reject this value because it
     * contains {@code /} (forward slash) and {@code ..} (double-dot) sequences.</p>
     *
     * @return an attachment with a path-traversal filename
     */
    public static Attachment invalidPathTraversalFilename() {
        return new Attachment(
                "../../etc/passwd",
                "application/pdf",
                VALID_PDF_BASE64
        );
    }

    /**
     * Returns an {@link Attachment} whose {@code filename} contains a CRLF sequence,
     * which is a MIME header-injection attempt.
     *
     * <p>The {@code @SafeFilename} constraint must reject this value because it
     * contains a line-terminator character (U+000D CARRIAGE RETURN and U+000A LINE FEED).
     * If injected verbatim into a {@code Content-Disposition: attachment; filename="..."}
     * header, the CRLF would allow the attacker to craft additional MIME headers.</p>
     *
     * @return an attachment with a header-injection filename
     */
    public static Attachment invalidHeaderInjectionFilename() {
        return new Attachment(
                "innocent.pdf\r\nContent-Type: text/html",
                "application/pdf",
                VALID_PDF_BASE64
        );
    }

    /**
     * Returns an {@link Attachment} whose {@code base64Data} contains characters that
     * are not in the standard Base64 alphabet ({@code A-Z}, {@code a-z}, {@code 0-9},
     * {@code +}, {@code /}, {@code =}).
     *
     * <p>The {@code @ValidBase64} constraint must reject this value because
     * {@code Base64.getDecoder().decode("not-valid-base64!!!")} throws
     * {@link IllegalArgumentException} (the {@code -} and {@code !} characters are
     * not in the standard alphabet).</p>
     *
     * @return an attachment with invalid Base64 data
     */
    public static Attachment invalidBase64() {
        return new Attachment(
                "resume.pdf",
                "application/pdf",
                "not-valid-base64!!!"
        );
    }

    /**
     * Returns an {@link Attachment} whose {@code mimeType} does not conform to the
     * RFC 6838 {@code type/subtype} format.
     *
     * <p>The {@code @ValidMimeType} constraint must reject this value because
     * {@code "application"} lacks the mandatory forward-slash separator, so the
     * regex {@code ^[a-zA-Z0-9!#$&+\-.^_`{|}~]+/[a-zA-Z0-9!#$&+\-.^_`{|}~]+$} does
     * not match.</p>
     *
     * @return an attachment with a malformed MIME type
     */
    public static Attachment invalidMalformedMimeType() {
        return new Attachment(
                "resume.pdf",
                "application",   // missing /subtype
                VALID_PDF_BASE64
        );
    }
}
