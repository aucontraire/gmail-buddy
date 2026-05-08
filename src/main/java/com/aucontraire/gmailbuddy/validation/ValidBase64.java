package com.aucontraire.gmailbuddy.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Constraint annotation that validates strings as standard (non-URL-safe) Base64
 * encoded data, as defined by RFC 4648 §4.
 *
 * <p>Validation is performed by attempting to decode the string using
 * {@link java.util.Base64#getDecoder()} (the standard Base64 decoder, NOT the
 * URL-safe decoder). If the string cannot be decoded — due to invalid characters,
 * incorrect padding, or any other encoding error — the constraint is violated.</p>
 *
 * <p>Note that URL-safe Base64 (which replaces {@code +} with {@code -} and
 * {@code /} with {@code _}) will NOT pass this validation unless the characters
 * happen to be absent from the specific value. Use the standard encoder/decoder
 * (not URL-safe) when encoding attachment binary data for use with this constraint.</p>
 *
 * <p>Null values are treated as valid — presence enforcement is the responsibility
 * of {@link jakarta.validation.constraints.NotBlank} or
 * {@link jakarta.validation.constraints.NotNull}, not this constraint.</p>
 *
 * <p>The {@code TYPE_USE} target enables per-element annotation on generic type
 * arguments, e.g. {@code List<@ValidBase64 String>}.</p>
 *
 * @see ValidBase64Validator
 */
@Documented
@Constraint(validatedBy = ValidBase64Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidBase64 {

    /**
     * Violation message returned when the constraint is not satisfied.
     *
     * @return the error message template
     */
    String message() default "Value must be a valid standard Base64-encoded string (not URL-safe)";

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
