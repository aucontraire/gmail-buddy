package com.aucontraire.gmailbuddy.exception;

import com.aucontraire.gmailbuddy.constants.ProblemTypes;
import com.aucontraire.gmailbuddy.dto.error.ProblemDetail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for GlobalExceptionHandler with RFC 7807 ProblemDetail responses.
 *
 * @since 1.0
 */
@DisplayName("GlobalExceptionHandler RFC 7807 Tests")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new GlobalExceptionHandler();

        // Inject the mocked request using reflection
        try {
            var field = GlobalExceptionHandler.class.getDeclaredField("request");
            field.setAccessible(true);
            field.set(handler, request);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mock request", e);
        }

        // Mock common request URI
        when(request.getRequestURI()).thenReturn("/api/v1/gmail/messages");
    }

    @Test
    @DisplayName("ResourceNotFoundException returns RFC 7807 problem detail with 404")
    void testResourceNotFoundException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Message not found");

        ResponseEntity<ProblemDetail> response = handler.handleResourceNotFoundException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("application/problem+json"));

        ProblemDetail problem = response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getType().toString()).isEqualTo(ProblemTypes.RESOURCE_NOT_FOUND);
        assertThat(problem.getTitle()).isEqualTo("Resource Not Found");
        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getDetail()).isEqualTo("Message not found");
        assertThat(problem.getInstance().toString()).isEqualTo("/api/v1/gmail/messages");
        assertThat(problem.getRequestId()).isNotNull();
        assertThat(problem.getRetryable()).isFalse();
        assertThat(problem.getCategory()).isEqualTo("CLIENT_ERROR");
        assertThat(problem.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("GmailApiException returns RFC 7807 problem detail with 502")
    void testGmailApiException() {
        GmailApiException ex = new GmailApiException("Gmail API unavailable");

        ResponseEntity<ProblemDetail> response = handler.handleGmailApiException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);

        ProblemDetail problem = response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getType().toString()).isEqualTo(ProblemTypes.GMAIL_API_ERROR);
        assertThat(problem.getTitle()).isEqualTo("Gmail API Error");
        assertThat(problem.getStatus()).isEqualTo(502);
        assertThat(problem.getDetail()).isEqualTo("Gmail API unavailable");
        assertThat(problem.getCategory()).isEqualTo("SERVER_ERROR");
    }

    @Test
    @DisplayName("AuthenticationException returns RFC 7807 problem detail with 401")
    void testAuthenticationException() {
        com.aucontraire.gmailbuddy.exception.AuthenticationException ex =
            new com.aucontraire.gmailbuddy.exception.AuthenticationException("Invalid token");

        ResponseEntity<ProblemDetail> response = handler.handleAuthenticationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ProblemDetail problem = response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getType().toString()).isEqualTo(ProblemTypes.AUTHENTICATION_FAILED);
        assertThat(problem.getTitle()).isEqualTo("Authentication Failed");
        assertThat(problem.getStatus()).isEqualTo(401);
        assertThat(problem.getDetail()).isEqualTo("Invalid token");
    }

    @Test
    @DisplayName("AuthorizationException returns RFC 7807 problem detail with 403")
    void testAuthorizationException() {
        AuthorizationException ex = new AuthorizationException("Insufficient permissions");

        ResponseEntity<ProblemDetail> response = handler.handleAuthorizationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ProblemDetail problem = response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getType().toString()).isEqualTo(ProblemTypes.AUTHORIZATION_FAILED);
        assertThat(problem.getTitle()).isEqualTo("Authorization Failed");
        assertThat(problem.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("RateLimitException returns RFC 7807 with Retry-After header")
    void testRateLimitException() {
        RateLimitException ex = new RateLimitException("Too many requests", 120);

        ResponseEntity<ProblemDetail> response = handler.handleRateLimitException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("120");

        ProblemDetail problem = response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getType().toString()).isEqualTo(ProblemTypes.RATE_LIMIT_EXCEEDED);
        assertThat(problem.getTitle()).isEqualTo("Rate Limit Exceeded");
        assertThat(problem.getStatus()).isEqualTo(429);
        assertThat(problem.getRetryable()).isTrue();
        assertThat(problem.getExtensions()).containsEntry("retryAfterSeconds", 120L);
    }

    @Test
    @DisplayName("ServiceUnavailableException returns RFC 7807 with Retry-After header")
    void testServiceUnavailableException() {
        ServiceUnavailableException ex = new ServiceUnavailableException("Maintenance mode", 300);

        ResponseEntity<ProblemDetail> response = handler.handleServiceUnavailableException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("300");

        ProblemDetail problem = response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getType().toString()).isEqualTo(ProblemTypes.SERVICE_UNAVAILABLE);
        assertThat(problem.getRetryable()).isTrue();
    }

    @Test
    @DisplayName("ValidationException returns RFC 7807 problem detail with 400")
    void testValidationException() {
        ValidationException ex = new ValidationException("Invalid email format");

        ResponseEntity<ProblemDetail> response = handler.handleValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ProblemDetail problem = response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getType().toString()).isEqualTo(ProblemTypes.VALIDATION_ERROR);
        assertThat(problem.getTitle()).isEqualTo("Validation Error");
        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getRetryable()).isFalse();
    }

    @Test
    @DisplayName("MethodArgumentNotValidException returns RFC 7807 with field errors")
    void testMethodArgumentNotValidException() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);

        FieldError fieldError1 = new FieldError("filterCriteria", "from", "Must be valid email");
        FieldError fieldError2 = new FieldError("filterCriteria", "subject", "Too long");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError1, fieldError2));

        ResponseEntity<ProblemDetail> response = handler.handleMethodArgumentNotValidException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ProblemDetail problem = response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getType().toString()).isEqualTo(ProblemTypes.VALIDATION_ERROR);
        assertThat(problem.getDetail()).contains("2 field(s)");
        assertThat(problem.getExtensions())
            .containsEntry("field:from", "Must be valid email")
            .containsEntry("field:subject", "Too long");
    }

    @Test
    @DisplayName("ConstraintViolationException returns RFC 7807 with constraint details")
    void testConstraintViolationException() {
        ConstraintViolation<?> violation1 = mock(ConstraintViolation.class);
        ConstraintViolation<?> violation2 = mock(ConstraintViolation.class);
        Path path1 = mock(Path.class);
        Path path2 = mock(Path.class);

        when(violation1.getPropertyPath()).thenReturn(path1);
        when(violation1.getMessage()).thenReturn("Invalid format");
        when(path1.toString()).thenReturn("email");

        when(violation2.getPropertyPath()).thenReturn(path2);
        when(violation2.getMessage()).thenReturn("Too short");
        when(path2.toString()).thenReturn("password");

        ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation1, violation2));

        ResponseEntity<ProblemDetail> response = handler.handleConstraintViolationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ProblemDetail problem = response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getType().toString()).isEqualTo(ProblemTypes.CONSTRAINT_VIOLATION);
        assertThat(problem.getDetail()).contains("2 parameter(s)");
        assertThat(problem.getExtensions())
            .containsEntry("constraint:email", "Invalid format")
            .containsEntry("constraint:password", "Too short");
    }

    @Test
    @DisplayName("Generic Exception returns RFC 7807 problem detail with 500")
    void testGenericException() {
        RuntimeException ex = new RuntimeException("Unexpected error");

        ResponseEntity<ProblemDetail> response = handler.handleGenericException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        ProblemDetail problem = response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getType().toString()).isEqualTo(ProblemTypes.INTERNAL_ERROR);
        assertThat(problem.getTitle()).isEqualTo("Internal Server Error");
        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getDetail()).contains("unexpected error");
        assertThat(problem.getRequestId()).isNotNull();
        assertThat(problem.getCategory()).isEqualTo("SERVER_ERROR");
    }

    @Test
    @DisplayName("All responses include X-Request-ID header")
    void testRequestIdHeaderPresent() {
        ValidationException ex = new ValidationException("Test");

        ResponseEntity<ProblemDetail> response = handler.handleValidationException(ex);

        assertThat(response.getHeaders().getFirst("X-Request-ID")).isNotNull();
        assertThat(response.getBody().getRequestId()).isNotNull();
        assertThat(response.getHeaders().getFirst("X-Request-ID"))
            .isEqualTo(response.getBody().getRequestId());
    }

    @Test
    @DisplayName("Content-Type is application/problem+json for all responses")
    void testContentTypeIsProblemJson() {
        ValidationException ex = new ValidationException("Test");

        ResponseEntity<ProblemDetail> response = handler.handleValidationException(ex);

        assertThat(response.getHeaders().getContentType())
            .isEqualTo(MediaType.parseMediaType("application/problem+json"));
    }
}
