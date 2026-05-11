package com.aucontraire.gmailbuddy.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response envelope for {@code GET /api/v1/gmail/messages/{messageId}/attachments}.
 *
 * <p>Returns a single-page list of attachment metadata items for the specified message.
 * When the message has no attachments, {@code results} is an empty list and
 * {@code totalCount} is 0 — never HTTP 404 (per FR-024).</p>
 *
 * @param results    list of attachment metadata items; never null; empty when message has no attachments
 * @param totalCount total number of attachments on this message (equals results.size())
 */
@Schema(description = "Response envelope for listing attachments on a message")
public record AttachmentListResponse(
        @Schema(description = "List of attachment metadata items; empty when message has no attachments")
        List<MessageAttachmentMetadata> results,

        @Schema(description = "Total number of attachments on this message", example = "2")
        int totalCount
) {}
