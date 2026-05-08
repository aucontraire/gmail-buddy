package com.aucontraire.gmailbuddy.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Base64;

/**
 * Validator for the {@link ValidBase64} constraint.
 *
 * <p>Returns {@code false} when the supplied string cannot be decoded as standard
 * (non-URL-safe) Base64 by the JDK's {@link Base64#getDecoder()}.</p>
 *
 * <h3>Validation approach</h3>
 * <p>Validation is performed by calling {@code Base64.getDecoder().decode(value)} inside
 * a try/catch for {@link IllegalArgumentException}. If the exception is thrown — which
 * the JDK guarantees for any input containing characters outside the Base64 alphabet
 * ({@code A-Z}, {@code a-z}, {@code 0-9}, {@code +}, {@code /}), or with incorrect
 * padding — the constraint is violated and {@code false} is returned.</p>
 *
 * <p>This decode-attempt approach is more definitive than a regex check: a regex such
 * as {@code ^[A-Za-z0-9+/]*={0,2}$} accepts strings with correct alphabet characters
 * but incorrect padding lengths that {@code Base64.getDecoder()} would still reject.
 * The JDK decoder is authoritative on what is decodable.</p>
 *
 * <h3>Standard vs URL-safe Base64</h3>
 * <p>This validator uses the <em>standard</em> Base64 decoder ({@code +} and {@code /}
 * as alphabet characters, {@code =} padding), not the URL-safe decoder ({@code -} and
 * {@code _}). Callers must encode attachment binary content using standard Base64.
 * URL-safe Base64 strings will fail validation if they contain {@code -} or {@code _}
 * characters (which are not in the standard alphabet).</p>
 *
 * <h3>Performance note</h3>
 * <p>Decoding large Base64 strings allocates a {@code byte[]} proportional to the
 * decoded size. For a 25 MB attachment this is approximately 18.75 MB of heap. This
 * allocation is unavoidable with the decode-attempt approach. The allocation is
 * short-lived (eligible for GC immediately after validation). Bean Validation runs
 * before any MIME construction, so the heap usage is bounded to one concurrent
 * validation at a time under typical request load.</p>
 *
 * <p>Returns {@code true} for {@code null} values. Presence validation is delegated
 * to {@link jakarta.validation.constraints.NotBlank} so that each annotation retains
 * a single, clear responsibility.</p>
 *
 * @see ValidBase64
 */
public class ValidBase64Validator implements ConstraintValidator<ValidBase64, String> {

    /**
     * Initializes the validator. No state is required for this implementation.
     *
     * @param constraintAnnotation the annotation instance (unused)
     */
    @Override
    public void initialize(ValidBase64 constraintAnnotation) {
        // No initialization required; validator is stateless.
    }

    /**
     * Returns {@code true} if {@code value} is {@code null} or can be decoded as
     * standard Base64; {@code false} if {@link Base64#getDecoder()} throws
     * {@link IllegalArgumentException}.
     *
     * @param value   the string to inspect (may be {@code null})
     * @param context the constraint validator context
     * @return {@code true} when the value is null or a valid standard Base64 string
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        try {
            Base64.getDecoder().decode(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
