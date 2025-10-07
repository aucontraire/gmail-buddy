package com.aucontraire.gmailbuddy.exception;

import com.aucontraire.gmailbuddy.service.BulkOperationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

@DisplayName("BatchOperationException Tests")
class BatchOperationExceptionTest {

    @Test
    @DisplayName("Should create exception with message and operation result")
    void constructor_WithMessageAndResult_ShouldInitializeCorrectly() {
        // Arrange
        BulkOperationResult result = new BulkOperationResult("DELETE");
        result.addSuccess("msg1");
        result.addFailure("msg2", "Error message");
        result.markCompleted();
        String message = "Batch operation failed";

        // Act
        BatchOperationException exception = new BatchOperationException(message, result);

        // Assert
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getOperationResult()).isSameAs(result);
        assertThat(exception.isPartialFailure()).isTrue(); // Has both successes and failures
        assertThat(exception.isCompleteFailure()).isFalse();
        assertThat(exception.getErrorCode()).isEqualTo("BATCH_OPERATION_ERROR");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.MULTI_STATUS.value());
    }

    @Test
    @DisplayName("Should create exception with message, result, and cause")
    void constructor_WithMessageResultAndCause_ShouldInitializeCorrectly() {
        // Arrange
        BulkOperationResult result = new BulkOperationResult("DELETE");
        result.addFailure("msg1", "Error");
        result.markCompleted();
        String message = "Batch operation failed";
        Throwable cause = new RuntimeException("Underlying cause");

        // Act
        BatchOperationException exception = new BatchOperationException(message, result, cause);

        // Assert
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getOperationResult()).isSameAs(result);
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.isPartialFailure()).isFalse(); // No successes
        assertThat(exception.isCompleteFailure()).isTrue();
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
    }

    @Test
    @DisplayName("Should create partial failure exception with correct message and status")
    void partialFailure_ShouldCreateCorrectException() {
        // Arrange
        BulkOperationResult result = new BulkOperationResult("MODIFY_LABELS");
        result.addSuccess("msg1");
        result.addSuccess("msg2");
        result.addSuccess("msg3");
        result.addFailure("msg4", "Label not found");
        result.addFailure("msg5", "Permission denied");
        result.markCompleted();

        // Act
        BatchOperationException exception = BatchOperationException.partialFailure(result);

        // Assert
        assertThat(exception.getMessage())
                .contains("Batch MODIFY_LABELS operation partially failed")
                .contains("3/5 operations succeeded")
                .contains("60.0% success rate");
        assertThat(exception.isPartialFailure()).isTrue();
        assertThat(exception.isCompleteFailure()).isFalse();
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.MULTI_STATUS.value());
        assertThat(exception.getSuccessCount()).isEqualTo(3);
        assertThat(exception.getFailureCount()).isEqualTo(2);
        assertThat(exception.getTotalOperations()).isEqualTo(5);
        assertThat(exception.getSuccessRate()).isEqualTo(60.0);
    }

    @Test
    @DisplayName("Should create complete failure exception with correct message and status")
    void completeFailure_ShouldCreateCorrectException() {
        // Arrange
        BulkOperationResult result = new BulkOperationResult("DELETE");
        result.addFailure("msg1", "Message not found");
        result.addFailure("msg2", "Permission denied");
        result.addFailure("msg3", "Invalid message ID");
        result.markCompleted();

        // Act
        BatchOperationException exception = BatchOperationException.completeFailure(result);

        // Assert
        assertThat(exception.getMessage())
                .contains("Batch DELETE operation completely failed")
                .contains("0/3 operations succeeded");
        assertThat(exception.isPartialFailure()).isFalse();
        assertThat(exception.isCompleteFailure()).isTrue();
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
        assertThat(exception.getSuccessCount()).isEqualTo(0);
        assertThat(exception.getFailureCount()).isEqualTo(3);
        assertThat(exception.getTotalOperations()).isEqualTo(3);
        assertThat(exception.getSuccessRate()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should create complete failure exception with cause")
    void completeFailureWithCause_ShouldCreateCorrectException() {
        // Arrange
        BulkOperationResult result = new BulkOperationResult("DELETE");
        result.addFailure("msg1", "Network error");
        result.markCompleted();
        Throwable cause = new RuntimeException("Network connection failed");

        // Act
        BatchOperationException exception = BatchOperationException.completeFailure(result, cause);

        // Assert
        assertThat(exception.getMessage())
                .contains("Batch DELETE operation completely failed")
                .contains("0/1 operations succeeded");
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.isCompleteFailure()).isTrue();
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
    }

    @ParameterizedTest
    @MethodSource("httpStatusTestCases")
    @DisplayName("Should return correct HTTP status based on failure type")
    void getHttpStatus_ShouldReturnCorrectStatus(boolean hasSuccesses, int expectedStatus) {
        // Arrange
        BulkOperationResult result = new BulkOperationResult("TEST");
        if (hasSuccesses) {
            result.addSuccess("msg1");
        }
        result.addFailure("msg2", "Error");
        result.markCompleted();

        // Act
        BatchOperationException exception = new BatchOperationException("Test", result);

        // Assert
        assertThat(exception.getHttpStatus()).isEqualTo(expectedStatus);
    }

    static Stream<Arguments> httpStatusTestCases() {
        return Stream.of(
                Arguments.of(true, HttpStatus.MULTI_STATUS.value()),   // Partial failure
                Arguments.of(false, HttpStatus.BAD_GATEWAY.value())    // Complete failure
        );
    }

    @Test
    @DisplayName("Should handle edge case with zero operations")
    void exception_WithZeroOperations_ShouldHandleGracefully() {
        // Arrange
        BulkOperationResult result = new BulkOperationResult("EMPTY");
        result.markCompleted();

        // Act
        BatchOperationException exception = BatchOperationException.completeFailure(result);

        // Assert
        assertThat(exception.getMessage())
                .contains("Batch EMPTY operation completely failed")
                .contains("0/0 operations succeeded");
        assertThat(exception.getTotalOperations()).isEqualTo(0);
        assertThat(exception.getSuccessCount()).isEqualTo(0);
        assertThat(exception.getFailureCount()).isEqualTo(0);
        assertThat(exception.getSuccessRate()).isEqualTo(0.0);
        assertThat(exception.isCompleteFailure()).isTrue();
    }

    @Test
    @DisplayName("Should handle partial failure with single success")
    void partialFailure_SingleSuccess_ShouldCalculateCorrectly() {
        // Arrange
        BulkOperationResult result = new BulkOperationResult("DELETE");
        result.addSuccess("msg1");
        result.addFailure("msg2", "Error 1");
        result.addFailure("msg3", "Error 2");
        result.addFailure("msg4", "Error 3");
        result.markCompleted();

        // Act
        BatchOperationException exception = BatchOperationException.partialFailure(result);

        // Assert
        assertThat(exception.getMessage())
                .contains("1/4 operations succeeded")
                .contains("25.0% success rate");
        assertThat(exception.getSuccessRate()).isEqualTo(25.0);
        assertThat(exception.isPartialFailure()).isTrue();
    }

    @Test
    @DisplayName("Should handle partial failure with high success rate")
    void partialFailure_HighSuccessRate_ShouldCalculateCorrectly() {
        // Arrange
        BulkOperationResult result = new BulkOperationResult("MODIFY_LABELS");
        // Add 99 successes and 1 failure
        for (int i = 1; i <= 99; i++) {
            result.addSuccess("msg" + i);
        }
        result.addFailure("msg100", "Single failure");
        result.markCompleted();

        // Act
        BatchOperationException exception = BatchOperationException.partialFailure(result);

        // Assert
        assertThat(exception.getMessage())
                .contains("99/100 operations succeeded")
                .contains("99.0% success rate");
        assertThat(exception.getSuccessRate()).isEqualTo(99.0);
        assertThat(exception.isPartialFailure()).isTrue();
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.MULTI_STATUS.value());
    }

    @Test
    @DisplayName("Should provide access to underlying result properties")
    void getProperties_ShouldDelegateToOperationResult() {
        // Arrange
        BulkOperationResult result = new BulkOperationResult("DELETE");
        result.addSuccess("msg1");
        result.addSuccess("msg2");
        result.addFailure("msg3", "Error 1");
        result.markCompleted();

        BatchOperationException exception = new BatchOperationException("Test", result);

        // Act & Assert
        assertThat(exception.getSuccessCount()).isEqualTo(result.getSuccessCount());
        assertThat(exception.getFailureCount()).isEqualTo(result.getFailureCount());
        assertThat(exception.getTotalOperations()).isEqualTo(result.getTotalOperations());
        assertThat(exception.getSuccessRate()).isEqualTo(result.getSuccessRate());
        assertThat(exception.getOperationResult()).isSameAs(result);
    }

    @Test
    @DisplayName("Should handle various operation types correctly")
    void exception_VariousOperationTypes_ShouldHandleCorrectly() {
        // Test different operation types
        String[] operationTypes = {"DELETE", "MODIFY_LABELS", "MOVE", "ARCHIVE", "MARK_READ"};

        for (String operationType : operationTypes) {
            // Arrange
            BulkOperationResult result = new BulkOperationResult(operationType);
            result.addFailure("msg1", "Error");
            result.markCompleted();

            // Act
            BatchOperationException exception = BatchOperationException.completeFailure(result);

            // Assert
            assertThat(exception.getMessage())
                    .contains("Batch " + operationType + " operation completely failed");
        }
    }

    @Test
    @DisplayName("Should maintain exception inheritance hierarchy")
    void exception_ShouldExtendCorrectHierarchy() {
        // Arrange
        BulkOperationResult result = new BulkOperationResult("DELETE");
        result.addFailure("msg1", "Error");
        result.markCompleted();

        // Act
        BatchOperationException exception = new BatchOperationException("Test", result);

        // Assert
        assertThat(exception).isInstanceOf(GmailBuddyServerException.class);
        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception).isInstanceOf(Exception.class);
        assertThat(exception).isInstanceOf(Throwable.class);
    }

    @Test
    @DisplayName("Should handle null values gracefully")
    void exception_NullValues_ShouldHandleGracefully() {
        // Arrange
        BulkOperationResult result = new BulkOperationResult("DELETE");
        result.addFailure("msg1", null); // null error message
        result.markCompleted();

        // Act & Assert - Should not throw exceptions
        assertThatNoException().isThrownBy(() -> {
            BatchOperationException exception = BatchOperationException.completeFailure(result);
            assertThat(exception.getMessage()).isNotNull();
            assertThat(exception.getOperationResult()).isNotNull();
        });
    }

    @Test
    @DisplayName("Should provide meaningful exception information for debugging")
    void exception_DebuggingInformation_ShouldBeMeaningful() {
        // Arrange
        BulkOperationResult result = new BulkOperationResult("DELETE");
        result.addSuccess("msg1");
        result.addSuccess("msg2");
        result.addFailure("msg3", "Rate limit exceeded");
        result.addFailure("msg4", "Message not found");
        result.addFailure("msg5", "Permission denied");
        result.markCompleted();

        // Act
        BatchOperationException exception = BatchOperationException.partialFailure(result);

        // Assert
        String message = exception.getMessage();
        assertThat(message)
                .contains("DELETE") // Operation type
                .contains("2/5") // Success/total ratio
                .contains("40.0%"); // Success rate

        // Verify access to detailed failure information
        assertThat(exception.getOperationResult().getFailedOperations())
                .hasSize(3)
                .containsKey("msg3")
                .containsKey("msg4")
                .containsKey("msg5");

        assertThat(exception.getOperationResult().getSuccessfulOperations())
                .hasSize(2)
                .contains("msg1", "msg2");
    }

    @Test
    @DisplayName("Should handle edge case of single operation failure")
    void exception_SingleOperationFailure_ShouldHandleCorrectly() {
        // Arrange
        BulkOperationResult result = new BulkOperationResult("DELETE");
        result.addFailure("singleMsg", "Network timeout");
        result.markCompleted();

        // Act
        BatchOperationException exception = BatchOperationException.completeFailure(result);

        // Assert
        assertThat(exception.getMessage())
                .contains("Batch DELETE operation completely failed")
                .contains("0/1 operations succeeded");
        assertThat(exception.getTotalOperations()).isEqualTo(1);
        assertThat(exception.getFailureCount()).isEqualTo(1);
        assertThat(exception.getSuccessCount()).isEqualTo(0);
        assertThat(exception.isCompleteFailure()).isTrue();
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
    }

    @Test
    @DisplayName("Should handle edge case of single operation success with failure")
    void exception_SingleSuccessWithFailure_ShouldBePartialFailure() {
        // Arrange
        BulkOperationResult result = new BulkOperationResult("MODIFY_LABELS");
        result.addSuccess("msg1");
        result.addFailure("msg2", "Invalid label");
        result.markCompleted();

        // Act
        BatchOperationException exception = BatchOperationException.partialFailure(result);

        // Assert
        assertThat(exception.getMessage())
                .contains("Batch MODIFY_LABELS operation partially failed")
                .contains("1/2 operations succeeded")
                .contains("50.0% success rate");
        assertThat(exception.getTotalOperations()).isEqualTo(2);
        assertThat(exception.isPartialFailure()).isTrue();
        assertThat(exception.isCompleteFailure()).isFalse();
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.MULTI_STATUS.value());
    }
}