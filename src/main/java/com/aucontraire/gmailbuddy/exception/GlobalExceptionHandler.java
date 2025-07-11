package com.aucontraire.gmailbuddy.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for the Gmail Buddy application.
 * Provides centralized exception handling across all controllers with support
 * for the new exception hierarchy, correlation IDs, and structured error responses.
 * 
 * @author Gmail Buddy Team
 * @since 1.0
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles all GmailBuddyException instances with proper HTTP status mapping.
     */
    @ExceptionHandler(GmailBuddyException.class)
    public ResponseEntity<ErrorResponse> handleGmailBuddyException(GmailBuddyException ex) {
        ErrorResponse.Builder builder = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .message(ex.getMessage())
                .correlationId(ex.getCorrelationId())
                .category(ex.isClientError() ? "CLIENT_ERROR" : "SERVER_ERROR");

        // Add retry information for specific exception types
        if (ex instanceof RateLimitException rateLimitEx) {
            builder.retryable(true).retryAfterSeconds(rateLimitEx.getRetryAfterSeconds());
        } else if (ex instanceof ServiceUnavailableException serviceEx) {
            builder.retryable(true).retryAfterSeconds(serviceEx.getRetryAfterSeconds());
        } else if (ex instanceof GmailApiException apiEx) {
            builder.retryable(apiEx.isRetryable());
        }

        ErrorResponse errorResponse = builder.build();
        HttpStatus status = HttpStatus.valueOf(ex.getHttpStatus());

        // Log appropriately based on error type
        if (ex.isClientError()) {
            logger.warn("Client error [{}]: {} (correlation: {})", ex.getErrorCode(), ex.getMessage(), ex.getCorrelationId());
        } else {
            logger.error("Server error [{}]: {} (correlation: {})", ex.getErrorCode(), ex.getMessage(), ex.getCorrelationId(), ex);
        }

        // Add retry headers for rate limiting and service unavailable
        HttpHeaders headers = new HttpHeaders();
        if (ex instanceof RateLimitException rateLimitEx) {
            headers.add("Retry-After", String.valueOf(rateLimitEx.getRetryAfterSeconds()));
        } else if (ex instanceof ServiceUnavailableException serviceEx) {
            headers.add("Retry-After", String.valueOf(serviceEx.getRetryAfterSeconds()));
        }

        return new ResponseEntity<>(errorResponse, headers, status);
    }

    /**
     * Handles Spring validation exceptions for request body validation.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });

        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("VALIDATION_ERROR")
                .message("Input validation failed")
                .details(fieldErrors)
                .category("CLIENT_ERROR")
                .build();

        logger.warn("Validation error: {}", fieldErrors);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles constraint validation exceptions for method parameters.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException ex) {
        Map<String, String> violations = new HashMap<>();
        
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String propertyPath = violation.getPropertyPath().toString();
            String message = violation.getMessage();
            violations.put(propertyPath, message);
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("CONSTRAINT_VIOLATION")
                .message("Constraint validation failed")
                .details(violations)
                .category("CLIENT_ERROR")
                .build();

        logger.warn("Constraint violation: {}", violations);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles legacy GmailServiceException for backward compatibility.
     * @deprecated Use new exception hierarchy instead
     */
    @ExceptionHandler(GmailServiceException.class)
    public ResponseEntity<ErrorResponse> handleGmailServiceException(GmailServiceException ex) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("GMAIL_SERVICE_ERROR")
                .message(ex.getMessage())
                .category("SERVER_ERROR")
                .build();

        logger.error("Gmail service error: {}", ex.getMessage(), ex);
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Handles legacy MessageNotFoundException for backward compatibility.
     * @deprecated Use ResourceNotFoundException instead
     */
    @ExceptionHandler(MessageNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotFoundException(MessageNotFoundException ex) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("MESSAGE_NOT_FOUND")
                .message(ex.getMessage())
                .category("CLIENT_ERROR")
                .build();

        logger.warn("Message not found: {}", ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    /**
     * Handles all unexpected exceptions as internal server errors.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        // Create an InternalServerException to get correlation ID
        InternalServerException internalEx = new InternalServerException("Unexpected error: " + ex.getMessage(), ex);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(internalEx.getErrorCode())
                .message("An unexpected error occurred")
                .correlationId(internalEx.getCorrelationId())
                .category("SERVER_ERROR")
                .build();

        logger.error("Unexpected error [{}]: {} (correlation: {})", internalEx.getErrorCode(), ex.getMessage(), internalEx.getCorrelationId(), ex);
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}