package com.aucontraire.gmailbuddy.repository;

import com.aucontraire.gmailbuddy.client.GmailBatchClient;
import com.aucontraire.gmailbuddy.client.GmailClient;
import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import com.aucontraire.gmailbuddy.exception.InvalidRecipientException;
import com.aucontraire.gmailbuddy.exception.MessageTooLargeException;
import com.aucontraire.gmailbuddy.exception.RateLimitException;
import com.aucontraire.gmailbuddy.exception.ResourceNotFoundException;
import com.aucontraire.gmailbuddy.mapper.GmailMessageMapper;
import com.aucontraire.gmailbuddy.service.SentMessageResult;
import com.aucontraire.gmailbuddy.service.TokenProvider;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Draft;
import com.google.api.services.gmail.model.Message;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GmailRepositoryImpl#sendDraft(String, String)}.
 *
 * <p>Exercises the Gmail API mock chain:
 * {@code Gmail → Users → Drafts → Send → execute()}.
 * Verifies success mapping via {@link GmailMessageMapper}, plus the full
 * error-mapping suite for {@link GoogleJsonResponseException}.</p>
 *
 * <p>Each test follows Arrange-Act-Assert with clearly separated sections per
 * Constitution §VIII.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GmailRepositoryImpl — sendDraft")
class GmailRepositoryImplSendDraftTest {

    // -------------------------------------------------------------------------
    // Standard test constants
    // -------------------------------------------------------------------------

    private static final String TEST_USER_ID      = "me";
    private static final String TEST_ACCESS_TOKEN = "test-access-token-xyz";
    private static final String TEST_DRAFT_ID     = "r-9876543210";
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

    // Gmail API call chain: Gmail → Users → Drafts → Send
    @Mock private Gmail gmail;
    @Mock private Gmail.Users users;
    @Mock private Gmail.Users.Drafts drafts;
    @Mock private Gmail.Users.Drafts.Send draftsSend;

    private GmailRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new GmailRepositoryImpl(
                gmailClient, gmailBatchClient, tokenProvider, properties, gmailMessageMapper);
    }

    // -------------------------------------------------------------------------
    // Helper: set up the standard Gmail drafts.send mock chain
    // -------------------------------------------------------------------------

    private void givenGmailDraftSendChainReturns(Message sentMessage) throws Exception {
        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.drafts()).thenReturn(drafts);
        when(drafts.send(eq(TEST_USER_ID), any(Draft.class))).thenReturn(draftsSend);
        when(draftsSend.execute()).thenReturn(sentMessage);
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
    @DisplayName("sendDraft_validDraftId_returnsSentMessageResultWithCorrectIds")
    void sendDraft_validDraftId_returnsSentMessageResultWithCorrectIds() throws Exception {
        // Arrange
        Message sentMessage = new Message().setId(TEST_MESSAGE_ID).setThreadId(TEST_THREAD_ID);
        givenGmailDraftSendChainReturns(sentMessage);

        SentMessageResult expectedResult = new SentMessageResult(TEST_MESSAGE_ID, TEST_THREAD_ID);
        when(gmailMessageMapper.toSentMessageResult(sentMessage)).thenReturn(expectedResult);

        // Act
        SentMessageResult result = repository.sendDraft(TEST_USER_ID, TEST_DRAFT_ID);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.messageId()).isEqualTo(TEST_MESSAGE_ID);
        assertThat(result.threadId()).isEqualTo(TEST_THREAD_ID);
        verify(gmailMessageMapper).toSentMessageResult(sentMessage);
    }

    // -------------------------------------------------------------------------
    // Verify the correct 3-call Gmail API chain is invoked
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendDraft_validDraftId_invokesCorrectThreeCallGmailApiChain")
    void sendDraft_validDraftId_invokesCorrectThreeCallGmailApiChain() throws Exception {
        // Arrange
        Message sentMessage = new Message().setId(TEST_MESSAGE_ID).setThreadId(TEST_THREAD_ID);
        givenGmailDraftSendChainReturns(sentMessage);

        when(gmailMessageMapper.toSentMessageResult(any()))
                .thenReturn(new SentMessageResult(TEST_MESSAGE_ID, TEST_THREAD_ID));

        // Act
        repository.sendDraft(TEST_USER_ID, TEST_DRAFT_ID);

        // Assert: verify the full 3-call chain — gmail.users().drafts().send(...).execute()
        verify(tokenProvider).getAccessToken();
        verify(gmailClient).createGmailService(TEST_ACCESS_TOKEN);
        verify(gmail).users();
        verify(users).drafts();
        verify(drafts).send(eq(TEST_USER_ID), any(Draft.class));
        verify(draftsSend).execute();
    }

    // -------------------------------------------------------------------------
    // Verify the Draft submitted to drafts.send contains only the draftId
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendDraft_validDraftId_submitsDraftPayloadContainingOnlyDraftId")
    void sendDraft_validDraftId_submitsDraftPayloadContainingOnlyDraftId() throws Exception {
        // Arrange: capture the Draft argument to verify it contains only the id.
        Message sentMessage = new Message().setId(TEST_MESSAGE_ID).setThreadId(TEST_THREAD_ID);

        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.drafts()).thenReturn(drafts);

        ArgumentCaptor<Draft> draftCaptor = ArgumentCaptor.forClass(Draft.class);
        when(drafts.send(eq(TEST_USER_ID), draftCaptor.capture())).thenReturn(draftsSend);
        when(draftsSend.execute()).thenReturn(sentMessage);
        when(gmailMessageMapper.toSentMessageResult(sentMessage))
                .thenReturn(new SentMessageResult(TEST_MESSAGE_ID, TEST_THREAD_ID));

        // Act
        repository.sendDraft(TEST_USER_ID, TEST_DRAFT_ID);

        // Assert: per the Javadoc, only the id field is set; Gmail resolves content
        // from the draft store.
        Draft captured = draftCaptor.getValue();
        assertThat(captured.getId()).isEqualTo(TEST_DRAFT_ID);
        // The message field must be null — only the draft id is needed.
        assertThat(captured.getMessage()).isNull();
    }

    // -------------------------------------------------------------------------
    // GoogleJsonResponseException 403 dailySendLimitExceeded → RateLimitException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendDraft_dailySendLimitExceededError_throwsRateLimitExceptionWithRetryAfter86400")
    void sendDraft_dailySendLimitExceededError_throwsRateLimitExceptionWithRetryAfter86400()
            throws Exception {
        // Arrange
        GoogleJsonResponseException gmailError =
                buildGoogleJsonException(403, "dailySendLimitExceeded");

        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.drafts()).thenReturn(drafts);
        when(drafts.send(eq(TEST_USER_ID), any(Draft.class))).thenReturn(draftsSend);
        when(draftsSend.execute()).thenThrow(gmailError);

        // Act & Assert
        assertThatThrownBy(() -> repository.sendDraft(TEST_USER_ID, TEST_DRAFT_ID))
                .isInstanceOf(RateLimitException.class)
                .satisfies(ex -> {
                    RateLimitException rle = (RateLimitException) ex;
                    assertThat(rle.getRetryAfterSeconds()).isEqualTo(86400L);
                });
    }

    // -------------------------------------------------------------------------
    // GoogleJsonResponseException — invalidArgument → InvalidRecipientException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendDraft_invalidArgumentError_throwsInvalidRecipientException")
    void sendDraft_invalidArgumentError_throwsInvalidRecipientException() throws Exception {
        // Arrange: Gmail rejects the draft recipient with 400 invalidArgument.
        GoogleJsonResponseException gmailError =
                buildGoogleJsonException(400, "invalidArgument");

        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.drafts()).thenReturn(drafts);
        when(drafts.send(eq(TEST_USER_ID), any(Draft.class))).thenReturn(draftsSend);
        when(draftsSend.execute()).thenThrow(gmailError);

        // Act & Assert: Gmail's semantic rejection of a recipient maps to
        // InvalidRecipientException (HTTP 422), not ValidationException (HTTP 400).
        assertThatThrownBy(() -> repository.sendDraft(TEST_USER_ID, TEST_DRAFT_ID))
                .isInstanceOf(InvalidRecipientException.class);
    }

    // -------------------------------------------------------------------------
    // GoogleJsonResponseException — messageTooLarge → MessageTooLargeException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendDraft_messageTooLargeError_throwsMessageTooLargeException")
    void sendDraft_messageTooLargeError_throwsMessageTooLargeException() throws Exception {
        // Arrange: Gmail rejects the draft's assembled MIME payload as too large.
        GoogleJsonResponseException gmailError =
                buildGoogleJsonException(413, "messageTooLarge");

        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.drafts()).thenReturn(drafts);
        when(drafts.send(eq(TEST_USER_ID), any(Draft.class))).thenReturn(draftsSend);
        when(draftsSend.execute()).thenThrow(gmailError);

        // Act & Assert: Gmail's MIME-size rejection maps to MessageTooLargeException
        // (HTTP 413), not ValidationException (HTTP 400). The assembled MIME stream
        // exceeded Gmail's maximum allowed size (35 MB raw), not a local validation failure.
        assertThatThrownBy(() -> repository.sendDraft(TEST_USER_ID, TEST_DRAFT_ID))
                .isInstanceOf(MessageTooLargeException.class);
    }

    // -------------------------------------------------------------------------
    // GoogleJsonResponseException 404 → ResourceNotFoundException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendDraft_draftNotFound_throwsResourceNotFoundException")
    void sendDraft_draftNotFound_throwsResourceNotFoundException() throws Exception {
        // Arrange: 404 means the draft was already sent, discarded, or the id is invalid.
        GoogleJsonResponseException gmailError = mock(GoogleJsonResponseException.class);
        when(gmailError.getStatusCode()).thenReturn(404);
        // 404 mapping does not examine the reason field — status code alone is sufficient.
        when(gmailError.getMessage()).thenReturn("Draft not found");

        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.drafts()).thenReturn(drafts);
        when(drafts.send(eq(TEST_USER_ID), any(Draft.class))).thenReturn(draftsSend);
        when(draftsSend.execute()).thenThrow(gmailError);

        // Act & Assert
        assertThatThrownBy(() -> repository.sendDraft(TEST_USER_ID, TEST_DRAFT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // GeneralSecurityException from getGmailService() wraps to IOException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendDraft_generalSecurityExceptionFromServiceCreation_wrapsToIOException")
    void sendDraft_generalSecurityExceptionFromServiceCreation_wrapsToIOException()
            throws Exception {
        // Arrange: gmailClient.createGmailService throws GeneralSecurityException.
        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN))
                .thenThrow(new GeneralSecurityException("Key store failure"));

        // Act & Assert
        assertThatThrownBy(() -> repository.sendDraft(TEST_USER_ID, TEST_DRAFT_ID))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Security exception creating Gmail service");
    }
}
