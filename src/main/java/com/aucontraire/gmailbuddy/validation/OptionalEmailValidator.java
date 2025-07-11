package com.aucontraire.gmailbuddy.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * Validator implementation for the {@link OptionalEmail} annotation.
 * Validates email format only when a value is provided, allowing null or empty values.
 * 
 * @author Gmail Buddy Team
 * @since 1.0
 */
public class OptionalEmailValidator implements ConstraintValidator<OptionalEmail, String> {
    
    /**
     * Regular expression pattern for validating email addresses.
     * Based on RFC 5322 specification with practical constraints.
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );

    /**
     * Initializes the validator. No initialization required for this implementation.
     * 
     * @param constraintAnnotation the annotation instance
     */
    @Override
    public void initialize(OptionalEmail constraintAnnotation) {
        // No initialization needed
    }

    /**
     * Validates the email value. Returns true if the value is null, empty, or a valid email.
     * 
     * @param email the email value to validate
     * @param context the constraint validator context
     * @return true if valid, false otherwise
     */
    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        // Allow null or empty values (they are optional)
        if (email == null || email.trim().isEmpty()) {
            return true;
        }
        
        // Validate email format if value is provided
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }
}