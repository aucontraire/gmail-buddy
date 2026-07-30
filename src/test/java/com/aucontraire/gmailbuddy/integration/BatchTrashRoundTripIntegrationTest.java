package com.aucontraire.gmailbuddy.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Mockito-based proof of the Gmail API request shape produced by
 * {@link GmailRepositoryImpl#batchTrashMessages} and
 * {@link GmailRepositoryImpl#batchUntrashMessages} (feature 005 US1, T007).
 *
 * <p>Instantiates {@link GmailRepositoryImpl} directly with mocked collaborators
 * (no Spring context is needed — none of its constructor dependencies used by
 * these two methods require one) and captures the {@link ModifyMessageRequest}
 * passed to {@link GmailBatchClient#batchModifyLabels} via an
 * {@link ArgumentCaptor}. Asserts that trash sets {@code addLabelIds == ["TRASH"]}
 * and untrash sets {@code removeLabelIds == ["TRASH"]} (FR-001, FR-002).</p>
 *
 * <p><strong>Scope note:</strong> this test proves the outgoing request shape only —
 * it never calls the real Gmail API. The LIVE proof that Gmail actually moves a
 * message to/from Trash when this request is sent is the manual quickstart
 * verification step T034, not this test.</p>
 */
@DisplayName("GmailRepositoryImpl batchTrash/batchUntrash — Gmail API request shape (T007)")
class BatchTrashRoundTripIntegrationTest {

    private static final String USER_ID = "me";
    private static final String TRASH_LABEL_ID = "TRASH";

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
                gmailClient, gmailBatchClient, tokenProvider, properties, gmailMessageMapper, gmailQueryBuilder);
    }

    @Test
    @DisplayName("batchTrashMessages_validMessageIds_sendsModifyRequestWithTrashLabelAdded")
    void batchTrashMessages_validMessageIds_sendsModifyRequestWithTrashLabelAdded() throws Exception {
        // Arrange
        List<String> messageIds = BatchOperationFixtures.validMessageIds(3);
        BulkOperationResult stubResult = BatchOperationFixtures.buildAllSuccessResult(messageIds);
        ArgumentCaptor<ModifyMessageRequest> captor = ArgumentCaptor.forClass(ModifyMessageRequest.class);
        when(gmailBatchClient.batchModifyLabels(eq(gmailServiceStub), eq(USER_ID), eq(messageIds), captor.capture()))
                .thenReturn(stubResult);

        // Act
        gmailRepository.batchTrashMessages(USER_ID, messageIds);

        // Assert: TRASH label added, nothing removed
        ModifyMessageRequest sentRequest = captor.getValue();
        assertThat(sentRequest.getAddLabelIds()).containsExactly(TRASH_LABEL_ID);
        assertThat(sentRequest.getRemoveLabelIds()).isNull();
    }

    @Test
    @DisplayName("batchUntrashMessages_validMessageIds_sendsModifyRequestWithTrashLabelRemoved")
    void batchUntrashMessages_validMessageIds_sendsModifyRequestWithTrashLabelRemoved() throws Exception {
        // Arrange
        List<String> messageIds = BatchOperationFixtures.validMessageIds(3);
        BulkOperationResult stubResult = BatchOperationFixtures.buildAllSuccessResult(messageIds);
        ArgumentCaptor<ModifyMessageRequest> captor = ArgumentCaptor.forClass(ModifyMessageRequest.class);
        when(gmailBatchClient.batchModifyLabels(eq(gmailServiceStub), eq(USER_ID), eq(messageIds), captor.capture()))
                .thenReturn(stubResult);

        // Act
        gmailRepository.batchUntrashMessages(USER_ID, messageIds);

        // Assert: TRASH label removed, nothing added
        ModifyMessageRequest sentRequest = captor.getValue();
        assertThat(sentRequest.getRemoveLabelIds()).containsExactly(TRASH_LABEL_ID);
        assertThat(sentRequest.getAddLabelIds()).isNull();
    }
}
