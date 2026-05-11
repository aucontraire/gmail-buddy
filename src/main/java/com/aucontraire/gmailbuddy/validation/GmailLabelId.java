package com.aucontraire.gmailbuddy.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;
import java.lang.annotation.*;

/**
 * Constraint annotation that validates a Gmail label identifier.
 *
 * <p>Gmail label IDs cover both system labels ({@code INBOX}, {@code SENT},
 * {@code CATEGORY_PERSONAL}, {@code SPAM}, etc.) and user-created labels
 * ({@code Label_42}, {@code Label_3145}). The accepted character set is
 * {@code [A-Za-z0-9_]} with a maximum length of 128 characters: hyphens,
 * slashes, and dots are explicitly rejected — Gmail does not issue label IDs
 * containing those characters, so any input with them is malformed.</p>
 *
 * <p>This is a <strong>composed constraint</strong>: the meta-annotation
 * {@link Pattern} is automatically processed by Hibernate Validator, so no
 * dedicated {@code GmailLabelIdValidator} class is required (Constitution
 * Anti-Slop §A2 — no unnecessary abstractions). The
 * {@link ReportAsSingleViolation} annotation ensures only a single violation
 * message is surfaced when the constraint fails.</p>
 *
 * <p>Null values are treated as valid — presence enforcement is the
 * responsibility of {@link jakarta.validation.constraints.NotBlank} or
 * {@link jakarta.validation.constraints.NotNull}, not this constraint.</p>
 *
 * <p>The {@code TYPE_USE} target enables per-element annotation on generic type
 * arguments, e.g. {@code List<@GmailLabelId String>}.</p>
 *
 * @see GmailMessageId
 * @see GmailDraftId
 * @see GmailAttachmentId
 */
@Documented
@Pattern(regexp = "[A-Za-z0-9_]{1,128}")
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface GmailLabelId {

    String message() default "must be a valid Gmail label ID (alphanumeric and underscore; max 128 characters)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
