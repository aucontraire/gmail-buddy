package com.aucontraire.gmailbuddy.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Full thread content returned by {@code GET /api/v1/gmail/threads/{id}}.
 * Contains all nested messages in chronological ascending order (oldest first).
 * {@code labelIds} is the union of all per-message label sets.
 */
@Schema(description = "Full thread detail including all nested messages")
public record ThreadDetailResponse(
        @Schema(description = "Gmail thread ID",
                example = "1a2b3c4d5e6f7890")
        String threadId,

        @Schema(description = "Union of label IDs across all messages in the thread; empty list if no labels",
                example = "[\"INBOX\",\"CATEGORY_PERSONAL\",\"Label_42\"]")
        List<String> labelIds,

        @Schema(description = "All messages in the thread in chronological ascending order (oldest first); empty list for edge-case empty thread",
                example = "[]")
        List<MessageDetailResponse> messages
) {}
