package com.aucontraire.gmailbuddy.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Per-label summary returned in {@code GET /api/v1/gmail/labels} list responses.
 *
 * <p>Fields correspond to what Gmail's {@code users.labels.list} response provides.
 * For full detail (message/thread counts, color), use
 * {@code GET /api/v1/gmail/labels/{id}} which returns a {@link LabelDetailResponse}.</p>
 *
 * @param id                    Gmail label ID (e.g., {@code INBOX}, {@code Label_42})
 * @param name                  Display name (e.g., {@code INBOX}, {@code Recruiters})
 * @param type                  Label type: {@code "system"} or {@code "user"}
 * @param messageListVisibility Gmail's {@code messageListVisibility} field; {@code null} if not set
 * @param labelListVisibility   Gmail's {@code labelListVisibility} field; {@code null} if not set
 */
@Schema(description = "Per-label summary item in the label list response")
public record LabelSummary(
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
        String labelListVisibility
) {}
