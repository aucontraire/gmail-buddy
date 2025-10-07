package com.aucontraire.gmailbuddy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Configuration properties for Gmail Buddy application.
 * Centralizes all configuration values with validation and default values.
 * 
 * @author Gmail Buddy Team
 * @since 1.0
 */
@ConfigurationProperties(prefix = "gmail-buddy")
@Validated
public record GmailBuddyProperties(
    @Valid @NotNull GmailApi gmailApi,
    @Valid @NotNull OAuth2 oauth2,
    @Valid @NotNull ErrorHandling errorHandling,
    @Valid @NotNull Validation validation,
    @Valid @NotNull Security security,
    @Valid @NotNull Environment environment
) {

    /**
     * Gmail API related configuration properties.
     */
    public record GmailApi(
        @NotBlank String applicationName,
        @NotBlank String defaultUserId,
        @Min(1) @Max(1000) int defaultLatestMessagesLimit,
        @Min(1) @Max(1000) long batchDeleteMaxResults,
        @Valid @NotNull RateLimit rateLimit,
        @Valid @NotNull ServiceUnavailable serviceUnavailable,
        @Valid @NotNull MessageProcessing messageProcessing,
        @Valid @NotNull QueryOperators queryOperators
    ) {
        public GmailApi {
            // Validation will be handled by the annotations
            // Default values will be set in application.properties
        }

        /**
         * Rate limiting configuration for Gmail API calls.
         */
        public record RateLimit(
            @Positive long defaultRetrySeconds,
            @Valid @NotNull BatchOperations batchOperations
        ) {
            // Default values are set in application.properties

            /**
             * Batch operation specific rate limiting configuration.
             */
            public record BatchOperations(
                @Min(100) @Max(5000) long delayBetweenBatchesMs,
                @Min(1) @Max(5) int maxRetryAttempts,
                @Min(500) @Max(10000) long initialBackoffMs,
                @Min(1) @Max(5) double backoffMultiplier,
                @Min(5000) @Max(60000) long maxBackoffMs,
                @Min(10) @Max(100) int maxBatchSize,
                @Min(0) @Max(100) long microDelayBetweenOperationsMs
            ) {
                // Default values are set in application.properties
            }
        }

        /**
         * Service unavailable retry configuration.
         */
        public record ServiceUnavailable(
            @Positive long defaultRetrySeconds
        ) {
            // Default values are set in application.properties
        }

        /**
         * Message processing configuration.
         */
        public record MessageProcessing(
            @Valid @NotNull MimeTypes mimeTypes,
            @Valid @NotNull Labels labels
        ) {
            public record MimeTypes(
                @NotBlank String html,
                @NotBlank String plain
            ) {
                // Default values are set in application.properties
            }

            public record Labels(
                @NotBlank String unread
            ) {
                // Default values are set in application.properties
            }
        }

        /**
         * Gmail query operators configuration.
         */
        public record QueryOperators(
            @NotBlank String from,
            @NotBlank String to,
            @NotBlank String subject,
            @NotBlank String hasAttachment,
            @NotBlank String label,
            @NotBlank String and
        ) {
            // Default values are set in application.properties
        }
    }

    /**
     * OAuth2 configuration properties.
     */
    public record OAuth2(
        @NotBlank String clientRegistrationId,
        @Valid @NotNull Token token
    ) {
        // Default values are set in application.properties

        /**
         * OAuth2 token configuration.
         */
        public record Token(
            @NotBlank String prefix
        ) {
            // Default values are set in application.properties
        }
    }

    /**
     * Error handling configuration properties.
     */
    public record ErrorHandling(
        @Valid @NotNull ErrorCodes errorCodes,
        @Valid @NotNull ErrorCategories errorCategories
    ) {
        /**
         * Error codes used throughout the application.
         */
        public record ErrorCodes(
            @NotBlank String rateLimitExceeded,
            @NotBlank String serviceUnavailable,
            @NotBlank String validationError,
            @NotBlank String constraintViolation,
            @NotBlank String gmailServiceError,
            @NotBlank String messageNotFound,
            @NotBlank String authenticationError,
            @NotBlank String authorizationError,
            @NotBlank String resourceNotFound,
            @NotBlank String gmailApiError,
            @NotBlank String internalServerError
        ) {
            // Default values are set in application.properties
        }

        /**
         * Error categories for response classification.
         */
        public record ErrorCategories(
            @NotBlank String clientError,
            @NotBlank String serverError
        ) {
            // Default values are set in application.properties
        }
    }

    /**
     * Validation configuration properties.
     */
    public record Validation(
        @Valid @NotNull GmailQuery gmailQuery,
        @Valid @NotNull Email email
    ) {
        /**
         * Gmail query validation patterns.
         */
        public record GmailQuery(
            @NotBlank String dangerousPattern,
            @NotBlank String validOperatorsPattern
        ) {
            // Default values are set in application.properties
        }

        /**
         * Email validation configuration.
         */
        public record Email(
            @NotBlank @Pattern(regexp = "^.+$") String pattern
        ) {
            // Default values are set in application.properties
        }
    }

    /**
     * Security configuration properties.
     */
    public record Security(
        @Valid @NotNull String[] permitAllPatterns,
        @Valid @NotNull OAuth2Security oauth2Security
    ) {
        // Default values are set in application.properties

        /**
         * OAuth2 security configuration.
         */
        public record OAuth2Security(
            @NotBlank String defaultSuccessUrl,
            @NotBlank String authorizationBaseUri
        ) {
            // Default values are set in application.properties
        }
    }

    /**
     * Environment configuration properties.
     */
    public record Environment(
        @Valid @NotNull EnvFile envFile
    ) {
        /**
         * Environment file configuration.
         */
        public record EnvFile(
            @NotBlank String directory,
            @NotBlank String name
        ) {
            // Default values are set in application.properties
        }
    }
}