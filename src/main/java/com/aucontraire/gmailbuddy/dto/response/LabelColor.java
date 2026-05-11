package com.aucontraire.gmailbuddy.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Color settings for a Gmail label.
 *
 * <p>System labels typically do not have a color set (the field will be null
 * in the enclosing {@link LabelDetailResponse}). User-created labels may
 * optionally have a text and background color chosen from Gmail's predefined
 * color palette.</p>
 *
 * @param textColor       hex color code for the label text (e.g., {@code "#222222"})
 * @param backgroundColor hex color code for the label background (e.g., {@code "#16a766"})
 */
@Schema(description = "Color settings for a Gmail label")
public record LabelColor(
        @Schema(description = "Hex color code for the label text", example = "#222222")
        String textColor,

        @Schema(description = "Hex color code for the label background", example = "#16a766")
        String backgroundColor
) {}
