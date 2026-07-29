package com.aucontraire.gmailbuddy.integration;

import com.aucontraire.gmailbuddy.client.GmailBatchClient;
import com.aucontraire.gmailbuddy.client.GmailClient;
import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import com.aucontraire.gmailbuddy.fixture.BatchOperationFixtures;
import com.aucontraire.gmailbuddy.mapper.GmailMessageMapper;
import com.aucontraire.gmailbuddy.repository.GmailRepositoryImpl;
import com.aucontraire.gmailbuddy.service.BulkOperationResult;
import com.aucontraire.gmailbuddy.service.GmailQueryBuilder;
import com.aucontraire.gmailbuddy.service.TokenProvider;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ModifyMessageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mockito-based proof of the Gmail API request shape produced by
 * {@link GmailRepositoryImpl#batchModifyLabelsByIds} (feature 005 US2, T015).
 *
 * <p>Instantiates {@link GmailRepositoryImpl} directly with mocked collaborators
 * (no Spring context needed — mirrors {@link BatchTrashRoundTripIntegrationTest})
 * and captures the {@link ModifyMessageRequest} passed to
 * {@link GmailBatchClient#batchModifyLabels} via an {@link ArgumentCaptor}.
 * Asserts that raw label ids are passed through to the request unmodified — no
 * name-to-id resolution occurs at this layer (FR-006) — and that an empty or
 * null add/remove list is omitted from the request entirely rather than sent as
 * an empty list.</p>
 *
 * <p><strong>Scope note:</strong> this test proves the outgoing request shape
 * only — it never calls the real Gmail API.</p>
 */
@DisplayName("GmailRepositoryImpl.batchModifyLabelsByIds — Gmail API request shape (T015)")
class BatchModifyLabelsIntegrationTest {

    private static final String USER_ID = "me";

    private GmailBatchClient gmailBatchClient;
    private GmailRepositoryImpl gmailRepository;
    private Gmail gmailServiceStub;

    @BeforeEach
    void setUp() throws Exception {
        GmailClient gmailClient = mock(GmailClient.class);
        gmailBatchClient = mock(GmailBatchClient.class);
        TokenProvider tokenProvider = mock(TokenProvider.class);
        GmailBuddyProperties properties = mock(GmailBuddyProperties.class);
        GmailMessageMapper gmailMessageMapper = mock(GmailMessageMapper.class);
        GmailQueryBuilder gmailQueryBuilder = mock(GmailQueryBuilder.class);

        gmailServiceStub = mock(Gmail.class);
        when(tokenProvider.getAccessToken()).thenReturn("fake-access-token");
        when(gmailClient.createGmailService(anyString())).thenReturn(gmailServiceStub);

        gmailRepository = new GmailRepositoryImpl(
                gmailClient, gmailBatchClient, tokenProvider, properties,
                gmailMessageMapper, gmailQueryBuilder);
    }

    // -------------------------------------------------------------------------
    // (a) Both add and remove supplied -> captured request carries exactly those
    //     raw ids, unresolved
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchModifyLabelsByIds_addAndRemoveSupplied_sendsModifyRequestWithRawLabelIdsUnresolved")
    void batchModifyLabelsByIds_addAndRemoveSupplied_sendsModifyRequestWithRawLabelIdsUnresolved() throws Exception {
        // Arrange
        List<String> messageIds = BatchOperationFixtures.validMessageIds(2);
        List<String> labelIdsToAdd = List.of("Label_A");
        List<String> labelIdsToRemove = List.of("Label_B");
        BulkOperationResult stubResult = BatchOperationFixtures.buildAllSuccessResult(messageIds);
        ArgumentCaptor<ModifyMessageRequest> captor = ArgumentCaptor.forClass(ModifyMessageRequest.class);
        when(gmailBatchClient.batchModifyLabels(eq(gmailServiceStub), eq(USER_ID), eq(messageIds), captor.capture()))
                .thenReturn(stubResult);

        // Act
        gmailRepository.batchModifyLabelsByIds(USER_ID, messageIds, labelIdsToAdd, labelIdsToRemove);

        // Assert: exactly the raw ids passed in, no name-to-id resolution
        ModifyMessageRequest sentRequest = captor.getValue();
        assertThat(sentRequest.getAddLabelIds()).containsExactly("Label_A");
        assertThat(sentRequest.getRemoveLabelIds()).containsExactly("Label_B");
    }

    // -------------------------------------------------------------------------
    // (b) Empty add list, non-empty remove list -> addLabelIds omitted (null),
    //     not sent as an empty list
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchModifyLabelsByIds_emptyAddList_omitsAddLabelIdsFromRequest")
    void batchModifyLabelsByIds_emptyAddList_omitsAddLabelIdsFromRequest() throws Exception {
        // Arrange
        List<String> messageIds = BatchOperationFixtures.validMessageIds(1);
        List<String> labelIdsToAdd = List.of();
        List<String> labelIdsToRemove = List.of("Label_B");
        BulkOperationResult stubResult = BatchOperationFixtures.buildAllSuccessResult(messageIds);
        ArgumentCaptor<ModifyMessageRequest> captor = ArgumentCaptor.forClass(ModifyMessageRequest.class);
        when(gmailBatchClient.batchModifyLabels(eq(gmailServiceStub), eq(USER_ID), eq(messageIds), captor.capture()))
                .thenReturn(stubResult);

        // Act
        gmailRepository.batchModifyLabelsByIds(USER_ID, messageIds, labelIdsToAdd, labelIdsToRemove);

        // Assert: addLabelIds is omitted (null), not an empty list
        ModifyMessageRequest sentRequest = captor.getValue();
        assertThat(sentRequest.getAddLabelIds()).isNull();
        assertThat(sentRequest.getRemoveLabelIds()).containsExactly("Label_B");
    }

    // -------------------------------------------------------------------------
    // (c) Null remove list, non-empty add list -> removeLabelIds omitted (null)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchModifyLabelsByIds_nullRemoveList_omitsRemoveLabelIdsFromRequest")
    void batchModifyLabelsByIds_nullRemoveList_omitsRemoveLabelIdsFromRequest() throws Exception {
        // Arrange
        List<String> messageIds = BatchOperationFixtures.validMessageIds(1);
        List<String> labelIdsToAdd = List.of("Label_A");
        BulkOperationResult stubResult = BatchOperationFixtures.buildAllSuccessResult(messageIds);
        ArgumentCaptor<ModifyMessageRequest> captor = ArgumentCaptor.forClass(ModifyMessageRequest.class);
        when(gmailBatchClient.batchModifyLabels(eq(gmailServiceStub), eq(USER_ID), eq(messageIds), captor.capture()))
                .thenReturn(stubResult);

        // Act
        gmailRepository.batchModifyLabelsByIds(USER_ID, messageIds, labelIdsToAdd, null);

        // Assert: removeLabelIds is omitted (null), addLabelIds carries the raw id
        ModifyMessageRequest sentRequest = captor.getValue();
        assertThat(sentRequest.getAddLabelIds()).containsExactly("Label_A");
        assertThat(sentRequest.getRemoveLabelIds()).isNull();
    }

    // -------------------------------------------------------------------------
    // (d) The BulkOperationResult returned by GmailBatchClient is passed back
    //     unchanged
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchModifyLabelsByIds_delegatesToBatchClient_returnsResultUnchanged")
    void batchModifyLabelsByIds_delegatesToBatchClient_returnsResultUnchanged() throws Exception {
        // Arrange
        List<String> messageIds = BatchOperationFixtures.validMessageIds(3);
        List<String> labelIdsToAdd = List.of("Label_A");
        BulkOperationResult stubResult = BatchOperationFixtures.buildPartialResult(messageIds, 2);
        when(gmailBatchClient.batchModifyLabels(eq(gmailServiceStub), eq(USER_ID), eq(messageIds), any(ModifyMessageRequest.class)))
                .thenReturn(stubResult);

        // Act
        BulkOperationResult result = gmailRepository.batchModifyLabelsByIds(USER_ID, messageIds, labelIdsToAdd, null);

        // Assert: same instance returned, so per-id success/failure detail survives untouched
        assertThat(result).isSameAs(stubResult);
    }
}
