package com.aucontraire.gmailbuddy.dto;

import com.aucontraire.gmailbuddy.validation.NoHeaderInjection;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for the feature-005 US3 create-label endpoint ({@code POST /labels}).
 *
 * <p>{@code name} MUST be non-blank, at most 225 characters (Gmail's practical
 * label-name limit — provisional per data-model.md, confirmed/adjusted by task T032),
 * and free of control characters. Two constraints combine to enforce this:
 * {@link NoHeaderInjection} rejects the seven Unicode line-terminator characters
 * (including CR {@code \r} and LF {@code \n}), and the {@code \p{Cntrl}}-based
 * {@code @Pattern} below rejects the full ASCII control-character range
 * (U+0000–U+001F, U+007F), which additionally covers NUL ({@code \0}) — together
 * satisfying FR-011 without reusing {@code @SafeFilename}, whose path-traversal
 * checks (rejecting {@code /}) would incorrectly block Gmail's legitimate
 * {@code Parent/Child} nested-label naming convention.</p>
 *
 * <p>{@code messageListVisibility} and {@code labelListVisibility} are optional;
 * when present, each MUST be one of Gmail's allowed enum values (FR-011). Both use
 * the standard bean-validation convention of treating {@code null} as valid — presence
 * is optional, not enforced by these annotations.</p>
 */
@Schema(description = "Request to create a new Gmail label (POST /api/v1/gmail/labels)")
public record CreateLabelRequest(
        @Schema(description = "Display name for the new label", example = "pending-purge")
                @NotBlank(message = "name must not be blank")
                @Size(max = 225, message = "name must not exceed 225 characters")
                @NoHeaderInjection
                @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "name must not contain control characters")
                String name,
        @Schema(
                        description = "Gmail messageListVisibility setting; null uses Gmail's default",
                        example = "show",
                        nullable = true,
                        allowableValues = {"show", "hide"})
                @Pattern(regexp = "^(show|hide)$", message = "messageListVisibility must be 'show' or 'hide'")
                String messageListVisibility,
        @Schema(
                        description = "Gmail labelListVisibility setting; null uses Gmail's default",
                        example = "labelShow",
                        nullable = true,
                        allowableValues = {"labelShow", "labelShowIfUnread", "labelHide"})
                @Pattern(
                        regexp = "^(labelShow|labelShowIfUnread|labelHide)$",
                        message = "labelListVisibility must be one of labelShow, labelShowIfUnread, labelHide")
                String labelListVisibility) {}
