package com.aucontraire.gmailbuddy.validation;

import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Service that provides validation patterns from configuration properties.
 * This allows validation classes to access configurable patterns.
 * 
 * @author Gmail Buddy Team
 * @since 1.0
 */
@Service
public class ValidationPatternsService {
    
    private final GmailBuddyProperties properties;
    private final Pattern dangerousPattern;
    private final Pattern validOperatorsPattern;
    private final Pattern emailPattern;
    
    @Autowired
    public ValidationPatternsService(GmailBuddyProperties properties) {
        this.properties = properties;
        this.dangerousPattern = Pattern.compile(
            properties.validation().gmailQuery().dangerousPattern(),
            Pattern.CASE_INSENSITIVE
        );
        this.validOperatorsPattern = Pattern.compile(
            properties.validation().gmailQuery().validOperatorsPattern(),
            Pattern.CASE_INSENSITIVE
        );
        this.emailPattern = Pattern.compile(
            properties.validation().email().pattern(),
            Pattern.CASE_INSENSITIVE
        );
    }
    
    /**
     * Gets the pattern for detecting dangerous characters in Gmail queries.
     * 
     * @return the dangerous character pattern
     */
    public Pattern getDangerousPattern() {
        return dangerousPattern;
    }
    
    /**
     * Gets the pattern for valid Gmail operators.
     * 
     * @return the valid operators pattern
     */
    public Pattern getValidOperatorsPattern() {
        return validOperatorsPattern;
    }
    
    /**
     * Gets the pattern for email validation.
     * 
     * @return the email validation pattern
     */
    public Pattern getEmailPattern() {
        return emailPattern;
    }
    
    /**
     * Validates a Gmail query string for dangerous characters.
     * 
     * @param query the query to validate
     * @return true if the query contains dangerous characters, false otherwise
     */
    public boolean containsDangerousCharacters(String query) {
        return query != null && dangerousPattern.matcher(query).matches();
    }
    
    /**
     * Validates an email address format.
     * 
     * @param email the email to validate
     * @return true if the email format is valid, false otherwise
     */
    public boolean isValidEmail(String email) {
        return email != null && emailPattern.matcher(email).matches();
    }
}