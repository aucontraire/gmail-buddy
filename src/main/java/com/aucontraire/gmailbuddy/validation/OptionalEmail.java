package com.aucontraire.gmailbuddy.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Custom validation annotation for optional email fields.
 * Unlike the standard {@link jakarta.validation.constraints.Email} annotation,
 * this validator allows null or empty values while still validating email format
 * when a value is provided.
 * 
 * @author Gmail Buddy Team
 * @since 1.0
 */
@Documented
@Constraint(validatedBy = OptionalEmailValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface OptionalEmail {
    /**
     * The error message to return when validation fails.
     * 
     * @return the error message
     */
    String message() default "Must be a valid email address";
    
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