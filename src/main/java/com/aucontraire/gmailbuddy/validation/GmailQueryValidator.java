package com.aucontraire.gmailbuddy.validation;

import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

/**
 * Validator implementation for the {@link ValidGmailQuery} annotation.
 * Provides security-focused validation of Gmail query strings to prevent
 * injection attacks while allowing legitimate Gmail search operators.
 * 
 * <p>This validator implements a two-layer security approach:
 * <ol>
 *   <li>Blocks dangerous characters commonly used in injection attacks</li>
 *   <li>Delegates complex syntax validation to Gmail API for flexibility</li>
 * </ol>
 * </p>
 * 
 * @author Gmail Buddy Team
 * @since 1.0
 */
public class GmailQueryValidator implements ConstraintValidator<ValidGmailQuery, String> {
    
    /**
     * Pattern to detect potentially dangerous characters that could be used
     * for injection attacks including XSS, script injection, and command injection.
     */
    private static final Pattern DANGEROUS_PATTERN = Pattern.compile(
        ".*[<>\"'&;\\|\\*\\?\\[\\]\\(\\)\\{\\}\\$`\\\\].*",
        Pattern.CASE_INSENSITIVE
    );
    
    /**
     * Pattern for valid Gmail search operators (kept for future use).
     * Currently not enforced to allow Gmail API to handle syntax validation.
     */
    private static final Pattern VALID_GMAIL_OPERATORS = Pattern.compile(
        "^(?:[\\w\\s\\-@\\.\\+]*|(?:from|to|subject|has|in|is|after|before|older|newer|label|category|filename|cc|bcc):[\\w\\s\\-@\\.\\+]*\\s*)*$",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Initializes the validator. No initialization required for this implementation.
     * 
     * @param constraintAnnotation the annotation instance
     */
    @Override
    public void initialize(ValidGmailQuery constraintAnnotation) {
        // No initialization needed
    }

    /**
     * Validates the Gmail query string for security and basic format compliance.
     * 
     * <p>Validation rules:
     * <ul>
     *   <li>Null or empty values are considered valid (optional fields)</li>
     *   <li>Dangerous characters are blocked to prevent injection attacks</li>
     *   <li>Complex syntax validation is delegated to Gmail API</li>
     * </ul>
     * </p>
     * 
     * @param query the query string to validate
     * @param context the constraint validator context for custom error messages
     * @return true if valid, false otherwise
     */
    @Override
    public boolean isValid(String query, ConstraintValidatorContext context) {
        if (query == null || query.trim().isEmpty()) {
            return true; // null/empty values are handled by @NotNull/@NotEmpty
        }
        
        query = query.trim();
        
        // Check for dangerous characters that could be used for injection
        if (DANGEROUS_PATTERN.matcher(query).matches()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Query contains invalid characters. Only letters, numbers, spaces, hyphens, @ . : + and Gmail operators are allowed"
            ).addConstraintViolation();
            return false;
        }
        
        // For simplicity, if it doesn't contain dangerous characters, allow it
        // Gmail API will handle invalid query syntax and return appropriate errors
        return true;
    }
}