# Gmail Buddy Configuration Reference

This document provides a comprehensive reference for all configuration properties available in Gmail Buddy.

## Table of Contents

- [Overview](#overview)
- [Gmail API Configuration](#gmail-api-configuration)
- [OAuth2 Configuration](#oauth2-configuration)
- [Error Handling Configuration](#error-handling-configuration)
- [Validation Configuration](#validation-configuration)
- [Security Configuration](#security-configuration)
- [Environment Configuration](#environment-configuration)
- [Environment-Specific Configurations](#environment-specific-configurations)
- [Configuration Examples](#configuration-examples)

## Overview

Gmail Buddy uses Spring Boot's `@ConfigurationProperties` to centralize all configuration values. All properties are prefixed with `gmail-buddy` and organized into logical groups.

## Gmail API Configuration

Configuration properties for Gmail API interactions.

### Basic Settings

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `gmail-buddy.gmail-api.application-name` | String | `gmail-buddy` | Application name sent to Gmail API |
| `gmail-buddy.gmail-api.default-user-id` | String | `me` | Default Gmail user ID for API calls |
| `gmail-buddy.gmail-api.default-latest-messages-limit` | Integer | `50` | Default number of latest messages to fetch |
| `gmail-buddy.gmail-api.batch-delete-max-results` | Long | `500` | Maximum results for batch delete operations |

### Rate Limiting

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `gmail-buddy.gmail-api.rate-limit.default-retry-seconds` | Long | `60` | Default retry delay when rate limited (seconds) |

### Service Availability

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `gmail-buddy.gmail-api.service-unavailable.default-retry-seconds` | Long | `300` | Default retry delay when service unavailable (seconds) |

### Message Processing

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `gmail-buddy.gmail-api.message-processing.mime-types.html` | String | `text/html` | HTML MIME type for message parsing |
| `gmail-buddy.gmail-api.message-processing.mime-types.plain` | String | `text/plain` | Plain text MIME type for message parsing |
| `gmail-buddy.gmail-api.message-processing.labels.unread` | String | `UNREAD` | Gmail label name for unread messages |

### Query Operators

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `gmail-buddy.gmail-api.query-operators.from` | String | `from:` | Gmail query operator for sender |
| `gmail-buddy.gmail-api.query-operators.to` | String | `to:` | Gmail query operator for recipient |
| `gmail-buddy.gmail-api.query-operators.subject` | String | `subject:` | Gmail query operator for subject |
| `gmail-buddy.gmail-api.query-operators.has-attachment` | String | `has:attachment ` | Gmail query operator for attachments |
| `gmail-buddy.gmail-api.query-operators.label` | String | `label:` | Gmail query operator for labels |
| `gmail-buddy.gmail-api.query-operators.and` | String | ` AND ` | Gmail query AND operator |

## OAuth2 Configuration

Configuration for OAuth2 authentication with Google.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `gmail-buddy.oauth2.client-registration-id` | String | `google` | OAuth2 client registration ID |
| `gmail-buddy.oauth2.token.prefix` | String | `Bearer ` | Token prefix for authorization headers |

## Error Handling Configuration

Configuration for error codes and categories used throughout the application.

### Error Codes

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `gmail-buddy.error-handling.error-codes.rate-limit-exceeded` | String | `RATE_LIMIT_EXCEEDED` | Error code for rate limiting |
| `gmail-buddy.error-handling.error-codes.service-unavailable` | String | `SERVICE_UNAVAILABLE` | Error code for service unavailable |
| `gmail-buddy.error-handling.error-codes.validation-error` | String | `VALIDATION_ERROR` | Error code for validation failures |
| `gmail-buddy.error-handling.error-codes.constraint-violation` | String | `CONSTRAINT_VIOLATION` | Error code for constraint violations |
| `gmail-buddy.error-handling.error-codes.gmail-service-error` | String | `GMAIL_SERVICE_ERROR` | Error code for Gmail service errors |
| `gmail-buddy.error-handling.error-codes.message-not-found` | String | `MESSAGE_NOT_FOUND` | Error code for missing messages |
| `gmail-buddy.error-handling.error-codes.authentication-error` | String | `AUTHENTICATION_ERROR` | Error code for authentication failures |
| `gmail-buddy.error-handling.error-codes.authorization-error` | String | `AUTHORIZATION_ERROR` | Error code for authorization failures |
| `gmail-buddy.error-handling.error-codes.resource-not-found` | String | `RESOURCE_NOT_FOUND` | Error code for missing resources |
| `gmail-buddy.error-handling.error-codes.gmail-api-error` | String | `GMAIL_API_ERROR` | Error code for Gmail API errors |
| `gmail-buddy.error-handling.error-codes.internal-server-error` | String | `INTERNAL_SERVER_ERROR` | Error code for internal errors |

### Error Categories

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `gmail-buddy.error-handling.error-categories.client-error` | String | `CLIENT_ERROR` | Category for 4xx client errors |
| `gmail-buddy.error-handling.error-categories.server-error` | String | `SERVER_ERROR` | Category for 5xx server errors |

## Validation Configuration

Configuration for input validation patterns and rules.

### Gmail Query Validation

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `gmail-buddy.validation.gmail-query.dangerous-pattern` | String | `.*[<>"'&;\\|\\*\\?\\[\\]\\(\\)\\{\\}\\$\`\\\\].*` | Regex pattern for dangerous characters |
| `gmail-buddy.validation.gmail-query.valid-operators-pattern` | String | Complex regex | Regex pattern for valid Gmail operators |

### Email Validation

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `gmail-buddy.validation.email.pattern` | String | `^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$` | Regex pattern for email validation |

## Security Configuration

Configuration for application security settings.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `gmail-buddy.security.permit-all-patterns` | String Array | `["/login**", "/oauth2/**"]` | URL patterns that don't require authentication |
| `gmail-buddy.security.oauth2-security.default-success-url` | String | `/dashboard` | Default URL after successful OAuth2 login |
| `gmail-buddy.security.oauth2-security.authorization-base-uri` | String | `/oauth2/authorization` | Base URI for OAuth2 authorization |

## Environment Configuration

Configuration for environment file handling.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `gmail-buddy.environment.env-file.directory` | String | `./` | Directory containing the .env file |
| `gmail-buddy.environment.env-file.name` | String | `.env` | Name of the environment file |

## Environment-Specific Configurations

Gmail Buddy provides different configurations for different environments:

### Development (`application-dev.properties`)
- Debug logging enabled
- Smaller message limits for faster development
- Shorter retry times
- Less strict security settings

### Production (`application-prod.properties`)
- Info level logging
- Higher message limits for performance
- Longer retry times for reliability
- Strict security settings

### Test (`application-test.properties`)
- Debug logging for test debugging
- Very small limits for fast tests
- Minimal retry times
- Relaxed security for testing

## Configuration Examples

### Custom Rate Limiting
```properties
# Custom rate limiting configuration
gmail-buddy.gmail-api.rate-limit.default-retry-seconds=90
gmail-buddy.gmail-api.service-unavailable.default-retry-seconds=450
```

### Custom Error Messages
```properties
# Custom error codes for internationalization
gmail-buddy.error-handling.error-codes.rate-limit-exceeded=LIMITE_EXCEDIDO
gmail-buddy.error-handling.error-codes.validation-error=ERROR_VALIDACION
```

### Custom Validation Patterns
```properties
# Stricter email validation
gmail-buddy.validation.email.pattern=^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}$

# Custom dangerous character pattern
gmail-buddy.validation.gmail-query.dangerous-pattern=.*[<>\"'].*
```

### Custom Gmail API Settings
```properties
# Custom application name and limits
gmail-buddy.gmail-api.application-name=my-custom-gmail-app
gmail-buddy.gmail-api.default-latest-messages-limit=75
gmail-buddy.gmail-api.batch-delete-max-results=750
```

## Validation Rules

All configuration properties include validation annotations to ensure:

- Required properties are not null or blank
- Numeric values are within acceptable ranges
- String patterns are valid regex expressions
- Array values contain valid elements

Invalid configuration will prevent application startup with clear error messages indicating which properties need correction.

## Best Practices

1. **Environment-Specific Overrides**: Use environment-specific property files for different deployment environments
2. **Security**: Never commit sensitive values like client secrets to version control
3. **Testing**: Use the test profile for unit and integration tests
4. **Monitoring**: Adjust retry times and limits based on actual Gmail API usage patterns
5. **Validation**: Customize validation patterns based on your application's security requirements

## Troubleshooting

### Configuration Not Loading
- Ensure `@EnableConfigurationProperties` is present
- Verify property names match exactly (case-sensitive)
- Check for typos in property keys

### Validation Errors on Startup
- Review validation annotations in `GmailBuddyProperties`
- Ensure numeric values are within specified ranges
- Verify regex patterns are valid

### Environment Profile Issues
- Set `spring.profiles.active` properly
- Ensure environment-specific property files exist
- Check property precedence order