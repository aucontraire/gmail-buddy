package com.aucontraire.gmailbuddy.repository;

import com.aucontraire.gmailbuddy.client.GmailBatchClient;
import com.aucontraire.gmailbuddy.client.GmailClient;
import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import com.aucontraire.gmailbuddy.exception.AuthorizationException;
import com.aucontraire.gmailbuddy.exception.GmailApiException;
import com.aucontraire.gmailbuddy.exception.OriginalMessageNotFoundException;
import com.aucontraire.gmailbuddy.exception.RateLimitException;
import com.aucontraire.gmailbuddy.exception.ServiceUnavailableException;
import com.aucontraire.gmailbuddy.mapper.GmailMessageMapper;
import com.aucontraire.gmailbuddy.service.OriginalMessageLookup;
import com.aucontraire.gmailbuddy.service.TokenProvider;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GmailRepositoryImpl#getMessageHeaders(String, String)}.
 *
 * <p>Covers T027 (success case, error mapping, case-insensitive header name) and
 * T031 (null-payload and missing Message-ID null-safety guards).</p>
 *
 * <p>Follows the same mock-chain pattern as {@link GmailRepositoryImplSendMessageTest}:
 * Mockito mocks for the full Gmail API client chain
 * {@code Gmail → Users → Messages → Get → execute()}.
 * No Spring context is loaded.</p>
 *
 * <p>Each test follows Arrange-Act-Assert per Constitution §VIII.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GmailRepositoryImpl — getMessageHeaders (T027 + T031)")
class GmailRepositoryImplThreadingTest {

    // -------------------------------------------------------------------------
    // Test constants
    // -------------------------------------------------------------------------

    private static final String TEST_USER_ID      = "me";
    private static final String TEST_ACCESS_TOKEN = "test-access-token-threading-abc";
    private static final String TEST_MESSAGE_ID   = "1a2b3c4d5e6f7a8b";
    private static final String TEST_THREAD_ID    = "thread-1a2b3c4d5e6f7a8b";
    private static final String TEST_RFC_MSG_ID   = "<CABc123xyz@mail.gmail.com>";

    // -------------------------------------------------------------------------
    // Mocks for Gmail API dependency chain
    // -------------------------------------------------------------------------

    @Mock private GmailClient gmailClient;
    @Mock private GmailBatchClient gmailBatchClient;
    @Mock private TokenProvider tokenProvider;
    @Mock private GmailBuddyProperties properties;
    @Mock private GmailMessageMapper gmailMessageMapper;

    // Gmail API call chain: Gmail → Users → Messages → Get
    @Mock private Gmail gmail;
    @Mock private Gmail.Users users;
    @Mock private Gmail.Users.Messages messages;
    @Mock private Gmail.Users.Messages.Get messagesGet;

    private GmailRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new GmailRepositoryImpl(
                gmailClient, gmailBatchClient, tokenProvider, properties, gmailMessageMapper);
    }

    // -------------------------------------------------------------------------
    // Helper: set up the standard Gmail messages.get mock chain
    // -------------------------------------------------------------------------

    private void givenGmailMessageGetChainReturns(Message response) throws Exception {
        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.messages()).thenReturn(messages);
        when(messages.get(eq(TEST_USER_ID), eq(TEST_MESSAGE_ID))).thenReturn(messagesGet);
        when(messagesGet.setFormat(eq("metadata"))).thenReturn(messagesGet);
        when(messagesGet.setMetadataHeaders(any())).thenReturn(messagesGet);
        when(messagesGet.execute()).thenReturn(response);
    }

    // -------------------------------------------------------------------------
    // Helper: build a minimal valid Gmail Message with Message-ID header
    // -------------------------------------------------------------------------

    private Message buildValidMessage(String headerName, String rfcMessageId) {
        MessagePartHeader header = new MessagePartHeader();
        header.setName(headerName);
        header.setValue(rfcMessageId);

        MessagePart payload = new MessagePart();
        payload.setHeaders(List.of(header));

        Message message = new Message();
        message.setId(TEST_MESSAGE_ID);
        message.setThreadId(TEST_THREAD_ID);
        message.setPayload(payload);
        return message;
    }

    // -------------------------------------------------------------------------
    // Helper: build a GoogleJsonResponseException with status and Retry-After header
    // -------------------------------------------------------------------------

    private GoogleJsonResponseException buildGoogleJsonException(int statusCode) {
        return buildGoogleJsonException(statusCode, null);
    }

    private GoogleJsonResponseException buildGoogleJsonException(int statusCode, String retryAfterValue) {
        GoogleJsonResponseException exception = mock(GoogleJsonResponseException.class);
        when(exception.getStatusCode()).thenReturn(statusCode);

        if (retryAfterValue != null) {
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.set("Retry-After", retryAfterValue);
            when(exception.getHeaders()).thenReturn(httpHeaders);
        }

        return exception;
    }

    // =========================================================================
    // T027 — Success and error-mapping cases
    // =========================================================================

    // -------------------------------------------------------------------------
    // Success case — returns OriginalMessageLookup with correct fields
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getMessageHeaders_validMessageWithMessageIdHeader_returnsLookupWithCorrectFields")
    void getMessageHeaders_validMessageWithMessageIdHeader_returnsLookupWithCorrectFields()
            throws Exception {
        // Arrange
        Message gmailResponse = buildValidMessage("Message-ID", TEST_RFC_MSG_ID);
        givenGmailMessageGetChainReturns(gmailResponse);

        // Act
        OriginalMessageLookup result = repository.getMessageHeaders(TEST_USER_ID, TEST_MESSAGE_ID);

        // Assert: all three fields must round-trip correctly
        assertThat(result).isNotNull();
        assertThat(result.messageId()).isEqualTo(TEST_MESSAGE_ID);
        assertThat(result.threadId()).isEqualTo(TEST_THREAD_ID);
        assertThat(result.rfcMessageId()).isEqualTo(TEST_RFC_MSG_ID);
    }

    // -------------------------------------------------------------------------
    // Helper: set up mock chain with a specific exception thrown on execute()
    // -------------------------------------------------------------------------

    private void givenGmailMessageGetChainThrows(Exception exception) throws Exception {
        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.messages()).thenReturn(messages);
        when(messages.get(eq(TEST_USER_ID), eq(TEST_MESSAGE_ID))).thenReturn(messagesGet);
        when(messagesGet.setFormat(eq("metadata"))).thenReturn(messagesGet);
        when(messagesGet.setMetadataHeaders(any())).thenReturn(messagesGet);
        when(messagesGet.execute()).thenThrow(exception);
    }

    // -------------------------------------------------------------------------
    // Gmail 404 → OriginalMessageNotFoundException (HTTP 422, FR-008)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getMessageHeaders_gmail404Response_throwsOriginalMessageNotFoundException")
    void getMessageHeaders_gmail404Response_throwsOriginalMessageNotFoundException()
            throws Exception {
        // Arrange: build the exception FIRST (before any when() call in this test)
        // to avoid Mockito UnfinishedStubbing from mock() calls inside when()
        GoogleJsonResponseException e404 = buildGoogleJsonException(404);
        givenGmailMessageGetChainThrows(e404);

        // Act & Assert: 404 maps to OriginalMessageNotFoundException (not ResourceNotFoundException)
        assertThatThrownBy(() -> repository.getMessageHeaders(TEST_USER_ID, TEST_MESSAGE_ID))
                .isInstanceOf(OriginalMessageNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // Gmail 403 → AuthorizationException (HTTP 403, FR-008c)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getMessageHeaders_gmail403Response_throwsAuthorizationException")
    void getMessageHeaders_gmail403Response_throwsAuthorizationException() throws Exception {
        // Arrange
        GoogleJsonResponseException e403 = buildGoogleJsonException(403);
        givenGmailMessageGetChainThrows(e403);

        // Act & Assert: 403 maps to AuthorizationException (FR-008c)
        assertThatThrownBy(() -> repository.getMessageHeaders(TEST_USER_ID, TEST_MESSAGE_ID))
                .isInstanceOf(AuthorizationException.class);
    }

    // -------------------------------------------------------------------------
    // Gmail 5xx → GmailApiException (HTTP 502, FR-008a)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getMessageHeaders_gmail500Response_throwsGmailApiException")
    void getMessageHeaders_gmail500Response_throwsGmailApiException() throws Exception {
        // Arrange: Gmail returns 500 — transient server-side failure
        GoogleJsonResponseException e500 = buildGoogleJsonException(500);
        givenGmailMessageGetChainThrows(e500);

        // Act & Assert: 5xx maps to GmailApiException (FR-008a)
        assertThatThrownBy(() -> repository.getMessageHeaders(TEST_USER_ID, TEST_MESSAGE_ID))
                .isInstanceOf(GmailApiException.class);
    }

    @Test
    @DisplayName("getMessageHeaders_gmail503Response_throwsGmailApiException")
    void getMessageHeaders_gmail503Response_throwsGmailApiException() throws Exception {
        // Arrange: Gmail returns 503 — service temporarily unavailable at provider level
        GoogleJsonResponseException e503 = buildGoogleJsonException(503);
        givenGmailMessageGetChainThrows(e503);

        // Act & Assert: 5xx (503) maps to GmailApiException (FR-008a)
        assertThatThrownBy(() -> repository.getMessageHeaders(TEST_USER_ID, TEST_MESSAGE_ID))
                .isInstanceOf(GmailApiException.class);
    }

    // -------------------------------------------------------------------------
    // Gmail 429 → RateLimitException with non-zero retryAfterSeconds (FR-008a)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getMessageHeaders_gmail429Response_throwsRateLimitExceptionWithNonZeroRetryAfterSeconds")
    void getMessageHeaders_gmail429Response_throwsRateLimitExceptionWithNonZeroRetryAfterSeconds()
            throws Exception {
        // Arrange: Gmail returns 429 with Retry-After: 30 header.
        // Build exception first, then set up the mock chain.
        GoogleJsonResponseException e429 = buildGoogleJsonException(429, "30");
        givenGmailMessageGetChainThrows(e429);

        // Act & Assert: 429 maps to RateLimitException with non-zero retryAfterSeconds
        assertThatThrownBy(() -> repository.getMessageHeaders(TEST_USER_ID, TEST_MESSAGE_ID))
                .isInstanceOf(RateLimitException.class)
                .satisfies(ex -> {
                    RateLimitException rle = (RateLimitException) ex;
                    assertThat(rle.getRetryAfterSeconds()).isGreaterThan(0);
                    assertThat(rle.getRetryAfterSeconds()).isEqualTo(30L);
                });
    }

    @Test
    @DisplayName("getMessageHeaders_gmail429ResponseWithoutRetryAfterHeader_throwsRateLimitExceptionWithDefaultSeconds")
    void getMessageHeaders_gmail429ResponseWithoutRetryAfterHeader_throwsRateLimitExceptionWithDefaultSeconds()
            throws Exception {
        // Arrange: Gmail returns 429 with no Retry-After header — should fall back to default (60s)
        GoogleJsonResponseException e429 = buildGoogleJsonException(429);
        givenGmailMessageGetChainThrows(e429);

        // Act & Assert: should still throw RateLimitException with a non-zero default
        assertThatThrownBy(() -> repository.getMessageHeaders(TEST_USER_ID, TEST_MESSAGE_ID))
                .isInstanceOf(RateLimitException.class)
                .satisfies(ex -> {
                    RateLimitException rle = (RateLimitException) ex;
                    assertThat(rle.getRetryAfterSeconds()).isGreaterThan(0);
                });
    }

    // -------------------------------------------------------------------------
    // SocketTimeoutException → ServiceUnavailableException (HTTP 503, FR-008a)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getMessageHeaders_socketTimeoutExceptionFromTransport_throwsServiceUnavailableException")
    void getMessageHeaders_socketTimeoutExceptionFromTransport_throwsServiceUnavailableException()
            throws Exception {
        // Arrange: transport-level socket timeout — network did not respond in time
        givenGmailMessageGetChainThrows(new SocketTimeoutException("Connection timed out"));

        // Act & Assert: SocketTimeoutException maps to ServiceUnavailableException (FR-008a)
        assertThatThrownBy(() -> repository.getMessageHeaders(TEST_USER_ID, TEST_MESSAGE_ID))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    // -------------------------------------------------------------------------
    // Case-insensitive Message-ID header name match (FR-008 research.md Decision 11)
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"Message-ID", "message-id", "MESSAGE-ID", "Message-Id", "mEsSaGe-Id"})
    @DisplayName("getMessageHeaders_caseInsensitiveMessageIdHeaderName_returnsCorrectRfcMessageId")
    void getMessageHeaders_caseInsensitiveMessageIdHeaderName_returnsCorrectRfcMessageId(
            String headerName) throws Exception {
        // Arrange: Gmail may return the Message-ID header with any capitalisation
        Message gmailResponse = buildValidMessage(headerName, TEST_RFC_MSG_ID);
        givenGmailMessageGetChainReturns(gmailResponse);

        // Act
        OriginalMessageLookup result = repository.getMessageHeaders(TEST_USER_ID, TEST_MESSAGE_ID);

        // Assert: regardless of capitalisation, the rfcMessageId must be extracted correctly
        assertThat(result.rfcMessageId()).isEqualTo(TEST_RFC_MSG_ID);
    }

    // =========================================================================
    // T031 — Null-safety: null payload and absent Message-ID header
    // =========================================================================

    // -------------------------------------------------------------------------
    // T031a: getPayload() returns null → OriginalMessageNotFoundException (not NPE)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getMessageHeaders_nullPayloadOnSuccessResponse_throwsOriginalMessageNotFoundExceptionNotNpe")
    void getMessageHeaders_nullPayloadOnSuccessResponse_throwsOriginalMessageNotFoundExceptionNotNpe()
            throws Exception {
        // Arrange: Gmail returns HTTP 200 but the message payload is null.
        // This can occur for draft messages or internally-generated Gmail messages in
        // metadata format. The implementation must guard against null payload (research.md
        // Open follow-up "null-safety on getPayload()") and throw OriginalMessageNotFoundException
        // rather than NullPointerException.
        Message gmailResponse = new Message();
        gmailResponse.setId(TEST_MESSAGE_ID);
        gmailResponse.setThreadId(TEST_THREAD_ID);
        gmailResponse.setPayload(null);  // null payload — the guard under test
        givenGmailMessageGetChainReturns(gmailResponse);

        // Act & Assert: must throw OriginalMessageNotFoundException, not NPE
        assertThatThrownBy(() -> repository.getMessageHeaders(TEST_USER_ID, TEST_MESSAGE_ID))
                .isInstanceOf(OriginalMessageNotFoundException.class)
                .isNotInstanceOf(NullPointerException.class);
    }

    // -------------------------------------------------------------------------
    // T031b: payload present but no Message-ID header → OriginalMessageNotFoundException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getMessageHeaders_payloadPresentButNoMessageIdHeader_throwsOriginalMessageNotFoundException")
    void getMessageHeaders_payloadPresentButNoMessageIdHeader_throwsOriginalMessageNotFoundException()
            throws Exception {
        // Arrange: Gmail returns HTTP 200, payload exists, but has no Message-ID header.
        // Some internally-generated Gmail messages may lack this header. The implementation
        // must fail-closed (OriginalMessageNotFoundException) rather than returning a
        // lookup with null rfcMessageId, as a null rfcMessageId would break the
        // In-Reply-To / References construction downstream.
        MessagePartHeader unrelatedHeader = new MessagePartHeader();
        unrelatedHeader.setName("X-Some-Other-Header");
        unrelatedHeader.setValue("some-value");

        MessagePart payload = new MessagePart();
        payload.setHeaders(List.of(unrelatedHeader));  // no Message-ID header

        Message gmailResponse = new Message();
        gmailResponse.setId(TEST_MESSAGE_ID);
        gmailResponse.setThreadId(TEST_THREAD_ID);
        gmailResponse.setPayload(payload);

        givenGmailMessageGetChainReturns(gmailResponse);

        // Act & Assert
        assertThatThrownBy(() -> repository.getMessageHeaders(TEST_USER_ID, TEST_MESSAGE_ID))
                .isInstanceOf(OriginalMessageNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // T031c: empty headers list → OriginalMessageNotFoundException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getMessageHeaders_payloadWithEmptyHeadersList_throwsOriginalMessageNotFoundException")
    void getMessageHeaders_payloadWithEmptyHeadersList_throwsOriginalMessageNotFoundException()
            throws Exception {
        // Arrange: payload exists but headers list is empty
        MessagePart payload = new MessagePart();
        payload.setHeaders(List.of());  // empty headers list

        Message gmailResponse = new Message();
        gmailResponse.setId(TEST_MESSAGE_ID);
        gmailResponse.setThreadId(TEST_THREAD_ID);
        gmailResponse.setPayload(payload);

        givenGmailMessageGetChainReturns(gmailResponse);

        // Act & Assert
        assertThatThrownBy(() -> repository.getMessageHeaders(TEST_USER_ID, TEST_MESSAGE_ID))
                .isInstanceOf(OriginalMessageNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // GeneralSecurityException from getGmailService() wraps to IOException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getMessageHeaders_generalSecurityExceptionFromServiceCreation_wrapsToIOException")
    void getMessageHeaders_generalSecurityExceptionFromServiceCreation_wrapsToIOException()
            throws Exception {
        // Arrange: key store failure when creating the Gmail service
        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN))
                .thenThrow(new GeneralSecurityException("Key store failure"));

        // Act & Assert: GeneralSecurityException must be wrapped in IOException
        assertThatThrownBy(() -> repository.getMessageHeaders(TEST_USER_ID, TEST_MESSAGE_ID))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Security exception creating Gmail service");
    }
}
