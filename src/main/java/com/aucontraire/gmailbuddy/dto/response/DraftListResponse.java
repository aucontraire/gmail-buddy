package com.aucontraire.gmailbuddy.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Pagination envelope returned by {@code GET /api/v1/gmail/drafts}.
 *
 * <p>Mirrors the field-naming convention of {@code MessageListResponse} per FR-003.
 * The {@code results} list is never null — the mapper uses {@code List.of()} for an
 * empty page. See data-model §2.</p>
 *
 * @param results       page of draft summaries; {@code List.of()} for an empty result
 * @param nextPageToken token for the next page; null when all results are exhausted
 * @param totalCount    {@code resultSizeEstimate} from the Gmail API; may be null
 */
@Schema(description = "Paginated list of draft summaries")
public record DraftListResponse(
        @Schema(description = "Page of draft summaries; empty list if no drafts found")
        List<DraftListItem> results,

        @Schema(description = "Token for the next page; null when all results are exhausted",
                example = "AKmmh...")
        String nextPageToken,

        @Schema(description = "Estimated total count from Gmail API; may be null", example = "42")
        Integer totalCount
) {}
