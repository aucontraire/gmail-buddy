package com.aucontraire.gmailbuddy.dto.response;

/**
 * Response DTO returned after a message is delivered successfully.
 *
 * <p>Returned by both:</p>
 * <ul>
 *   <li>{@code POST /api/v1/gmail/messages} — direct send (HTTP 201 Created)</li>
 *   <li>{@code POST /api/v1/gmail/drafts/{draftId}/send} — programmatic draft send
 *       (HTTP 200 OK)</li>
 * </ul>
 *
 * <p>The {@code status} field is always {@code "SENT"} for this response type.
 * Use the {@link #sent(String, String)} factory method to construct instances so
 * the sentinel value is set in one place and callers cannot accidentally pass an
 * incorrect status string.</p>
 *
 * @param messageId the Gmail-assigned identifier for the delivered message
 * @param threadId  the Gmail conversation thread this message belongs to
 * @param status    always {@code "SENT"}
 */
public record SendMessageResponse(String messageId, String threadId, String status) {

    /**
     * Factory method that creates a {@code SendMessageResponse} with {@code status}
     * fixed to {@code "SENT"}.
     *
     * @param messageId the Gmail-assigned message identifier
     * @param threadId  the Gmail conversation thread identifier
     * @return a new {@code SendMessageResponse} instance
     */
    public static SendMessageResponse sent(String messageId, String threadId) {
        return new SendMessageResponse(messageId, threadId, "SENT");
    }
}
