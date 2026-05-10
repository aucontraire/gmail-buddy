package com.aucontraire.gmailbuddy.service;

import java.util.List;

/**
 * Internal domain record returned by {@code GmailRepository.listDrafts(...)}
 * holding a page of enriched draft results plus pagination state.
 *
 * <p>Each {@link DraftDetailResult} in {@code drafts} represents a single draft
 * fully populated via {@code users.drafts.get(format=FULL)}. The repository
 * performs the per-item enrichment before returning the list, so the
 * service/controller layer does not need to make additional Gmail API calls.</p>
 *
 * <p>Mirrors the field-naming convention of {@link MessageListResult} (the
 * older non-record equivalent for messages) and is the domain analog of
 * {@link com.aucontraire.gmailbuddy.dto.response.DraftListResponse}.</p>
 *
 * @param drafts        per-item enriched draft results; {@code List.of()} for empty page
 * @param nextPageToken token for the next page, or {@code null} when exhausted
 * @param totalCount    {@code resultSizeEstimate} from Gmail API; may be {@code null}
 */
public record DraftListResult(
        List<DraftDetailResult> drafts,
        String nextPageToken,
        Integer totalCount
) {}
