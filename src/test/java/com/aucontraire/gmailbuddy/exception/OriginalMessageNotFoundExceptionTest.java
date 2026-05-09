package com.aucontraire.gmailbuddy.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OriginalMessageNotFoundException}.
 *
 * <p>Validates that all constructor variants initialise the error code, message, and
 * cause correctly, and that {@link OriginalMessageNotFoundException#getHttpStatus()}
 * always returns 422 Unprocessable Entity.</p>
 *
 * <p>Mirrors the structure of {@link InvalidRecipientExceptionTest} per the task
 * constraint: "mirror {@code InvalidRecipientException} exactly".</p>
 */
@DisplayName("OriginalMessageNotFoundException Tests")
class OriginalMessageNotFoundExceptionTest {

    // ---------------------------------------------------------------
    // Constructor: OriginalMessageNotFoundException(String message)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Constructor with message initialises error code, message, and correlation ID")
    void constructor_WithMessageOnly_ShouldInitialiseFields() {
        // Arrange
        String message = "No message found for inReplyToMessageId: 1a2b3c4d";

        // Act
        OriginalMessageNotFoundException exception = new OriginalMessageNotFoundException(message);

        // Assert
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getErrorCode()).isEqualTo("ORIGINAL_MESSAGE_NOT_FOUND");
        assertThat(exception.getCorrelationId()).isNotNull().isNotEmpty();
        assertThat(exception.getCause()).isNull();
    }

    // ---------------------------------------------------------------
    // Constructor: OriginalMessageNotFoundException(String message, Throwable cause)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Constructor with message and cause preserves the upstream cause")
    void constructor_WithMessageAndCause_ShouldPreserveCause() {
        // Arrange
        String message = "Gmail returned 404 for message ID: deadbeef";
        Throwable cause = new RuntimeException("GoogleJsonResponseException mock");

        // Act
        OriginalMessageNotFoundException exception =
                new OriginalMessageNotFoundException(message, cause);

        // Assert
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getErrorCode()).isEqualTo("ORIGINAL_MESSAGE_NOT_FOUND");
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getCorrelationId()).isNotNull().isNotEmpty();
    }

    // ---------------------------------------------------------------
    // getHttpStatus()
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getHttpStatus returns 422 Unprocessable Entity for single-arg constructor")
    void getHttpStatus_SingleArgConstructor_Returns422UnprocessableEntity() {
        // Arrange
        OriginalMessageNotFoundException exception =
                new OriginalMessageNotFoundException("message not found");

        // Act & Assert
        assertThat(exception.getHttpStatus())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
    }

    @Test
    @DisplayName("getHttpStatus returns 422 Unprocessable Entity for two-arg constructor")
    void getHttpStatus_TwoArgConstructor_Returns422UnprocessableEntity() {
        // Arrange
        OriginalMessageNotFoundException exception =
                new OriginalMessageNotFoundException("message not found", new Exception("api error"));

        // Act & Assert
        assertThat(exception.getHttpStatus())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
    }

    // ---------------------------------------------------------------
    // isClientError() — inherited final method from GmailBuddyClientException
    // ---------------------------------------------------------------

    @Test
    @DisplayName("isClientError returns true because the caller can fix by correcting the ID")
    void isClientError_Always_ReturnsTrue() {
        // Arrange
        OriginalMessageNotFoundException exception =
                new OriginalMessageNotFoundException("original message not accessible");

        // Act
        boolean clientError = exception.isClientError();

        // Assert: the caller can retry with a different inReplyToMessageId or omit it.
        assertThat(clientError).isTrue();
    }

    // ---------------------------------------------------------------
    // Inheritance — must extend GmailBuddyClientException (not ValidationException)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("OriginalMessageNotFoundException extends GmailBuddyClientException")
    void classHierarchy_ExtendsClientExceptionNotValidationException() {
        // Arrange
        OriginalMessageNotFoundException exception =
                new OriginalMessageNotFoundException("original message not found");

        // Assert: extends the client base, so GlobalExceptionHandler.handleValidationException
        // does NOT catch it — only the dedicated handleOriginalMessageNotFoundException does.
        assertThat(exception).isInstanceOf(GmailBuddyClientException.class);
        assertThat(exception).isInstanceOf(GmailBuddyException.class);
        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception).isNotInstanceOf(ValidationException.class);
    }
}
