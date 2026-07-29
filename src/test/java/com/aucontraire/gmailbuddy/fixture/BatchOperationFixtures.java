package com.aucontraire.gmailbuddy.fixture;

import com.aucontraire.gmailbuddy.service.BulkOperationResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Static factory methods that produce Gmail message-id lists and
 * {@link BulkOperationResult} instances for use in unit and integration tests
 * throughout the batch-by-id endpoints feature (Phase 2 Foundational, T004).
 *
 * <h2>Design</h2>
 * <p>All methods are static. This class carries no Spring context
 * ({@code @Component} is intentionally absent) so it can be used from any test
 * layer — plain JUnit, {@code @WebMvcTest}, {@code @SpringBootTest} — without
 * needing a running application context. Mirrors the design of
 * {@link ReadApiFixtures}, {@link SendMessageRequestFixtures}, and
 * {@link AttachmentFixtures}.</p>
 *
 * <h2>Naming conventions</h2>
 * <p>Method names follow the pattern {@code valid*()} for message-id lists that
 * conform to the {@code @GmailMessageId} format (hex characters, max 32 length —
 * see {@link com.aucontraire.gmailbuddy.validation.GmailMessageId}), and
 * {@code build*Result()} for {@link BulkOperationResult} builders shaped around
 * the three outcome scenarios a batch endpoint can produce: all-success,
 * partial, and all-fail.</p>
 */
public final class BatchOperationFixtures {

    /** Operation type label used for fixture {@link BulkOperationResult} instances. */
    private static final String FIXTURE_OPERATION_TYPE = "BATCH_FIXTURE";

    /** Error message used for failed operations in fixture results. */
    private static final String DEFAULT_ERROR_MESSAGE = "Gmail message not found";

    /** Small, well-formed hex message ids (matches {@code [0-9a-fA-F]{1,32}}). */
    private static final List<String> VALID_MESSAGE_IDS = List.of(
            "18e1f9a2b3c4d5e6",
            "18e1f9a2b3c4d5e7",
            "18e1f9a2b3c4d5e8",
            "18e1f9a2b3c4d5e9",
            "18e1f9a2b3c4d5ea"
    );

    // Utility class — no instances.
    private BatchOperationFixtures() {
        throw new AssertionError(
                "BatchOperationFixtures is a static factory class and must not be instantiated");
    }

    // -------------------------------------------------------------------------
    // Message-id list fixtures
    // -------------------------------------------------------------------------

    /**
     * Returns an immutable list of {@code n} well-formed Gmail short hex
     * message ids, drawn from {@link #VALID_MESSAGE_IDS}.
     *
     * @param n the number of ids to return; must be between 1 and
     *          {@link #VALID_MESSAGE_IDS}'s size (inclusive)
     * @return an immutable list of {@code n} valid message ids
     * @throws IllegalArgumentException if {@code n} is out of range
     */
    public static List<String> validMessageIds(int n) {
        if (n < 1 || n > VALID_MESSAGE_IDS.size()) {
            throw new IllegalArgumentException(
                    "n must be between 1 and " + VALID_MESSAGE_IDS.size() + " but was " + n);
        }
        return VALID_MESSAGE_IDS.subList(0, n);
    }

    /**
     * Returns the full, immutable list of well-formed Gmail short hex message
     * ids used as the backing source for {@link #validMessageIds(int)}.
     *
     * @return an immutable list of 5 valid message ids
     */
    public static List<String> allValidMessageIds() {
        return VALID_MESSAGE_IDS;
    }

    // -------------------------------------------------------------------------
    // BulkOperationResult builders
    // -------------------------------------------------------------------------

    /**
     * Returns a completed {@link BulkOperationResult} where every id in
     * {@code messageIds} was recorded as a success and none failed.
     *
     * @param messageIds the ids to record as successful
     * @return a completed all-success result
     */
    public static BulkOperationResult buildAllSuccessResult(List<String> messageIds) {
        BulkOperationResult result = new BulkOperationResult(FIXTURE_OPERATION_TYPE);
        for (String id : messageIds) {
            result.addSuccess(id);
        }
        result.markCompleted();
        return result;
    }

    /**
     * Returns a completed {@link BulkOperationResult} where the first
     * {@code successCount} ids in {@code messageIds} are recorded as
     * successful and the remainder are recorded as failed with
     * {@link #DEFAULT_ERROR_MESSAGE}.
     *
     * @param messageIds   the full ordered list of ids to distribute between
     *                     success and failure
     * @param successCount the number of leading ids to record as successful;
     *                     must be between 1 and {@code messageIds.size() - 1}
     *                     inclusive so the result is a genuine partial outcome
     * @return a completed partial-success result
     * @throws IllegalArgumentException if {@code successCount} does not leave
     *                                   at least one success and one failure
     */
    public static BulkOperationResult buildPartialResult(List<String> messageIds, int successCount) {
        if (successCount < 1 || successCount >= messageIds.size()) {
            throw new IllegalArgumentException(
                    "successCount must be between 1 and messageIds.size() - 1 (inclusive) "
                            + "but was " + successCount + " for a list of size " + messageIds.size());
        }
        BulkOperationResult result = new BulkOperationResult(FIXTURE_OPERATION_TYPE);
        List<String> successes = messageIds.subList(0, successCount);
        List<String> failures = messageIds.subList(successCount, messageIds.size());
        for (String id : successes) {
            result.addSuccess(id);
        }
        for (String id : failures) {
            result.addFailure(id, DEFAULT_ERROR_MESSAGE);
        }
        result.markCompleted();
        return result;
    }

    /**
     * Returns a completed {@link BulkOperationResult} where every id in
     * {@code messageIds} was recorded as a failure with
     * {@link #DEFAULT_ERROR_MESSAGE} and none succeeded.
     *
     * @param messageIds the ids to record as failed
     * @return a completed all-fail result
     */
    public static BulkOperationResult buildAllFailResult(List<String> messageIds) {
        BulkOperationResult result = new BulkOperationResult(FIXTURE_OPERATION_TYPE);
        for (String id : messageIds) {
            result.addFailure(id, DEFAULT_ERROR_MESSAGE);
        }
        result.markCompleted();
        return result;
    }

    /**
     * Returns a defensive mutable copy of {@code source}, useful for tests
     * that need to mutate a fixture-derived list without affecting the shared
     * static fixtures.
     *
     * @param source the list to copy
     * @return a new, mutable {@link ArrayList} containing the same elements
     */
    public static List<String> mutableCopy(List<String> source) {
        return new ArrayList<>(source);
    }
}
