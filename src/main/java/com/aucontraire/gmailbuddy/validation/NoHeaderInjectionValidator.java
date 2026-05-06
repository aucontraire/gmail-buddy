package com.aucontraire.gmailbuddy.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for the {@link NoHeaderInjection} constraint.
 *
 * <p>Returns {@code false} when the supplied string contains a carriage-return
 * ({@code '\r'}, U+000D) or a line-feed ({@code '\n'}, U+000A) character.
 * These characters are the classic vehicle for CRLF / header-injection attacks
 * when user-controlled input is written verbatim into an email header.</p>
 *
 * <p>Returns {@code true} for {@code null} values. Presence validation is
 * delegated to {@link jakarta.validation.constraints.NotBlank} or
 * {@link jakarta.validation.constraints.NotNull} so that each annotation retains
 * a single, clear responsibility.</p>
 *
 * <p>The check intentionally uses {@link String#indexOf(int)} rather than a
 * regex so that the hot path (no injection characters present) pays only two
 * linear scans with no regex compilation or backtracking overhead.</p>
 *
 * @see NoHeaderInjection
 */
public class NoHeaderInjectionValidator implements ConstraintValidator<NoHeaderInjection, String> {

    /**
     * Initializes the validator. No state is required for this implementation.
     *
     * @param constraintAnnotation the annotation instance (unused)
     */
    @Override
    public void initialize(NoHeaderInjection constraintAnnotation) {
        // No initialization required; validator is stateless.
    }

    /**
     * Returns {@code true} if {@code value} is {@code null} or contains neither
     * {@code '\r'} nor {@code '\n'}; {@code false} otherwise.
     *
     * @param value   the string to inspect (may be {@code null})
     * @param context the constraint validator context
     * @return {@code true} when no header-injection characters are present
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return value.indexOf('\r') < 0 && value.indexOf('\n') < 0;
    }
}
