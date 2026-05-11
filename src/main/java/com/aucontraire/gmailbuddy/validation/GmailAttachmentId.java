package com.aucontraire.gmailbuddy.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;
import java.lang.annotation.*;

/**
 * Constraint annotation that validates a Gmail attachment identifier.
 *
 * <p>Gmail attachment IDs are opaque alphanumeric strings (with hyphens and
 * underscores) that may be very long — Gmail attachment IDs are encoded
 * location pointers and can exceed several hundred characters
 * (e.g., {@code ANGjdJ8BwFpn3nQ0oFQ7wPjVLfRx...}). The accepted character set
 * is {@code [A-Za-z0-9_-]} with a maximum length of 1024 characters:
 * permissive enough for Gmail's actual attachment ID format, restrictive
 * enough to reject path-traversal characters ({@code /}, {@code .},
 * whitespace, NUL bytes) and oversized inputs.</p>
 *
 * <p>This is a <strong>composed constraint</strong>: the meta-annotation
 * {@link Pattern} is automatically processed by Hibernate Validator, so no
 * dedicated {@code GmailAttachmentIdValidator} class is required (Constitution
 * Anti-Slop §A2 — no unnecessary abstractions). The
 * {@link ReportAsSingleViolation} annotation ensures only a single violation
 * message is surfaced when the constraint fails.</p>
 *
 * <p>Null values are treated as valid — presence enforcement is the
 * responsibility of {@link jakarta.validation.constraints.NotBlank} or
 * {@link jakarta.validation.constraints.NotNull}, not this constraint.</p>
 *
 * <p>The {@code TYPE_USE} target enables per-element annotation on generic type
 * arguments, e.g. {@code List<@GmailAttachmentId String>}.</p>
 *
 * @see GmailMessageId
 * @see GmailDraftId
 * @see GmailLabelId
 */
@Documented
@Pattern(regexp = "[A-Za-z0-9_-]{1,1024}")
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface GmailAttachmentId {

    String message() default "must be a valid Gmail attachment ID (alphanumeric, hyphen, underscore; max 1024 characters)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
