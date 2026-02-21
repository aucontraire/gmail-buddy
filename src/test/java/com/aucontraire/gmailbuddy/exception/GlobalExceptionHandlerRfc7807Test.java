package com.aucontraire.gmailbuddy.exception;

import com.aucontraire.gmailbuddy.constants.ProblemTypes;
import com.aucontraire.gmailbuddy.dto.error.ProblemDetail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for RFC 7807 compliant error responses in GlobalExceptionHandler.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler RFC 7807 Tests")
class GlobalExceptionHandlerRfc7807Test {

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        // Set up MDC for request ID
        MDC.put("requestId", "test-request-123");
        // Mock request URI
        when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
    }

    @Nested
    @DisplayName("Resource Not Found Tests")
    class ResourceNotFoundTests {

        @Test
        @DisplayName("Should return RFC 7807 ProblemDetail for ResourceNotFoundException")
        void shouldReturnRfc7807ForResourceNotFoundException() {
            ResourceNotFoundException exception = new ResourceNotFoundException("Message with ID abc123 not found");

            ResponseEntity<ProblemDetail> response = exceptionHandler.handleResourceNotFoundException(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getHeaders().getContentType()).hasToString("application/problem+json");
            assertThat(response.getHeaders().getFirst("X-Request-ID")).isEqualTo("test-request-123");

            ProblemDetail problem = response.getBody();
            assertThat(problem).isNotNull();
            assertThat(problem.getType()).hasToString(ProblemTypes.RESOURCE_NOT_FOUND);
            assertThat(problem.getTitle()).isEqualTo("Resource Not Found");
            assertThat(problem.getStatus()).isEqualTo(404);
            assertThat(problem.getDetail()).isEqualTo("Message with ID abc123 not found");
            assertThat(problem.getInstance()).hasToString("/api/v1/gmail/messages");
            assertThat(problem.getRequestId()).isEqualTo("test-request-123");
            assertThat(problem.getRetryable()).isFalse();
            assertThat(problem.getCategory()).isEqualTo("CLIENT_ERROR");
        }
    }

    @Nested
    @DisplayName("Gmail API Exception Tests")
    class GmailApiExceptionTests {

        @Test
        @DisplayName("Should return RFC 7807 ProblemDetail for GmailApiException")
        void shouldReturnRfc7807ForGmailApiException() {
            IOException cause = new IOException("Network timeout");
            GmailApiException exception = new GmailApiException("Failed to communicate with Gmail API", cause);

            ResponseEntity<ProblemDetail> response = exceptionHandler.handleGmailApiException(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(response.getHeaders().getContentType()).hasToString("application/problem+json");

            ProblemDetail problem = response.getBody();
            assertThat(problem).isNotNull();
            assertThat(problem.getType()).hasToString(ProblemTypes.GMAIL_API_ERROR);
            assertThat(problem.getTitle()).isEqualTo("Gmail API Error");
            assertThat(problem.getStatus()).isEqualTo(502);
            assertThat(problem.getDetail()).contains("Failed to communicate with Gmail API");
            assertThat(problem.getRetryable()).isTrue(); // IOException is retryable
            assertThat(problem.getCategory()).isEqualTo("SERVER_ERROR");
        }

        @Test
        @DisplayName("Should mark non-retryable Gmail API errors appropriately")
        void shouldMarkNonRetryableErrors() {
            GmailApiException exception = new GmailApiException("Invalid API configuration", false);

            ResponseEntity<ProblemDetail> response = exceptionHandler.handleGmailApiException(exception);

            ProblemDetail problem = response.getBody();
            assertThat(problem).isNotNull();
            assertThat(problem.getRetryable()).isFalse();
        }
    }

    @Nested
    @DisplayName("Authentication and Authorization Tests")
    class AuthenticationAuthorizationTests {

        @Test
        @DisplayName("Should return RFC 7807 for custom AuthenticationException")
        void shouldReturnRfc7807ForCustomAuthenticationException() {
            com.aucontraire.gmailbuddy.exception.AuthenticationException exception =
                    new com.aucontraire.gmailbuddy.exception.AuthenticationException("OAuth2 token expired");

            ResponseEntity<ProblemDetail> response = exceptionHandler.handleAuthenticationException(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            ProblemDetail problem = response.getBody();
            assertThat(problem).isNotNull();
            assertThat(problem.getType()).hasToString(ProblemTypes.AUTHENTICATION_FAILED);
            assertThat(problem.getTitle()).isEqualTo("Authentication Failed");
            assertThat(problem.getStatus()).isEqualTo(401);
            assertThat(problem.getDetail()).isEqualTo("OAuth2 token expired");
            assertThat(problem.getRetryable()).isFalse();
            assertThat(problem.getCategory()).isEqualTo("CLIENT_ERROR");
        }

        @Test
        @DisplayName("Should return RFC 7807 for Spring Security AuthenticationException")
        void shouldReturnRfc7807ForSpringAuthenticationException() {
            AuthenticationException exception = new AuthenticationException("Bad credentials") {};

            ResponseEntity<ProblemDetail> response = exceptionHandler.handleAuthenticationException(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            ProblemDetail problem = response.getBody();
            assertThat(problem).isNotNull();
            assertThat(problem.getType()).hasToString(ProblemTypes.AUTHENTICATION_FAILED);
            assertThat(problem.getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("Should return RFC 7807 for AuthorizationException")
        void shouldReturnRfc7807ForAuthorizationException() {
            AuthorizationException exception = new AuthorizationException("Insufficient permissions");

            ResponseEntity<ProblemDetail> response = exceptionHandler.handleAuthorizationException(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

            ProblemDetail problem = response.getBody();
            assertThat(problem).isNotNull();
            assertThat(problem.getType()).hasToString(ProblemTypes.AUTHORIZATION_FAILED);
            assertThat(problem.getTitle()).isEqualTo("Authorization Failed");
            assertThat(problem.getStatus()).isEqualTo(403);
            assertThat(problem.getDetail()).isEqualTo("Insufficient permissions");
            assertThat(problem.getRetryable()).isFalse();
            assertThat(problem.getCategory()).isEqualTo("CLIENT_ERROR");
        }

        @Test
        @DisplayName("Should return RFC 7807 for AccessDeniedException")
        void shouldReturnRfc7807ForAccessDeniedException() {
            AccessDeniedException exception = new AccessDeniedException("Access is denied");

            ResponseEntity<ProblemDetail> response = exceptionHandler.handleAuthorizationException(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

            ProblemDetail problem = response.getBody();
            assertThat(problem).isNotNull();
            assertThat(problem.getType()).hasToString(ProblemTypes.AUTHORIZATION_FAILED);
            assertThat(problem.getStatus()).isEqualTo(403);
        }
    }

    @Nested
    @DisplayName("Rate Limit Tests")
    class RateLimitTests {

        @Test
        @DisplayName("Should return RFC 7807 with Retry-After header for RateLimitException")
        void shouldReturnRfc7807WithRetryAfterHeader() {
            long retryAfter = 60L;
            RateLimitException exception = new RateLimitException("Rate limit exceeded: 100 requests/minute", retryAfter);

            ResponseEntity<ProblemDetail> response = exceptionHandler.handleRateLimitException(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("60");
            assertThat(response.getHeaders().getContentType()).hasToString("application/problem+json");

            ProblemDetail problem = response.getBody();
            assertThat(problem).isNotNull();
            assertThat(problem.getType()).hasToString(ProblemTypes.RATE_LIMIT_EXCEEDED);
            assertThat(problem.getTitle()).isEqualTo("Rate Limit Exceeded");
            assertThat(problem.getStatus()).isEqualTo(429);
            assertThat(problem.getRetryable()).isTrue();
            assertThat(problem.getCategory()).isEqualTo("CLIENT_ERROR");
            assertThat(problem.getExtensions()).containsEntry("retryAfterSeconds", retryAfter);
        }
    }

    @Nested
    @DisplayName("Service Unavailable Tests")
    class ServiceUnavailableTests {

        @Test
        @DisplayName("Should return RFC 7807 with Retry-After header for ServiceUnavailableException")
        void shouldReturnRfc7807WithRetryAfterHeader() {
            long retryAfter = 300L;
            ServiceUnavailableException exception =
                    new ServiceUnavailableException("Service temporarily unavailable", retryAfter);

            ResponseEntity<ProblemDetail> response = exceptionHandler.handleServiceUnavailableException(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("300");

            ProblemDetail problem = response.getBody();
            assertThat(problem).isNotNull();
            assertThat(problem.getType()).hasToString(ProblemTypes.SERVICE_UNAVAILABLE);
            assertThat(problem.getTitle()).isEqualTo("Service Unavailable");
            assertThat(problem.getStatus()).isEqualTo(503);
            assertThat(problem.getRetryable()).isTrue();
            assertThat(problem.getCategory()).isEqualTo("SERVER_ERROR");
            assertThat(problem.getExtensions()).containsEntry("retryAfterSeconds", retryAfter);
        }
    }

    @Nested
    @DisplayName("Validation Exception Tests")
    class ValidationExceptionTests {

        @Test
        @DisplayName("Should return RFC 7807 for ValidationException")
        void shouldReturnRfc7807ForValidationException() {
            ValidationException exception = new ValidationException("Invalid email format");

            ResponseEntity<ProblemDetail> response = exceptionHandler.handleValidationException(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            ProblemDetail problem = response.getBody();
            assertThat(problem).isNotNull();
            assertThat(problem.getType()).hasToString(ProblemTypes.VALIDATION_ERROR);
            assertThat(problem.getTitle()).isEqualTo("Validation Error");
            assertThat(problem.getStatus()).isEqualTo(400);
            assertThat(problem.getDetail()).isEqualTo("Invalid email format");
            assertThat(problem.getRetryable()).isFalse();
            assertThat(problem.getCategory()).isEqualTo("CLIENT_ERROR");
        }

        @Test
        @DisplayName("Should return RFC 7807 with field errors for MethodArgumentNotValidException")
        void shouldReturnRfc7807WithFieldErrors() {
            MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
            BindingResult bindingResult = mock(BindingResult.class);

            FieldError fieldError1 = new FieldError("filterCriteria", "from", "Invalid email format");
            FieldError fieldError2 = new FieldError("filterCriteria", "subject", "Subject is required");

            when(exception.getBindingResult()).thenReturn(bindingResult);
            when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError1, fieldError2));

            ResponseEntity<ProblemDetail> response = exceptionHandler.handleMethodArgumentNotValidException(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            ProblemDetail problem = response.getBody();
            assertThat(problem).isNotNull();
            assertThat(problem.getType()).hasToString(ProblemTypes.VALIDATION_ERROR);
            assertThat(problem.getTitle()).isEqualTo("Validation Error");
            assertThat(problem.getStatus()).isEqualTo(400);
            assertThat(problem.getDetail()).contains("2 field(s)");
            assertThat(problem.getExtensions())
                    .containsEntry("field:from", "Invalid email format")
                    .containsEntry("field:subject", "Subject is required");
        }

        @Test
        @DisplayName("Should return RFC 7807 with constraint violations for ConstraintViolationException")
        void shouldReturnRfc7807WithConstraintViolations() {
            ConstraintViolationException exception = mock(ConstraintViolationException.class);

            ConstraintViolation<?> violation1 = mock(ConstraintViolation.class);
            ConstraintViolation<?> violation2 = mock(ConstraintViolation.class);
            Path path1 = mock(Path.class);
            Path path2 = mock(Path.class);

            when(violation1.getPropertyPath()).thenReturn(path1);
            when(violation1.getMessage()).thenReturn("must not be null");
            when(path1.toString()).thenReturn("userId");

            when(violation2.getPropertyPath()).thenReturn(path2);
            when(violation2.getMessage()).thenReturn("must be positive");
            when(path2.toString()).thenReturn("limit");

            when(exception.getConstraintViolations()).thenReturn(Set.of(violation1, violation2));

            ResponseEntity<ProblemDetail> response = exceptionHandler.handleConstraintViolationException(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            ProblemDetail problem = response.getBody();
            assertThat(problem).isNotNull();
            assertThat(problem.getType()).hasToString(ProblemTypes.CONSTRAINT_VIOLATION);
            assertThat(problem.getTitle()).isEqualTo("Constraint Violation");
            assertThat(problem.getStatus()).isEqualTo(400);
            assertThat(problem.getDetail()).contains("2 parameter(s)");
            assertThat(problem.getExtensions())
                    .containsEntry("constraint:userId", "must not be null")
                    .containsEntry("constraint:limit", "must be positive");
        }
    }

    @Nested
    @DisplayName("Generic GmailBuddyException Tests")
    class GenericGmailBuddyExceptionTests {

        @Test
        @DisplayName("Should handle generic GmailBuddyException with correct problem type mapping")
        void shouldHandleGenericGmailBuddyException() {
            ValidationException exception = new ValidationException("Validation failed");

            ResponseEntity<ProblemDetail> response = exceptionHandler.handleGmailBuddyException(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            ProblemDetail problem = response.getBody();
            assertThat(problem).isNotNull();
            assertThat(problem.getType()).hasToString(ProblemTypes.VALIDATION_ERROR);
            assertThat(problem.getStatus()).isEqualTo(400);
            assertThat(problem.getCategory()).isEqualTo("CLIENT_ERROR");
        }
    }

    @Nested
    @DisplayName("Generic Exception Tests")
    class GenericExceptionTests {

        @Test
        @DisplayName("Should return RFC 7807 for unexpected exceptions")
        void shouldReturnRfc7807ForUnexpectedException() {
            RuntimeException exception = new RuntimeException("Unexpected error occurred");

            ResponseEntity<ProblemDetail> response = exceptionHandler.handleGenericException(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getHeaders().getContentType()).hasToString("application/problem+json");

            ProblemDetail problem = response.getBody();
            assertThat(problem).isNotNull();
            assertThat(problem.getType()).hasToString(ProblemTypes.INTERNAL_ERROR);
            assertThat(problem.getTitle()).isEqualTo("Internal Server Error");
            assertThat(problem.getStatus()).isEqualTo(500);
            assertThat(problem.getDetail()).contains("unexpected error occurred");
            assertThat(problem.getRetryable()).isFalse();
            assertThat(problem.getCategory()).isEqualTo("SERVER_ERROR");
            assertThat(problem.getRequestId()).isNotNull();
        }

        @Test
        @DisplayName("Should handle NullPointerException as internal error")
        void shouldHandleNullPointerException() {
            NullPointerException exception = new NullPointerException("Null value encountered");

            ResponseEntity<ProblemDetail> response = exceptionHandler.handleGenericException(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

            ProblemDetail problem = response.getBody();
            assertThat(problem).isNotNull();
            assertThat(problem.getType()).hasToString(ProblemTypes.INTERNAL_ERROR);
            assertThat(problem.getStatus()).isEqualTo(500);
            assertThat(problem.getCategory()).isEqualTo("SERVER_ERROR");
        }
    }

    @Nested
    @DisplayName("RFC 7807 Compliance Tests")
    class Rfc7807ComplianceTests {

        @Test
        @DisplayName("All responses should have required RFC 7807 fields")
        void allResponsesShouldHaveRequiredFields() {
            ResourceNotFoundException exception = new ResourceNotFoundException("Test error");

            ResponseEntity<ProblemDetail> response = exceptionHandler.handleResourceNotFoundException(exception);

            ProblemDetail problem = response.getBody();
            assertThat(problem).isNotNull();

            // Required RFC 7807 fields
            assertThat(problem.getType()).isNotNull();
            assertThat(problem.getTitle()).isNotNull();
            assertThat(problem.getStatus()).isNotNull();
        }

        @Test
        @DisplayName("All responses should have application/problem+json content type")
        void allResponsesShouldHaveCorrectContentType() {
            ResourceNotFoundException exception = new ResourceNotFoundException("Test error");

            ResponseEntity<ProblemDetail> response = exceptionHandler.handleResourceNotFoundException(exception);

            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("application/problem+json"));
        }

        @Test
        @DisplayName("All responses should include X-Request-ID header")
        void allResponsesShouldIncludeRequestIdHeader() {
            ResourceNotFoundException exception = new ResourceNotFoundException("Test error");

            ResponseEntity<ProblemDetail> response = exceptionHandler.handleResourceNotFoundException(exception);

            assertThat(response.getHeaders().getFirst("X-Request-ID")).isNotNull();
        }

        @Test
        @DisplayName("Instance URI should match request path")
        void instanceUriShouldMatchRequestPath() {
            when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages/123");

            ResourceNotFoundException exception = new ResourceNotFoundException("Test error");

            ResponseEntity<ProblemDetail> response = exceptionHandler.handleResourceNotFoundException(exception);

            ProblemDetail problem = response.getBody();
            assertThat(problem).isNotNull();
            assertThat(problem.getInstance()).hasToString("/api/v1/gmail/messages/123");
        }
    }

    @Nested
    @DisplayName("Request ID Handling Tests")
    class RequestIdHandlingTests {

        @Test
        @DisplayName("Should use MDC request ID when available")
        void shouldUseMdcRequestId() {
            MDC.put("requestId", "custom-request-id");

            ResourceNotFoundException exception = new ResourceNotFoundException("Test error");

            ResponseEntity<ProblemDetail> response = exceptionHandler.handleResourceNotFoundException(exception);

            ProblemDetail problem = response.getBody();
            assertThat(problem).isNotNull();
            assertThat(problem.getRequestId()).isEqualTo("custom-request-id");
            assertThat(response.getHeaders().getFirst("X-Request-ID")).isEqualTo("custom-request-id");

            MDC.remove("requestId");
        }

        @Test
        @DisplayName("Should generate request ID when MDC is empty")
        void shouldGenerateRequestIdWhenMdcEmpty() {
            MDC.remove("requestId");

            ResourceNotFoundException exception = new ResourceNotFoundException("Test error");

            ResponseEntity<ProblemDetail> response = exceptionHandler.handleResourceNotFoundException(exception);

            ProblemDetail problem = response.getBody();
            assertThat(problem).isNotNull();
            assertThat(problem.getRequestId()).isNotNull();
            assertThat(problem.getRequestId()).matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
        }
    }
}
