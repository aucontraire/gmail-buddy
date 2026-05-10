package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import com.aucontraire.gmailbuddy.exception.GmailApiException;
import com.aucontraire.gmailbuddy.mapper.FilterCriteriaMapper;
import com.aucontraire.gmailbuddy.mapper.GmailMessageMapper;
import com.aucontraire.gmailbuddy.repository.GmailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for {@link GmailService#sendDraft(String, String)}.
 *
 * <p>Verifies the thin pass-through orchestration: service delegates directly to
 * {@link GmailRepository#sendDraft(String, String)} without constructing a
 * MimeMessage (the draft content already lives on Gmail's side). No Spring
 * context is loaded; all dependencies are hand-mocked.</p>
 *
 * <p>Each test follows Arrange-Act-Assert per Constitution §VIII.</p>
 */
@DisplayName("GmailService — sendDraft")
class GmailServiceSendDraftTest {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    private static final String USER_ID        = "me";
    private static final String TEST_DRAFT_ID  = "r-9876543210";
    private static final String TEST_MSG_ID    = "19a2b3c4d5e6f7g8";
    private static final String TEST_THREAD_ID = "thread-19a2b3c4d5e6f7g8";

    // -------------------------------------------------------------------------
    // System under test + all collaborators (hand-mocked)
    // -------------------------------------------------------------------------

    private GmailRepository gmailRepository;
    private GmailQueryBuilder gmailQueryBuilder;
    private FilterCriteriaMapper filterCriteriaMapper;
    private MimeMessageBuilder mimeMessageBuilder;
    private GmailBuddyProperties properties;
    private GmailBuddyProperties.Send send;
    private GmailService gmailService;

    @BeforeEach
    void setUp() {
        gmailRepository      = mock(GmailRepository.class);
        gmailQueryBuilder    = mock(GmailQueryBuilder.class);
        filterCriteriaMapper = mock(FilterCriteriaMapper.class);
        mimeMessageBuilder   = mock(MimeMessageBuilder.class);
        properties           = mock(GmailBuddyProperties.class);
        send                 = mock(GmailBuddyProperties.Send.class);
        when(properties.send()).thenReturn(send);
        when(send.maxTotalPayloadSize()).thenReturn(DataSize.ofMegabytes(25));
        gmailService = new GmailService(
                gmailRepository, gmailQueryBuilder, filterCriteriaMapper, mimeMessageBuilder,
                mock(GmailMessageMapper.class), properties);
    }

    // -------------------------------------------------------------------------
    // Happy path — delegates correctly and returns repository result
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendDraft_validDraftId_callsRepositorySendDraftExactlyOnce")
    void sendDraft_validDraftId_callsRepositorySendDraftExactlyOnce() throws Exception {
        // Arrange
        SentMessageResult expected = new SentMessageResult(TEST_MSG_ID, TEST_THREAD_ID);
        when(gmailRepository.sendDraft(USER_ID, TEST_DRAFT_ID)).thenReturn(expected);

        // Act
        gmailService.sendDraft(USER_ID, TEST_DRAFT_ID);

        // Assert: repository.sendDraft is called exactly once with the correct arguments.
        verify(gmailRepository).sendDraft(USER_ID, TEST_DRAFT_ID);
    }

    @Test
    @DisplayName("sendDraft_validDraftId_returnsExactResultFromRepository")
    void sendDraft_validDraftId_returnsExactResultFromRepository() throws Exception {
        // Arrange
        SentMessageResult expected = new SentMessageResult(TEST_MSG_ID, TEST_THREAD_ID);
        when(gmailRepository.sendDraft(USER_ID, TEST_DRAFT_ID)).thenReturn(expected);

        // Act
        SentMessageResult actual = gmailService.sendDraft(USER_ID, TEST_DRAFT_ID);

        // Assert
        assertThat(actual).isSameAs(expected);
        assertThat(actual.messageId()).isEqualTo(TEST_MSG_ID);
        assertThat(actual.threadId()).isEqualTo(TEST_THREAD_ID);
    }

    // -------------------------------------------------------------------------
    // No MimeMessageBuilder involvement — pure thin pass-through
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendDraft_validDraftId_doesNotInvokeMimeMessageBuilder")
    void sendDraft_validDraftId_doesNotInvokeMimeMessageBuilder() throws Exception {
        // Arrange: sendDraft does NOT construct a MimeMessage; draft content already
        // lives in Gmail's draft store.
        SentMessageResult result = new SentMessageResult(TEST_MSG_ID, TEST_THREAD_ID);
        when(gmailRepository.sendDraft(USER_ID, TEST_DRAFT_ID)).thenReturn(result);

        // Act
        gmailService.sendDraft(USER_ID, TEST_DRAFT_ID);

        // Assert: MimeMessageBuilder must not be touched.
        // Any invocation on mimeMessageBuilder would be detected by strict Mockito
        // — this test succeeds if no call was made.
        org.mockito.Mockito.verifyNoInteractions(mimeMessageBuilder);
    }

    // -------------------------------------------------------------------------
    // IOException from repository propagates as GmailApiException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendDraft_repositoryThrowsIOException_wrapsToGmailApiException")
    void sendDraft_repositoryThrowsIOException_wrapsToGmailApiException() throws Exception {
        // Arrange
        IOException cause = new IOException("Network timeout reaching Gmail API");
        when(gmailRepository.sendDraft(USER_ID, TEST_DRAFT_ID)).thenThrow(cause);

        // Act & Assert
        assertThatThrownBy(() -> gmailService.sendDraft(USER_ID, TEST_DRAFT_ID))
                .isInstanceOf(GmailApiException.class)
                .hasMessageContaining("Failed to send draft for draftId: " + TEST_DRAFT_ID)
                .hasCause(cause);
    }
}
