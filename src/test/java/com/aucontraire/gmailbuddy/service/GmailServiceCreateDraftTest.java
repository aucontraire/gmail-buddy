package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.dto.SendMessageDTO;
import com.aucontraire.gmailbuddy.exception.GmailApiException;
import com.aucontraire.gmailbuddy.fixture.SendMessageRequestFixtures;
import com.aucontraire.gmailbuddy.mapper.FilterCriteriaMapper;
import com.aucontraire.gmailbuddy.repository.GmailRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for {@link GmailService#createDraft(String, SendMessageDTO)}.
 *
 * <p>Verifies the service orchestration: DTO → MimeMessage (via {@link MimeMessageBuilder})
 * → repository call → return {@link DraftCreationResult}. No Spring context is loaded;
 * all dependencies are hand-mocked.</p>
 *
 * <p>Each test follows Arrange-Act-Assert per Constitution §VIII.</p>
 */
@DisplayName("GmailService — createDraft")
class GmailServiceCreateDraftTest {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    private static final String USER_ID        = "me";
    private static final String TEST_DRAFT_ID  = "r-9876543210";
    private static final String TEST_MSG_ID    = "19a2b3c4d5e6f7g8";
    private static final String TEST_THREAD_ID = "19a2b3c4d5e6f7g8";

    // -------------------------------------------------------------------------
    // System under test + all collaborators (hand-mocked)
    // -------------------------------------------------------------------------

    private GmailRepository gmailRepository;
    private GmailQueryBuilder gmailQueryBuilder;
    private FilterCriteriaMapper filterCriteriaMapper;
    private MimeMessageBuilder mimeMessageBuilder;
    private GmailService gmailService;

    @BeforeEach
    void setUp() {
        gmailRepository      = mock(GmailRepository.class);
        gmailQueryBuilder    = mock(GmailQueryBuilder.class);
        filterCriteriaMapper = mock(FilterCriteriaMapper.class);
        mimeMessageBuilder   = mock(MimeMessageBuilder.class);
        gmailService = new GmailService(
                gmailRepository, gmailQueryBuilder, filterCriteriaMapper, mimeMessageBuilder);
    }

    // -------------------------------------------------------------------------
    // Helper: create a no-op MimeMessage for stubbing
    // -------------------------------------------------------------------------

    private MimeMessage emptyMimeMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createDraft_validDto_callsMimeMessageBuilderExactlyOnce")
    void createDraft_validDto_callsMimeMessageBuilderExactlyOnce()
            throws Exception {
        // Arrange
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
        MimeMessage mimeMessage = emptyMimeMessage();
        DraftCreationResult expected =
                new DraftCreationResult(TEST_DRAFT_ID, TEST_MSG_ID, TEST_THREAD_ID);

        when(mimeMessageBuilder.build(eq(dto), isNull())).thenReturn(mimeMessage);
        when(gmailRepository.createDraft(eq(USER_ID), eq(mimeMessage), isNull())).thenReturn(expected);

        // Act
        gmailService.createDraft(USER_ID, dto);

        // Assert: MimeMessageBuilder.build is called exactly once with the dto.
        verify(mimeMessageBuilder).build(eq(dto), isNull());
    }

    @Test
    @DisplayName("createDraft_validDto_callsRepositoryCreateDraftExactlyOnce")
    void createDraft_validDto_callsRepositoryCreateDraftExactlyOnce()
            throws Exception {
        // Arrange
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
        MimeMessage mimeMessage = emptyMimeMessage();
        DraftCreationResult expected =
                new DraftCreationResult(TEST_DRAFT_ID, TEST_MSG_ID, TEST_THREAD_ID);

        when(mimeMessageBuilder.build(eq(dto), isNull())).thenReturn(mimeMessage);
        when(gmailRepository.createDraft(eq(USER_ID), eq(mimeMessage), isNull())).thenReturn(expected);

        // Act
        gmailService.createDraft(USER_ID, dto);

        // Assert: repository.createDraft is called exactly once with the userId
        // and the MimeMessage produced by the builder.
        verify(gmailRepository).createDraft(eq(USER_ID), eq(mimeMessage), isNull());
    }

    @Test
    @DisplayName("createDraft_validDto_returnsExactResultFromRepository")
    void createDraft_validDto_returnsExactResultFromRepository() throws Exception {
        // Arrange
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
        MimeMessage mimeMessage = emptyMimeMessage();
        DraftCreationResult expected =
                new DraftCreationResult(TEST_DRAFT_ID, TEST_MSG_ID, TEST_THREAD_ID);

        when(mimeMessageBuilder.build(eq(dto), isNull())).thenReturn(mimeMessage);
        when(gmailRepository.createDraft(eq(USER_ID), eq(mimeMessage), isNull())).thenReturn(expected);

        // Act
        DraftCreationResult actual = gmailService.createDraft(USER_ID, dto);

        // Assert
        assertThat(actual).isSameAs(expected);
        assertThat(actual.draftId()).isEqualTo(TEST_DRAFT_ID);
        assertThat(actual.messageId()).isEqualTo(TEST_MSG_ID);
        assertThat(actual.threadId()).isEqualTo(TEST_THREAD_ID);
    }

    // -------------------------------------------------------------------------
    // MessagingException from MimeMessageBuilder wraps to GmailApiException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createDraft_mimeMessageBuilderThrowsMessagingException_wrapsToGmailApiException")
    void createDraft_mimeMessageBuilderThrowsMessagingException_wrapsToGmailApiException()
            throws Exception {
        // Arrange
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
        MessagingException cause = new MessagingException("JavaMail header set failure");

        when(mimeMessageBuilder.build(eq(dto), isNull())).thenThrow(cause);

        // Act & Assert
        assertThatThrownBy(() -> gmailService.createDraft(USER_ID, dto))
                .isInstanceOf(GmailApiException.class)
                .hasMessageContaining("Failed to construct email message for draft")
                .hasCause(cause);

        // Verify repository is NOT called when builder fails.
        verify(gmailRepository, never()).createDraft(any(), any(), any());
    }

    @Test
    @DisplayName("createDraft_mimeMessageBuilderThrowsUnsupportedEncodingException_wrapsToGmailApiException")
    void createDraft_mimeMessageBuilderThrowsUnsupportedEncodingException_wrapsToGmailApiException()
            throws Exception {
        // Arrange
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
        UnsupportedEncodingException cause = new UnsupportedEncodingException("UTF-8 not supported");

        when(mimeMessageBuilder.build(eq(dto), isNull())).thenThrow(cause);

        // Act & Assert
        assertThatThrownBy(() -> gmailService.createDraft(USER_ID, dto))
                .isInstanceOf(GmailApiException.class)
                .hasMessageContaining("Failed to construct email message for draft")
                .hasCause(cause);

        verify(gmailRepository, never()).createDraft(any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // IOException from repository propagates as GmailApiException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createDraft_repositoryThrowsIOException_wrapsToGmailApiException")
    void createDraft_repositoryThrowsIOException_wrapsToGmailApiException() throws Exception {
        // Arrange
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
        MimeMessage mimeMessage = emptyMimeMessage();
        IOException cause = new IOException("Network timeout reaching Gmail API");

        when(mimeMessageBuilder.build(eq(dto), isNull())).thenReturn(mimeMessage);
        when(gmailRepository.createDraft(eq(USER_ID), eq(mimeMessage), isNull())).thenThrow(cause);

        // Act & Assert
        assertThatThrownBy(() -> gmailService.createDraft(USER_ID, dto))
                .isInstanceOf(GmailApiException.class)
                .hasMessageContaining("Failed to create draft for user: " + USER_ID)
                .hasCause(cause);
    }

    // -------------------------------------------------------------------------
    // Multi-recipient DTO — still delegates to builder and repository
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createDraft_multiRecipientDto_delegatesToBuilderAndRepositoryCorrectly")
    void createDraft_multiRecipientDto_delegatesToBuilderAndRepositoryCorrectly()
            throws Exception {
        // Arrange: multi-recipient DTO to confirm service is not hard-coding recipient handling.
        SendMessageDTO dto = SendMessageRequestFixtures.validMultiRecipientWithCcAndBcc();
        MimeMessage mimeMessage = emptyMimeMessage();
        DraftCreationResult expected =
                new DraftCreationResult(TEST_DRAFT_ID, TEST_MSG_ID, TEST_THREAD_ID);

        when(mimeMessageBuilder.build(eq(dto), isNull())).thenReturn(mimeMessage);
        when(gmailRepository.createDraft(eq(USER_ID), eq(mimeMessage), isNull())).thenReturn(expected);

        // Act
        DraftCreationResult actual = gmailService.createDraft(USER_ID, dto);

        // Assert
        verify(mimeMessageBuilder).build(eq(dto), isNull());
        verify(gmailRepository).createDraft(eq(USER_ID), eq(mimeMessage), isNull());
        assertThat(actual).isSameAs(expected);
    }
}
