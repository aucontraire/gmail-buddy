package com.aucontraire.gmailbuddy.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Pagination envelope for {@code GET /api/v1/gmail/threads}.
 * Mirrors the field-naming convention of the existing {@code MessageListResponse}.
 */
@Schema(description = "Paginated list of thread summaries")
public record ThreadListResponse(
        @Schema(description = "Page of thread summaries; empty list when no results match",
                example = "[]")
        List<ThreadSummary> results,

        @Schema(description = "Token for the next page; null when all results are exhausted",
                example = "eyJwYWdlVG9rZW4i...")
        String nextPageToken,

        @Schema(description = "resultSizeEstimate from Gmail API; may be absent",
                example = "14")
        Integer totalCount
) {}
