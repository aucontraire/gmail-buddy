package com.aucontraire.gmailbuddy.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;
import java.lang.annotation.*;

/**
 * Constraint annotation that validates a Gmail draft identifier.
 *
 * <p>Gmail draft IDs are opaque alphanumeric strings using a letter-prefix +
 * digits format (e.g., {@code r9068706262700056809}) — distinct from Gmail
 * <strong>message</strong> IDs which are short hex (e.g., {@code 1976a4bc3fe89d0c}).
 * The accepted character set is {@code [A-Za-z0-9_-]} with a maximum length of
 * 128 characters: permissive enough for Gmail's actual draft ID format,
 * restrictive enough to reject path-traversal characters ({@code /}, {@code .},
 * whitespace, etc.) and oversized inputs.</p>
 *
 * <p>This is a <strong>composed constraint</strong>: the meta-annotation
 * {@link Pattern} is automatically processed by Hibernate Validator, so no
 * dedicated {@code GmailDraftIdValidator} class is required (Constitution
 * Anti-Slop §A2 — no unnecessary abstractions). The
 * {@link ReportAsSingleViolation} annotation ensures only a single violation
 * message is surfaced when the constraint fails.</p>
 *
 * <p>Originally, draft IDs and message IDs both used the same hex regex
 * ({@code [0-9a-fA-F]{1,32}}). That assumption was wrong for drafts —
 * discovered during smoke testing the {@code GET /api/v1/gmail/drafts/{id}}
 * endpoint when a real Gmail-issued ID {@code r9068706262700056809} was
 * rejected by the validator. See ROADMAP R-023 and feature 003 spec.</p>
 *
 * <p>Null values are treated as valid — presence enforcement is the
 * responsibility of {@link jakarta.validation.constraints.NotBlank} or
 * {@link jakarta.validation.constraints.NotNull}, not this constraint.</p>
 *
 * <p>The {@code TYPE_USE} target enables per-element annotation on generic type
 * arguments, e.g. {@code List<@GmailDraftId String>}.</p>
 *
 * @see GmailMessageId
 */
@Documented
@Pattern(regexp = "[A-Za-z0-9_-]{1,128}")
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface GmailDraftId {

    /**
     * Violation message returned when the constraint is not satisfied.
     *
     * @return the error message template
     */
    String message() default "must be a valid Gmail draft identifier (alphanumeric, hyphen, or underscore; max 128 characters)";

    /**
     * Validation groups this constraint belongs to.
     *
     * @return the groups
     */
    Class<?>[] groups() default {};

    /**
     * Payload for extensibility by constraint consumers.
     *
     * @return the payload
     */
    Class<? extends Payload>[] payload() default {};
}
