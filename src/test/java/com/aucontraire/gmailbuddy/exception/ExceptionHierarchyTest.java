package com.aucontraire.gmailbuddy.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for the Gmail Buddy exception hierarchy.
 * Tests all exception classes, their inheritance, correlation ID generation,
 * HTTP status mapping, and error categorization.
 * 
 * @author Gmail Buddy Team
 * @since 1.0
 */
class ExceptionHierarchyTest {

    // ===========================================
    // Base Exception Tests
    // ===========================================

    @Test
    void testGmailBuddyException_abstractClass() {
        // GmailBuddyException should be abstract and cannot be instantiated directly
        assertTrue(GmailBuddyException.class.isAssignableFrom(ValidationException.class));
        assertTrue(GmailBuddyException.class.isAssignableFrom(GmailApiException.class));
    }

    // ===========================================
    // Client Exception Tests (4xx errors)
    // ===========================================

    @Test
    void testValidationException_basicConstructor() {
        String message = "Validation failed";
        ValidationException exception = new ValidationException(message);
        
        assertEquals("VALIDATION_ERROR", exception.getErrorCode());
        assertEquals(message, exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST.value(), exception.getHttpStatus());
        assertTrue(exception.isClientError());
        assertNotNull(exception.getCorrelationId());
        assertNull(exception.getCause());
    }

    @Test
    void testValidationException_withCause() {
        String message = "Validation failed";
        IllegalArgumentException cause = new IllegalArgumentException("Invalid input");
        ValidationException exception = new ValidationException(message, cause);
        
        assertEquals("VALIDATION_ERROR", exception.getErrorCode());
        assertEquals(message, exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST.value(), exception.getHttpStatus());
        assertTrue(exception.isClientError());
        assertNotNull(exception.getCorrelationId());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void testValidationException_withCorrelationId() {
        String message = "Validation failed";
        String correlationId = "test-correlation-123";
        ValidationException exception = new ValidationException(message, correlationId);
        
        assertEquals("VALIDATION_ERROR", exception.getErrorCode());
        assertEquals(message, exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST.value(), exception.getHttpStatus());
        assertTrue(exception.isClientError());
        assertEquals(correlationId, exception.getCorrelationId());
        assertNull(exception.getCause());
    }

    @Test
    void testAuthenticationException_basicConstructor() {
        String message = "Authentication failed";
        AuthenticationException exception = new AuthenticationException(message);
        
        assertEquals("AUTHENTICATION_ERROR", exception.getErrorCode());
        assertEquals(message, exception.getMessage());
        assertEquals(HttpStatus.UNAUTHORIZED.value(), exception.getHttpStatus());
        assertTrue(exception.isClientError());
        assertNotNull(exception.getCorrelationId());
    }

    @Test
    void testAuthenticationException_withCause() {
        String message = "Authentication failed";
        SecurityException cause = new SecurityException("Invalid token");
        AuthenticationException exception = new AuthenticationException(message, cause);
        
        assertEquals("AUTHENTICATION_ERROR", exception.getErrorCode());
        assertEquals(message, exception.getMessage());
        assertEquals(HttpStatus.UNAUTHORIZED.value(), exception.getHttpStatus());
        assertTrue(exception.isClientError());
        assertNotNull(exception.getCorrelationId());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void testAuthorizationException_basicConstructor() {
        String message = "Access denied";
        AuthorizationException exception = new AuthorizationException(message);
        
        assertEquals("AUTHORIZATION_ERROR", exception.getErrorCode());
        assertEquals(message, exception.getMessage());
        assertEquals(HttpStatus.FORBIDDEN.value(), exception.getHttpStatus());
        assertTrue(exception.isClientError());
        assertNotNull(exception.getCorrelationId());
    }

    @Test
    void testResourceNotFoundException_basicConstructor() {
        String message = "Resource not found";
        ResourceNotFoundException exception = new ResourceNotFoundException(message);
        
        assertEquals("RESOURCE_NOT_FOUND", exception.getErrorCode());
        assertEquals(message, exception.getMessage());
        assertEquals(HttpStatus.NOT_FOUND.value(), exception.getHttpStatus());
        assertTrue(exception.isClientError());
        assertNotNull(exception.getCorrelationId());
    }

    @Test
    void testRateLimitException_basicConstructor() {
        String message = "Rate limit exceeded";
        RateLimitException exception = new RateLimitException(message);
        
        assertEquals("RATE_LIMIT_EXCEEDED", exception.getErrorCode());
        assertEquals(message, exception.getMessage());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), exception.getHttpStatus());
        assertTrue(exception.isClientError());
        assertNotNull(exception.getCorrelationId());
        assertEquals(60L, exception.getRetryAfterSeconds()); // Default retry after
    }

    @Test
    void testRateLimitException_withCustomRetryAfter() {
        String message = "Rate limit exceeded";
        long retryAfter = 120L;
        RateLimitException exception = new RateLimitException(message, retryAfter);
        
        assertEquals("RATE_LIMIT_EXCEEDED", exception.getErrorCode());
        assertEquals(message, exception.getMessage());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), exception.getHttpStatus());
        assertTrue(exception.isClientError());
        assertNotNull(exception.getCorrelationId());
        assertEquals(retryAfter, exception.getRetryAfterSeconds());
    }

    @Test
    void testRateLimitException_withCauseAndRetryAfter() {
        String message = "Rate limit exceeded";
        long retryAfter = 300L;
        IOException cause = new IOException("API quota exceeded");
        RateLimitException exception = new RateLimitException(message, cause, retryAfter);
        
        assertEquals("RATE_LIMIT_EXCEEDED", exception.getErrorCode());
        assertEquals(message, exception.getMessage());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), exception.getHttpStatus());
        assertTrue(exception.isClientError());
        assertNotNull(exception.getCorrelationId());
        assertEquals(retryAfter, exception.getRetryAfterSeconds());
        assertEquals(cause, exception.getCause());
    }

    // ===========================================
    // Server Exception Tests (5xx errors)
    // ===========================================

    @Test
    void testGmailApiException_basicConstructor() {
        String message = "Gmail API error";
        GmailApiException exception = new GmailApiException(message);
        
        assertEquals("GMAIL_API_ERROR", exception.getErrorCode());
        assertEquals(message, exception.getMessage());
        assertEquals(HttpStatus.BAD_GATEWAY.value(), exception.getHttpStatus());
        assertFalse(exception.isClientError());
        assertNotNull(exception.getCorrelationId());
        assertFalse(exception.isRetryable()); // Default not retryable
    }

    @Test
    void testGmailApiException_withCause() {
        String message = "Gmail API error";
        IOException cause = new IOException("Connection timeout");
        GmailApiException exception = new GmailApiException(message, cause);
        
        assertEquals("GMAIL_API_ERROR", exception.getErrorCode());
        assertEquals(message, exception.getMessage());
        assertEquals(HttpStatus.BAD_GATEWAY.value(), exception.getHttpStatus());
        assertFalse(exception.isClientError());
        assertNotNull(exception.getCorrelationId());
        assertTrue(exception.isRetryable()); // Retryable when caused by IOException
        assertEquals(cause, exception.getCause());
    }

    @Test
    void testGmailApiException_withExplicitRetryable() {
        String message = "Gmail API error";
        boolean retryable = true;
        GmailApiException exception = new GmailApiException(message, retryable);
        
        assertEquals("GMAIL_API_ERROR", exception.getErrorCode());
        assertEquals(message, exception.getMessage());
        assertEquals(HttpStatus.BAD_GATEWAY.value(), exception.getHttpStatus());
        assertFalse(exception.isClientError());
        assertNotNull(exception.getCorrelationId());
        assertEquals(retryable, exception.isRetryable());
    }

    @Test
    void testGmailApiException_retryableLogic() {
        // Test automatic retry determination based on cause type
        IOException networkError = new IOException("Network error");
        GmailApiException retryableException = new GmailApiException("Network failure", networkError);
        assertTrue(retryableException.isRetryable());
        
        IllegalArgumentException businessError = new IllegalArgumentException("Invalid request");
        GmailApiException nonRetryableException = new GmailApiException("Business logic error", businessError);
        assertFalse(nonRetryableException.isRetryable());
    }

    @Test
    void testServiceUnavailableException_basicConstructor() {
        String message = "Service temporarily unavailable";
        ServiceUnavailableException exception = new ServiceUnavailableException(message);
        
        assertEquals("SERVICE_UNAVAILABLE", exception.getErrorCode());
        assertEquals(message, exception.getMessage());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), exception.getHttpStatus());
        assertFalse(exception.isClientError());
        assertNotNull(exception.getCorrelationId());
        assertEquals(300L, exception.getRetryAfterSeconds()); // Default retry after
    }

    @Test
    void testServiceUnavailableException_withCustomRetryAfter() {
        String message = "Service temporarily unavailable";
        long retryAfter = 180L;
        ServiceUnavailableException exception = new ServiceUnavailableException(message, retryAfter);
        
        assertEquals("SERVICE_UNAVAILABLE", exception.getErrorCode());
        assertEquals(message, exception.getMessage());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), exception.getHttpStatus());
        assertFalse(exception.isClientError());
        assertNotNull(exception.getCorrelationId());
        assertEquals(retryAfter, exception.getRetryAfterSeconds());
    }

    @Test
    void testInternalServerException_basicConstructor() {
        InternalServerException exception = new InternalServerException();
        
        assertEquals("INTERNAL_SERVER_ERROR", exception.getErrorCode());
        assertEquals("An unexpected error occurred while processing your request", exception.getMessage());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), exception.getHttpStatus());
        assertFalse(exception.isClientError());
        assertNotNull(exception.getCorrelationId());
    }

    @Test
    void testInternalServerException_withCause() {
        RuntimeException cause = new RuntimeException("Database connection failed");
        InternalServerException exception = new InternalServerException(cause);
        
        assertEquals("INTERNAL_SERVER_ERROR", exception.getErrorCode());
        assertEquals("An unexpected error occurred while processing your request", exception.getMessage());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), exception.getHttpStatus());
        assertFalse(exception.isClientError());
        assertNotNull(exception.getCorrelationId());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void testInternalServerException_withMessageAndCause() {
        String message = "Critical system error";
        Exception cause = new Exception("System failure");
        InternalServerException exception = new InternalServerException(message, cause);
        
        assertEquals("INTERNAL_SERVER_ERROR", exception.getErrorCode());
        assertEquals(message, exception.getMessage());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), exception.getHttpStatus());
        assertFalse(exception.isClientError());
        assertNotNull(exception.getCorrelationId());
        assertEquals(cause, exception.getCause());
    }

    // ===========================================
    // Legacy Exception Tests (Backward Compatibility)
    // ===========================================

    @Test
    void testResourceNotFoundException_properties() {
        String message = "Resource not found";
        ResourceNotFoundException exception = new ResourceNotFoundException(message);
        
        assertEquals("RESOURCE_NOT_FOUND", exception.getErrorCode());
        assertEquals(message, exception.getMessage());
        assertEquals(HttpStatus.NOT_FOUND.value(), exception.getHttpStatus());
        assertTrue(exception.isClientError());
        assertNotNull(exception.getCorrelationId());
    }

    @Test
    void testGmailApiException_properties() {
        String message = "Gmail API error";
        GmailApiException exception = new GmailApiException(message);
        
        assertEquals("GMAIL_API_ERROR", exception.getErrorCode());
        assertEquals(message, exception.getMessage());
        assertEquals(HttpStatus.BAD_GATEWAY.value(), exception.getHttpStatus());
        assertFalse(exception.isClientError());
        assertFalse(exception.isRetryable()); // Default not retryable
        assertNotNull(exception.getCorrelationId());
    }

    // ===========================================
    // Correlation ID Tests
    // ===========================================

    @Test
    void testCorrelationId_uniqueness() {
        ValidationException exception1 = new ValidationException("Test message 1");
        ValidationException exception2 = new ValidationException("Test message 2");
        
        assertNotNull(exception1.getCorrelationId());
        assertNotNull(exception2.getCorrelationId());
        assertNotEquals(exception1.getCorrelationId(), exception2.getCorrelationId());
    }

    @Test
    void testCorrelationId_format() {
        ValidationException exception = new ValidationException("Test message");
        String correlationId = exception.getCorrelationId();
        
        assertNotNull(correlationId);
        assertFalse(correlationId.isEmpty());
        // Correlation ID should be UUID format (36 characters with dashes)
        assertTrue(correlationId.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"));
    }

    @Test
    void testCorrelationId_preservation() {
        String customCorrelationId = "custom-correlation-id-123";
        ValidationException exception = new ValidationException("Test message", customCorrelationId);
        
        assertEquals(customCorrelationId, exception.getCorrelationId());
    }

    // ===========================================
    // Inheritance Tests
    // ===========================================

    @Test
    void testClientExceptionHierarchy() {
        ValidationException validationException = new ValidationException("Validation error");
        AuthenticationException authException = new AuthenticationException("Auth error");
        AuthorizationException authzException = new AuthorizationException("Authz error");
        ResourceNotFoundException notFoundException = new ResourceNotFoundException("Not found");
        RateLimitException rateLimitException = new RateLimitException("Rate limited");
        
        // All should be client exceptions
        assertTrue(validationException instanceof GmailBuddyClientException);
        assertTrue(authException instanceof GmailBuddyClientException);
        assertTrue(authzException instanceof GmailBuddyClientException);
        assertTrue(notFoundException instanceof GmailBuddyClientException);
        assertTrue(rateLimitException instanceof GmailBuddyClientException);
        
        // All should be GmailBuddyExceptions
        assertTrue(validationException instanceof GmailBuddyException);
        assertTrue(authException instanceof GmailBuddyException);
        assertTrue(authzException instanceof GmailBuddyException);
        assertTrue(notFoundException instanceof GmailBuddyException);
        assertTrue(rateLimitException instanceof GmailBuddyException);
        
        // All should be RuntimeExceptions
        assertTrue(validationException instanceof RuntimeException);
        assertTrue(authException instanceof RuntimeException);
        assertTrue(authzException instanceof RuntimeException);
        assertTrue(notFoundException instanceof RuntimeException);
        assertTrue(rateLimitException instanceof RuntimeException);
    }

    @Test
    void testServerExceptionHierarchy() {
        GmailApiException apiException = new GmailApiException("API error");
        ServiceUnavailableException serviceException = new ServiceUnavailableException("Service unavailable");
        InternalServerException internalException = new InternalServerException("Internal error", "test-correlation");
        
        // All should be server exceptions
        assertTrue(apiException instanceof GmailBuddyServerException);
        assertTrue(serviceException instanceof GmailBuddyServerException);
        assertTrue(internalException instanceof GmailBuddyServerException);
        
        // All should be GmailBuddyExceptions
        assertTrue(apiException instanceof GmailBuddyException);
        assertTrue(serviceException instanceof GmailBuddyException);
        assertTrue(internalException instanceof GmailBuddyException);
        
        // All should be RuntimeExceptions
        assertTrue(apiException instanceof RuntimeException);
        assertTrue(serviceException instanceof RuntimeException);
        assertTrue(internalException instanceof RuntimeException);
    }

    // ===========================================
    // Error Categorization Tests
    // ===========================================

    @Test
    void testClientErrorCategorization() {
        assertTrue(new ValidationException("Error").isClientError());
        assertTrue(new AuthenticationException("Error").isClientError());
        assertTrue(new AuthorizationException("Error").isClientError());
        assertTrue(new ResourceNotFoundException("Error").isClientError());
        assertTrue(new RateLimitException("Error").isClientError());
    }

    @Test
    void testServerErrorCategorization() {
        assertFalse(new GmailApiException("Error").isClientError());
        assertFalse(new ServiceUnavailableException("Error").isClientError());
        assertFalse(new InternalServerException("Error", "correlation").isClientError());
    }

    // ===========================================
    // HTTP Status Code Tests
    // ===========================================

    @Test
    void testHttpStatusCodes() {
        assertEquals(400, new ValidationException("Error").getHttpStatus());
        assertEquals(401, new AuthenticationException("Error").getHttpStatus());
        assertEquals(403, new AuthorizationException("Error").getHttpStatus());
        assertEquals(404, new ResourceNotFoundException("Error").getHttpStatus());
        assertEquals(429, new RateLimitException("Error").getHttpStatus());
        assertEquals(500, new InternalServerException("Error", "correlation").getHttpStatus());
        assertEquals(502, new GmailApiException("Error").getHttpStatus());
        assertEquals(503, new ServiceUnavailableException("Error").getHttpStatus());
    }
}