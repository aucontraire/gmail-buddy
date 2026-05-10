package com.aucontraire.gmailbuddy.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Per-item summary returned in {@code GET /api/v1/gmail/drafts} list responses.
 *
 * <p>Populated from {@code users.drafts.get(format=FULL)} per item after the
 * initial {@code users.drafts.list} call returns the draft IDs. List fields are
 * never null — the mapper uses {@code List.of()} as the fallback. See data-model §1.</p>
 *
 * @param id              Gmail draft identifier
 * @param to              recipient addresses; {@code List.of()} if absent
 * @param cc              CC addresses; {@code List.of()} if absent
 * @param bcc             BCC addresses; {@code List.of()} if absent
 * @param subject         subject line; null if the header is absent
 * @param snippet         Gmail-provided body preview (~100 chars)
 * @param threadId        Gmail thread ID; null if not a threaded draft
 * @param attachmentCount count of attachment parts; 0 if none
 */
@Schema(description = "Summary of a single draft in a list response")
public record DraftListItem(
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

        @Schema(description = "Gmail-provided body preview (~100 chars)",
                example = "Hi Sarah, I wanted to follow up on my application...")
        String snippet,

        @Schema(description = "Gmail thread ID; null if not a threaded draft", example = "1976a4bc3fe89d0c")
        String threadId,

        @Schema(description = "Count of attachment parts; 0 if no attachments", example = "1")
        int attachmentCount
) {}
