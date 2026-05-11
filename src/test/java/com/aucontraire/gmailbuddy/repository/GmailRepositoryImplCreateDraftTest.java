package com.aucontraire.gmailbuddy.repository;

import com.aucontraire.gmailbuddy.client.GmailBatchClient;
import com.aucontraire.gmailbuddy.client.GmailClient;
import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import com.aucontraire.gmailbuddy.exception.InvalidRecipientException;
import com.aucontraire.gmailbuddy.exception.MessageTooLargeException;
import com.aucontraire.gmailbuddy.exception.RateLimitException;
import com.aucontraire.gmailbuddy.mapper.GmailMessageMapper;
import com.aucontraire.gmailbuddy.service.DraftCreationResult;
import com.aucontraire.gmailbuddy.service.GmailQueryBuilder;
import com.aucontraire.gmailbuddy.service.TokenProvider;
import com.aucontraire.gmailbuddy.util.MimeMessageTestUtil;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Draft;
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
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GmailRepositoryImpl#createDraft(String, MimeMessage)}.
 *
 * <p>Exercises the full Gmail API mock chain (Gmail → Users → Drafts → Create → execute)
 * and verifies the three error-mapping paths for {@link GoogleJsonResponseException}.</p>
 *
 * <p>Each test follows Arrange-Act-Assert with clearly separated sections per
 * Constitution §VIII.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GmailRepositoryImpl — createDraft")
class GmailRepositoryImplCreateDraftTest {

    // -------------------------------------------------------------------------
    // Standard test constants
    // -------------------------------------------------------------------------

    private static final String TEST_USER_ID     = "me";
    private static final String TEST_ACCESS_TOKEN = "test-access-token-abc";
    private static final String TEST_DRAFT_ID    = "r-9876543210";
    private static final String TEST_MESSAGE_ID  = "19a2b3c4d5e6f7g8";
    private static final String TEST_THREAD_ID   = "19a2b3c4d5e6f7g8";

    // -------------------------------------------------------------------------
    // Mocks for the full Gmail service dependency chain
    // -------------------------------------------------------------------------

    @Mock private GmailClient gmailClient;
    @Mock private GmailBatchClient gmailBatchClient;
    @Mock private TokenProvider tokenProvider;
    @Mock private GmailBuddyProperties properties;
    @Mock private GmailMessageMapper gmailMessageMapper;
    @Mock private GmailQueryBuilder gmailQueryBuilder;

    // Gmail API call chain
    @Mock private Gmail gmail;
    @Mock private Gmail.Users users;
    @Mock private Gmail.Users.Drafts drafts;
    @Mock private Gmail.Users.Drafts.Create draftsCreate;

    private GmailRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new GmailRepositoryImpl(
                gmailClient, gmailBatchClient, tokenProvider, properties, gmailMessageMapper, gmailQueryBuilder);
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
        mimeMessage.setText("Hi there, I wanted to follow up.");
        mimeMessage.saveChanges();
        return mimeMessage;
    }

    // -------------------------------------------------------------------------
    // Helper: set up the standard Gmail draft mock chain
    // -------------------------------------------------------------------------

    private void givenGmailDraftChainReturns(Draft createdDraft) throws Exception {
        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.drafts()).thenReturn(drafts);
        when(drafts.create(eq(TEST_USER_ID), any(Draft.class))).thenReturn(draftsCreate);
        when(draftsCreate.execute()).thenReturn(createdDraft);
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
        error.setErrors(java.util.List.of(errorInfo));

        GoogleJsonResponseException exception = mock(GoogleJsonResponseException.class);
        when(exception.getDetails()).thenReturn(error);
        when(exception.getMessage()).thenReturn("Gmail API error: " + reason);
        when(exception.getStatusCode()).thenReturn(statusCode);
        return exception;
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createDraft_validMimeMessage_returnsDraftCreationResultWithCorrectIds")
    void createDraft_validMimeMessage_returnsDraftCreationResultWithCorrectIds() throws Exception {
        // Arrange
        MimeMessage mimeMessage = buildTestMimeMessage();

        Message nestedMessage = new Message().setId(TEST_MESSAGE_ID).setThreadId(TEST_THREAD_ID);
        Draft createdDraft = new Draft().setId(TEST_DRAFT_ID).setMessage(nestedMessage);

        givenGmailDraftChainReturns(createdDraft);

        DraftCreationResult expectedResult =
                new DraftCreationResult(TEST_DRAFT_ID, TEST_MESSAGE_ID, TEST_THREAD_ID);
        when(gmailMessageMapper.toDraftCreationResult(createdDraft)).thenReturn(expectedResult);

        // Act
        DraftCreationResult result = repository.createDraft(TEST_USER_ID, mimeMessage);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.draftId()).isEqualTo(TEST_DRAFT_ID);
        assertThat(result.messageId()).isEqualTo(TEST_MESSAGE_ID);
        assertThat(result.threadId()).isEqualTo(TEST_THREAD_ID);
        verify(gmailMessageMapper).toDraftCreationResult(createdDraft);
    }

    @Test
    @DisplayName("createDraft_validMimeMessage_base64UrlEncodedPayloadReconstitutesToCorrectMimeHeaders")
    void createDraft_validMimeMessage_base64UrlEncodedPayloadReconstitutesToCorrectMimeHeaders()
            throws Exception {
        // Arrange: capture the Draft argument passed to drafts.create(…) so we can
        // inspect the base64url-encoded raw payload and decode it back to a MimeMessage.
        MimeMessage originalMimeMessage = buildTestMimeMessage();

        Message nestedMessage = new Message().setId(TEST_MESSAGE_ID).setThreadId(TEST_THREAD_ID);
        Draft createdDraft = new Draft().setId(TEST_DRAFT_ID).setMessage(nestedMessage);

        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.drafts()).thenReturn(drafts);

        ArgumentCaptor<Draft> draftCaptor = ArgumentCaptor.forClass(Draft.class);
        when(drafts.create(eq(TEST_USER_ID), draftCaptor.capture())).thenReturn(draftsCreate);
        when(draftsCreate.execute()).thenReturn(createdDraft);
        when(gmailMessageMapper.toDraftCreationResult(createdDraft))
                .thenReturn(new DraftCreationResult(TEST_DRAFT_ID, TEST_MESSAGE_ID, TEST_THREAD_ID));

        // Act
        repository.createDraft(TEST_USER_ID, originalMimeMessage);

        // Assert: decode the base64url raw payload and verify subject + recipient.
        Draft capturedDraft = draftCaptor.getValue();
        assertThat(capturedDraft.getMessage()).isNotNull();
        String raw = capturedDraft.getMessage().getRaw();
        assertThat(raw).isNotBlank();

        MimeMessage reconstituted = MimeMessageTestUtil.fromBase64Url(raw);
        // getHeader returns the raw wire value which JavaMail RFC 2047 Q-encodes for
        // non-ASCII characters (e.g. the em-dash becomes =E2=80=93 and spaces become _).
        // MimeUtility.decodeText decodes the RFC 2047 encoded-word back to the
        // human-readable Unicode string so we can assert on the original content.
        String rawSubject = MimeMessageTestUtil.getHeader(reconstituted, "Subject");
        String subject = MimeUtility.decodeText(rawSubject);
        String toHeader = MimeMessageTestUtil.getHeader(reconstituted, "To");

        assertThat(subject).contains("Software Engineer");
        assertThat(toHeader).contains("recruiter@example.com");
    }

    // -------------------------------------------------------------------------
    // GoogleJsonResponseException — dailySendLimitExceeded → RateLimitException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createDraft_dailySendLimitExceededError_throwsRateLimitExceptionWithRetryAfter86400")
    void createDraft_dailySendLimitExceededError_throwsRateLimitExceptionWithRetryAfter86400()
            throws Exception {
        // Arrange
        MimeMessage mimeMessage = buildTestMimeMessage();
        GoogleJsonResponseException gmailError =
                buildGoogleJsonException(403, "dailySendLimitExceeded");

        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.drafts()).thenReturn(drafts);
        when(drafts.create(eq(TEST_USER_ID), any(Draft.class))).thenReturn(draftsCreate);
        when(draftsCreate.execute()).thenThrow(gmailError);

        // Act & Assert
        assertThatThrownBy(() -> repository.createDraft(TEST_USER_ID, mimeMessage))
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
    @DisplayName("createDraft_invalidArgumentError_throwsInvalidRecipientException")
    void createDraft_invalidArgumentError_throwsInvalidRecipientException() throws Exception {
        // Arrange
        MimeMessage mimeMessage = buildTestMimeMessage();
        GoogleJsonResponseException gmailError =
                buildGoogleJsonException(400, "invalidArgument");

        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.drafts()).thenReturn(drafts);
        when(drafts.create(eq(TEST_USER_ID), any(Draft.class))).thenReturn(draftsCreate);
        when(draftsCreate.execute()).thenThrow(gmailError);

        // Act & Assert: Gmail's semantic rejection of a recipient maps to
        // InvalidRecipientException (HTTP 422), not ValidationException (HTTP 400).
        assertThatThrownBy(() -> repository.createDraft(TEST_USER_ID, mimeMessage))
                .isInstanceOf(InvalidRecipientException.class);
    }

    // -------------------------------------------------------------------------
    // GoogleJsonResponseException — messageTooLarge → MessageTooLargeException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createDraft_messageTooLargeError_throwsMessageTooLargeException")
    void createDraft_messageTooLargeError_throwsMessageTooLargeException() throws Exception {
        // Arrange: Gmail rejects the MIME payload as too large.
        MimeMessage mimeMessage = buildTestMimeMessage();
        GoogleJsonResponseException gmailError =
                buildGoogleJsonException(413, "messageTooLarge");

        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.drafts()).thenReturn(drafts);
        when(drafts.create(eq(TEST_USER_ID), any(Draft.class))).thenReturn(draftsCreate);
        when(draftsCreate.execute()).thenThrow(gmailError);

        // Act & Assert: Gmail's MIME-size rejection maps to MessageTooLargeException
        // (HTTP 413), not ValidationException (HTTP 400). The assembled MIME stream
        // exceeded Gmail's maximum allowed size (35 MB raw), not a local validation failure.
        assertThatThrownBy(() -> repository.createDraft(TEST_USER_ID, mimeMessage))
                .isInstanceOf(MessageTooLargeException.class);
    }

    // -------------------------------------------------------------------------
    // GeneralSecurityException from getGmailService() wraps to IOException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createDraft_generalSecurityExceptionFromServiceCreation_wrapsToIOException")
    void createDraft_generalSecurityExceptionFromServiceCreation_wrapsToIOException()
            throws Exception {
        // Arrange: gmailClient.createGmailService throws GeneralSecurityException —
        // the repository must wrap it in an IOException per the interface contract.
        MimeMessage mimeMessage = buildTestMimeMessage();

        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN))
                .thenThrow(new GeneralSecurityException("Key store failure"));

        // Act & Assert
        assertThatThrownBy(() -> repository.createDraft(TEST_USER_ID, mimeMessage))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Security exception creating Gmail service");
    }

    @Test
    @DisplayName("createDraft_withThreadId_generalSecurityException_wrapsToIOException (line 544-545 coverage)")
    void createDraft_withThreadId_generalSecurityExceptionFromServiceCreation_wrapsToIOException()
            throws Exception {
        // Arrange: GeneralSecurityException thrown when creating Gmail service with threaded overload
        // This covers lines 544-545 in GmailRepositoryImpl.createDraft(String, MimeMessage, String)
        MimeMessage mimeMessage = buildTestMimeMessage();

        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN))
                .thenThrow(new GeneralSecurityException("Key store failure in threaded draft"));

        // Act & Assert: GeneralSecurityException must be wrapped in IOException (lines 544-545)
        assertThatThrownBy(() -> repository.createDraft(TEST_USER_ID, mimeMessage, "thread-xyz"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Security exception creating Gmail service");
    }

    // -------------------------------------------------------------------------
    // Verify the correct Gmail API chain is invoked
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createDraft_validRequest_invokesCorrectGmailApiChain")
    void createDraft_validRequest_invokesCorrectGmailApiChain() throws Exception {
        // Arrange
        MimeMessage mimeMessage = buildTestMimeMessage();

        Message nestedMessage = new Message().setId(TEST_MESSAGE_ID).setThreadId(TEST_THREAD_ID);
        Draft createdDraft = new Draft().setId(TEST_DRAFT_ID).setMessage(nestedMessage);
        givenGmailDraftChainReturns(createdDraft);

        when(gmailMessageMapper.toDraftCreationResult(any()))
                .thenReturn(new DraftCreationResult(TEST_DRAFT_ID, TEST_MESSAGE_ID, TEST_THREAD_ID));

        // Act
        repository.createDraft(TEST_USER_ID, mimeMessage);

        // Assert: verify the full API call chain.
        verify(tokenProvider).getAccessToken();
        verify(gmailClient).createGmailService(TEST_ACCESS_TOKEN);
        verify(gmail).users();
        verify(users).drafts();
        verify(drafts).create(eq(TEST_USER_ID), any(Draft.class));
        verify(draftsCreate).execute();
    }

    // -------------------------------------------------------------------------
    // T057 coverage gap — createDraft(String, MimeMessage, String) with non-null threadId (line 529)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createDraft_withNonNullThreadId_setsThreadIdOnDraftMessageBeforeCreate (line 529 branch)")
    void createDraft_withNonNullThreadId_setsThreadIdOnDraftMessageBeforeCreate() throws Exception {
        // Arrange: provide a non-null threadId to exercise the if (threadId != null) branch (line 529)
        MimeMessage mimeMessage = buildTestMimeMessage();
        String resolvedThreadId = "thread-xyz-resolved";

        Message nestedMessage = new Message().setId(TEST_MESSAGE_ID).setThreadId(resolvedThreadId);
        Draft createdDraft = new Draft().setId(TEST_DRAFT_ID).setMessage(nestedMessage);

        givenGmailDraftChainReturns(createdDraft);
        DraftCreationResult expectedResult = new DraftCreationResult(TEST_DRAFT_ID, TEST_MESSAGE_ID, resolvedThreadId);
        when(gmailMessageMapper.toDraftCreationResult(createdDraft)).thenReturn(expectedResult);

        // Act: call the threaded overload with a non-null threadId
        DraftCreationResult result = repository.createDraft(TEST_USER_ID, mimeMessage, resolvedThreadId);

        // Assert: result maps to the expected draft IDs
        assertThat(result).isNotNull();
        assertThat(result.draftId()).isEqualTo(TEST_DRAFT_ID);
        assertThat(result.threadId()).isEqualTo(resolvedThreadId);

        // Verify: the Draft's nested Message had setThreadId called
        ArgumentCaptor<Draft> draftCaptor = ArgumentCaptor.forClass(Draft.class);
        verify(drafts).create(eq(TEST_USER_ID), draftCaptor.capture());
        assertThat(draftCaptor.getValue().getMessage().getThreadId()).isEqualTo(resolvedThreadId);
    }
}
