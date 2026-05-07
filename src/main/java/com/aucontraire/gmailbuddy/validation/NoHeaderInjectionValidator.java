package com.aucontraire.gmailbuddy.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for the {@link NoHeaderInjection} constraint.
 *
 * <p>Returns {@code false} when the supplied string contains any Unicode
 * line-terminator character. The full set of rejected characters is:</p>
 * <ul>
 *   <li>U+000A LINE FEED ({@code '\n'})</li>
 *   <li>U+000B VERTICAL TAB ({@code ''})</li>
 *   <li>U+000C FORM FEED ({@code ''})</li>
 *   <li>U+000D CARRIAGE RETURN ({@code '\r'})</li>
 *   <li>U+0085 NEXT LINE ({@code ''})</li>
 *   <li>U+2028 LINE SEPARATOR ({@code ' '})</li>
 *   <li>U+2029 PARAGRAPH SEPARATOR ({@code ' '})</li>
 * </ul>
 *
 * <p>U+000A and U+000D are the classic CRLF / header-injection vehicles.
 * The remaining five characters are defence-in-depth: they are recognised as
 * line terminators by the Unicode Standard, the Java Language Specification,
 * and various parsers, so removing any dependency on downstream encoding
 * behaviour (e.g. RFC 2047 folding) is cheap and prudent.</p>
 *
 * <p>Returns {@code true} for {@code null} values. Presence validation is
 * delegated to {@link jakarta.validation.constraints.NotBlank} or
 * {@link jakarta.validation.constraints.NotNull} so that each annotation retains
 * a single, clear responsibility.</p>
 *
 * <p>The check intentionally uses {@link String#indexOf(int)} rather than a
 * regex so that the hot path (no injection characters present) pays only linear
 * scans with no regex compilation or backtracking overhead.</p>
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
     * Returns {@code true} if {@code value} is {@code null} or contains no
     * Unicode line-terminator character; {@code false} otherwise.
     *
     * <p>The seven characters checked are U+000A, U+000B, U+000C, U+000D,
     * U+0085, U+2028, and U+2029 — the complete set recognised as line
     * terminators by the Unicode Standard and the Java Language Specification.</p>
     *
     * @param value   the string to inspect (may be {@code null})
     * @param context the constraint validator context
     * @return {@code true} when no line-terminator characters are present
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return value.indexOf('\n')      < 0  // U+000A LINE FEED
            && value.indexOf('')  < 0  // U+000B VERTICAL TAB
            && value.indexOf('')  < 0  // U+000C FORM FEED
            && value.indexOf('\r')      < 0  // U+000D CARRIAGE RETURN
            && value.indexOf('')  < 0  // U+0085 NEXT LINE
            && value.indexOf(' ')  < 0  // U+2028 LINE SEPARATOR
            && value.indexOf(' ')  < 0; // U+2029 PARAGRAPH SEPARATOR
    }
}
