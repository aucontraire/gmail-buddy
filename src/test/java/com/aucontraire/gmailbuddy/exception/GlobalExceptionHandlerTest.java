package com.aucontraire.gmailbuddy.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for the GlobalExceptionHandler.
 * Tests all exception handlers, HTTP status mapping, error response structure,
 * retry headers, correlation ID propagation, and logging behavior.
 * 
 * @author Gmail Buddy Team
 * @since 1.0
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler globalExceptionHandler;

    @BeforeEach
    void setUp() {
        globalExceptionHandler = new GlobalExceptionHandler();
    }

    // ===========================================
    // GmailBuddyException Handler Tests
    // ===========================================

    @Test
    void testHandleGmailBuddyException_ValidationException() {
        ValidationException exception = new ValidationException("Invalid input data");
        
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGmailBuddyException(exception);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals("VALIDATION_ERROR", errorResponse.getCode());
        assertEquals("Invalid input data", errorResponse.getMessage());
        assertEquals(exception.getCorrelationId(), errorResponse.getCorrelationId());
        assertEquals("CLIENT_ERROR", errorResponse.getCategory());
        assertNull(errorResponse.getRetryable());
        assertNull(errorResponse.getRetryAfterSeconds());
        assertNotNull(errorResponse.getTimestamp());
    }

    @Test
    void testHandleGmailBuddyException_AuthenticationException() {
        AuthenticationException exception = new AuthenticationException("Authentication failed");
        
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGmailBuddyException(exception);
        
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals("AUTHENTICATION_ERROR", errorResponse.getCode());
        assertEquals("Authentication failed", errorResponse.getMessage());
        assertEquals(exception.getCorrelationId(), errorResponse.getCorrelationId());
        assertEquals("CLIENT_ERROR", errorResponse.getCategory());
    }

    @Test
    void testHandleGmailBuddyException_AuthorizationException() {
        AuthorizationException exception = new AuthorizationException("Access denied");
        
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGmailBuddyException(exception);
        
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals("AUTHORIZATION_ERROR", errorResponse.getCode());
        assertEquals("Access denied", errorResponse.getMessage());
        assertEquals("CLIENT_ERROR", errorResponse.getCategory());
    }

    @Test
    void testHandleGmailBuddyException_ResourceNotFoundException() {
        ResourceNotFoundException exception = new ResourceNotFoundException("Resource not found");
        
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGmailBuddyException(exception);
        
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals("RESOURCE_NOT_FOUND", errorResponse.getCode());
        assertEquals("Resource not found", errorResponse.getMessage());
        assertEquals("CLIENT_ERROR", errorResponse.getCategory());
    }

    @Test
    void testHandleGmailBuddyException_RateLimitException() {
        long retryAfter = 120L;
        RateLimitException exception = new RateLimitException("Rate limit exceeded", retryAfter);
        
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGmailBuddyException(exception);
        
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        
        // Check Retry-After header
        assertNotNull(response.getHeaders().get("Retry-After"));
        assertEquals("120", response.getHeaders().getFirst("Retry-After"));
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals("RATE_LIMIT_EXCEEDED", errorResponse.getCode());
        assertEquals("Rate limit exceeded", errorResponse.getMessage());
        assertEquals("CLIENT_ERROR", errorResponse.getCategory());
        assertEquals(true, errorResponse.getRetryable());
        assertEquals(retryAfter, errorResponse.getRetryAfterSeconds());
    }

    @Test
    void testHandleGmailBuddyException_GmailApiException_Retryable() {
        IOException cause = new IOException("Network timeout");
        GmailApiException exception = new GmailApiException("Gmail API error", cause);
        
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGmailBuddyException(exception);
        
        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals("GMAIL_API_ERROR", errorResponse.getCode());
        assertEquals("Gmail API error", errorResponse.getMessage());
        assertEquals("SERVER_ERROR", errorResponse.getCategory());
        assertEquals(true, errorResponse.getRetryable());
        assertNull(errorResponse.getRetryAfterSeconds());
    }

    @Test
    void testHandleGmailBuddyException_GmailApiException_NonRetryable() {
        GmailApiException exception = new GmailApiException("Gmail API configuration error", false);
        
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGmailBuddyException(exception);
        
        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals("GMAIL_API_ERROR", errorResponse.getCode());
        assertEquals("Gmail API configuration error", errorResponse.getMessage());
        assertEquals("SERVER_ERROR", errorResponse.getCategory());
        assertEquals(false, errorResponse.getRetryable());
    }

    @Test
    void testHandleGmailBuddyException_ServiceUnavailableException() {
        long retryAfter = 300L;
        ServiceUnavailableException exception = new ServiceUnavailableException("Service temporarily unavailable", retryAfter);
        
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGmailBuddyException(exception);
        
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        
        // Check Retry-After header
        assertNotNull(response.getHeaders().get("Retry-After"));
        assertEquals("300", response.getHeaders().getFirst("Retry-After"));
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals("SERVICE_UNAVAILABLE", errorResponse.getCode());
        assertEquals("Service temporarily unavailable", errorResponse.getMessage());
        assertEquals("SERVER_ERROR", errorResponse.getCategory());
        assertEquals(true, errorResponse.getRetryable());
        assertEquals(retryAfter, errorResponse.getRetryAfterSeconds());
    }

    @Test
    void testHandleGmailBuddyException_InternalServerException() {
        InternalServerException exception = new InternalServerException("Critical system error", "test-correlation-123");
        
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGmailBuddyException(exception);
        
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals("INTERNAL_SERVER_ERROR", errorResponse.getCode());
        assertEquals("Critical system error", errorResponse.getMessage());
        assertEquals("test-correlation-123", errorResponse.getCorrelationId());
        assertEquals("SERVER_ERROR", errorResponse.getCategory());
        assertNull(errorResponse.getRetryable());
    }

    // ===========================================
    // Validation Exception Handler Tests
    // ===========================================

    @Test
    void testHandleValidationExceptions_MethodArgumentNotValidException() {
        // Create mock MethodArgumentNotValidException
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        
        // Create mock field errors
        FieldError fieldError1 = new FieldError("filterCriteria", "from", "Must be a valid email address");
        FieldError fieldError2 = new FieldError("filterCriteria", "subject", "Subject must not exceed 255 characters");
        
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getAllErrors()).thenReturn(java.util.List.of(fieldError1, fieldError2));
        
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleValidationExceptions(exception);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals("VALIDATION_ERROR", errorResponse.getCode());
        assertEquals("Input validation failed", errorResponse.getMessage());
        assertEquals("CLIENT_ERROR", errorResponse.getCategory());
        
        // Check field errors in details
        assertNotNull(errorResponse.getDetails());
        assertEquals("Must be a valid email address", errorResponse.getDetails().get("from"));
        assertEquals("Subject must not exceed 255 characters", errorResponse.getDetails().get("subject"));
    }

    @Test
    void testHandleConstraintViolationException() {
        // Create mock ConstraintViolationException
        ConstraintViolationException exception = mock(ConstraintViolationException.class);
        
        // Create mock constraint violations
        ConstraintViolation<?> violation1 = mock(ConstraintViolation.class);
        ConstraintViolation<?> violation2 = mock(ConstraintViolation.class);
        Path path1 = mock(Path.class);
        Path path2 = mock(Path.class);
        
        when(violation1.getPropertyPath()).thenReturn(path1);
        when(violation1.getMessage()).thenReturn("Query contains invalid characters");
        when(path1.toString()).thenReturn("query");
        
        when(violation2.getPropertyPath()).thenReturn(path2);
        when(violation2.getMessage()).thenReturn("Email format is invalid");
        when(path2.toString()).thenReturn("from");
        
        when(exception.getConstraintViolations()).thenReturn(Set.of(violation1, violation2));
        
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleConstraintViolationException(exception);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals("CONSTRAINT_VIOLATION", errorResponse.getCode());
        assertEquals("Constraint validation failed", errorResponse.getMessage());
        assertEquals("CLIENT_ERROR", errorResponse.getCategory());
        
        // Check constraint violations in details
        assertNotNull(errorResponse.getDetails());
        assertEquals("Query contains invalid characters", errorResponse.getDetails().get("query"));
        assertEquals("Email format is invalid", errorResponse.getDetails().get("from"));
    }

    // ===========================================
    // Legacy Exception Handler Tests
    // ===========================================

    @Test
    void testHandleGmailApiException() {
        GmailApiException exception = new GmailApiException("Gmail API error");
        
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGmailBuddyException(exception);
        
        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals("GMAIL_API_ERROR", errorResponse.getCode());
        assertEquals("Gmail API error", errorResponse.getMessage());
        assertEquals("SERVER_ERROR", errorResponse.getCategory());
        assertNotNull(errorResponse.getCorrelationId()); // Modern handler extracts correlation ID
    }

    @Test
    void testHandleResourceNotFoundException() {
        ResourceNotFoundException exception = new ResourceNotFoundException("Resource not found");
        
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGmailBuddyException(exception);
        
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals("RESOURCE_NOT_FOUND", errorResponse.getCode());
        assertEquals("Resource not found", errorResponse.getMessage());
        assertEquals("CLIENT_ERROR", errorResponse.getCategory());
        assertNotNull(errorResponse.getCorrelationId()); // Modern handler extracts correlation ID
    }

    // ===========================================
    // Generic Exception Handler Tests
    // ===========================================

    @Test
    void testHandleGenericException() {
        RuntimeException exception = new RuntimeException("Unexpected runtime error");
        
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGenericException(exception);
        
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals("INTERNAL_SERVER_ERROR", errorResponse.getCode());
        assertEquals("An unexpected error occurred", errorResponse.getMessage());
        assertEquals("SERVER_ERROR", errorResponse.getCategory());
        assertNotNull(errorResponse.getCorrelationId()); // Should generate correlation ID
    }

    @Test
    void testHandleGenericException_WithNullPointerException() {
        NullPointerException exception = new NullPointerException("Null pointer access");
        
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGenericException(exception);
        
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals("INTERNAL_SERVER_ERROR", errorResponse.getCode());
        assertEquals("An unexpected error occurred", errorResponse.getMessage());
        assertEquals("SERVER_ERROR", errorResponse.getCategory());
        assertNotNull(errorResponse.getCorrelationId());
    }

    // ===========================================
    // Retry Header Tests
    // ===========================================

    @Test
    void testRetryAfterHeaders_RateLimitException() {
        RateLimitException exception = new RateLimitException("Rate limited", 180L);
        
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGmailBuddyException(exception);
        
        assertEquals("180", response.getHeaders().getFirst("Retry-After"));
    }

    @Test
    void testRetryAfterHeaders_ServiceUnavailableException() {
        ServiceUnavailableException exception = new ServiceUnavailableException("Service down", 600L);
        
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGmailBuddyException(exception);
        
        assertEquals("600", response.getHeaders().getFirst("Retry-After"));
    }

    @Test
    void testNoRetryAfterHeaders_OtherExceptions() {
        ValidationException exception = new ValidationException("Validation error");
        
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGmailBuddyException(exception);
        
        assertNull(response.getHeaders().getFirst("Retry-After"));
    }

    // ===========================================
    // Error Response Structure Tests
    // ===========================================

    @Test
    void testErrorResponseStructure_AllFields() {
        RateLimitException exception = new RateLimitException("Rate limit test", 90L);
        
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGmailBuddyException(exception);
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        
        // Verify all expected fields are present
        assertNotNull(errorResponse.getCode());
        assertNotNull(errorResponse.getMessage());
        assertNotNull(errorResponse.getCorrelationId());
        assertNotNull(errorResponse.getCategory());
        assertNotNull(errorResponse.getTimestamp());
        assertNotNull(errorResponse.getRetryable());
        assertNotNull(errorResponse.getRetryAfterSeconds());
        
        // Verify field values
        assertEquals("RATE_LIMIT_EXCEEDED", errorResponse.getCode());
        assertEquals("Rate limit test", errorResponse.getMessage());
        assertEquals("CLIENT_ERROR", errorResponse.getCategory());
        assertEquals(true, errorResponse.getRetryable());
        assertEquals(90L, errorResponse.getRetryAfterSeconds());
    }

    @Test
    void testErrorResponseStructure_MinimalFields() {
        ValidationException exception = new ValidationException("Simple validation error");
        
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGmailBuddyException(exception);
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        
        // Verify required fields are present
        assertNotNull(errorResponse.getCode());
        assertNotNull(errorResponse.getMessage());
        assertNotNull(errorResponse.getCorrelationId());
        assertNotNull(errorResponse.getCategory());
        assertNotNull(errorResponse.getTimestamp());
        
        // Verify optional fields are null (due to @JsonInclude(NON_NULL))
        assertNull(errorResponse.getRetryable());
        assertNull(errorResponse.getRetryAfterSeconds());
        assertNull(errorResponse.getDetails());
    }

    // ===========================================
    // Correlation ID Propagation Tests
    // ===========================================

    @Test
    void testCorrelationIdPropagation() {
        String expectedCorrelationId = "test-correlation-456";
        ValidationException exception = new ValidationException("Test message", expectedCorrelationId);
        
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGmailBuddyException(exception);
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals(expectedCorrelationId, errorResponse.getCorrelationId());
    }

    @Test
    void testCorrelationIdGeneration_GenericException() {
        RuntimeException exception = new RuntimeException("Generic error");
        
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGenericException(exception);
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertNotNull(errorResponse.getCorrelationId());
        // Should be UUID format
        assertTrue(errorResponse.getCorrelationId().matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"));
    }
}