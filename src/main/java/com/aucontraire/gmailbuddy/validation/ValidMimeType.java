package com.aucontraire.gmailbuddy.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Constraint annotation that validates MIME type strings against the RFC 6838
 * {@code type "/" subtype} format.
 *
 * <p>A valid MIME type consists of a non-empty type token, a forward slash separator,
 * and a non-empty subtype token. Both tokens must consist exclusively of RFC 6838
 * token characters: ALPHA, DIGIT, and the following symbols:
 * {@code ! # $ & + - . ^ _ ` { | } ~}. No whitespace, control characters, or other
 * special characters are permitted.</p>
 *
 * <p>No content-type whitelist is enforced — any syntactically well-formed RFC 6838
 * type/subtype is accepted (e.g., {@code application/pdf}, {@code image/png},
 * {@code text/plain}, {@code application/vnd.ms-excel}).</p>
 *
 * <p>Null values are treated as valid — presence enforcement is the responsibility
 * of {@link jakarta.validation.constraints.NotBlank} or
 * {@link jakarta.validation.constraints.NotNull}, not this constraint.</p>
 *
 * <p>The {@code TYPE_USE} target enables per-element annotation on generic type
 * arguments, e.g. {@code List<@ValidMimeType String>}.</p>
 *
 * @see ValidMimeTypeValidator
 */
@Documented
@Constraint(validatedBy = ValidMimeTypeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidMimeType {

    /**
     * Violation message returned when the constraint is not satisfied.
     *
     * @return the error message template
     */
    String message() default "MIME type must be a valid RFC 6838 type/subtype (e.g. application/pdf)";

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
