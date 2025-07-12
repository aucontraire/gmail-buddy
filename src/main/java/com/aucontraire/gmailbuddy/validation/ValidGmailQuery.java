package com.aucontraire.gmailbuddy.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Custom validation annotation for Gmail query syntax validation.
 * Validates that query strings are safe and follow Gmail search patterns,
 * preventing potential injection attacks while allowing valid Gmail operators.
 * 
 * <p>Supported Gmail operators include: from:, to:, subject:, has:, in:, is:,
 * after:, before:, older:, newer:, label:, category:, filename:, cc:, bcc:</p>
 * 
 * <p>Security features:
 * <ul>
 *   <li>Blocks dangerous characters that could be used for injection attacks</li>
 *   <li>Allows null/empty values (treated as valid)</li>
 *   <li>Validates against XSS and script injection patterns</li>
 * </ul>
 * </p>
 * 
 * @author Gmail Buddy Team
 * @since 1.0
 */
@Documented
@Constraint(validatedBy = GmailQueryValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidGmailQuery {
    /**
     * The error message to return when validation fails.
     * 
     * @return the error message
     */
    String message() default "Invalid Gmail query syntax";
    
    /**
     * Validation groups for this constraint.
     * 
     * @return the validation groups
     */
    Class<?>[] groups() default {};
    
    /**
     * Payload for extensibility purposes.
     * 
     * @return the payload
     */
    Class<? extends Payload>[] payload() default {};
}