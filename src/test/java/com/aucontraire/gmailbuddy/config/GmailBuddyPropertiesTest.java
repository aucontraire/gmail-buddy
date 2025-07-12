package com.aucontraire.gmailbuddy.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for Gmail Buddy configuration properties.
 * Tests property loading, validation, default values, and environment-specific configurations.
 * 
 * @author Gmail Buddy Team
 * @since 1.0
 */
@SpringBootTest(classes = {ConfigurationPropertiesConfig.class})
@EnableConfigurationProperties(GmailBuddyProperties.class)
class GmailBuddyPropertiesTest {

    @Autowired
    private GmailBuddyProperties properties;

    @Autowired
    private Validator validator;

    // ===========================================
    // Default Values Tests
    // ===========================================

    @Test
    void testDefaultGmailApiValues() {
        assertNotNull(properties.gmailApi());
        assertEquals("gmail-buddy", properties.gmailApi().applicationName());
        assertEquals("me", properties.gmailApi().defaultUserId());
        assertEquals(50, properties.gmailApi().defaultLatestMessagesLimit());
        assertEquals(500L, properties.gmailApi().batchDeleteMaxResults());
    }

    @Test
    void testDefaultRateLimitValues() {
        assertNotNull(properties.gmailApi().rateLimit());
        assertEquals(60L, properties.gmailApi().rateLimit().defaultRetrySeconds());
    }

    @Test
    void testDefaultServiceUnavailableValues() {
        assertNotNull(properties.gmailApi().serviceUnavailable());
        assertEquals(300L, properties.gmailApi().serviceUnavailable().defaultRetrySeconds());
    }

    @Test
    void testDefaultMessageProcessingValues() {
        var messageProcessing = properties.gmailApi().messageProcessing();
        assertNotNull(messageProcessing);
        
        assertEquals("text/html", messageProcessing.mimeTypes().html());
        assertEquals("text/plain", messageProcessing.mimeTypes().plain());
        assertEquals("UNREAD", messageProcessing.labels().unread());
    }

    @Test
    void testDefaultQueryOperatorValues() {
        var operators = properties.gmailApi().queryOperators();
        assertNotNull(operators);
        
        assertEquals("from:", operators.from());
        assertEquals("to:", operators.to());
        assertEquals("subject:", operators.subject());
        assertEquals("has:attachment ", operators.hasAttachment());
        assertEquals("label:", operators.label());
        assertEquals("\\ AND\\ ", operators.and());
    }

    @Test
    void testDefaultOAuth2Values() {
        assertNotNull(properties.oauth2());
        assertEquals("google", properties.oauth2().clientRegistrationId());
        assertEquals("Bearer ", properties.oauth2().token().prefix());
    }

    @Test
    void testDefaultErrorHandlingValues() {
        var errorHandling = properties.errorHandling();
        assertNotNull(errorHandling);
        
        var errorCodes = errorHandling.errorCodes();
        assertEquals("RATE_LIMIT_EXCEEDED", errorCodes.rateLimitExceeded());
        assertEquals("SERVICE_UNAVAILABLE", errorCodes.serviceUnavailable());
        assertEquals("VALIDATION_ERROR", errorCodes.validationError());
        assertEquals("CONSTRAINT_VIOLATION", errorCodes.constraintViolation());
        assertEquals("GMAIL_SERVICE_ERROR", errorCodes.gmailServiceError());
        assertEquals("MESSAGE_NOT_FOUND", errorCodes.messageNotFound());
        assertEquals("AUTHENTICATION_ERROR", errorCodes.authenticationError());
        assertEquals("AUTHORIZATION_ERROR", errorCodes.authorizationError());
        assertEquals("RESOURCE_NOT_FOUND", errorCodes.resourceNotFound());
        assertEquals("GMAIL_API_ERROR", errorCodes.gmailApiError());
        assertEquals("INTERNAL_SERVER_ERROR", errorCodes.internalServerError());
        
        var errorCategories = errorHandling.errorCategories();
        assertEquals("CLIENT_ERROR", errorCategories.clientError());
        assertEquals("SERVER_ERROR", errorCategories.serverError());
    }

    @Test
    void testDefaultValidationValues() {
        var validation = properties.validation();
        assertNotNull(validation);
        
        assertNotNull(validation.gmailQuery().dangerousPattern());
        assertTrue(validation.gmailQuery().dangerousPattern().contains("[<>\"'&;"));
        
        assertNotNull(validation.gmailQuery().validOperatorsPattern());
        assertTrue(validation.gmailQuery().validOperatorsPattern().startsWith("^"));
        
        assertNotNull(validation.email().pattern());
        assertTrue(validation.email().pattern().contains("@"));
    }

    @Test
    void testDefaultSecurityValues() {
        var security = properties.security();
        assertNotNull(security);
        
        assertNotNull(security.permitAllPatterns());
        assertEquals(2, security.permitAllPatterns().length);
        assertEquals("/login**", security.permitAllPatterns()[0]);
        assertEquals("/oauth2/**", security.permitAllPatterns()[1]);
        
        assertEquals("/dashboard", security.oauth2Security().defaultSuccessUrl());
        assertEquals("/oauth2/authorization", security.oauth2Security().authorizationBaseUri());
    }

    @Test
    void testDefaultEnvironmentValues() {
        var environment = properties.environment();
        assertNotNull(environment);
        
        assertEquals("./", environment.envFile().directory());
        assertEquals(".env", environment.envFile().name());
    }

    // ===========================================
    // Validation Tests
    // ===========================================

    @Test
    void testValidConfigurationPassesValidation() {
        Set<ConstraintViolation<GmailBuddyProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty(), "Default configuration should be valid");
    }

    @Test
    void testInvalidApplicationNameFailsValidation() {
        var invalidGmailApi = new GmailBuddyProperties.GmailApi(
                "", // Invalid empty application name
                properties.gmailApi().defaultUserId(),
                properties.gmailApi().defaultLatestMessagesLimit(),
                properties.gmailApi().batchDeleteMaxResults(),
                properties.gmailApi().rateLimit(),
                properties.gmailApi().serviceUnavailable(),
                properties.gmailApi().messageProcessing(),
                properties.gmailApi().queryOperators()
        );
        
        var invalidProperties = new GmailBuddyProperties(
                invalidGmailApi,
                properties.oauth2(),
                properties.errorHandling(),
                properties.validation(),
                properties.security(),
                properties.environment()
        );
        
        Set<ConstraintViolation<GmailBuddyProperties>> violations = validator.validate(invalidProperties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("blank")));
    }

    @Test
    void testInvalidLatestMessagesLimitFailsValidation() {
        var invalidGmailApi = new GmailBuddyProperties.GmailApi(
                properties.gmailApi().applicationName(),
                properties.gmailApi().defaultUserId(),
                0, // Invalid limit (must be >= 1)
                properties.gmailApi().batchDeleteMaxResults(),
                properties.gmailApi().rateLimit(),
                properties.gmailApi().serviceUnavailable(),
                properties.gmailApi().messageProcessing(),
                properties.gmailApi().queryOperators()
        );
        
        var invalidProperties = new GmailBuddyProperties(
                invalidGmailApi,
                properties.oauth2(),
                properties.errorHandling(),
                properties.validation(),
                properties.security(),
                properties.environment()
        );
        
        Set<ConstraintViolation<GmailBuddyProperties>> violations = validator.validate(invalidProperties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("1")));
    }

    @Test
    void testInvalidRetrySecondsFailsValidation() {
        var invalidRateLimit = new GmailBuddyProperties.GmailApi.RateLimit(0L); // Invalid (must be positive)
        
        var invalidGmailApi = new GmailBuddyProperties.GmailApi(
                properties.gmailApi().applicationName(),
                properties.gmailApi().defaultUserId(),
                properties.gmailApi().defaultLatestMessagesLimit(),
                properties.gmailApi().batchDeleteMaxResults(),
                invalidRateLimit,
                properties.gmailApi().serviceUnavailable(),
                properties.gmailApi().messageProcessing(),
                properties.gmailApi().queryOperators()
        );
        
        var invalidProperties = new GmailBuddyProperties(
                invalidGmailApi,
                properties.oauth2(),
                properties.errorHandling(),
                properties.validation(),
                properties.security(),
                properties.environment()
        );
        
        Set<ConstraintViolation<GmailBuddyProperties>> violations = validator.validate(invalidProperties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("positive") || v.getPropertyPath().toString().contains("defaultRetrySeconds")));
    }

    // ===========================================
    // Configuration Loading Tests
    // ===========================================

    @Test
    void testConfigurationPropertiesAreInjected() {
        assertNotNull(properties);
        assertNotNull(properties.gmailApi());
        assertNotNull(properties.oauth2());
        assertNotNull(properties.errorHandling());
        assertNotNull(properties.validation());
        assertNotNull(properties.security());
        assertNotNull(properties.environment());
    }

    // ===========================================
    // Helper Methods
    // ===========================================
    
    // Helper methods can be added here if needed for test utilities

    // ===========================================
    // Environment-Specific Configuration Tests
    // ===========================================

    @Nested
    @SpringBootTest(classes = {ConfigurationPropertiesConfig.class})
    @EnableConfigurationProperties(GmailBuddyProperties.class)
    @TestPropertySource(properties = {
        "gmail-buddy.gmail-api.default-latest-messages-limit=25",
        "gmail-buddy.gmail-api.rate-limit.default-retry-seconds=45"
    })
    class CustomPropertiesTest {

        @Autowired
        private GmailBuddyProperties properties;

        @Test
        void testCustomPropertiesOverrideDefaults() {
            assertEquals(25, properties.gmailApi().defaultLatestMessagesLimit());
            assertEquals(45L, properties.gmailApi().rateLimit().defaultRetrySeconds());
        }
    }

    @Nested
    @SpringBootTest(classes = {ConfigurationPropertiesConfig.class})
    @EnableConfigurationProperties(GmailBuddyProperties.class)
    @TestPropertySource(properties = {
        "gmail-buddy.oauth2.client-registration-id=custom-google",
        "gmail-buddy.oauth2.token.prefix=Token"
    })
    class OAuth2CustomPropertiesTest {

        @Autowired
        private GmailBuddyProperties properties;

        @Test
        void testCustomOAuth2Properties() {
            assertEquals("custom-google", properties.oauth2().clientRegistrationId());
            assertEquals("Token", properties.oauth2().token().prefix());
        }
    }

    @Nested
    @SpringBootTest(classes = {ConfigurationPropertiesConfig.class})
    @EnableConfigurationProperties(GmailBuddyProperties.class)
    @TestPropertySource(properties = {
        "gmail-buddy.error-handling.error-codes.rate-limit-exceeded=CUSTOM_RATE_LIMIT",
        "gmail-buddy.error-handling.error-categories.client-error=CLIENT_FAULT"
    })
    class ErrorHandlingCustomPropertiesTest {

        @Autowired
        private GmailBuddyProperties properties;

        @Test
        void testCustomErrorHandlingProperties() {
            assertEquals("CUSTOM_RATE_LIMIT", properties.errorHandling().errorCodes().rateLimitExceeded());
            assertEquals("CLIENT_FAULT", properties.errorHandling().errorCategories().clientError());
        }
    }

    @Nested
    @SpringBootTest(classes = {ConfigurationPropertiesConfig.class})
    @EnableConfigurationProperties(GmailBuddyProperties.class)
    @TestPropertySource(properties = {
        "gmail-buddy.validation.email.pattern=^[a-z]+@[a-z]+\\\\.[a-z]{2,4}$",
        "gmail-buddy.security.oauth2-security.default-success-url=/home"
    })
    class ValidationAndSecurityCustomPropertiesTest {

        @Autowired
        private GmailBuddyProperties properties;

        @Test
        void testCustomValidationAndSecurityProperties() {
            assertEquals("^[a-z]+@[a-z]+\\.[a-z]{2,4}$", properties.validation().email().pattern());
            assertEquals("/home", properties.security().oauth2Security().defaultSuccessUrl());
        }
    }
}