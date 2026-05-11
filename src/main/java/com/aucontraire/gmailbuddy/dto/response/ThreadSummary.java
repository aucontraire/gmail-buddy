package com.aucontraire.gmailbuddy.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Per-thread summary returned in {@code GET /api/v1/gmail/threads} list responses.
 * Fields correspond exactly to what Gmail's {@code users.threads.list} stub provides
 * — no per-item enrichment (Clarifications Q1 in spec.md).
 */
@Schema(description = "Thread summary returned in list responses")
public record ThreadSummary(
        @Schema(description = "Gmail thread ID (hex format, same as message ID)",
                example = "1a2b3c4d5e6f7890")
        String id,

        @Schema(description = "Preview from the most recent message; Gmail-controlled truncation (~100 chars); null if stub has no snippet",
                example = "Hi, I wanted to follow up regarding the Backend Engineer position...")
        String snippet,

        @Schema(description = "Gmail history ID for this thread; null if absent from the stub",
                example = "987654")
        String historyId
) {}
