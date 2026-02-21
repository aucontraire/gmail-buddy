package com.aucontraire.gmailbuddy.dto.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for ProblemDetail RFC 7807 class.
 *
 * @since 1.0
 */
@DisplayName("ProblemDetail RFC 7807 Tests")
class ProblemDetailTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    @Test
    @DisplayName("Builder creates valid ProblemDetail with all required fields")
    void testBuilderWithRequiredFields() {
        ProblemDetail problem = ProblemDetail.builder()
            .type("/problems/validation-error")
            .title("Validation Error")
            .status(400)
            .build();

        assertThat(problem.getType()).isEqualTo(URI.create("/problems/validation-error"));
        assertThat(problem.getTitle()).isEqualTo("Validation Error");
        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("Builder creates ProblemDetail with all fields")
    void testBuilderWithAllFields() {
        ProblemDetail problem = ProblemDetail.builder()
            .type("/problems/validation-error")
            .title("Validation Error")
            .status(400)
            .detail("Email field is required")
            .instance("/api/v1/gmail/messages")
            .requestId("test-request-123")
            .retryable(false)
            .category("CLIENT_ERROR")
            .extension("field", "email")
            .build();

        assertThat(problem.getType()).isEqualTo(URI.create("/problems/validation-error"));
        assertThat(problem.getTitle()).isEqualTo("Validation Error");
        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getDetail()).isEqualTo("Email field is required");
        assertThat(problem.getInstance()).isEqualTo(URI.create("/api/v1/gmail/messages"));
        assertThat(problem.getRequestId()).isEqualTo("test-request-123");
        assertThat(problem.getRetryable()).isFalse();
        assertThat(problem.getCategory()).isEqualTo("CLIENT_ERROR");
        assertThat(problem.getExtensions()).containsEntry("field", "email");
    }

    @Test
    @DisplayName("Builder throws IllegalStateException when type is missing")
    void testBuilderRequiresType() {
        assertThatThrownBy(() ->
            ProblemDetail.builder()
                .title("Error")
                .status(400)
                .build()
        ).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("type is required");
    }

    @Test
    @DisplayName("Builder throws IllegalStateException when title is missing")
    void testBuilderRequiresTitle() {
        assertThatThrownBy(() ->
            ProblemDetail.builder()
                .type("/problems/error")
                .status(400)
                .build()
        ).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("title is required");
    }

    @Test
    @DisplayName("Builder throws IllegalStateException when status is missing")
    void testBuilderRequiresStatus() {
        assertThatThrownBy(() ->
            ProblemDetail.builder()
                .type("/problems/error")
                .title("Error")
                .build()
        ).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("status is required");
    }

    @Test
    @DisplayName("Builder accepts URI objects for type and instance")
    void testBuilderWithUriObjects() {
        URI typeUri = URI.create("/problems/test");
        URI instanceUri = URI.create("/api/test");

        ProblemDetail problem = ProblemDetail.builder()
            .type(typeUri)
            .title("Test")
            .status(500)
            .instance(instanceUri)
            .build();

        assertThat(problem.getType()).isEqualTo(typeUri);
        assertThat(problem.getInstance()).isEqualTo(instanceUri);
    }

    @Test
    @DisplayName("Builder allows multiple extensions")
    void testBuilderWithMultipleExtensions() {
        ProblemDetail problem = ProblemDetail.builder()
            .type("/problems/validation-error")
            .title("Validation Error")
            .status(400)
            .extension("field1", "error1")
            .extension("field2", "error2")
            .extension("field3", "error3")
            .build();

        assertThat(problem.getExtensions())
            .hasSize(3)
            .containsEntry("field1", "error1")
            .containsEntry("field2", "error2")
            .containsEntry("field3", "error3");
    }

    @Test
    @DisplayName("Builder allows setting extensions map directly")
    void testBuilderWithExtensionsMap() {
        Map<String, Object> extensions = Map.of(
            "field1", "value1",
            "field2", "value2"
        );

        ProblemDetail problem = ProblemDetail.builder()
            .type("/problems/test")
            .title("Test")
            .status(400)
            .extensions(extensions)
            .build();

        assertThat(problem.getExtensions()).containsAllEntriesOf(extensions);
    }

    @Test
    @DisplayName("JSON serialization includes all non-null fields")
    void testJsonSerialization() throws Exception {
        ProblemDetail problem = ProblemDetail.builder()
            .type("/problems/validation-error")
            .title("Validation Error")
            .status(400)
            .detail("Invalid input")
            .instance("/api/test")
            .requestId("req-123")
            .retryable(false)
            .category("CLIENT_ERROR")
            .extension("field", "email")
            .build();

        String json = objectMapper.writeValueAsString(problem);

        assertThat(json).contains("\"type\":\"/problems/validation-error\"");
        assertThat(json).contains("\"title\":\"Validation Error\"");
        assertThat(json).contains("\"status\":400");
        assertThat(json).contains("\"detail\":\"Invalid input\"");
        assertThat(json).contains("\"instance\":\"/api/test\"");
        assertThat(json).contains("\"requestId\":\"req-123\"");
        assertThat(json).contains("\"retryable\":false");
        assertThat(json).contains("\"category\":\"CLIENT_ERROR\"");
        assertThat(json).contains("\"extensions\"");
    }

    @Test
    @DisplayName("JSON serialization excludes null fields")
    void testJsonSerializationExcludesNulls() throws Exception {
        ProblemDetail problem = ProblemDetail.builder()
            .type("/problems/test")
            .title("Test")
            .status(500)
            .build();

        String json = objectMapper.writeValueAsString(problem);

        assertThat(json).doesNotContain("\"detail\":");
        assertThat(json).doesNotContain("\"instance\":");
        assertThat(json).doesNotContain("\"retryable\":");
        assertThat(json).doesNotContain("\"category\":");
        assertThat(json).doesNotContain("\"extensions\":");
    }

    @Test
    @DisplayName("JSON deserialization recreates ProblemDetail correctly")
    void testJsonDeserialization() throws Exception {
        String json = """
            {
                "type": "/problems/validation-error",
                "title": "Validation Error",
                "status": 400,
                "detail": "Invalid input",
                "instance": "/api/test",
                "requestId": "req-123",
                "timestamp": "2025-10-13T12:00:00Z",
                "retryable": false,
                "category": "CLIENT_ERROR",
                "extensions": {"field": "email"}
            }
            """;

        ProblemDetail problem = objectMapper.readValue(json, ProblemDetail.class);

        assertThat(problem.getType().toString()).isEqualTo("/problems/validation-error");
        assertThat(problem.getTitle()).isEqualTo("Validation Error");
        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getDetail()).isEqualTo("Invalid input");
        assertThat(problem.getInstance().toString()).isEqualTo("/api/test");
        assertThat(problem.getRequestId()).isEqualTo("req-123");
        assertThat(problem.getRetryable()).isFalse();
        assertThat(problem.getCategory()).isEqualTo("CLIENT_ERROR");
        assertThat(problem.getExtensions()).containsEntry("field", "email");
    }

    @Test
    @DisplayName("toString includes all fields")
    void testToString() {
        ProblemDetail problem = ProblemDetail.builder()
            .type("/problems/test")
            .title("Test")
            .status(500)
            .detail("Test detail")
            .build();

        String toString = problem.toString();

        assertThat(toString).contains("ProblemDetail{");
        assertThat(toString).contains("type=/problems/test");
        assertThat(toString).contains("title='Test'");
        assertThat(toString).contains("status=500");
        assertThat(toString).contains("detail='Test detail'");
    }
}
