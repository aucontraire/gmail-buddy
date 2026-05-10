package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import com.aucontraire.gmailbuddy.dto.SendMessageDTO;
import com.aucontraire.gmailbuddy.exception.GmailApiException;
import com.aucontraire.gmailbuddy.fixture.SendMessageRequestFixtures;
import com.aucontraire.gmailbuddy.mapper.FilterCriteriaMapper;
import com.aucontraire.gmailbuddy.mapper.GmailMessageMapper;
import com.aucontraire.gmailbuddy.repository.GmailRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

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
 * Pure Mockito unit tests for {@link GmailService#sendMessage(String, SendMessageDTO)}.
 *
 * <p>Mirrors {@code GmailServiceCreateDraftTest}. Verifies the service orchestration:
 * DTO → MimeMessage (via {@link MimeMessageBuilder}) → repository call →
 * return {@link SentMessageResult}. No Spring context is loaded; all dependencies
 * are hand-mocked.</p>
 *
 * <p>Each test follows Arrange-Act-Assert per Constitution §VIII.</p>
 */
@DisplayName("GmailService — sendMessage")
class GmailServiceSendMessageTest {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    private static final String USER_ID        = "me";
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
    // Helper: create a stub MimeMessage that can survive writeTo() in Stage 2
    // -------------------------------------------------------------------------

    private MimeMessage emptyMimeMessage() {
        try {
            MimeMessage msg = new MimeMessage(Session.getInstance(new Properties()));
            msg.setContent("stub", "text/plain");
            msg.saveChanges();
            return msg;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create stub MimeMessage", e);
        }
    }

    // -------------------------------------------------------------------------
    // Happy path — delegates correctly and returns repository result
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendMessage_validDto_callsMimeMessageBuilderExactlyOnce")
    void sendMessage_validDto_callsMimeMessageBuilderExactlyOnce() throws Exception {
        // Arrange
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
        MimeMessage mimeMessage = emptyMimeMessage();
        SentMessageResult expected = new SentMessageResult(TEST_MSG_ID, TEST_THREAD_ID);

        when(mimeMessageBuilder.build(eq(dto), isNull())).thenReturn(mimeMessage);
        when(gmailRepository.sendMessage(eq(USER_ID), eq(mimeMessage), isNull())).thenReturn(expected);

        // Act
        gmailService.sendMessage(USER_ID, dto);

        // Assert: MimeMessageBuilder.build is called exactly once with the dto.
        verify(mimeMessageBuilder).build(eq(dto), isNull());
    }

    @Test
    @DisplayName("sendMessage_validDto_callsRepositorySendMessageExactlyOnce")
    void sendMessage_validDto_callsRepositorySendMessageExactlyOnce() throws Exception {
        // Arrange
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
        MimeMessage mimeMessage = emptyMimeMessage();
        SentMessageResult expected = new SentMessageResult(TEST_MSG_ID, TEST_THREAD_ID);

        when(mimeMessageBuilder.build(eq(dto), isNull())).thenReturn(mimeMessage);
        when(gmailRepository.sendMessage(eq(USER_ID), eq(mimeMessage), isNull())).thenReturn(expected);

        // Act
        gmailService.sendMessage(USER_ID, dto);

        // Assert: repository.sendMessage is called exactly once with the userId
        // and the MimeMessage produced by the builder.
        verify(gmailRepository).sendMessage(eq(USER_ID), eq(mimeMessage), isNull());
    }

    @Test
    @DisplayName("sendMessage_validDto_returnsExactResultFromRepository")
    void sendMessage_validDto_returnsExactResultFromRepository() throws Exception {
        // Arrange
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
        MimeMessage mimeMessage = emptyMimeMessage();
        SentMessageResult expected = new SentMessageResult(TEST_MSG_ID, TEST_THREAD_ID);

        when(mimeMessageBuilder.build(eq(dto), isNull())).thenReturn(mimeMessage);
        when(gmailRepository.sendMessage(eq(USER_ID), eq(mimeMessage), isNull())).thenReturn(expected);

        // Act
        SentMessageResult actual = gmailService.sendMessage(USER_ID, dto);

        // Assert
        assertThat(actual).isSameAs(expected);
        assertThat(actual.messageId()).isEqualTo(TEST_MSG_ID);
        assertThat(actual.threadId()).isEqualTo(TEST_THREAD_ID);
    }

    // -------------------------------------------------------------------------
    // MessagingException from MimeMessageBuilder wraps to GmailApiException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendMessage_mimeMessageBuilderThrowsMessagingException_wrapsToGmailApiException")
    void sendMessage_mimeMessageBuilderThrowsMessagingException_wrapsToGmailApiException()
            throws Exception {
        // Arrange
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
        MessagingException cause = new MessagingException("JavaMail header set failure");

        when(mimeMessageBuilder.build(eq(dto), isNull())).thenThrow(cause);

        // Act & Assert
        assertThatThrownBy(() -> gmailService.sendMessage(USER_ID, dto))
                .isInstanceOf(GmailApiException.class)
                .hasMessageContaining("Failed to construct email message for send")
                .hasCause(cause);

        // Verify repository is NOT called when builder fails.
        verify(gmailRepository, never()).sendMessage(any(), any(), any());
    }

    @Test
    @DisplayName("sendMessage_mimeMessageBuilderThrowsUnsupportedEncodingException_wrapsToGmailApiException")
    void sendMessage_mimeMessageBuilderThrowsUnsupportedEncodingException_wrapsToGmailApiException()
            throws Exception {
        // Arrange
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
        UnsupportedEncodingException cause = new UnsupportedEncodingException("UTF-8 not supported");

        when(mimeMessageBuilder.build(eq(dto), isNull())).thenThrow(cause);

        // Act & Assert
        assertThatThrownBy(() -> gmailService.sendMessage(USER_ID, dto))
                .isInstanceOf(GmailApiException.class)
                .hasMessageContaining("Failed to construct email message for send")
                .hasCause(cause);

        verify(gmailRepository, never()).sendMessage(any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // IOException from repository propagates as GmailApiException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendMessage_repositoryThrowsIOException_wrapsToGmailApiException")
    void sendMessage_repositoryThrowsIOException_wrapsToGmailApiException() throws Exception {
        // Arrange
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
        MimeMessage mimeMessage = emptyMimeMessage();
        IOException cause = new IOException("Network timeout reaching Gmail API");

        when(mimeMessageBuilder.build(eq(dto), isNull())).thenReturn(mimeMessage);
        when(gmailRepository.sendMessage(eq(USER_ID), eq(mimeMessage), isNull())).thenThrow(cause);

        // Act & Assert
        assertThatThrownBy(() -> gmailService.sendMessage(USER_ID, dto))
                .isInstanceOf(GmailApiException.class)
                .hasMessageContaining("Failed to send message for user: " + USER_ID)
                .hasCause(cause);
    }

    // -------------------------------------------------------------------------
    // Multi-recipient DTO — still delegates to builder and repository
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendMessage_multiRecipientDto_delegatesToBuilderAndRepositoryCorrectly")
    void sendMessage_multiRecipientDto_delegatesToBuilderAndRepositoryCorrectly()
            throws Exception {
        // Arrange: multi-recipient DTO to confirm service is not hard-coding recipient handling.
        SendMessageDTO dto = SendMessageRequestFixtures.validMultiRecipientWithCcAndBcc();
        MimeMessage mimeMessage = emptyMimeMessage();
        SentMessageResult expected = new SentMessageResult(TEST_MSG_ID, TEST_THREAD_ID);

        when(mimeMessageBuilder.build(eq(dto), isNull())).thenReturn(mimeMessage);
        when(gmailRepository.sendMessage(eq(USER_ID), eq(mimeMessage), isNull())).thenReturn(expected);

        // Act
        SentMessageResult actual = gmailService.sendMessage(USER_ID, dto);

        // Assert
        verify(mimeMessageBuilder).build(eq(dto), isNull());
        verify(gmailRepository).sendMessage(eq(USER_ID), eq(mimeMessage), isNull());
        assertThat(actual).isSameAs(expected);
    }
}
