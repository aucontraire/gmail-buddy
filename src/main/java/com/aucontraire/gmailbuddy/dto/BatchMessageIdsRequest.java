package com.aucontraire.gmailbuddy.dto;

import com.aucontraire.gmailbuddy.validation.GmailMessageId;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request DTO for the feature-005 batch-by-id mutation endpoints
 * ({@code POST /messages/batchTrash}, {@code POST /messages/batchUntrash}).
 *
 * <p>{@code messageIds} MUST be non-empty; each element MUST be a valid Gmail
 * short hex identifier ({@link GmailMessageId}). The configurable upper bound
 * on batch size ({@code gmail-buddy.gmail-api.batch-delete-max-results}) is
 * enforced at the service layer, not here, so the cap can be changed without
 * recompiling this DTO (spec.md data-model.md, feature 005 T008).</p>
 */
@Schema(description = "A list of Gmail message IDs to apply a batch mutation to")
public record BatchMessageIdsRequest(

    @Schema(description = "Gmail message IDs to move to/restore from Trash", example = "[\"18d1a2b3c4d5e6f7\", \"18d1a2b3c4d5e700\"]")
    @NotEmpty(message = "messageIds must not be empty")
    List<@NotNull @GmailMessageId String> messageIds

) {
}
