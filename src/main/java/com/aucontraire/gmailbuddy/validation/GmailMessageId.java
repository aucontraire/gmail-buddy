package com.aucontraire.gmailbuddy.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;
import java.lang.annotation.*;

/**
 * Constraint annotation that validates a Gmail short hex identifier — used for
 * message IDs, thread IDs, and the {@code inReplyToMessageId} reference.
 *
 * <p>Gmail message and thread IDs are opaque hex strings up to 32 characters
 * (e.g., {@code 1976a4bc3fe89d0c}). The accepted character set is
 * {@code [0-9a-fA-F]} with a maximum length of 32 characters. This is the
 * same regex applied to {@code SendMessageDTO.threadId} and
 * {@code SendMessageDTO.inReplyToMessageId} fields, and to {@code messageId}
 * path variables on the messages endpoints.</p>
 *
 * <p>This annotation is intentionally separate from {@link GmailDraftId} —
 * draft IDs use a different format (letter-prefix + alphanumeric, see
 * {@link GmailDraftId} javadoc). Conflating the two regexes was the original
 * source of the validation bug fixed in feature 003 (PR #17); this annotation
 * pair makes the two formats explicit at the call site.</p>
 *
 * <p>This is a <strong>composed constraint</strong>: the meta-annotation
 * {@link Pattern} is automatically processed by Hibernate Validator, so no
 * dedicated {@code GmailMessageIdValidator} class is required (Constitution
 * Anti-Slop §A2 — no unnecessary abstractions). The
 * {@link ReportAsSingleViolation} annotation ensures only a single violation
 * message is surfaced when the constraint fails.</p>
 *
 * <p>Null values are treated as valid — presence enforcement is the
 * responsibility of {@link jakarta.validation.constraints.NotBlank} or
 * {@link jakarta.validation.constraints.NotNull}, not this constraint.</p>
 *
 * <p>The {@code TYPE_USE} target enables per-element annotation on generic type
 * arguments, e.g. {@code List<@GmailMessageId String>}.</p>
 *
 * @see GmailDraftId
 */
@Documented
@Pattern(regexp = "[0-9a-fA-F]{1,32}")
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface GmailMessageId {

    /**
     * Violation message returned when the constraint is not satisfied.
     *
     * @return the error message template
     */
    String message() default "must be a valid Gmail short hex identifier (hex characters; max 32 characters)";

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
