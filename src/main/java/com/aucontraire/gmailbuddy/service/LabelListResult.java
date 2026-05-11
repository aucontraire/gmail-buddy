package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.dto.response.LabelSummary;

import java.util.List;

/**
 * Internal domain record holding a full list of Gmail label summaries.
 *
 * <p>Returned by {@code GmailRepository.listLabels(...)}. Uses
 * {@link LabelSummary} directly as the element type — the summary fields
 * ({@code id}, {@code name}, {@code type}, {@code messageListVisibility},
 * {@code labelListVisibility}) are domain-clean (no Gmail SDK types), following
 * the same reasoning as {@code ThreadListResult} using {@code ThreadSummary}
 * per data-model §18.</p>
 *
 * <p>Gmail's {@code users.labels.list} does not paginate, so there is no
 * {@code nextPageToken} field here.</p>
 *
 * @param labels     All visible labels; {@code List.of()} if the user has no labels
 * @param totalCount {@code labels.size()} — always computed from the list, never null
 */
public record LabelListResult(
        List<LabelSummary> labels,
        int totalCount
) {}
