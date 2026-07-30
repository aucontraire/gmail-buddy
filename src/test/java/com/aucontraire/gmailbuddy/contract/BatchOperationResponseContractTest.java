package com.aucontraire.gmailbuddy.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.aucontraire.gmailbuddy.dto.response.BatchOperationResponse;
import com.aucontraire.gmailbuddy.mapper.ResponseMapper;
import com.aucontraire.gmailbuddy.service.BulkOperationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

/**
 * Contract / golden-master test pinning the exact JSON wire-shape of {@link BatchOperationResponse}
 * — the response of the feature-005 batch endpoints (batchTrash / batchUntrash / batchModifyLabels).
 *
 * <p>The fixtures under {@code src/test/resources/contract/batch-operation-response-*.json} are the
 * canonical examples of what this API emits (any consumer may vendor them). This test serializes the
 * real DTO produced by {@link ResponseMapper#toBatchOperationResponse} and fails if a DTO or mapper
 * change alters the wire shape (field names, {@code status} vocabulary, per-id outcome structure),
 * catching accidental contract drift at build time rather than in integration.
 *
 * <p>The non-deterministic {@code metadata} object ({@code durationMs} varies per run) is asserted
 * present with numeric {@code durationMs}/{@code quotaUsed} but excluded from the pinned shape.
 * Object-key order (e.g. {@code failedOperations}) is compared order-independently via {@link
 * JsonNode} tree equality.
 */
@JsonTest
class BatchOperationResponseContractTest {

    @Autowired
    private ObjectMapper objectMapper;

    private final ResponseMapper responseMapper = new ResponseMapper();

    @Test
    void fullSuccess_matchesCanonicalShape() throws Exception {
        BulkOperationResult result = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_MODIFY);
        result.addSuccess("id1");
        result.addSuccess("id2");
        result.markCompleted();
        assertMatchesFixtureIgnoringMetadata(result, "contract/batch-operation-response-success.json");
    }

    @Test
    void partialFailure_matchesCanonicalShape() throws Exception {
        BulkOperationResult result = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_MODIFY);
        result.addSuccess("id1");
        result.addFailure("id2", "notFound");
        result.markCompleted();
        assertMatchesFixtureIgnoringMetadata(result, "contract/batch-operation-response-partial-failure.json");
    }

    @Test
    void totalFailure_matchesCanonicalShape() throws Exception {
        BulkOperationResult result = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_MODIFY);
        result.addFailure("id1", "notFound");
        result.addFailure("id2", "notFound");
        result.markCompleted();
        assertMatchesFixtureIgnoringMetadata(result, "contract/batch-operation-response-total-failure.json");
    }

    /**
     * Serializes the DTO with the production ObjectMapper and asserts the stable contract shape
     * matches {@code fixturePath}. The {@code metadata} object is asserted present + numeric, then
     * excluded from the comparison (its {@code durationMs} is non-deterministic).
     */
    private void assertMatchesFixtureIgnoringMetadata(BulkOperationResult result, String fixturePath) throws Exception {
        BatchOperationResponse dto = responseMapper.toBatchOperationResponse(result);
        JsonNode actual = objectMapper.readTree(objectMapper.writeValueAsString(dto));

        // Document (but do not pin) metadata: present, with numeric durationMs + quotaUsed.
        assertThat(actual.has("metadata")).as("metadata present").isTrue();
        assertThat(actual.path("metadata").path("durationMs").isNumber())
                .as("metadata.durationMs numeric")
                .isTrue();
        assertThat(actual.path("metadata").path("quotaUsed").isNumber())
                .as("metadata.quotaUsed numeric")
                .isTrue();

        // Pin the stable contract shape only (metadata excluded — non-deterministic durationMs).
        ((ObjectNode) actual).remove("metadata");

        JsonNode expected;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(fixturePath)) {
            assertThat(in)
                    .as("fixture %s must be on the classpath", fixturePath)
                    .isNotNull();
            expected = objectMapper.readTree(in);
        }

        // JsonNode equality is object-key-order-independent (handles failedOperations map ordering).
        assertThat(actual).isEqualTo(expected);
    }
}
