package com.aucontraire.gmailbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import com.aucontraire.gmailbuddy.exception.ValidationException;
import com.aucontraire.gmailbuddy.fixture.BatchOperationFixtures;
import com.aucontraire.gmailbuddy.mapper.FilterCriteriaMapper;
import com.aucontraire.gmailbuddy.mapper.GmailMessageMapper;
import com.aucontraire.gmailbuddy.repository.GmailRepository;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link GmailService#batchTrashMessages} and
 * {@link GmailService#batchUntrashMessages} (feature 005 US1, T013).
 *
 * <p>Covers the two behaviors owned by the service layer for these endpoints:</p>
 * <ul>
 *   <li>Best-effort delegation to {@link GmailRepository} — the returned
 *       {@link BulkOperationResult} is the exact same instance the repository
 *       produced, so per-id success/failure detail is preserved untouched.</li>
 *   <li>The {@code validateBatchSize} guard, which rejects a batch exceeding the
 *       configured {@code gmail-buddy.gmail-api.batch-delete-max-results} ceiling
 *       with a {@link ValidationException} (mapped to HTTP 400) and allows a
 *       batch exactly at that ceiling through (FR-004).</li>
 * </ul>
 *
 * <p>Mirrors the plain-Mockito setup pattern in {@code GmailServiceTest} — no
 * Spring context is needed.</p>
 */
@DisplayName("GmailService.batchTrashMessages / batchUntrashMessages (T013)")
class GmailServiceBatchTrashTest {

    /**
     * Named constant for the configured batch-size ceiling used by the boundary
     * tests below, so the boundary math at each call site is self-explanatory
     * rather than a magic number.
     */
    private static final long TEST_MAX_BATCH_SIZE = 5L;

    private static final String USER_ID = "me";

    private GmailRepository gmailRepository;
    private GmailService gmailService;

    @BeforeEach
    void setUp() {
        gmailRepository = mock(GmailRepository.class);
        GmailQueryBuilder gmailQueryBuilder = mock(GmailQueryBuilder.class);
        FilterCriteriaMapper filterCriteriaMapper = mock(FilterCriteriaMapper.class);
        MimeMessageBuilder mimeMessageBuilder = mock(MimeMessageBuilder.class);
        GmailMessageMapper gmailMessageMapper = mock(GmailMessageMapper.class);
        GmailBuddyProperties properties = mock(GmailBuddyProperties.class);
        GmailBuddyProperties.GmailApi gmailApiProperties = mock(GmailBuddyProperties.GmailApi.class);
        when(properties.gmailApi()).thenReturn(gmailApiProperties);
        when(gmailApiProperties.batchDeleteMaxResults()).thenReturn(TEST_MAX_BATCH_SIZE);

        gmailService = new GmailService(
                gmailRepository,
                gmailQueryBuilder,
                filterCriteriaMapper,
                mimeMessageBuilder,
                gmailMessageMapper,
                properties);
    }

    // -------------------------------------------------------------------------
    // Delegation: BulkOperationResult returned unchanged, best-effort per-id preserved
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchTrashMessages_delegatesToRepository_returnsResultUnchanged")
    void batchTrashMessages_delegatesToRepository_returnsResultUnchanged() throws Exception {
        // Arrange
        List<String> messageIds = BatchOperationFixtures.validMessageIds(3);
        BulkOperationResult repositoryResult = BatchOperationFixtures.buildPartialResult(messageIds, 2);
        when(gmailRepository.batchTrashMessages(USER_ID, messageIds)).thenReturn(repositoryResult);

        // Act
        BulkOperationResult result = gmailService.batchTrashMessages(USER_ID, messageIds);

        // Assert: same instance returned, so per-id success/failure detail survives untouched
        assertThat(result).isSameAs(repositoryResult);
        verify(gmailRepository).batchTrashMessages(USER_ID, messageIds);
    }

    @Test
    @DisplayName("batchUntrashMessages_delegatesToRepository_returnsResultUnchanged")
    void batchUntrashMessages_delegatesToRepository_returnsResultUnchanged() throws Exception {
        // Arrange
        List<String> messageIds = BatchOperationFixtures.validMessageIds(3);
        BulkOperationResult repositoryResult = BatchOperationFixtures.buildPartialResult(messageIds, 1);
        when(gmailRepository.batchUntrashMessages(USER_ID, messageIds)).thenReturn(repositoryResult);

        // Act
        BulkOperationResult result = gmailService.batchUntrashMessages(USER_ID, messageIds);

        // Assert
        assertThat(result).isSameAs(repositoryResult);
        verify(gmailRepository).batchUntrashMessages(USER_ID, messageIds);
    }

    // -------------------------------------------------------------------------
    // validateBatchSize: rejects over the configured max, passes at the boundary
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchTrashMessages_batchSizeExceedsConfiguredMax_throwsValidationException")
    void batchTrashMessages_batchSizeExceedsConfiguredMax_throwsValidationException() throws Exception {
        // Arrange: one more than the configured max. messageIds carries no uniqueness
        // constraint, so a repeated valid id is sufficient to exercise the size guard.
        List<String> oversizedIds = oversizedMessageIds();

        // Act & Assert
        assertThrows(ValidationException.class, () -> gmailService.batchTrashMessages(USER_ID, oversizedIds));
        verifyRepositoryNeverCalled();
    }

    @Test
    @DisplayName("batchUntrashMessages_batchSizeExceedsConfiguredMax_throwsValidationException")
    void batchUntrashMessages_batchSizeExceedsConfiguredMax_throwsValidationException() throws Exception {
        // Arrange
        List<String> oversizedIds = oversizedMessageIds();

        // Act & Assert
        assertThrows(ValidationException.class, () -> gmailService.batchUntrashMessages(USER_ID, oversizedIds));
        verifyRepositoryNeverCalled();
    }

    @Test
    @DisplayName("batchTrashMessages_batchSizeAtConfiguredMax_doesNotThrowAndDelegatesToRepository")
    void batchTrashMessages_batchSizeAtConfiguredMax_doesNotThrowAndDelegatesToRepository() throws Exception {
        // Arrange: exactly at the configured max — the boundary case for validateBatchSize.
        List<String> messageIdsAtMax = messageIdsOfSize((int) TEST_MAX_BATCH_SIZE);
        BulkOperationResult repositoryResult = BatchOperationFixtures.buildAllSuccessResult(messageIdsAtMax);
        when(gmailRepository.batchTrashMessages(USER_ID, messageIdsAtMax)).thenReturn(repositoryResult);

        // Act
        BulkOperationResult result = gmailService.batchTrashMessages(USER_ID, messageIdsAtMax);

        // Assert: no exception, repository invoked, result passed through
        assertThat(result).isSameAs(repositoryResult);
        verify(gmailRepository).batchTrashMessages(USER_ID, messageIdsAtMax);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Builds a batch one element larger than {@link #TEST_MAX_BATCH_SIZE}. */
    private static List<String> oversizedMessageIds() {
        return messageIdsOfSize((int) TEST_MAX_BATCH_SIZE + 1);
    }

    /**
     * Builds a batch of exactly {@code size} valid (but not necessarily unique) Gmail
     * message ids — {@code messageIds} carries no uniqueness constraint, so repeating a
     * single valid id is sufficient for size-only assertions.
     */
    private static List<String> messageIdsOfSize(int size) {
        String validId = BatchOperationFixtures.validMessageIds(1).get(0);
        return BatchOperationFixtures.mutableCopy(Collections.nCopies(size, validId));
    }

    private void verifyRepositoryNeverCalled() throws Exception {
        verify(gmailRepository, never()).batchTrashMessages(any(), any());
        verify(gmailRepository, never()).batchUntrashMessages(any(), any());
    }
}
