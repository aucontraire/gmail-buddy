package com.aucontraire.gmailbuddy.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Full draft response returned by {@code GET /api/v1/gmail/drafts/{id}} and
 * {@code PUT /api/v1/gmail/drafts/{id}} (FR-006, FR-017).
 *
 * <p>Both endpoints return this same record shape to allow callers to reuse
 * parsing code. The body field is always populated (echoing the full body
 * regardless of bodyType); attachment metadata excludes binary content per
 * FR-006 (binary download is out of scope, tracked as R-019).</p>
 *
 * <p>See data-model §4 for the canonical field list.</p>
 */
@Schema(description = "Full draft contents (no binary attachment data)")
public record DraftDetailResponse(
        @Schema(description = "Gmail draft identifier", example = "r-9876543210")
        String id,

        @Schema(description = "To recipient addresses; empty list if absent",
                example = "[\"hiring-manager@bigcorp.example\"]")
        List<String> to,

        @Schema(description = "CC recipient addresses; empty list if absent")
        List<String> cc,

        @Schema(description = "BCC recipient addresses; empty list if absent")
        List<String> bcc,

        @Schema(description = "Subject line; null if absent",
                example = "Following up — Senior Backend Engineer application")
        String subject,

        @Schema(description = "Decoded body text (HTML or plain per bodyType); null if not extractable")
        String body,

        @Schema(description = "Body content type", example = "html", allowableValues = {"html", "text"})
        String bodyType,

        @Schema(description = "Gmail thread ID; null if not a threaded draft", example = "1976a4bc3fe89d0c")
        String threadId,

        @Schema(description = "Original message ID this draft replies to; null if not a reply",
                example = "1976a4bc3fe89d0c")
        String inReplyToMessageId,

        @Schema(description = "Attachment metadata (filename, mimeType, sizeBytes); empty list if no attachments")
        List<AttachmentMetadata> attachments
) {}
