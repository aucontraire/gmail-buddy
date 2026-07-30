package com.aucontraire.gmailbuddy.dto;

import com.aucontraire.gmailbuddy.validation.GmailLabelId;
import com.aucontraire.gmailbuddy.validation.GmailMessageId;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Request DTO for the feature-005 US2 batch-by-id label-modify endpoint
 * ({@code POST /messages/batchModifyLabels}).
 *
 * <p>{@code messageIds} MUST be non-empty; each element MUST be a valid Gmail
 * short hex identifier ({@link GmailMessageId}). {@code labelIdsToAdd} and
 * {@code labelIdsToRemove} are raw Gmail label ids ({@link GmailLabelId}) —
 * this endpoint performs NO name-to-id resolution, in contrast with the
 * existing by-filter {@code POST /messages/filter/modifyLabels} (FR-006).
 * At least one of the two label lists MUST be non-empty; a request where
 * both are empty/absent is a no-op modify and is rejected with HTTP 400
 * (FR-007, {@link #isAtLeastOneLabelListNonEmpty()}).</p>
 */
@Schema(description = "A list of Gmail message IDs plus raw label IDs to add/remove on exactly those messages")
public record BatchModifyLabelsByIdRequest(
        @Schema(description = "Gmail message IDs to modify labels on", example = "[\"18d1a2b3c4d5e6f7\"]")
                @NotEmpty(message = "messageIds must not be empty")
                List<@NotNull @GmailMessageId String> messageIds,
        @Schema(description = "Raw Gmail label IDs to add to each message", example = "[\"Label_42\"]")
                List<@NotNull @GmailLabelId String> labelIdsToAdd,
        @Schema(description = "Raw Gmail label IDs to remove from each message", example = "[\"UNREAD\"]")
                List<@NotNull @GmailLabelId String> labelIdsToRemove) {

    /**
     * Class-level constraint (FR-007): a no-op modify — both {@code labelIdsToAdd}
     * and {@code labelIdsToRemove} empty or absent — is a client error.
     *
     * @return true if at least one of the two label lists is non-empty
     */
    @AssertTrue(message = "at least one of labelIdsToAdd or labelIdsToRemove must be non-empty")
    public boolean isAtLeastOneLabelListNonEmpty() {
        return (labelIdsToAdd != null && !labelIdsToAdd.isEmpty())
                || (labelIdsToRemove != null && !labelIdsToRemove.isEmpty());
    }
}
