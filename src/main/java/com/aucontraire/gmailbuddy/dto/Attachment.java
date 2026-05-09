package com.aucontraire.gmailbuddy.dto;

import com.aucontraire.gmailbuddy.validation.SafeFilename;
import com.aucontraire.gmailbuddy.validation.ValidBase64;
import com.aucontraire.gmailbuddy.validation.ValidMimeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Value type representing a single file attachment in a send or draft-create request.
 *
 * <p>Each field is required when an {@code Attachment} record is present in the
 * {@code attachments} list of {@link SendMessageDTO}. All three fields carry Bean
 * Validation annotations that are evaluated when the enclosing {@code SendMessageDTO}
 * is validated via {@code @Valid} at the controller layer.</p>
 *
 * <h2>Validation rules</h2>
 * <ul>
 *   <li>{@code filename} — required; max 255 UTF-8 characters; must not contain
 *       line-terminator characters (header-injection defence) or path-traversal
 *       sequences ({@code ..}, {@code /}, {@code \}, NUL byte).</li>
 *   <li>{@code mimeType} — required; must conform to the RFC 6838 {@code type/subtype}
 *       format. No content-type whitelist is enforced.</li>
 *   <li>{@code base64Data} — required; must be decodable by the JDK's standard
 *       (non-URL-safe) {@link java.util.Base64#getDecoder()}.</li>
 * </ul>
 *
 * <h2>Logging policy</h2>
 * <p>Per FR-019 and FR-020, the values of all three fields MUST NOT be logged at any
 * level. Only attachment count and MIME type lists are safe to log as diagnostic
 * metadata.</p>
 *
 * <h2>No compact constructor</h2>
 * <p>All three fields are required; no null normalization is needed. The absence of a
 * compact constructor is intentional — if any field is null, {@code @NotBlank} will
 * reject the request before the record is used.</p>
 */
@Schema(description = "A single file attachment encoded as standard Base64")
public record Attachment(

    @Schema(
        description = "Attachment display name, max 255 UTF-8 chars, no path separators (/, \\, ..), " +
                      "no line terminator characters. RFC 2047 encoding for non-ASCII characters is " +
                      "applied at MIME generation time.",
        example = "resume.pdf",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank
    @Size(max = 255)
    @SafeFilename
    String filename,

    @Schema(
        description = "RFC 6838 type/subtype (e.g. application/pdf, image/jpeg). Well-formedness is " +
                      "validated against the RFC 6838 token character set; no content-type whitelist is enforced.",
        example = "application/pdf",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank
    @ValidMimeType
    String mimeType,

    @Schema(
        description = "Standard Base64 encoding of the attachment binary content (not URL-safe Base64). " +
                      "The value is decoded server-side using java.util.Base64.getDecoder().",
        example = "JVBERi0xLjQK...",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank
    @ValidBase64
    String base64Data

) {}
