package com.aucontraire.gmailbuddy.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Full label content returned by {@code GET /api/v1/gmail/labels/{id}}.
 *
 * <p>Extends the field set of {@link LabelSummary} with color and message/thread
 * count fields. Implemented as a standalone record (Java records are final — no
 * class inheritance from {@code LabelSummary}). Color fields are wrapped in a
 * {@link LabelColor} record; system labels that have no color configured will
 * have {@code color = null}.</p>
 *
 * @param id                    Gmail label ID
 * @param name                  Display name
 * @param type                  {@code "system"} or {@code "user"}
 * @param messageListVisibility Gmail's messageListVisibility field; null if not set
 * @param labelListVisibility   Gmail's labelListVisibility field; null if not set
 * @param color                 Label color; null if no color is configured
 * @param messagesTotal         Total messages carrying this label; null if not returned by Gmail
 * @param messagesUnread        Unread messages with this label; null if not returned
 * @param threadsTotal          Total threads with this label; null if not returned
 * @param threadsUnread         Unread threads with this label; null if not returned
 */
@Schema(description = "Full label detail including counts and color settings")
public record LabelDetailResponse(
        @Schema(description = "Gmail label ID", example = "INBOX")
        String id,

        @Schema(description = "Display name of the label", example = "INBOX")
        String name,

        @Schema(description = "Label type: system or user", example = "system",
                allowableValues = {"system", "user"})
        String type,

        @Schema(description = "Gmail messageListVisibility setting; null if not configured",
                example = "show", nullable = true)
        String messageListVisibility,

        @Schema(description = "Gmail labelListVisibility setting; null if not configured",
                example = "labelShow", nullable = true)
        String labelListVisibility,

        @Schema(description = "Label color; null if no color is configured on this label",
                nullable = true)
        LabelColor color,

        @Schema(description = "Total messages with this label; null if not populated by Gmail",
                example = "42", nullable = true)
        Integer messagesTotal,

        @Schema(description = "Unread messages with this label; null if not populated by Gmail",
                example = "5", nullable = true)
        Integer messagesUnread,

        @Schema(description = "Total threads with this label; null if not populated by Gmail",
                example = "38", nullable = true)
        Integer threadsTotal,

        @Schema(description = "Unread threads with this label; null if not populated by Gmail",
                example = "4", nullable = true)
        Integer threadsUnread
) {}
