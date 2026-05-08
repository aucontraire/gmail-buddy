package com.aucontraire.gmailbuddy.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Constraint annotation that rejects strings unsafe for use as an email attachment
 * filename in a {@code Content-Disposition: attachment; filename="..."} header.
 *
 * <p>A safe filename must not contain:</p>
 * <ul>
 *   <li>Any of the seven Unicode line-terminator characters (U+000A, U+000B, U+000C,
 *       U+000D, U+0085, U+2028, U+2029) — the same set rejected by
 *       {@link NoHeaderInjection}, which would allow crafting malicious MIME headers
 *       by injecting newlines into the {@code Content-Disposition} header value.</li>
 *   <li>Path-traversal sequences: {@code ..} (two consecutive dots), {@code /}
 *       (forward slash), {@code \} (backslash), or the NUL byte (U+0000). These
 *       sequences do not affect server-side security (the server never writes files
 *       to disk), but they can confuse naive auto-saving mail clients that interpret
 *       the filename as a filesystem path.</li>
 * </ul>
 *
 * <p>This annotation is intentionally separate from {@link NoHeaderInjection}: that
 * annotation covers the header-injection threat model for email header fields (subject,
 * recipients). {@code @SafeFilename} covers both the header-injection threat AND the
 * path-traversal threat specific to attachment filenames — two distinct concerns in
 * one annotation per the Single Responsibility Principle (each annotation has exactly
 * one responsibility: "this string is safe to use as an attachment filename").</p>
 *
 * <p>Null values are treated as valid — presence enforcement is the responsibility
 * of {@link jakarta.validation.constraints.NotBlank} or
 * {@link jakarta.validation.constraints.NotNull}, not this constraint.</p>
 *
 * <p>The {@code TYPE_USE} target enables per-element annotation on generic type
 * arguments, e.g. {@code List<@SafeFilename String>}.</p>
 *
 * @see SafeFilenameValidator
 */
@Documented
@Constraint(validatedBy = SafeFilenameValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeFilename {

    /**
     * Violation message returned when the constraint is not satisfied.
     *
     * @return the error message template
     */
    String message() default "Filename must not contain line-terminator characters, path-traversal sequences (.. / \\), or NUL bytes";

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
