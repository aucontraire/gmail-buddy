package com.aucontraire.gmailbuddy.dto.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("ResponseMetadata Tests")
class ResponseMetadataTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("Default constructor sets timestamp to current time")
    void testDefaultConstructor() {
        Instant before = Instant.now();
        ResponseMetadata metadata = new ResponseMetadata();
        Instant after = Instant.now();

        assertNotNull(metadata.getTimestamp());
        assertThat(metadata.getTimestamp())
            .isAfterOrEqualTo(before)
            .isBeforeOrEqualTo(after);
    }

    @Test
    @DisplayName("Builder creates metadata with all fields")
    void testBuilderWithAllFields() {
        ResponseMetadata metadata = ResponseMetadata.builder()
            .durationMs(150L)
            .quotaUsed(50)
            .build();

        assertThat(metadata.getTimestamp()).isNotNull();
        assertThat(metadata.getDurationMs()).isEqualTo(150L);
        assertThat(metadata.getQuotaUsed()).isEqualTo(50);
    }

    @Test
    @DisplayName("Builder creates metadata with only duration")
    void testBuilderWithOnlyDuration() {
        ResponseMetadata metadata = ResponseMetadata.builder()
            .durationMs(200L)
            .build();

        assertThat(metadata.getDurationMs()).isEqualTo(200L);
        assertThat(metadata.getQuotaUsed()).isNull();
    }

    @Test
    @DisplayName("Builder creates metadata with only quota")
    void testBuilderWithOnlyQuota() {
        ResponseMetadata metadata = ResponseMetadata.builder()
            .quotaUsed(100)
            .build();

        assertThat(metadata.getQuotaUsed()).isEqualTo(100);
        assertThat(metadata.getDurationMs()).isNull();
    }

    @Test
    @DisplayName("Builder creates metadata with no optional fields")
    void testBuilderWithNoOptionalFields() {
        ResponseMetadata metadata = ResponseMetadata.builder().build();

        assertThat(metadata.getTimestamp()).isNotNull();
        assertThat(metadata.getDurationMs()).isNull();
        assertThat(metadata.getQuotaUsed()).isNull();
    }

    @Test
    @DisplayName("JSON serialization includes all non-null fields")
    void testJsonSerializationWithAllFields() throws Exception {
        ResponseMetadata metadata = ResponseMetadata.builder()
            .durationMs(150L)
            .quotaUsed(50)
            .build();

        String json = objectMapper.writeValueAsString(metadata);

        assertThat(json).contains("\"timestamp\":");
        assertThat(json).contains("\"durationMs\":150");
        assertThat(json).contains("\"quotaUsed\":50");
    }

    @Test
    @DisplayName("JSON serialization excludes null fields")
    void testJsonSerializationExcludesNullFields() throws Exception {
        ResponseMetadata metadata = ResponseMetadata.builder()
            .durationMs(150L)
            .build();

        String json = objectMapper.writeValueAsString(metadata);

        assertThat(json).contains("\"timestamp\":");
        assertThat(json).contains("\"durationMs\":150");
        assertThat(json).doesNotContain("quotaUsed");  // Should be omitted
    }

    @Test
    @DisplayName("JSON deserialization recreates object correctly")
    void testJsonDeserialization() throws Exception {
        String json = """
            {
                "timestamp": "2025-10-12T10:30:00.000Z",
                "durationMs": 156,
                "quotaUsed": 50
            }
            """;

        ResponseMetadata metadata = objectMapper.readValue(json, ResponseMetadata.class);

        assertThat(metadata.getTimestamp()).isNotNull();
        assertThat(metadata.getDurationMs()).isEqualTo(156L);
        assertThat(metadata.getQuotaUsed()).isEqualTo(50);
    }

    @Test
    @DisplayName("toString contains all fields")
    void testToString() {
        ResponseMetadata metadata = ResponseMetadata.builder()
            .durationMs(150L)
            .quotaUsed(50)
            .build();

        String toString = metadata.toString();

        assertThat(toString).contains("ResponseMetadata{");
        assertThat(toString).contains("timestamp=");
        assertThat(toString).contains("durationMs=150");
        assertThat(toString).contains("quotaUsed=50");
    }

    @Test
    @DisplayName("Builder can be reused")
    void testBuilderReuse() {
        ResponseMetadata.Builder builder = ResponseMetadata.builder();

        ResponseMetadata metadata1 = builder.durationMs(100L).build();
        ResponseMetadata metadata2 = builder.durationMs(200L).build();

        // Both should have duration set (builder mutates the same instance)
        assertThat(metadata1.getDurationMs()).isEqualTo(200L); // Last value wins
        assertThat(metadata2.getDurationMs()).isEqualTo(200L);
    }

    @Test
    @DisplayName("Zero values are serialized")
    void testZeroValues() throws Exception {
        ResponseMetadata metadata = ResponseMetadata.builder()
            .durationMs(0L)
            .quotaUsed(0)
            .build();

        String json = objectMapper.writeValueAsString(metadata);

        assertThat(json).contains("\"durationMs\":0");
        assertThat(json).contains("\"quotaUsed\":0");
    }

    @Test
    @DisplayName("Negative values are allowed (for edge case testing)")
    void testNegativeValues() {
        ResponseMetadata metadata = ResponseMetadata.builder()
            .durationMs(-1L)
            .quotaUsed(-1)
            .build();

        assertThat(metadata.getDurationMs()).isEqualTo(-1L);
        assertThat(metadata.getQuotaUsed()).isEqualTo(-1);
    }
}