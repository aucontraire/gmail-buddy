package com.aucontraire.gmailbuddy.dto;

import com.aucontraire.gmailbuddy.validation.MaxBodySize;
import com.aucontraire.gmailbuddy.validation.NoHeaderInjection;
import com.aucontraire.gmailbuddy.validation.OptionalEmail;
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
    String bodyType

) {

    /**
     * Compact constructor that normalises null collections to empty immutable lists
     * and defaults {@code bodyType} to {@code "text"} when absent.
     *
     * <p>Defensive {@link List#copyOf} calls ensure the internal state cannot be
     * mutated through a reference the caller retained before passing the list in.</p>
     */
    public SendMessageDTO {
        to = to == null ? List.of() : List.copyOf(to);
        cc = cc == null ? List.of() : List.copyOf(cc);
        bcc = bcc == null ? List.of() : List.copyOf(bcc);
        bodyType = bodyType == null ? "text" : bodyType;
    }
}
