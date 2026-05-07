package com.aucontraire.gmailbuddy.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link InvalidRecipientException}.
 *
 * <p>Validates that all three constructor variants initialise the error code,
 * message, correlation ID, and cause correctly, and that {@link
 * InvalidRecipientException#getHttpStatus()} always returns 422.</p>
 *
 * <p>Mirrors the structure of {@link MessageSendExceptionTest}.</p>
 */
@DisplayName("InvalidRecipientException Tests")
class InvalidRecipientExceptionTest {

    // ---------------------------------------------------------------
    // Constructor: InvalidRecipientException(String message)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Constructor with message initialises error code, message, and correlation ID")
    void constructor_WithMessageOnly_ShouldInitialiseFields() {
        // Arrange
        String message = "Gmail rejected one or more recipient addresses or message fields";

        // Act
        InvalidRecipientException exception = new InvalidRecipientException(message);

        // Assert
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getErrorCode()).isEqualTo("INVALID_RECIPIENT");
        assertThat(exception.getCorrelationId()).isNotNull().isNotEmpty();
        assertThat(exception.getCause()).isNull();
    }

    // ---------------------------------------------------------------
    // Constructor: InvalidRecipientException(String message, Throwable cause)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Constructor with message and cause preserves the upstream cause")
    void constructor_WithMessageAndCause_ShouldPreserveCause() {
        // Arrange
        String message = "Gmail rejected recipient: no such mailbox";
        Throwable cause = new RuntimeException("googleJsonResponseException mock");

        // Act
        InvalidRecipientException exception = new InvalidRecipientException(message, cause);

        // Assert
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getErrorCode()).isEqualTo("INVALID_RECIPIENT");
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getCorrelationId()).isNotNull().isNotEmpty();
    }

    // ---------------------------------------------------------------
    // Constructor: InvalidRecipientException(String message, String correlationId)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Constructor with message and correlationId stores the supplied correlation ID")
    void constructor_WithMessageAndCorrelationId_ShouldStoreCorrelationId() {
        // Arrange
        String message = "Gmail rejected recipient for traced request";
        String correlationId = "trace-xyz-999";

        // Act
        InvalidRecipientException exception = new InvalidRecipientException(message, correlationId);

        // Assert
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getErrorCode()).isEqualTo("INVALID_RECIPIENT");
        assertThat(exception.getCorrelationId()).isEqualTo(correlationId);
        assertThat(exception.getCause()).isNull();
    }

    // ---------------------------------------------------------------
    // getHttpStatus()
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getHttpStatus returns 422 Unprocessable Entity for all constructor variants")
    void getHttpStatus_Always_Returns422UnprocessableEntity() {
        // Arrange
        InvalidRecipientException fromMessage =
                new InvalidRecipientException("recipient rejected");
        InvalidRecipientException fromMessageAndCause =
                new InvalidRecipientException("recipient rejected", new Exception("api error"));
        InvalidRecipientException fromMessageAndCorrelation =
                new InvalidRecipientException("recipient rejected", "corr-id-001");

        // Act & Assert: all three constructors must resolve to 422, not 400.
        assertThat(fromMessage.getHttpStatus())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(fromMessageAndCause.getHttpStatus())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(fromMessageAndCorrelation.getHttpStatus())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
    }

    // ---------------------------------------------------------------
    // isClientError() — inherited final method from GmailBuddyClientException
    // ---------------------------------------------------------------

    @Test
    @DisplayName("isClientError returns true because the rejection is a client-addressable condition")
    void isClientError_Always_ReturnsTrue() {
        // Arrange
        InvalidRecipientException exception =
                new InvalidRecipientException("Gmail rejected recipient");

        // Act
        boolean clientError = exception.isClientError();

        // Assert: the caller can fix this by correcting the recipient address.
        assertThat(clientError).isTrue();
    }

    // ---------------------------------------------------------------
    // Inheritance — must extend GmailBuddyClientException (not ValidationException)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("InvalidRecipientException extends GmailBuddyClientException, not ValidationException")
    void classhierarchy_ExtendsClientExceptionNotValidationException() {
        // Arrange
        InvalidRecipientException exception =
                new InvalidRecipientException("recipient rejected");

        // Assert: extends the client base, not ValidationException, so that
        // GlobalExceptionHandler.handleValidationException does NOT catch it.
        assertThat(exception).isInstanceOf(GmailBuddyClientException.class);
        assertThat(exception).isInstanceOf(GmailBuddyException.class);
        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception).isNotInstanceOf(ValidationException.class);
    }
}
