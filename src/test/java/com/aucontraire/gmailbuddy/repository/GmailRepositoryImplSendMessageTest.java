package com.aucontraire.gmailbuddy.repository;

import com.aucontraire.gmailbuddy.client.GmailBatchClient;
import com.aucontraire.gmailbuddy.client.GmailClient;
import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import com.aucontraire.gmailbuddy.exception.AuthorizationException;
import com.aucontraire.gmailbuddy.exception.RateLimitException;
import com.aucontraire.gmailbuddy.exception.ValidationException;
import com.aucontraire.gmailbuddy.mapper.GmailMessageMapper;
import com.aucontraire.gmailbuddy.service.SentMessageResult;
import com.aucontraire.gmailbuddy.service.TokenProvider;
import com.aucontraire.gmailbuddy.util.MimeMessageTestUtil;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GmailRepositoryImpl#sendMessage(String, MimeMessage)}.
 *
 * <p>Exercises the full Gmail API mock chain:
 * {@code Gmail → Users → Messages → Send → execute()}.
 * Verifies base64url encoding of the raw payload, success mapping via
 * {@link GmailMessageMapper}, and the full error-mapping suite for
 * {@link GoogleJsonResponseException}.</p>
 *
 * <p>Each test follows Arrange-Act-Assert with clearly separated sections per
 * Constitution §VIII.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GmailRepositoryImpl — sendMessage")
class GmailRepositoryImplSendMessageTest {

    // -------------------------------------------------------------------------
    // Standard test constants
    // -------------------------------------------------------------------------

    private static final String TEST_USER_ID      = "me";
    private static final String TEST_ACCESS_TOKEN = "test-access-token-send-abc";
    private static final String TEST_MESSAGE_ID   = "19a2b3c4d5e6f7g8";
    private static final String TEST_THREAD_ID    = "thread-19a2b3c4d5e6f7g8";

    // -------------------------------------------------------------------------
    // Mocks for the full Gmail service dependency chain
    // -------------------------------------------------------------------------

    @Mock private GmailClient gmailClient;
    @Mock private GmailBatchClient gmailBatchClient;
    @Mock private TokenProvider tokenProvider;
    @Mock private GmailBuddyProperties properties;
    @Mock private GmailMessageMapper gmailMessageMapper;

    // Gmail API call chain: Gmail → Users → Messages → Send
    @Mock private Gmail gmail;
    @Mock private Gmail.Users users;
    @Mock private Gmail.Users.Messages messages;
    @Mock private Gmail.Users.Messages.Send messagesSend;

    private GmailRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new GmailRepositoryImpl(
                gmailClient, gmailBatchClient, tokenProvider, properties, gmailMessageMapper);
    }

    // -------------------------------------------------------------------------
    // Helper: build a minimal MimeMessage for use in tests
    // -------------------------------------------------------------------------

    private MimeMessage buildTestMimeMessage() throws MessagingException {
        Session session = Session.getInstance(new Properties());
        MimeMessage mimeMessage = new MimeMessage(session);
        mimeMessage.setFrom(new InternetAddress("sender@example.com"));
        mimeMessage.addRecipient(
                MimeMessage.RecipientType.TO,
                new InternetAddress("recruiter@example.com"));
        mimeMessage.setSubject("Software Engineer – Application Follow-up");
        mimeMessage.setText("Hi there, following up on my application.");
        mimeMessage.saveChanges();
        return mimeMessage;
    }

    // -------------------------------------------------------------------------
    // Helper: set up the standard Gmail messages.send mock chain
    // -------------------------------------------------------------------------

    private void givenGmailMessageSendChainReturns(Message sentMessage) throws Exception {
        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.messages()).thenReturn(messages);
        when(messages.send(eq(TEST_USER_ID), any(Message.class))).thenReturn(messagesSend);
        when(messagesSend.execute()).thenReturn(sentMessage);
    }

    // -------------------------------------------------------------------------
    // Helper: build a GoogleJsonResponseException with a specific reason code
    // -------------------------------------------------------------------------

    private GoogleJsonResponseException buildGoogleJsonException(int statusCode, String reason) {
        GoogleJsonError error = new GoogleJsonError();
        error.setCode(statusCode);
        error.setMessage("Gmail API error: " + reason);

        GoogleJsonError.ErrorInfo errorInfo = new GoogleJsonError.ErrorInfo();
        errorInfo.setReason(reason);
        error.setErrors(List.of(errorInfo));

        GoogleJsonResponseException exception = mock(GoogleJsonResponseException.class);
        when(exception.getDetails()).thenReturn(error);
        when(exception.getMessage()).thenReturn("Gmail API error: " + reason);
        when(exception.getStatusCode()).thenReturn(statusCode);
        return exception;
    }

    // -------------------------------------------------------------------------
    // Happy path — success returns SentMessageResult with correct ids
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendMessage_validMimeMessage_returnsSentMessageResultWithCorrectIds")
    void sendMessage_validMimeMessage_returnsSentMessageResultWithCorrectIds() throws Exception {
        // Arrange
        MimeMessage mimeMessage = buildTestMimeMessage();
        Message sentMessage = new Message().setId(TEST_MESSAGE_ID).setThreadId(TEST_THREAD_ID);
        givenGmailMessageSendChainReturns(sentMessage);

        SentMessageResult expectedResult = new SentMessageResult(TEST_MESSAGE_ID, TEST_THREAD_ID);
        when(gmailMessageMapper.toSentMessageResult(sentMessage)).thenReturn(expectedResult);

        // Act
        SentMessageResult result = repository.sendMessage(TEST_USER_ID, mimeMessage);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.messageId()).isEqualTo(TEST_MESSAGE_ID);
        assertThat(result.threadId()).isEqualTo(TEST_THREAD_ID);
        verify(gmailMessageMapper).toSentMessageResult(sentMessage);
    }

    // -------------------------------------------------------------------------
    // base64url-encoded raw payload reconstitutes via MimeMessageTestUtil
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendMessage_validMimeMessage_base64UrlEncodedPayloadReconstitutesToCorrectMimeHeaders")
    void sendMessage_validMimeMessage_base64UrlEncodedPayloadReconstitutesToCorrectMimeHeaders()
            throws Exception {
        // Arrange: capture the Message argument passed to messages.send(...) so we can
        // inspect the base64url-encoded raw payload and decode it back to a MimeMessage.
        MimeMessage originalMimeMessage = buildTestMimeMessage();
        Message sentMessage = new Message().setId(TEST_MESSAGE_ID).setThreadId(TEST_THREAD_ID);

        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.messages()).thenReturn(messages);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        when(messages.send(eq(TEST_USER_ID), messageCaptor.capture())).thenReturn(messagesSend);
        when(messagesSend.execute()).thenReturn(sentMessage);
        when(gmailMessageMapper.toSentMessageResult(sentMessage))
                .thenReturn(new SentMessageResult(TEST_MESSAGE_ID, TEST_THREAD_ID));

        // Act
        repository.sendMessage(TEST_USER_ID, originalMimeMessage);

        // Assert: decode the base64url raw payload and verify subject + recipient.
        // MimeUtility.decodeText must be applied to handle RFC 2047 Q-encoded headers
        // (e.g. the em-dash in the subject becomes =E2=80=93 on the wire).
        Message captured = messageCaptor.getValue();
        assertThat(captured.getRaw()).isNotBlank();

        MimeMessage reconstituted = MimeMessageTestUtil.fromBase64Url(captured.getRaw());
        String rawSubject = MimeMessageTestUtil.getHeader(reconstituted, "Subject");
        String subject = MimeUtility.decodeText(rawSubject);
        String toHeader = MimeMessageTestUtil.getHeader(reconstituted, "To");

        assertThat(subject).contains("Software Engineer");
        assertThat(toHeader).contains("recruiter@example.com");
    }

    // -------------------------------------------------------------------------
    // Verify the correct Gmail API chain is invoked
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendMessage_validRequest_invokesCorrectGmailApiChain")
    void sendMessage_validRequest_invokesCorrectGmailApiChain() throws Exception {
        // Arrange
        MimeMessage mimeMessage = buildTestMimeMessage();
        Message sentMessage = new Message().setId(TEST_MESSAGE_ID).setThreadId(TEST_THREAD_ID);
        givenGmailMessageSendChainReturns(sentMessage);

        when(gmailMessageMapper.toSentMessageResult(any()))
                .thenReturn(new SentMessageResult(TEST_MESSAGE_ID, TEST_THREAD_ID));

        // Act
        repository.sendMessage(TEST_USER_ID, mimeMessage);

        // Assert: verify the full API call chain.
        verify(tokenProvider).getAccessToken();
        verify(gmailClient).createGmailService(TEST_ACCESS_TOKEN);
        verify(gmail).users();
        verify(users).messages();
        verify(messages).send(eq(TEST_USER_ID), any(Message.class));
        verify(messagesSend).execute();
    }

    // -------------------------------------------------------------------------
    // GoogleJsonResponseException — invalidArgument → ValidationException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendMessage_invalidArgumentError_throwsValidationException")
    void sendMessage_invalidArgumentError_throwsValidationException() throws Exception {
        // Arrange: Gmail rejects the recipient with 400 invalidArgument.
        MimeMessage mimeMessage = buildTestMimeMessage();
        GoogleJsonResponseException gmailError =
                buildGoogleJsonException(400, "invalidArgument");

        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.messages()).thenReturn(messages);
        when(messages.send(eq(TEST_USER_ID), any(Message.class))).thenReturn(messagesSend);
        when(messagesSend.execute()).thenThrow(gmailError);

        // Act & Assert
        assertThatThrownBy(() -> repository.sendMessage(TEST_USER_ID, mimeMessage))
                .isInstanceOf(ValidationException.class);
    }

    // -------------------------------------------------------------------------
    // GoogleJsonResponseException — dailySendLimitExceeded → RateLimitException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendMessage_dailySendLimitExceededError_throwsRateLimitExceptionWithRetryAfter86400")
    void sendMessage_dailySendLimitExceededError_throwsRateLimitExceptionWithRetryAfter86400()
            throws Exception {
        // Arrange
        MimeMessage mimeMessage = buildTestMimeMessage();
        GoogleJsonResponseException gmailError =
                buildGoogleJsonException(403, "dailySendLimitExceeded");

        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.messages()).thenReturn(messages);
        when(messages.send(eq(TEST_USER_ID), any(Message.class))).thenReturn(messagesSend);
        when(messagesSend.execute()).thenThrow(gmailError);

        // Act & Assert
        assertThatThrownBy(() -> repository.sendMessage(TEST_USER_ID, mimeMessage))
                .isInstanceOf(RateLimitException.class)
                .satisfies(ex -> {
                    RateLimitException rle = (RateLimitException) ex;
                    assertThat(rle.getRetryAfterSeconds()).isEqualTo(86400L);
                });
    }

    // -------------------------------------------------------------------------
    // GoogleJsonResponseException — insufficientPermissions → AuthorizationException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendMessage_insufficientPermissionsError_throwsAuthorizationException")
    void sendMessage_insufficientPermissionsError_throwsAuthorizationException() throws Exception {
        // Arrange
        MimeMessage mimeMessage = buildTestMimeMessage();
        GoogleJsonResponseException gmailError =
                buildGoogleJsonException(403, "insufficientPermissions");

        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.messages()).thenReturn(messages);
        when(messages.send(eq(TEST_USER_ID), any(Message.class))).thenReturn(messagesSend);
        when(messagesSend.execute()).thenThrow(gmailError);

        // Act & Assert
        assertThatThrownBy(() -> repository.sendMessage(TEST_USER_ID, mimeMessage))
                .isInstanceOf(AuthorizationException.class);
    }

    // -------------------------------------------------------------------------
    // GoogleJsonResponseException — messageTooLarge → ValidationException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendMessage_messageTooLargeError_throwsValidationException")
    void sendMessage_messageTooLargeError_throwsValidationException() throws Exception {
        // Arrange
        MimeMessage mimeMessage = buildTestMimeMessage();
        GoogleJsonResponseException gmailError =
                buildGoogleJsonException(413, "messageTooLarge");

        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.messages()).thenReturn(messages);
        when(messages.send(eq(TEST_USER_ID), any(Message.class))).thenReturn(messagesSend);
        when(messagesSend.execute()).thenThrow(gmailError);

        // Act & Assert
        assertThatThrownBy(() -> repository.sendMessage(TEST_USER_ID, mimeMessage))
                .isInstanceOf(ValidationException.class);
    }

    // -------------------------------------------------------------------------
    // GeneralSecurityException from getGmailService() wraps to IOException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendMessage_generalSecurityExceptionFromServiceCreation_wrapsToIOException")
    void sendMessage_generalSecurityExceptionFromServiceCreation_wrapsToIOException()
            throws Exception {
        // Arrange: gmailClient.createGmailService throws GeneralSecurityException —
        // the repository must wrap it in an IOException per the interface contract.
        MimeMessage mimeMessage = buildTestMimeMessage();

        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN))
                .thenThrow(new GeneralSecurityException("Key store failure"));

        // Act & Assert
        assertThatThrownBy(() -> repository.sendMessage(TEST_USER_ID, mimeMessage))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Security exception creating Gmail service");
    }
}
