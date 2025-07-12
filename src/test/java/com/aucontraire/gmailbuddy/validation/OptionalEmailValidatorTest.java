package com.aucontraire.gmailbuddy.validation;

import com.aucontraire.gmailbuddy.validation.OptionalEmailValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;

class OptionalEmailValidatorTest {

    private OptionalEmailValidator validator;

    @Mock
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        validator = new OptionalEmailValidator();
    }

    @Test
    void testValidEmail() {
        assertTrue(validator.isValid("test@example.com", context));
        assertTrue(validator.isValid("user.name+tag@domain.co.uk", context));
        assertTrue(validator.isValid("simple@test.org", context));
    }

    @Test
    void testInvalidEmail() {
        assertFalse(validator.isValid("invalid-email", context));
        assertFalse(validator.isValid("@example.com", context));
        assertFalse(validator.isValid("test@", context));
        assertFalse(validator.isValid("test.example.com", context));
        assertFalse(validator.isValid("test@.com", context));
    }

    @Test
    void testEmptyAndNullValues() {
        // These should be valid (optional field)
        assertTrue(validator.isValid(null, context));
        assertTrue(validator.isValid("", context));
        assertTrue(validator.isValid("   ", context));
    }

    @Test
    void testEmailWithWhitespace() {
        assertTrue(validator.isValid("  test@example.com  ", context));
    }
}