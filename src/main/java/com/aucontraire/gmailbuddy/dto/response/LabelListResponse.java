package com.aucontraire.gmailbuddy.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Single-page label list envelope for {@code GET /api/v1/gmail/labels}.
 *
 * <p>Gmail's {@code users.labels.list} does not paginate — all visible labels
 * for the authenticated user are returned in a single response. The absence of a
 * {@code nextPageToken} field signals to TJS that pagination is not possible here
 * (compare with {@code ThreadListResponse} which does include pagination fields).</p>
 *
 * @param results    All visible labels for the authenticated user; {@code List.of()} for a user with no labels
 * @param totalCount {@code results.size()} — always present, never absent
 */
@Schema(description = "Non-paginated label list response")
public record LabelListResponse(
        @Schema(description = "All visible labels (system + user-created) for the authenticated user")
        List<LabelSummary> results,

        @Schema(description = "Total number of labels returned", example = "28")
        int totalCount
) {}
