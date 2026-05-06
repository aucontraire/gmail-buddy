package com.aucontraire.gmailbuddy.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Constraint annotation that rejects strings containing carriage-return ({@code \r})
 * or line-feed ({@code \n}) characters.
 *
 * <p>Apply this annotation to every string field whose value will land in an email
 * header (recipient addresses, subject line, and any future alias or reply-to fields).
 * Header injection via embedded CRLF sequences is a well-known attack vector for
 * crafting malicious headers and must be blocked before message construction begins.</p>
 *
 * <p>Null values are treated as valid — presence enforcement is the responsibility
 * of {@link jakarta.validation.constraints.NotBlank} or
 * {@link jakarta.validation.constraints.NotNull}, not this constraint. This separation
 * honours the Single Responsibility Principle and avoids duplicating presence semantics.</p>
 *
 * <p>The {@code TYPE_USE} target enables per-element annotation on generic type
 * arguments, allowing usage such as
 * {@code List<@NoHeaderInjection String>}.</p>
 *
 * @see NoHeaderInjectionValidator
 */
@Documented
@Constraint(validatedBy = NoHeaderInjectionValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoHeaderInjection {

    /**
     * Violation message returned when the constraint is not satisfied.
     *
     * @return the error message template
     */
    String message() default "Header-injection characters (\\r or \\n) are not permitted";

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
