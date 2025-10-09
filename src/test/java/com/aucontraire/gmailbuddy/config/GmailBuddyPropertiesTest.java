package com.aucontraire.gmailbuddy.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
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
    void testDefaultBatchOperationsValues() {
        var batchOperations = properties.gmailApi().rateLimit().batchOperations();
        assertNotNull(batchOperations);

        // Test the updated batch size configuration (changed from 15 to 50)
        assertEquals(50, batchOperations.maxBatchSize());
        // Test the reduced delay configuration (changed from 2000ms to 500ms for P0-4)
        assertEquals(500L, batchOperations.delayBetweenBatchesMs());
        assertEquals(4, batchOperations.maxRetryAttempts());
        assertEquals(2000L, batchOperations.initialBackoffMs());
        assertEquals(2.5, batchOperations.backoffMultiplier());
        assertEquals(60000L, batchOperations.maxBackoffMs());
        assertEquals(10L, batchOperations.microDelayBetweenOperationsMs());
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
        // Create a valid BatchOperations instance for testing
        var validBatchOperations = new GmailBuddyProperties.GmailApi.RateLimit.BatchOperations(
            1000L, 3, 1000L, 2.0, 30000L, 50, 10L
        );
        var invalidRateLimit = new GmailBuddyProperties.GmailApi.RateLimit(0L, validBatchOperations); // Invalid (must be positive)
        
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

    // ===========================================
    // Batch Size Configuration Tests (P0-3)
    // ===========================================

    @Nested
    @SpringBootTest(classes = {ConfigurationPropertiesConfig.class})
    @EnableConfigurationProperties(GmailBuddyProperties.class)
    @DisplayName("Batch Size Configuration Tests")
    class BatchSizeConfigurationTest {

        @Autowired
        private GmailBuddyProperties properties;

        @Autowired
        private Validator validator;

        @Test
        @DisplayName("Should load batch size 50 from configuration")
        void shouldLoadBatchSize50FromConfiguration() {
            // Verify that max-batch-size=50 is loaded correctly from application.properties
            int batchSize = properties.gmailApi()
                .rateLimit()
                .batchOperations()
                .maxBatchSize();

            assertThat(batchSize).isEqualTo(50);
        }

        @Test
        @DisplayName("Batch size 50 should be within valid range @Min(10) @Max(100)")
        void batchSize50_ShouldBeWithinValidRange() {
            int batchSize = properties.gmailApi()
                .rateLimit()
                .batchOperations()
                .maxBatchSize();

            // Verify 50 is within the validation constraints
            assertThat(batchSize).isGreaterThanOrEqualTo(10);
            assertThat(batchSize).isLessThanOrEqualTo(100);
            assertThat(batchSize).isBetween(10, 100);
        }

        @Test
        @DisplayName("Should validate batch size configuration passes validation")
        void batchSizeConfiguration_ShouldPassValidation() {
            // Verify the entire configuration is valid
            Set<ConstraintViolation<GmailBuddyProperties>> violations = validator.validate(properties);
            assertTrue(violations.isEmpty(), "Configuration with batch-size=50 should be valid");
        }

        @Test
        @DisplayName("Should handle minimum boundary condition (batch-size=10)")
        void batchSizeValidation_MinimumBoundary_ShouldBeValid() {
            // Create configuration with minimum valid batch size
            var batchOperations = new GmailBuddyProperties.GmailApi.RateLimit.BatchOperations(
                2000L, 4, 2000L, 2.5, 60000L, 10, 10L // min batch size
            );

            Set<ConstraintViolation<GmailBuddyProperties.GmailApi.RateLimit.BatchOperations>> violations =
                validator.validate(batchOperations);

            assertTrue(violations.isEmpty(), "Batch size 10 should be valid (minimum)");
        }

        @Test
        @DisplayName("Should handle maximum boundary condition (batch-size=100)")
        void batchSizeValidation_MaximumBoundary_ShouldBeValid() {
            // Create configuration with maximum valid batch size
            var batchOperations = new GmailBuddyProperties.GmailApi.RateLimit.BatchOperations(
                2000L, 4, 2000L, 2.5, 60000L, 100, 10L // max batch size
            );

            Set<ConstraintViolation<GmailBuddyProperties.GmailApi.RateLimit.BatchOperations>> violations =
                validator.validate(batchOperations);

            assertTrue(violations.isEmpty(), "Batch size 100 should be valid (maximum)");
        }

        @Test
        @DisplayName("Should reject batch size below minimum (batch-size=9)")
        void batchSizeValidation_BelowMinimum_ShouldFailValidation() {
            // Create configuration with batch size below minimum
            var batchOperations = new GmailBuddyProperties.GmailApi.RateLimit.BatchOperations(
                2000L, 4, 2000L, 2.5, 60000L, 9, 10L // below min
            );

            Set<ConstraintViolation<GmailBuddyProperties.GmailApi.RateLimit.BatchOperations>> violations =
                validator.validate(batchOperations);

            assertFalse(violations.isEmpty(), "Batch size 9 should fail validation (below minimum)");
            assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().contains("maxBatchSize")));
        }

        @Test
        @DisplayName("Should reject batch size above maximum (batch-size=101)")
        void batchSizeValidation_AboveMaximum_ShouldFailValidation() {
            // Create configuration with batch size above maximum
            var batchOperations = new GmailBuddyProperties.GmailApi.RateLimit.BatchOperations(
                2000L, 4, 2000L, 2.5, 60000L, 101, 10L // above max
            );

            Set<ConstraintViolation<GmailBuddyProperties.GmailApi.RateLimit.BatchOperations>> violations =
                validator.validate(batchOperations);

            assertFalse(violations.isEmpty(), "Batch size 101 should fail validation (above maximum)");
            assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().contains("maxBatchSize")));
        }

        @Test
        @DisplayName("Should verify all batch operation configuration values are loaded correctly")
        void batchOperationsConfiguration_AllValues_ShouldLoadCorrectly() {
            var batchOperations = properties.gmailApi()
                .rateLimit()
                .batchOperations();

            // Verify all configuration values from application.properties
            assertAll("Batch Operations Configuration",
                () -> assertEquals(50, batchOperations.maxBatchSize(),
                    "Max batch size should be 50 (updated from 15)"),
                () -> assertEquals(500L, batchOperations.delayBetweenBatchesMs(),
                    "Delay between batches should be 500ms (reduced from 2000ms for P0-4)"),
                () -> assertEquals(4, batchOperations.maxRetryAttempts(),
                    "Max retry attempts should be 4"),
                () -> assertEquals(2000L, batchOperations.initialBackoffMs(),
                    "Initial backoff should be 2000ms"),
                () -> assertEquals(2.5, batchOperations.backoffMultiplier(),
                    "Backoff multiplier should be 2.5"),
                () -> assertEquals(60000L, batchOperations.maxBackoffMs(),
                    "Max backoff should be 60000ms"),
                () -> assertEquals(10L, batchOperations.microDelayBetweenOperationsMs(),
                    "Micro delay should be 10ms")
            );
        }
    }

    @Nested
    @SpringBootTest(classes = {ConfigurationPropertiesConfig.class})
    @EnableConfigurationProperties(GmailBuddyProperties.class)
    @TestPropertySource(properties = {
        "gmail-buddy.gmail-api.rate-limit.batch-operations.max-batch-size=75"
    })
    @DisplayName("Custom Batch Size Override Tests")
    class CustomBatchSizeOverrideTest {

        @Autowired
        private GmailBuddyProperties properties;

        @Test
        @DisplayName("Should allow overriding batch size with custom value")
        void customBatchSize_ShouldOverrideDefault() {
            int batchSize = properties.gmailApi()
                .rateLimit()
                .batchOperations()
                .maxBatchSize();

            assertEquals(75, batchSize, "Custom batch size should override default");
        }
    }

    // ===========================================
    // Inter-Batch Delay Configuration Tests (P0-4)
    // ===========================================

    @Nested
    @SpringBootTest(classes = {ConfigurationPropertiesConfig.class})
    @EnableConfigurationProperties(GmailBuddyProperties.class)
    @DisplayName("Inter-Batch Delay Configuration Tests (P0-4)")
    class InterBatchDelayConfigurationTest {

        @Autowired
        private GmailBuddyProperties properties;

        @Autowired
        private Validator validator;

        @Test
        @DisplayName("Should load delay-between-batches-ms as 500ms from configuration")
        void shouldLoadDelayAs500MsFromConfiguration() {
            // Verify that delay-between-batches-ms=500 is loaded correctly from application.properties
            long delay = properties.gmailApi()
                .rateLimit()
                .batchOperations()
                .delayBetweenBatchesMs();

            assertThat(delay).isEqualTo(500L);
        }

        @Test
        @DisplayName("Delay 500ms should be within valid range @Min(100) @Max(5000)")
        void delay500Ms_ShouldBeWithinValidRange() {
            long delay = properties.gmailApi()
                .rateLimit()
                .batchOperations()
                .delayBetweenBatchesMs();

            // Verify 500ms is within the validation constraints
            assertThat(delay).isGreaterThanOrEqualTo(100L);
            assertThat(delay).isLessThanOrEqualTo(5000L);
            assertThat(delay).isBetween(100L, 5000L);
        }

        @Test
        @DisplayName("Should validate delay configuration passes validation")
        void delayConfiguration_ShouldPassValidation() {
            // Verify the entire configuration is valid
            Set<ConstraintViolation<GmailBuddyProperties>> violations = validator.validate(properties);
            assertTrue(violations.isEmpty(), "Configuration with delay=500ms should be valid");
        }

        @Test
        @DisplayName("Should handle minimum boundary condition (delay=100ms)")
        void delayValidation_MinimumBoundary_ShouldBeValid() {
            // Create configuration with minimum valid delay
            var batchOperations = new GmailBuddyProperties.GmailApi.RateLimit.BatchOperations(
                100L, 4, 2000L, 2.5, 60000L, 50, 10L // min delay
            );

            Set<ConstraintViolation<GmailBuddyProperties.GmailApi.RateLimit.BatchOperations>> violations =
                validator.validate(batchOperations);

            assertTrue(violations.isEmpty(), "Delay 100ms should be valid (minimum)");
        }

        @Test
        @DisplayName("Should handle maximum boundary condition (delay=5000ms)")
        void delayValidation_MaximumBoundary_ShouldBeValid() {
            // Create configuration with maximum valid delay
            var batchOperations = new GmailBuddyProperties.GmailApi.RateLimit.BatchOperations(
                5000L, 4, 2000L, 2.5, 60000L, 50, 10L // max delay
            );

            Set<ConstraintViolation<GmailBuddyProperties.GmailApi.RateLimit.BatchOperations>> violations =
                validator.validate(batchOperations);

            assertTrue(violations.isEmpty(), "Delay 5000ms should be valid (maximum)");
        }

        @Test
        @DisplayName("Should reject delay below minimum (delay=99ms)")
        void delayValidation_BelowMinimum_ShouldFailValidation() {
            // Create configuration with delay below minimum
            var batchOperations = new GmailBuddyProperties.GmailApi.RateLimit.BatchOperations(
                99L, 4, 2000L, 2.5, 60000L, 50, 10L // below min
            );

            Set<ConstraintViolation<GmailBuddyProperties.GmailApi.RateLimit.BatchOperations>> violations =
                validator.validate(batchOperations);

            assertFalse(violations.isEmpty(), "Delay 99ms should fail validation (below minimum)");
            assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().contains("delayBetweenBatchesMs")));
        }

        @Test
        @DisplayName("Should reject delay above maximum (delay=5001ms)")
        void delayValidation_AboveMaximum_ShouldFailValidation() {
            // Create configuration with delay above maximum
            var batchOperations = new GmailBuddyProperties.GmailApi.RateLimit.BatchOperations(
                5001L, 4, 2000L, 2.5, 60000L, 50, 10L // above max
            );

            Set<ConstraintViolation<GmailBuddyProperties.GmailApi.RateLimit.BatchOperations>> violations =
                validator.validate(batchOperations);

            assertFalse(violations.isEmpty(), "Delay 5001ms should fail validation (above maximum)");
            assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().contains("delayBetweenBatchesMs")));
        }

        @Test
        @DisplayName("Should calculate performance improvement from 2000ms to 500ms (75% reduction)")
        void delayReduction_PerformanceImprovement_ShouldBe75Percent() {
            // Verify performance improvement calculation
            long oldDelay = 2000L;
            long newDelay = 500L;

            double reduction = ((double)(oldDelay - newDelay) / oldDelay) * 100;

            assertThat(reduction).isEqualTo(75.0);
            assertThat(newDelay).isEqualTo(properties.gmailApi()
                .rateLimit()
                .batchOperations()
                .delayBetweenBatchesMs());
        }

        @Test
        @DisplayName("Should calculate delay overhead for 10 batches (2000ms vs 500ms)")
        void delayOverhead_10Batches_ShouldDemonstrateSavings() {
            // Old delay overhead: 10 batches × 2000ms = 20 seconds
            // New delay overhead: 10 batches × 500ms = 5 seconds
            // Improvement: 15 seconds saved (75% reduction)

            int batchCount = 10;
            long oldDelay = 2000L;
            long newDelay = 500L;

            long oldOverhead = (batchCount - 1) * oldDelay; // 9 delays between 10 batches = 18000ms
            long newOverhead = (batchCount - 1) * newDelay; // 9 delays between 10 batches = 4500ms
            long savings = oldOverhead - newOverhead;

            assertThat(oldOverhead).isEqualTo(18000L);
            assertThat(newOverhead).isEqualTo(4500L);
            assertThat(savings).isEqualTo(13500L);
            assertThat((double)savings / oldOverhead * 100).isEqualTo(75.0);
        }

        @Test
        @DisplayName("Should verify delay overhead calculation for various batch counts")
        void delayOverhead_VariousBatchCounts_ShouldCalculateCorrectly() {
            // Test delay overhead for different batch counts
            long oldDelay = 2000L;
            long newDelay = 500L;

            // Test cases: batch count -> expected savings
            Map<Integer, Long> testCases = Map.of(
                5, 6000L,   // (5-1) × 1500ms = 6000ms saved
                10, 13500L, // (10-1) × 1500ms = 13500ms saved
                20, 28500L  // (20-1) × 1500ms = 28500ms saved
            );

            testCases.forEach((batchCount, expectedSavings) -> {
                long oldOverhead = (batchCount - 1) * oldDelay;
                long newOverhead = (batchCount - 1) * newDelay;
                long savings = oldOverhead - newOverhead;

                assertThat(savings)
                    .as("With %d batches: delay reduction should save %dms", batchCount, expectedSavings)
                    .isEqualTo(expectedSavings);
            });
        }

        @Test
        @DisplayName("Should verify all batch operation configuration values are loaded correctly")
        void batchOperationsConfiguration_AllValues_IncludingDelay_ShouldLoadCorrectly() {
            var batchOperations = properties.gmailApi()
                .rateLimit()
                .batchOperations();

            // Verify all configuration values from application.properties
            assertAll("Batch Operations Configuration",
                () -> assertEquals(50, batchOperations.maxBatchSize(),
                    "Max batch size should be 50"),
                () -> assertEquals(500L, batchOperations.delayBetweenBatchesMs(),
                    "Delay between batches should be 500ms (reduced from 2000ms)"),
                () -> assertEquals(4, batchOperations.maxRetryAttempts(),
                    "Max retry attempts should be 4"),
                () -> assertEquals(2000L, batchOperations.initialBackoffMs(),
                    "Initial backoff should be 2000ms"),
                () -> assertEquals(2.5, batchOperations.backoffMultiplier(),
                    "Backoff multiplier should be 2.5"),
                () -> assertEquals(60000L, batchOperations.maxBackoffMs(),
                    "Max backoff should be 60000ms"),
                () -> assertEquals(10L, batchOperations.microDelayBetweenOperationsMs(),
                    "Micro delay should be 10ms")
            );
        }
    }

    @Nested
    @SpringBootTest(classes = {ConfigurationPropertiesConfig.class})
    @EnableConfigurationProperties(GmailBuddyProperties.class)
    @TestPropertySource(properties = {
        "gmail-buddy.gmail-api.rate-limit.batch-operations.delay-between-batches-ms=1000"
    })
    @DisplayName("Custom Delay Override Tests")
    class CustomDelayOverrideTest {

        @Autowired
        private GmailBuddyProperties properties;

        @Test
        @DisplayName("Should allow overriding delay with custom value")
        void customDelay_ShouldOverrideDefault() {
            long delay = properties.gmailApi()
                .rateLimit()
                .batchOperations()
                .delayBetweenBatchesMs();

            assertEquals(1000L, delay, "Custom delay should override default");
        }
    }
}