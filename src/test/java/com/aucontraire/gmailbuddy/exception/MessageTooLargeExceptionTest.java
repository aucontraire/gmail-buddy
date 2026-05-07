package com.aucontraire.gmailbuddy.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MessageTooLargeException}.
 *
 * <p>Validates that all three constructor variants initialise the error code,
 * message, correlation ID, and cause correctly, and that {@link
 * MessageTooLargeException#getHttpStatus()} always returns 413.</p>
 *
 * <p>Mirrors the structure of {@link InvalidRecipientExceptionTest}.</p>
 */
@DisplayName("MessageTooLargeException Tests")
class MessageTooLargeExceptionTest {

    // ---------------------------------------------------------------
    // Constructor: MessageTooLargeException(String message)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Constructor with message initialises error code, message, and correlation ID")
    void constructor_WithMessageOnly_ShouldInitialiseFields() {
        // Arrange
        String message = "Message exceeds Gmail's maximum allowed size";

        // Act
        MessageTooLargeException exception = new MessageTooLargeException(message);

        // Assert
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getErrorCode()).isEqualTo("MESSAGE_TOO_LARGE");
        assertThat(exception.getCorrelationId()).isNotNull().isNotEmpty();
        assertThat(exception.getCause()).isNull();
    }

    // ---------------------------------------------------------------
    // Constructor: MessageTooLargeException(String message, Throwable cause)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Constructor with message and cause preserves the upstream cause")
    void constructor_WithMessageAndCause_ShouldPreserveCause() {
        // Arrange
        String message = "Message exceeds Gmail's maximum allowed size";
        Throwable cause = new RuntimeException("googleJsonResponseException mock");

        // Act
        MessageTooLargeException exception = new MessageTooLargeException(message, cause);

        // Assert
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getErrorCode()).isEqualTo("MESSAGE_TOO_LARGE");
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getCorrelationId()).isNotNull().isNotEmpty();
    }

    // ---------------------------------------------------------------
    // Constructor: MessageTooLargeException(String message, String correlationId)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Constructor with message and correlationId stores the supplied correlation ID")
    void constructor_WithMessageAndCorrelationId_ShouldStoreCorrelationId() {
        // Arrange
        String message = "Message exceeds Gmail's maximum allowed size";
        String correlationId = "trace-abc-413";

        // Act
        MessageTooLargeException exception = new MessageTooLargeException(message, correlationId);

        // Assert
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getErrorCode()).isEqualTo("MESSAGE_TOO_LARGE");
        assertThat(exception.getCorrelationId()).isEqualTo(correlationId);
        assertThat(exception.getCause()).isNull();
    }

    // ---------------------------------------------------------------
    // getHttpStatus()
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getHttpStatus returns 413 Payload Too Large for all constructor variants")
    void getHttpStatus_Always_Returns413PayloadTooLarge() {
        // Arrange
        MessageTooLargeException fromMessage =
                new MessageTooLargeException("message too large");
        MessageTooLargeException fromMessageAndCause =
                new MessageTooLargeException("message too large", new Exception("api error"));
        MessageTooLargeException fromMessageAndCorrelation =
                new MessageTooLargeException("message too large", "corr-id-413");

        // Act & Assert: all three constructors must resolve to 413, not 400.
        assertThat(fromMessage.getHttpStatus())
                .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(fromMessageAndCause.getHttpStatus())
                .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(fromMessageAndCorrelation.getHttpStatus())
                .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
    }

    // ---------------------------------------------------------------
    // isClientError() — inherited final method from GmailBuddyClientException
    // ---------------------------------------------------------------

    @Test
    @DisplayName("isClientError returns true because the rejection is a client-addressable condition")
    void isClientError_Always_ReturnsTrue() {
        // Arrange
        MessageTooLargeException exception =
                new MessageTooLargeException("Message exceeds Gmail's maximum allowed size");

        // Act
        boolean clientError = exception.isClientError();

        // Assert: the caller can fix this by reducing the payload size.
        assertThat(clientError).isTrue();
    }

    // ---------------------------------------------------------------
    // Inheritance — must extend GmailBuddyClientException (not ValidationException)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("MessageTooLargeException extends GmailBuddyClientException, not ValidationException")
    void classHierarchy_ExtendsClientExceptionNotValidationException() {
        // Arrange
        MessageTooLargeException exception =
                new MessageTooLargeException("message too large");

        // Assert: extends the client base, not ValidationException, so that
        // GlobalExceptionHandler.handleValidationException does NOT catch it,
        // and handleMessageTooLargeException returns 413 instead of 400.
        assertThat(exception).isInstanceOf(GmailBuddyClientException.class);
        assertThat(exception).isInstanceOf(GmailBuddyException.class);
        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception).isNotInstanceOf(ValidationException.class);
    }
}
