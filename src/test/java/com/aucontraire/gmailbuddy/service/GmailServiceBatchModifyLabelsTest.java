package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import com.aucontraire.gmailbuddy.exception.ValidationException;
import com.aucontraire.gmailbuddy.fixture.BatchOperationFixtures;
import com.aucontraire.gmailbuddy.mapper.FilterCriteriaMapper;
import com.aucontraire.gmailbuddy.mapper.GmailMessageMapper;
import com.aucontraire.gmailbuddy.repository.GmailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GmailService#batchModifyLabelsByIds} (feature 005 US2, T021).
 *
 * <p>Covers the three behaviors owned by the service layer for this endpoint:</p>
 * <ul>
 *   <li>Best-effort delegation to {@link GmailRepository} — the returned
 *       {@link BulkOperationResult} is the exact same instance the repository
 *       produced, so per-id success/failure detail is preserved untouched.</li>
 *   <li>No transformation of the raw label ids or message ids passed through to
 *       the repository — the service does not resolve, rewrite, or filter them
 *       (FR-006).</li>
 *   <li>The {@code validateBatchSize} guard, which rejects a batch exceeding the
 *       configured {@code gmail-buddy.gmail-api.batch-delete-max-results} ceiling
 *       with a {@link ValidationException} (mapped to HTTP 400) and allows a
 *       batch exactly at that ceiling through (FR-004).</li>
 * </ul>
 *
 * <p>Mirrors the plain-Mockito setup pattern in {@code GmailServiceBatchTrashTest}
 * (T013) — no Spring context is needed.</p>
 */
@DisplayName("GmailService.batchModifyLabelsByIds (T021)")
class GmailServiceBatchModifyLabelsTest {

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

        gmailService = new GmailService(gmailRepository, gmailQueryBuilder, filterCriteriaMapper,
                mimeMessageBuilder, gmailMessageMapper, properties);
    }

    // -------------------------------------------------------------------------
    // Delegation: BulkOperationResult returned unchanged, per-id detail preserved
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchModifyLabelsByIds_delegatesToRepository_returnsResultUnchanged")
    void batchModifyLabelsByIds_delegatesToRepository_returnsResultUnchanged() throws Exception {
        // Arrange
        List<String> messageIds = BatchOperationFixtures.validMessageIds(3);
        List<String> labelIdsToAdd = List.of("IMPORTANT");
        List<String> labelIdsToRemove = List.of("UNREAD");
        BulkOperationResult repositoryResult = BatchOperationFixtures.buildPartialResult(messageIds, 2);
        when(gmailRepository.batchModifyLabelsByIds(USER_ID, messageIds, labelIdsToAdd, labelIdsToRemove))
                .thenReturn(repositoryResult);

        // Act
        BulkOperationResult result = gmailService.batchModifyLabelsByIds(
                USER_ID, messageIds, labelIdsToAdd, labelIdsToRemove);

        // Assert: same instance returned, so per-id success/failure detail survives untouched
        assertThat(result).isSameAs(repositoryResult);
        verify(gmailRepository).batchModifyLabelsByIds(USER_ID, messageIds, labelIdsToAdd, labelIdsToRemove);
    }

    // -------------------------------------------------------------------------
    // No transformation: messageIds / labelIdsToAdd / labelIdsToRemove pass through unchanged
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchModifyLabelsByIds_givenLabelIdsAndMessageIds_passesThemToRepositoryUnchanged")
    void batchModifyLabelsByIds_givenLabelIdsAndMessageIds_passesThemToRepositoryUnchanged() throws Exception {
        // Arrange
        List<String> messageIds = BatchOperationFixtures.validMessageIds(2);
        List<String> labelIdsToAdd = List.of("Label_1", "STARRED");
        List<String> labelIdsToRemove = List.of("INBOX");
        BulkOperationResult repositoryResult = BatchOperationFixtures.buildAllSuccessResult(messageIds);
        when(gmailRepository.batchModifyLabelsByIds(any(), any(), any(), any()))
                .thenReturn(repositoryResult);

        // Act
        gmailService.batchModifyLabelsByIds(USER_ID, messageIds, labelIdsToAdd, labelIdsToRemove);

        // Assert: exact same list contents (and identity where the service has no reason to copy)
        // reach the repository — no resolution, rewriting, or filtering happens in the service.
        ArgumentCaptor<List<String>> messageIdsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<String>> addCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<String>> removeCaptor = ArgumentCaptor.forClass(List.class);
        verify(gmailRepository).batchModifyLabelsByIds(
                eq(USER_ID), messageIdsCaptor.capture(), addCaptor.capture(), removeCaptor.capture());

        assertThat(messageIdsCaptor.getValue()).isEqualTo(messageIds);
        assertThat(addCaptor.getValue()).isEqualTo(labelIdsToAdd);
        assertThat(removeCaptor.getValue()).isEqualTo(labelIdsToRemove);
    }

    // -------------------------------------------------------------------------
    // validateBatchSize: rejects over the configured max, passes at the boundary
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchModifyLabelsByIds_batchSizeExceedsConfiguredMax_throwsValidationException")
    void batchModifyLabelsByIds_batchSizeExceedsConfiguredMax_throwsValidationException() throws Exception {
        // Arrange: one more than the configured max. messageIds carries no uniqueness
        // constraint, so a repeated valid id is sufficient to exercise the size guard.
        List<String> oversizedIds = oversizedMessageIds();
        List<String> labelIdsToAdd = List.of("IMPORTANT");
        List<String> labelIdsToRemove = Collections.emptyList();

        // Act & Assert
        assertThrows(ValidationException.class,
                () -> gmailService.batchModifyLabelsByIds(USER_ID, oversizedIds, labelIdsToAdd, labelIdsToRemove));
        verify(gmailRepository, never()).batchModifyLabelsByIds(any(), any(), any(), any());
    }

    @Test
    @DisplayName("batchModifyLabelsByIds_batchSizeAtConfiguredMax_doesNotThrowAndDelegatesToRepository")
    void batchModifyLabelsByIds_batchSizeAtConfiguredMax_doesNotThrowAndDelegatesToRepository() throws Exception {
        // Arrange: exactly at the configured max — the boundary case for validateBatchSize.
        List<String> messageIdsAtMax = messageIdsOfSize((int) TEST_MAX_BATCH_SIZE);
        List<String> labelIdsToAdd = List.of("IMPORTANT");
        List<String> labelIdsToRemove = List.of("UNREAD");
        BulkOperationResult repositoryResult = BatchOperationFixtures.buildAllSuccessResult(messageIdsAtMax);
        when(gmailRepository.batchModifyLabelsByIds(USER_ID, messageIdsAtMax, labelIdsToAdd, labelIdsToRemove))
                .thenReturn(repositoryResult);

        // Act
        BulkOperationResult result = gmailService.batchModifyLabelsByIds(
                USER_ID, messageIdsAtMax, labelIdsToAdd, labelIdsToRemove);

        // Assert: no exception, repository invoked, result passed through
        assertThat(result).isSameAs(repositoryResult);
        verify(gmailRepository).batchModifyLabelsByIds(USER_ID, messageIdsAtMax, labelIdsToAdd, labelIdsToRemove);
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
}
