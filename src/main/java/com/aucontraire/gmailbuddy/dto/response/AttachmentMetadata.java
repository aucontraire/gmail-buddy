package com.aucontraire.gmailbuddy.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Attachment metadata returned in draft detail responses.
 *
 * <p>Contains filename, MIME type, and decoded byte size only — no binary
 * content. Binary download is out of scope for this feature (tracked as R-019).
 * See spec FR-006 and data-model §3.</p>
 *
 * @param filename  filename from {@code MessagePart.filename} or
 *                  {@code Content-Disposition} filename parameter
 * @param mimeType  MIME type from {@code MessagePart.mimeType}
 * @param sizeBytes decoded byte size estimate from {@code MessagePartBody.size}
 */
@Schema(description = "Attachment summary (no binary content)")
public record AttachmentMetadata(
        @Schema(description = "Original filename of the attachment", example = "resume-2026.pdf")
        String filename,

        @Schema(description = "MIME type of the attachment", example = "application/pdf")
        String mimeType,

        @Schema(description = "Decoded byte size estimate from Gmail API", example = "245760")
        long sizeBytes
) {}
