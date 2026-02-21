package com.aucontraire.gmailbuddy.dto.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OperationStatus Enum Tests")
class OperationStatusTest {

    @Test
    @DisplayName("SUCCESS status has correct description")
    void testSuccessStatus() {
        assertThat(OperationStatus.SUCCESS.getDescription())
            .isEqualTo("Operation completed successfully");
    }

    @Test
    @DisplayName("PARTIAL_SUCCESS status has correct description")
    void testPartialSuccessStatus() {
        assertThat(OperationStatus.PARTIAL_SUCCESS.getDescription())
            .isEqualTo("Operation partially completed");
    }

    @Test
    @DisplayName("NO_RESULTS status has correct description")
    void testNoResultsStatus() {
        assertThat(OperationStatus.NO_RESULTS.getDescription())
            .isEqualTo("No items matched the criteria");
    }

    @Test
    @DisplayName("FAILURE status has correct description")
    void testFailureStatus() {
        assertThat(OperationStatus.FAILURE.getDescription())
            .isEqualTo("Operation failed");
    }

    @Test
    @DisplayName("All enum values are present")
    void testAllEnumValues() {
        OperationStatus[] values = OperationStatus.values();
        assertThat(values).hasSize(4);
        assertThat(values).containsExactlyInAnyOrder(
            OperationStatus.SUCCESS,
            OperationStatus.PARTIAL_SUCCESS,
            OperationStatus.NO_RESULTS,
            OperationStatus.FAILURE
        );
    }

    @Test
    @DisplayName("valueOf works correctly")
    void testValueOf() {
        assertThat(OperationStatus.valueOf("SUCCESS"))
            .isEqualTo(OperationStatus.SUCCESS);
        assertThat(OperationStatus.valueOf("NO_RESULTS"))
            .isEqualTo(OperationStatus.NO_RESULTS);
    }

    @Test
    @DisplayName("Enum can be used in switch statements")
    void testSwitchUsage() {
        String result = switch (OperationStatus.SUCCESS) {
            case SUCCESS -> "all good";
            case PARTIAL_SUCCESS -> "some failed";
            case NO_RESULTS -> "none found";
            case FAILURE -> "all failed";
        };

        assertThat(result).isEqualTo("all good");
    }
}