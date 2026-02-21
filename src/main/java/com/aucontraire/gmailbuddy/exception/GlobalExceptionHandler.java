package com.aucontraire.gmailbuddy.exception;

import com.aucontraire.gmailbuddy.constants.ProblemTypes;
import com.aucontraire.gmailbuddy.dto.error.ProblemDetail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for the Gmail Buddy application.
 * Provides centralized exception handling across all controllers with RFC 7807
 * compliant error responses, correlation IDs, and proper HTTP status code mapping.
 *
 * All error responses follow RFC 7807 (Problem Details for HTTP APIs) format
 * with consistent structure including type, title, status, detail, and instance.
 *
 * @author Gmail Buddy Team
 * @since 1.0
 */
@RestControllerAdvice(basePackages = "com.aucontraire.gmailbuddy.controller")
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String PROBLEM_JSON_MEDIA_TYPE = "application/problem+json";

    @Autowired
    private HttpServletRequest request;

    /**
     * Handles ResourceNotFoundException (message not found, etc.).
     * Maps to RFC 7807 ProblemDetail with 404 Not Found status.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleResourceNotFoundException(ResourceNotFoundException ex) {
        String requestId = getRequestId();
        HttpStatus status = HttpStatus.valueOf(ex.getHttpStatus());

        ProblemDetail problem = ProblemDetail.builder()
                .type(ProblemTypes.RESOURCE_NOT_FOUND)
                .title("Resource Not Found")
                .status(status.value())
                .detail(ex.getMessage())
                .instance(request.getRequestURI())
                .requestId(requestId)
                .retryable(false)
                .category("CLIENT_ERROR")
                .build();

        logger.warn("Resource not found [{}]: {} (correlation: {})", ex.getErrorCode(), ex.getMessage(), requestId);

        return buildProblemResponse(problem, status);
    }

    /**
     * Handles GmailApiException (Gmail API communication failures).
     * Maps to RFC 7807 ProblemDetail with 502 Bad Gateway status.
     */
    @ExceptionHandler(GmailApiException.class)
    public ResponseEntity<ProblemDetail> handleGmailApiException(GmailApiException ex) {
        String requestId = getRequestId();
        HttpStatus status = HttpStatus.valueOf(ex.getHttpStatus());

        ProblemDetail problem = ProblemDetail.builder()
                .type(ProblemTypes.GMAIL_API_ERROR)
                .title("Gmail API Error")
                .status(status.value())
                .detail(ex.getMessage())
                .instance(request.getRequestURI())
                .requestId(requestId)
                .retryable(ex.isRetryable())
                .category("SERVER_ERROR")
                .build();

        logger.error("Gmail API error [{}]: {} (correlation: {}, retryable: {})",
                ex.getErrorCode(), ex.getMessage(), requestId, ex.isRetryable(), ex);

        return buildProblemResponse(problem, status);
    }

    /**
     * Handles AuthenticationException (OAuth2 failures, invalid tokens).
     * Maps to RFC 7807 ProblemDetail with 401 Unauthorized status.
     */
    @ExceptionHandler({
            com.aucontraire.gmailbuddy.exception.AuthenticationException.class,
            AuthenticationException.class
    })
    public ResponseEntity<ProblemDetail> handleAuthenticationException(Exception ex) {
        String requestId = getRequestId();
        HttpStatus status = HttpStatus.UNAUTHORIZED;

        String message = ex.getMessage() != null ? ex.getMessage() : "Authentication failed";

        ProblemDetail problem = ProblemDetail.builder()
                .type(ProblemTypes.AUTHENTICATION_FAILED)
                .title("Authentication Failed")
                .status(status.value())
                .detail(message)
                .instance(request.getRequestURI())
                .requestId(requestId)
                .retryable(false)
                .category("CLIENT_ERROR")
                .build();

        logger.warn("Authentication failed: {} (correlation: {})", message, requestId);

        return buildProblemResponse(problem, status);
    }

    /**
     * Handles AuthorizationException and AccessDeniedException (permission failures).
     * Maps to RFC 7807 ProblemDetail with 403 Forbidden status.
     */
    @ExceptionHandler({
            AuthorizationException.class,
            AccessDeniedException.class
    })
    public ResponseEntity<ProblemDetail> handleAuthorizationException(Exception ex) {
        String requestId = getRequestId();
        HttpStatus status = HttpStatus.FORBIDDEN;

        String message = ex.getMessage() != null ? ex.getMessage() : "Access denied";

        ProblemDetail problem = ProblemDetail.builder()
                .type(ProblemTypes.AUTHORIZATION_FAILED)
                .title("Authorization Failed")
                .status(status.value())
                .detail(message)
                .instance(request.getRequestURI())
                .requestId(requestId)
                .retryable(false)
                .category("CLIENT_ERROR")
                .build();

        logger.warn("Authorization failed: {} (correlation: {})", message, requestId);

        return buildProblemResponse(problem, status);
    }

    /**
     * Handles RateLimitException (too many requests).
     * Maps to RFC 7807 ProblemDetail with 429 Too Many Requests status.
     */
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ProblemDetail> handleRateLimitException(RateLimitException ex) {
        String requestId = getRequestId();
        HttpStatus status = HttpStatus.valueOf(ex.getHttpStatus());

        ProblemDetail problem = ProblemDetail.builder()
                .type(ProblemTypes.RATE_LIMIT_EXCEEDED)
                .title("Rate Limit Exceeded")
                .status(status.value())
                .detail(ex.getMessage())
                .instance(request.getRequestURI())
                .requestId(requestId)
                .retryable(true)
                .category("CLIENT_ERROR")
                .extension("retryAfterSeconds", ex.getRetryAfterSeconds())
                .build();

        logger.warn("Rate limit exceeded [{}]: {} (correlation: {}, retry after: {}s)",
                ex.getErrorCode(), ex.getMessage(), requestId, ex.getRetryAfterSeconds());

        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", String.valueOf(ex.getRetryAfterSeconds()));
        headers.setContentType(MediaType.parseMediaType(PROBLEM_JSON_MEDIA_TYPE));
        headers.add("X-Request-ID", requestId);

        return new ResponseEntity<>(problem, headers, status);
    }

    /**
     * Handles ServiceUnavailableException (temporary service outages).
     * Maps to RFC 7807 ProblemDetail with 503 Service Unavailable status.
     */
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleServiceUnavailableException(ServiceUnavailableException ex) {
        String requestId = getRequestId();
        HttpStatus status = HttpStatus.valueOf(ex.getHttpStatus());

        ProblemDetail problem = ProblemDetail.builder()
                .type(ProblemTypes.SERVICE_UNAVAILABLE)
                .title("Service Unavailable")
                .status(status.value())
                .detail(ex.getMessage())
                .instance(request.getRequestURI())
                .requestId(requestId)
                .retryable(true)
                .category("SERVER_ERROR")
                .extension("retryAfterSeconds", ex.getRetryAfterSeconds())
                .build();

        logger.error("Service unavailable [{}]: {} (correlation: {}, retry after: {}s)",
                ex.getErrorCode(), ex.getMessage(), requestId, ex.getRetryAfterSeconds(), ex);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", String.valueOf(ex.getRetryAfterSeconds()));
        headers.setContentType(MediaType.parseMediaType(PROBLEM_JSON_MEDIA_TYPE));
        headers.add("X-Request-ID", requestId);

        return new ResponseEntity<>(problem, headers, status);
    }

    /**
     * Handles ValidationException (business rule validation failures).
     * Maps to RFC 7807 ProblemDetail with 400 Bad Request status.
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(ValidationException ex) {
        String requestId = getRequestId();
        HttpStatus status = HttpStatus.valueOf(ex.getHttpStatus());

        ProblemDetail problem = ProblemDetail.builder()
                .type(ProblemTypes.VALIDATION_ERROR)
                .title("Validation Error")
                .status(status.value())
                .detail(ex.getMessage())
                .instance(request.getRequestURI())
                .requestId(requestId)
                .retryable(false)
                .category("CLIENT_ERROR")
                .build();

        logger.warn("Validation error [{}]: {} (correlation: {})", ex.getErrorCode(), ex.getMessage(), requestId);

        return buildProblemResponse(problem, status);
    }

    /**
     * Handles generic GmailBuddyException instances not caught by more specific handlers.
     */
    @ExceptionHandler(GmailBuddyException.class)
    public ResponseEntity<ProblemDetail> handleGmailBuddyException(GmailBuddyException ex) {
        String requestId = getRequestId();
        HttpStatus status = HttpStatus.valueOf(ex.getHttpStatus());

        // Determine appropriate problem type based on error code
        String problemType = determineProblemType(ex.getErrorCode());

        ProblemDetail problem = ProblemDetail.builder()
                .type(problemType)
                .title(ex.getErrorCode())
                .status(status.value())
                .detail(ex.getMessage())
                .instance(request.getRequestURI())
                .requestId(requestId)
                .retryable(false)
                .category(ex.isClientError() ? "CLIENT_ERROR" : "SERVER_ERROR")
                .build();

        // Log appropriately based on error type
        if (ex.isClientError()) {
            logger.warn("Client error [{}]: {} (correlation: {})", ex.getErrorCode(), ex.getMessage(), requestId);
        } else {
            logger.error("Server error [{}]: {} (correlation: {})", ex.getErrorCode(), ex.getMessage(), requestId, ex);
        }

        return buildProblemResponse(problem, status);
    }

    /**
     * Handles Spring validation exceptions for request body validation.
     * Maps to RFC 7807 ProblemDetail with field-level validation errors.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        String requestId = getRequestId();
        Map<String, String> fieldErrors = new HashMap<>();

        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });

        ProblemDetail.Builder builder = ProblemDetail.builder()
                .type(ProblemTypes.VALIDATION_ERROR)
                .title("Validation Error")
                .status(HttpStatus.BAD_REQUEST.value())
                .detail("Input validation failed for " + fieldErrors.size() + " field(s)")
                .instance(request.getRequestURI())
                .requestId(requestId)
                .retryable(false)
                .category("CLIENT_ERROR");

        // Add field errors as extensions
        fieldErrors.forEach((field, message) -> builder.extension("field:" + field, message));

        ProblemDetail problem = builder.build();

        logger.warn("Validation error: {} (correlation: {})", fieldErrors, requestId);
        return buildProblemResponse(problem, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles constraint validation exceptions for method parameters.
     * Maps to RFC 7807 ProblemDetail with constraint violation details.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolationException(ConstraintViolationException ex) {
        String requestId = getRequestId();
        Map<String, String> violations = new HashMap<>();

        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String propertyPath = violation.getPropertyPath().toString();
            String message = violation.getMessage();
            violations.put(propertyPath, message);
        }

        ProblemDetail.Builder builder = ProblemDetail.builder()
                .type(ProblemTypes.CONSTRAINT_VIOLATION)
                .title("Constraint Violation")
                .status(HttpStatus.BAD_REQUEST.value())
                .detail("Constraint validation failed for " + violations.size() + " parameter(s)")
                .instance(request.getRequestURI())
                .requestId(requestId)
                .retryable(false)
                .category("CLIENT_ERROR");

        // Add constraint violations as extensions
        violations.forEach((property, message) -> builder.extension("constraint:" + property, message));

        ProblemDetail problem = builder.build();

        logger.warn("Constraint violation: {} (correlation: {})", violations, requestId);
        return buildProblemResponse(problem, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles all unexpected exceptions as internal server errors.
     * Maps to RFC 7807 ProblemDetail with 500 Internal Server Error status.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex) {
        String requestId = getRequestId();

        ProblemDetail problem = ProblemDetail.builder()
                .type(ProblemTypes.INTERNAL_ERROR)
                .title("Internal Server Error")
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .detail("An unexpected error occurred. Please contact support with the request ID.")
                .instance(request.getRequestURI())
                .requestId(requestId)
                .retryable(false)
                .category("SERVER_ERROR")
                .build();

        logger.error("Unexpected error: {} (correlation: {})", ex.getMessage(), requestId, ex);
        return buildProblemResponse(problem, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Helper method to get the request ID from MDC or generate a new one.
     *
     * @return the request ID
     */
    private String getRequestId() {
        String requestId = MDC.get("requestId");
        if (requestId == null || requestId.isEmpty()) {
            requestId = java.util.UUID.randomUUID().toString();
            MDC.put("requestId", requestId);
        }
        return requestId;
    }

    /**
     * Helper method to build a ResponseEntity with proper headers for RFC 7807 responses.
     *
     * @param problem the ProblemDetail to return
     * @param status the HTTP status
     * @return ResponseEntity with proper headers
     */
    private ResponseEntity<ProblemDetail> buildProblemResponse(ProblemDetail problem, HttpStatus status) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(PROBLEM_JSON_MEDIA_TYPE));
        headers.add("X-Request-ID", problem.getRequestId());
        return new ResponseEntity<>(problem, headers, status);
    }

    /**
     * Determines the appropriate problem type URI based on error code.
     *
     * @param errorCode the error code from the exception
     * @return the problem type URI
     */
    private String determineProblemType(String errorCode) {
        return switch (errorCode) {
            case "VALIDATION_ERROR" -> ProblemTypes.VALIDATION_ERROR;
            case "RESOURCE_NOT_FOUND" -> ProblemTypes.RESOURCE_NOT_FOUND;
            case "AUTHENTICATION_ERROR" -> ProblemTypes.AUTHENTICATION_FAILED;
            case "AUTHORIZATION_ERROR" -> ProblemTypes.AUTHORIZATION_FAILED;
            case "RATE_LIMIT_EXCEEDED" -> ProblemTypes.RATE_LIMIT_EXCEEDED;
            case "CONSTRAINT_VIOLATION" -> ProblemTypes.CONSTRAINT_VIOLATION;
            case "GMAIL_API_ERROR" -> ProblemTypes.GMAIL_API_ERROR;
            case "SERVICE_UNAVAILABLE" -> ProblemTypes.SERVICE_UNAVAILABLE;
            case "QUOTA_EXCEEDED" -> ProblemTypes.QUOTA_EXCEEDED;
            case "BATCH_OPERATION_ERROR" -> ProblemTypes.BATCH_OPERATION_ERROR;
            default -> ProblemTypes.INTERNAL_ERROR;
        };
    }
}