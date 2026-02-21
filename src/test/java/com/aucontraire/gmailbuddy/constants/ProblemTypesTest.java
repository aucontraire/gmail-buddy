package com.aucontraire.gmailbuddy.constants;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ProblemTypes RFC 7807 constants.
 *
 * @since 1.0
 */
@DisplayName("ProblemTypes Constants Tests")
class ProblemTypesTest {

    @Test
    @DisplayName("All problem type URIs start with /problems/")
    void testAllUrisHaveCorrectBase() {
        assertThat(ProblemTypes.VALIDATION_ERROR).startsWith("/problems/");
        assertThat(ProblemTypes.MESSAGE_NOT_FOUND).startsWith("/problems/");
        assertThat(ProblemTypes.RESOURCE_NOT_FOUND).startsWith("/problems/");
        assertThat(ProblemTypes.AUTHENTICATION_FAILED).startsWith("/problems/");
        assertThat(ProblemTypes.AUTHORIZATION_FAILED).startsWith("/problems/");
        assertThat(ProblemTypes.RATE_LIMIT_EXCEEDED).startsWith("/problems/");
        assertThat(ProblemTypes.CONSTRAINT_VIOLATION).startsWith("/problems/");
        assertThat(ProblemTypes.GMAIL_API_ERROR).startsWith("/problems/");
        assertThat(ProblemTypes.SERVICE_UNAVAILABLE).startsWith("/problems/");
        assertThat(ProblemTypes.INTERNAL_ERROR).startsWith("/problems/");
        assertThat(ProblemTypes.QUOTA_EXCEEDED).startsWith("/problems/");
        assertThat(ProblemTypes.BATCH_OPERATION_ERROR).startsWith("/problems/");
    }

    @Test
    @DisplayName("isClientError correctly identifies 4xx problem types")
    void testIsClientError() {
        assertThat(ProblemTypes.isClientError(ProblemTypes.VALIDATION_ERROR)).isTrue();
        assertThat(ProblemTypes.isClientError(ProblemTypes.MESSAGE_NOT_FOUND)).isTrue();
        assertThat(ProblemTypes.isClientError(ProblemTypes.RESOURCE_NOT_FOUND)).isTrue();
        assertThat(ProblemTypes.isClientError(ProblemTypes.AUTHENTICATION_FAILED)).isTrue();
        assertThat(ProblemTypes.isClientError(ProblemTypes.AUTHORIZATION_FAILED)).isTrue();
        assertThat(ProblemTypes.isClientError(ProblemTypes.RATE_LIMIT_EXCEEDED)).isTrue();
        assertThat(ProblemTypes.isClientError(ProblemTypes.CONSTRAINT_VIOLATION)).isTrue();

        // Server errors should return false
        assertThat(ProblemTypes.isClientError(ProblemTypes.GMAIL_API_ERROR)).isFalse();
        assertThat(ProblemTypes.isClientError(ProblemTypes.SERVICE_UNAVAILABLE)).isFalse();
        assertThat(ProblemTypes.isClientError(ProblemTypes.INTERNAL_ERROR)).isFalse();
    }

    @Test
    @DisplayName("isServerError correctly identifies 5xx problem types")
    void testIsServerError() {
        assertThat(ProblemTypes.isServerError(ProblemTypes.GMAIL_API_ERROR)).isTrue();
        assertThat(ProblemTypes.isServerError(ProblemTypes.SERVICE_UNAVAILABLE)).isTrue();
        assertThat(ProblemTypes.isServerError(ProblemTypes.INTERNAL_ERROR)).isTrue();
        assertThat(ProblemTypes.isServerError(ProblemTypes.QUOTA_EXCEEDED)).isTrue();
        assertThat(ProblemTypes.isServerError(ProblemTypes.BATCH_OPERATION_ERROR)).isTrue();

        // Client errors should return false
        assertThat(ProblemTypes.isServerError(ProblemTypes.VALIDATION_ERROR)).isFalse();
        assertThat(ProblemTypes.isServerError(ProblemTypes.MESSAGE_NOT_FOUND)).isFalse();
        assertThat(ProblemTypes.isServerError(ProblemTypes.AUTHENTICATION_FAILED)).isFalse();
    }

    @Test
    @DisplayName("getDescription returns correct descriptions for all problem types")
    void testGetDescription() {
        assertThat(ProblemTypes.getDescription(ProblemTypes.VALIDATION_ERROR))
            .isEqualTo("Input data failed validation rules");
        assertThat(ProblemTypes.getDescription(ProblemTypes.MESSAGE_NOT_FOUND))
            .isEqualTo("The requested Gmail message does not exist");
        assertThat(ProblemTypes.getDescription(ProblemTypes.RESOURCE_NOT_FOUND))
            .isEqualTo("The requested resource does not exist");
        assertThat(ProblemTypes.getDescription(ProblemTypes.AUTHENTICATION_FAILED))
            .isEqualTo("User authentication failed or token is invalid");
        assertThat(ProblemTypes.getDescription(ProblemTypes.AUTHORIZATION_FAILED))
            .isEqualTo("User lacks permission to perform this action");
        assertThat(ProblemTypes.getDescription(ProblemTypes.RATE_LIMIT_EXCEEDED))
            .isEqualTo("Too many requests in a given time window");
        assertThat(ProblemTypes.getDescription(ProblemTypes.INTERNAL_ERROR))
            .isEqualTo("Unexpected error occurred on the server");
    }

    @Test
    @DisplayName("getDescription returns null for unknown problem type")
    void testGetDescriptionForUnknown() {
        assertThat(ProblemTypes.getDescription("/problems/unknown-type")).isNull();
    }

    @Test
    @DisplayName("All problem type URIs are unique")
    void testAllUrisAreUnique() {
        assertThat(ProblemTypes.VALIDATION_ERROR).isNotEqualTo(ProblemTypes.MESSAGE_NOT_FOUND);
        assertThat(ProblemTypes.RESOURCE_NOT_FOUND).isNotEqualTo(ProblemTypes.AUTHENTICATION_FAILED);
        assertThat(ProblemTypes.RATE_LIMIT_EXCEEDED).isNotEqualTo(ProblemTypes.QUOTA_EXCEEDED);
        assertThat(ProblemTypes.GMAIL_API_ERROR).isNotEqualTo(ProblemTypes.SERVICE_UNAVAILABLE);
    }
}
