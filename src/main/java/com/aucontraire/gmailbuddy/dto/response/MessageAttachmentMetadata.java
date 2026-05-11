package com.aucontraire.gmailbuddy.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Attachment metadata with download identifier, returned in
 * {@code GET /api/v1/gmail/messages/{messageId}/attachments} responses and as
 * the element type inside {@link MessageDetailResponse#attachments}.
 *
 * <p>This is a new independent record — it does not extend
 * {@link AttachmentMetadata} (used by {@link DraftDetailResponse}) because
 * Java records are final and the receive-side metadata needs an additional
 * {@code attachmentId} field for the download endpoint. See data-model §5.</p>
 *
 * @param attachmentId Gmail opaque attachment ID; used as the
 *                     {@code {attachmentId}} path variable on the download endpoint
 * @param filename     Original filename from {@code MessagePart.filename};
 *                     {@code "unnamed"} if absent (never null)
 * @param mimeType     MIME type from {@code MessagePart.mimeType}
 * @param sizeBytes    Decoded byte size estimate from {@code MessagePartBody.size}
 */
@Schema(description = "Attachment metadata with download identifier (no binary content)")
public record MessageAttachmentMetadata(
        @Schema(description = "Gmail opaque attachment identifier",
                example = "ANGjdJ8BwFpn3nQ0oFQ7wPjVLfRx")
        String attachmentId,

        @Schema(description = "Original filename of the attachment", example = "job-description.pdf")
        String filename,

        @Schema(description = "MIME type of the attachment", example = "application/pdf")
        String mimeType,

        @Schema(description = "Decoded byte size estimate from Gmail API", example = "245760")
        long sizeBytes
) {}
