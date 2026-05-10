package com.aucontraire.gmailbuddy.dto;

import com.aucontraire.gmailbuddy.validation.GmailMessageId;
import com.aucontraire.gmailbuddy.validation.MaxBodySize;
import com.aucontraire.gmailbuddy.validation.NoHeaderInjection;
import com.aucontraire.gmailbuddy.validation.OptionalEmail;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request DTO for both the direct-send ({@code POST /api/v1/gmail/messages}) and
 * draft-creation ({@code POST /api/v1/gmail/drafts}) endpoints.
 *
 * <p>The same payload shape serves both endpoints; the difference is which Gmail
 * API method the service layer invokes. See spec.md § User Story 1 and § User
 * Story 3 for the rationale.</p>
 *
 * <h2>Validation rules</h2>
 * <ul>
 *   <li>{@code to} — required, 1–500 elements, each element must be a valid
 *       (non-blank) email address that contains no CRLF characters (FR-009,
 *       FR-010, FR-011, FR-015).</li>
 *   <li>{@code cc}, {@code bcc} — optional, default {@code []}, max 500 elements;
 *       each element is validated the same way as {@code to} elements (FR-010,
 *       FR-015).</li>
 *   <li>{@code subject} — required, max 998 characters (RFC 5322 single-header
 *       limit), no CRLF characters (FR-012, FR-015).</li>
 *   <li>{@code body} — required, UTF-8 byte length must not exceed
 *       {@code gmail-buddy.send.max-body-size} (FR-013).</li>
 *   <li>{@code bodyType} — optional, defaults to {@code "text"}, must be either
 *       {@code "text"} or {@code "html"} when present (FR-014).</li>
 *   <li>{@code threadId} — optional, Gmail thread ID to pin this message to a thread.
 *       When non-null, must be 1–32 hexadecimal characters. The {@code @Pattern}
 *       regex rejects all non-hex characters including CR/LF and Unicode line
 *       terminators (FR-001a, FR-001b).</li>
 *   <li>{@code inReplyToMessageId} — optional, Gmail short message ID of the message
 *       being replied to. Triggers a {@code users.messages.get} metadata lookup that
 *       populates the {@code In-Reply-To} and {@code References} MIME headers. When
 *       non-null, must be 1–32 hexadecimal characters (FR-003, FR-004).</li>
 *   <li>{@code attachments} — optional, default {@code []}. Null is normalized to an
 *       empty immutable list by the compact constructor. Each {@link Attachment} element
 *       is validated via {@code @Valid} cascade (FR-011, FR-012, FR-013).</li>
 * </ul>
 *
 * <h2>Compact constructor</h2>
 * <p>Null collections are normalized to empty immutable lists so callers never
 * receive a {@code null} collection reference from a deserialized payload.
 * {@code bodyType} defaults to {@code "text"} when omitted.</p>
 */
public record SendMessageDTO(

    @NotEmpty
    @Size(min = 1, max = 500)
    List<@NotBlank @OptionalEmail @NoHeaderInjection String> to,

    @Size(max = 500)
    List<@NotBlank @OptionalEmail @NoHeaderInjection String> cc,

    @Size(max = 500)
    List<@NotBlank @OptionalEmail @NoHeaderInjection String> bcc,

    @NotBlank
    @Size(max = 998)
    @NoHeaderInjection
    String subject,

    @NotBlank
    @MaxBodySize
    String body,

    @Pattern(regexp = "^(text|html)$")
    String bodyType,

    /**
     * Optional Gmail thread ID. When non-null, this message is placed into the
     * specified thread. Accepts only 1–32 hexadecimal characters.
     *
     * <p>If {@code inReplyToMessageId} is also supplied and the fetched original message's
     * thread ID differs from this value, the fetched thread ID wins (FR-006). The
     * {@code @Pattern} regex rejects all non-hex characters, including CR/LF and Unicode
     * line terminators, satisfying both the header-injection defence (FR-001a) and the
     * format requirement (FR-001b) — no separate {@code @NoHeaderInjection} annotation
     * is needed.</p>
     */
    @Schema(
        description = "Optional Gmail short thread ID (16-32 hex chars). When supplied, the outgoing message is " +
                      "placed in this thread. If inReplyToMessageId is also supplied and references a message in " +
                      "a different thread, the original message's threadId wins.",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED,
        nullable = true,
        example = "1976a4bc3fe89d0c"
    )
    @GmailMessageId
    String threadId,

    /**
     * Optional Gmail short message ID of the message being replied to. When non-null,
     * triggers a {@code users.messages.get} metadata lookup (quota: ~5 units) to extract
     * the RFC 5322 {@code Message-ID} header. That header is then set as the
     * {@code In-Reply-To} and {@code References} headers on the outgoing MIME message,
     * and the resolved thread ID is used to set {@code Message.setThreadId(...)}.
     *
     * <p>Accepts only 1–32 hexadecimal characters (same format as {@code threadId}).
     * If the specified message is not found or not accessible, the service throws
     * {@link com.aucontraire.gmailbuddy.exception.OriginalMessageNotFoundException}
     * (HTTP 422).</p>
     */
    @Schema(
        description = "Optional Gmail short message ID (16-32 hex chars). When supplied, gmail-buddy fetches the " +
                      "original message and constructs In-Reply-To and References headers for proper RFC 5322 " +
                      "threading. Returns 422 if the message doesn't exist.",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED,
        nullable = true,
        example = "1976a4bc3fe89d0c"
    )
    @GmailMessageId
    String inReplyToMessageId,

    /**
     * Optional list of file attachments. Null is normalized to an empty immutable list
     * by the compact constructor; an empty list is treated identically to absent (FR-015).
     * Each element is validated via {@code @Valid} cascade using the constraints declared
     * on {@link Attachment} fields.
     */
    @Schema(
        description = "Optional list of base64-encoded attachments. When non-empty, the message is built as " +
                      "multipart/mixed; total payload (body + attachments) capped at 25 MB.",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED,
        nullable = true
    )
    @Valid
    List<Attachment> attachments

) {

    /**
     * Compact constructor that normalises null collections to empty immutable lists,
     * defaults {@code bodyType} to {@code "text"} when absent, and normalises a null
     * {@code attachments} list to {@link List#of()}.
     *
     * <p>Defensive {@link List#copyOf} calls ensure the internal state cannot be
     * mutated through a reference the caller retained before passing the list in.</p>
     */
    public SendMessageDTO {
        to = to == null ? List.of() : List.copyOf(to);
        cc = cc == null ? List.of() : List.copyOf(cc);
        bcc = bcc == null ? List.of() : List.copyOf(bcc);
        bodyType = bodyType == null ? "text" : bodyType;
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}
