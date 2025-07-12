package com.aucontraire.gmailbuddy.validation;

import com.aucontraire.gmailbuddy.validation.GmailQueryValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class GmailQueryValidatorTest {

    private GmailQueryValidator validator;

    @Mock
    private ConstraintValidatorContext context;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        validator = new GmailQueryValidator();
        
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
        when(violationBuilder.addConstraintViolation()).thenReturn(context);
    }

    @Test
    void testValidQueries() {
        assertTrue(validator.isValid("from:test@example.com", context));
        assertTrue(validator.isValid("to:user@domain.com", context));
        assertTrue(validator.isValid("subject:meeting", context));
        assertTrue(validator.isValid("has:attachment", context));
        assertTrue(validator.isValid("in:inbox", context));
        assertTrue(validator.isValid("is:unread", context));
        assertTrue(validator.isValid("after:2023-01-01", context));
        assertTrue(validator.isValid("before:2023-12-31", context));
        assertTrue(validator.isValid("label:important", context));
        assertTrue(validator.isValid("category:primary", context));
        assertTrue(validator.isValid("filename:pdf", context));
    }

    @Test
    void testCombinedValidQueries() {
        assertTrue(validator.isValid("from:test@example.com subject:meeting", context));
        assertTrue(validator.isValid("has:attachment in:inbox", context));
        assertTrue(validator.isValid("from:user@domain.com after:2023-01-01", context));
    }

    @Test
    void testEmptyAndNullValues() {
        assertTrue(validator.isValid(null, context));
        assertTrue(validator.isValid("", context));
        assertTrue(validator.isValid("   ", context));
    }

    @Test
    void testInvalidCharacters() {
        assertFalse(validator.isValid("from:test<script>", context));
        assertFalse(validator.isValid("subject:\"test\"", context));
        assertFalse(validator.isValid("query with & ampersand", context));
        assertFalse(validator.isValid("query | with | pipes", context));
        assertFalse(validator.isValid("query * with * asterisks", context));
        assertFalse(validator.isValid("query with $variables", context));
        
        verify(context, atLeastOnce()).disableDefaultConstraintViolation();
        verify(context, atLeastOnce()).buildConstraintViolationWithTemplate(contains("invalid characters"));
    }

    @Test
    void testValidQueriesWithoutOperators() {
        // Since we simplified the validator, these should all be valid
        assertTrue(validator.isValid("invalidoperator:test", context));
        assertTrue(validator.isValid("random text without operators", context));
        assertTrue(validator.isValid("simple text search", context));
        assertTrue(validator.isValid("meeting notes", context));
        assertTrue(validator.isValid("project-name", context));
    }
}