package com.aucontraire.gmailbuddy.dto.response;

/**
 * Response DTO returned after a draft is staged successfully.
 *
 * <p>Returned by {@code POST /api/v1/gmail/drafts} (HTTP 201 Created).</p>
 *
 * <p>The {@code status} field is always {@code "DRAFT"} for this response type.
 * Use the {@link #drafted(String, String, String)} factory method to construct
 * instances so the sentinel value is set in one place and callers cannot
 * accidentally pass an incorrect status string.</p>
 *
 * <p>The {@code draftId} is the identifier passed to
 * {@code POST /api/v1/gmail/drafts/{draftId}/send} when the caller wants to
 * trigger programmatic delivery of the staged draft.</p>
 *
 * @param draftId   the Gmail-assigned draft identifier
 * @param messageId the Gmail-assigned underlying message identifier for the draft
 * @param threadId  the Gmail conversation thread this draft belongs to
 * @param status    always {@code "DRAFT"}
 */
public record DraftResponse(String draftId, String messageId, String threadId, String status) {

    /**
     * Factory method that creates a {@code DraftResponse} with {@code status}
     * fixed to {@code "DRAFT"}.
     *
     * @param draftId   the Gmail-assigned draft identifier
     * @param messageId the Gmail-assigned underlying message identifier
     * @param threadId  the Gmail conversation thread identifier
     * @return a new {@code DraftResponse} instance
     */
    public static DraftResponse drafted(String draftId, String messageId, String threadId) {
        return new DraftResponse(draftId, messageId, threadId, "DRAFT");
    }
}
