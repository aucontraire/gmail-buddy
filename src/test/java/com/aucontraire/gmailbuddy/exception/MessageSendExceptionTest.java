package com.aucontraire.gmailbuddy.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MessageSendException Tests")
class MessageSendExceptionTest {

    // ---------------------------------------------------------------
    // Constructor: MessageSendException(String message)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Constructor with message initialises error code, message, and correlation ID")
    void constructor_WithMessageOnly_ShouldInitialiseFields() {
        // Arrange
        String message = "Gmail API rejected the send request";

        // Act
        MessageSendException exception = new MessageSendException(message);

        // Assert
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getErrorCode()).isEqualTo("MESSAGE_SEND_FAILED");
        assertThat(exception.getCorrelationId()).isNotNull().isNotEmpty();
        assertThat(exception.getCause()).isNull();
    }

    // ---------------------------------------------------------------
    // Constructor: MessageSendException(String message, Throwable cause)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Constructor with message and cause preserves the upstream cause")
    void constructor_WithMessageAndCause_ShouldPreserveCause() {
        // Arrange
        String message = "Upstream Gmail API connection failed";
        Throwable cause = new RuntimeException("socket timeout");

        // Act
        MessageSendException exception = new MessageSendException(message, cause);

        // Assert
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getErrorCode()).isEqualTo("MESSAGE_SEND_FAILED");
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getCorrelationId()).isNotNull().isNotEmpty();
    }

    // ---------------------------------------------------------------
    // Constructor: MessageSendException(String message, String correlationId)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Constructor with message and correlationId stores the supplied correlation ID")
    void constructor_WithMessageAndCorrelationId_ShouldStoreCorrelationId() {
        // Arrange
        String message = "Send draft failed for traced request";
        String correlationId = "trace-abc-123";

        // Act
        MessageSendException exception = new MessageSendException(message, correlationId);

        // Assert
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getErrorCode()).isEqualTo("MESSAGE_SEND_FAILED");
        assertThat(exception.getCorrelationId()).isEqualTo(correlationId);
        assertThat(exception.getCause()).isNull();
    }

    // ---------------------------------------------------------------
    // getHttpStatus()
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getHttpStatus returns 502 Bad Gateway for all constructor variants")
    void getHttpStatus_Always_ReturnsBadGateway502() {
        // Arrange
        MessageSendException fromMessage = new MessageSendException("send failed");
        MessageSendException fromMessageAndCause =
                new MessageSendException("send failed", new Exception("api error"));
        MessageSendException fromMessageAndCorrelation =
                new MessageSendException("send failed", "corr-id-999");

        // Act & Assert
        assertThat(fromMessage.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
        assertThat(fromMessageAndCause.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
        assertThat(fromMessageAndCorrelation.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
    }

    // ---------------------------------------------------------------
    // isClientError() — inherited final method from GmailBuddyServerException
    // ---------------------------------------------------------------

    @Test
    @DisplayName("isClientError returns false because the fault originates in Gmail API")
    void isClientError_Always_ReturnsFalse() {
        // Arrange
        MessageSendException exception = new MessageSendException("upstream gmail failure");

        // Act
        boolean clientError = exception.isClientError();

        // Assert
        assertThat(clientError).isFalse();
    }
}
