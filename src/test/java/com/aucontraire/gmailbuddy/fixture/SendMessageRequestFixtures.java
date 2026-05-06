package com.aucontraire.gmailbuddy.fixture;

import com.aucontraire.gmailbuddy.dto.SendMessageDTO;

import java.util.List;

/**
 * Static factory methods that produce {@link SendMessageDTO} instances for use
 * in unit and integration tests throughout the send-and-draft feature
 * (Phase 2 Foundational, T028).
 *
 * <h2>Design</h2>
 * <p>All methods are static. This class carries no Spring context
 * ({@code @Component} is intentionally absent) so it can be used from any test
 * layer — plain JUnit, {@code @WebMvcTest}, {@code @SpringBootTest} — without
 * needing a running application context.</p>
 *
 * <h2>Naming conventions</h2>
 * <p>Method names follow the pattern {@code valid*()} for DTOs that should pass
 * Bean Validation, and {@code invalid*()} for DTOs that are expected to fail at
 * least one constraint. This mirrors the convention used in other test helpers
 * in this project (e.g., {@code TestTokenProvider}, {@code TestOAuth2AuthorizedClientService})
 * where "configure*" / "with*" semantics make the intent obvious without
 * requiring a doc comment to decode.</p>
 *
 * <h2>Oversized body note</h2>
 * <p>{@link #invalidOversizedBody()} uses {@code String.repeat()} to
 * produce a string that exceeds the 10 MB limit. A base unit of
 * {@code "A".repeat(1024)} (1 KiB of ASCII — each character is exactly 1 UTF-8
 * byte) repeated 10 241 times yields 10 240 KiB = 10 MB + 1 KiB, safely above
 * the {@code gmail-buddy.send.max-body-size=10MB} ceiling without materialising
 * a huge string literal in source code. The JVM allocates this lazily via the
 * compact String implementation; actual heap cost is ~10 MB for the duration of
 * the test.</p>
 *
 * <p>Used by: T029–T032, T033–T036, T041, T044, T048–T051.</p>
 */
public final class SendMessageRequestFixtures {

    // Convenient shared constants for reuse across factory methods.
    private static final String VALID_SINGLE_RECIPIENT = "recruiter@example.com";
    private static final String VALID_SUBJECT = "Software Engineer – Application Follow-up";
    private static final String VALID_TEXT_BODY =
            "Hi there,\n\nI wanted to follow up on my application for the Software Engineer role.\n\nBest regards";
    private static final String VALID_HTML_BODY =
            "<p>Hi there,</p><p>I wanted to follow up on my application for the "
            + "<strong>Software Engineer</strong> role.</p><p>Best regards</p>";

    // Utility class — no instances.
    private SendMessageRequestFixtures() {
        throw new AssertionError(
                "SendMessageRequestFixtures is a static factory class and must not be instantiated");
    }

    // -------------------------------------------------------------------------
    // Valid fixtures
    // -------------------------------------------------------------------------

    /**
     * Returns a minimal, fully valid {@link SendMessageDTO} with a single primary
     * recipient, no cc, no bcc, a plain-text body, and {@code bodyType} defaulted
     * to {@code "text"}.
     *
     * <p>This is the baseline happy-path fixture used by most unit tests to
     * establish that the system processes a well-formed request correctly.</p>
     *
     * @return a valid single-recipient DTO
     */
    public static SendMessageDTO validSingleRecipient() {
        return new SendMessageDTO(
                List.of(VALID_SINGLE_RECIPIENT),
                null,   // compact constructor normalises to List.of()
                null,   // compact constructor normalises to List.of()
                VALID_SUBJECT,
                VALID_TEXT_BODY,
                null    // compact constructor defaults to "text"
        );
    }

    /**
     * Returns a valid {@link SendMessageDTO} with multiple primary recipients and
     * non-empty {@code cc} and {@code bcc} lists, simulating a real outreach blast.
     *
     * <p>Use this fixture for tests that must verify per-element validation across
     * all three recipient fields, and for tests that assert the MimeMessage
     * construction handles multi-address headers (comma-separated values) correctly.</p>
     *
     * @return a valid multi-recipient DTO with cc and bcc populated
     */
    public static SendMessageDTO validMultiRecipientWithCcAndBcc() {
        return new SendMessageDTO(
                List.of("alice@example.com", "bob@example.com"),
                List.of("cc-recipient@example.com"),
                List.of("bcc-recipient@example.com"),
                VALID_SUBJECT,
                VALID_TEXT_BODY,
                "text"
        );
    }

    /**
     * Returns a valid {@link SendMessageDTO} with an HTML body and
     * {@code bodyType} explicitly set to {@code "html"}.
     *
     * <p>Use this fixture for tests that exercise the HTML pass-through path:
     * the service must forward the HTML body verbatim to the Gmail API without
     * sanitization (spec Decision 7, FR-014). Also useful for
     * {@code MimeMessageBuilderTest} to assert {@code Content-Type: text/html}
     * is set on the resulting {@link jakarta.mail.internet.MimeMessage}.</p>
     *
     * @return a valid single-recipient DTO whose body is HTML
     */
    public static SendMessageDTO validHtmlBody() {
        return new SendMessageDTO(
                List.of(VALID_SINGLE_RECIPIENT),
                null,
                null,
                VALID_SUBJECT,
                VALID_HTML_BODY,
                "html"
        );
    }

    // -------------------------------------------------------------------------
    // Invalid fixtures — expected to fail Bean Validation
    // -------------------------------------------------------------------------

    /**
     * Returns a {@link SendMessageDTO} whose {@code subject} contains a CRLF
     * sequence ({@code \r\n}), which is a header-injection attempt per FR-015.
     *
     * <p>This fixture is used by {@code @NoHeaderInjection} validator tests
     * (T029) and by controller/validation-slice tests (T033) to assert that:</p>
     * <ol>
     *   <li>The request is rejected before any MimeMessage is constructed or any
     *       Gmail API call is made.</li>
     *   <li>The error response uses the problem type
     *       {@code /problems/header-injection-detected} — distinct from the
     *       generic {@code /problems/validation-error}.</li>
     * </ol>
     *
     * @return an otherwise-valid DTO whose subject line carries CRLF characters
     */
    public static SendMessageDTO invalidCrlfInSubject() {
        return new SendMessageDTO(
                List.of(VALID_SINGLE_RECIPIENT),
                null,
                null,
                "Legitimate Subject\r\nX-Injected-Header: malicious",
                VALID_TEXT_BODY,
                "text"
        );
    }

    /**
     * Returns a {@link SendMessageDTO} with an empty {@code to} list, which must
     * be rejected by {@code @NotEmpty} per FR-009.
     *
     * <p>The compact constructor receives an explicit empty list here (rather than
     * {@code null}) to exercise the {@code @NotEmpty} constraint directly rather
     * than the null-normalisation path. Tests asserting on this fixture should
     * expect an HTTP 400 response with a field error on {@code to}.</p>
     *
     * @return a DTO with no primary recipients
     */
    public static SendMessageDTO invalidEmptyToList() {
        return new SendMessageDTO(
                List.of(),   // @NotEmpty should reject this
                null,
                null,
                VALID_SUBJECT,
                VALID_TEXT_BODY,
                "text"
        );
    }

    /**
     * Returns a {@link SendMessageDTO} with a body that exceeds the 10 MB
     * {@code @MaxBodySize} limit per FR-013.
     *
     * <p>Implementation note: a base unit of {@code "A".repeat(1024)} (1 KiB of
     * single-byte ASCII characters) repeated 10 241 times yields 10 241 KiB,
     * which is exactly 1 KiB above the 10 MB ceiling. Each {@code 'A'} is a
     * single-byte UTF-8 sequence, so byte length equals character length —
     * making the byte arithmetic straightforward and the test deterministic
     * regardless of platform or locale.</p>
     *
     * @return a DTO whose body UTF-8 byte length exceeds 10 MB
     */
    public static SendMessageDTO invalidOversizedBody() {
        // 1 KiB unit (1 024 ASCII bytes, each 1 UTF-8 byte) × 10 241 = 10 484 736 bytes
        // > 10 MB (10 485 760 bytes) — reliably above the limit.
        String oversizedBody = "A".repeat(1024).repeat(10_241);
        return new SendMessageDTO(
                List.of(VALID_SINGLE_RECIPIENT),
                null,
                null,
                VALID_SUBJECT,
                oversizedBody,
                "text"
        );
    }
}
