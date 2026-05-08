package com.aucontraire.gmailbuddy.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * Validator for the {@link ValidMimeType} constraint.
 *
 * <p>Returns {@code false} when the supplied string does not conform to the
 * RFC 6838 {@code type "/" subtype} MIME type format.</p>
 *
 * <h3>Validation rule</h3>
 * <p>The regex applied is:</p>
 * <pre>{@code ^[a-zA-Z0-9!#$&+\-.^_`{|}~]+/[a-zA-Z0-9!#$&+\-.^_`{|}~]+$}</pre>
 * <p>This matches the RFC 6838 / RFC 2045 token character set: ALPHA, DIGIT, and
 * the symbols {@code ! # $ & + - . ^ _ ` { | } ~}. Both the type portion and the
 * subtype portion must be non-empty and consist entirely of token characters,
 * separated by exactly one forward slash.</p>
 *
 * <p>The validator does NOT enforce a whitelist of known type names (e.g., it does
 * not restrict values to {@code application/*} or {@code image/*}). Any syntactically
 * well-formed {@code type/subtype} string passes. This satisfies the spec requirement
 * of syntactic validation without a content-type whitelist.</p>
 *
 * <p>Returns {@code true} for {@code null} values. Presence validation is delegated
 * to {@link jakarta.validation.constraints.NotBlank} so that each annotation retains
 * a single, clear responsibility.</p>
 *
 * <p>The {@link Pattern} is pre-compiled as a static constant so that the validator
 * does not incur regex compilation overhead on every call.</p>
 *
 * @see ValidMimeType
 */
public class ValidMimeTypeValidator implements ConstraintValidator<ValidMimeType, String> {

    /**
     * RFC 6838 MIME type pattern: {@code type/subtype} where both parts consist of
     * RFC 2045 token characters (ALPHA, DIGIT, and {@code ! # $ & + - . ^ _ ` { | } ~}).
     * The backtick character is included per RFC 6838 §4.2 token definition.
     */
    private static final Pattern MIME_TYPE_PATTERN =
            Pattern.compile("^[a-zA-Z0-9!#$&+\\-.^_`{|}~]+/[a-zA-Z0-9!#$&+\\-.^_`{|}~]+$");

    /**
     * Initializes the validator. No state is required for this implementation.
     *
     * @param constraintAnnotation the annotation instance (unused)
     */
    @Override
    public void initialize(ValidMimeType constraintAnnotation) {
        // No initialization required; validator is stateless.
    }

    /**
     * Returns {@code true} if {@code value} is {@code null} or matches the RFC 6838
     * MIME type format; {@code false} otherwise.
     *
     * @param value   the string to inspect (may be {@code null})
     * @param context the constraint validator context
     * @return {@code true} when the value is null or a syntactically valid MIME type
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return MIME_TYPE_PATTERN.matcher(value).matches();
    }
}
