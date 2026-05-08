package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.dto.SendMessageDTO;
import com.aucontraire.gmailbuddy.exception.GmailApiException;
import com.aucontraire.gmailbuddy.exception.OriginalMessageNotFoundException;
import com.aucontraire.gmailbuddy.fixture.SendMessageRequestFixtures;
import com.aucontraire.gmailbuddy.mapper.FilterCriteriaMapper;
import com.aucontraire.gmailbuddy.repository.GmailRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
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
 * Unit tests for the threading orchestration in {@link GmailService#sendMessage} and
 * {@link GmailService#createDraft} (T028 — Phase 3 US1).
 *
 * <p>These tests verify the service-layer decisions around {@link OriginalMessageLookup}:
 * when {@code inReplyToMessageId} is present the repository lookup IS called; when
 * only {@code threadId} is supplied the lookup is NOT called; lookup exceptions
 * propagate unchanged to the caller.</p>
 *
 * <p>All collaborators are plain Mockito mocks — no Spring context is loaded.
 * Constructor injection is used in {@link #setUp()} to produce the SUT.</p>
 */
@DisplayName("GmailService — threading orchestration (T028)")
class GmailServiceThreadingTest {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    private static final String USER_ID         = "me";
    private static final String ORIGINAL_MSG_ID = "1a2b3c4d5e6f7a8b";
    private static final String THREAD_ID       = "thread-1a2b3c4d5e6f7a8b";
    private static final String RFC_MSG_ID      = "<CABc123xyz@mail.gmail.com>";
    private static final String RETURNED_MSG_ID = "returned-msg-id-xyz";

    // -------------------------------------------------------------------------
    // SUT + collaborators
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
    // Helper: produce a no-op MimeMessage stub
    // -------------------------------------------------------------------------

    private MimeMessage emptyMimeMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    // -------------------------------------------------------------------------
    // Helper: build a DTO with inReplyToMessageId set
    // -------------------------------------------------------------------------

    private SendMessageDTO dtoWithInReplyTo(String inReplyToMessageId) {
        return new SendMessageDTO(
                List.of("recruiter@example.com"),
                null,
                null,
                "Re: Follow-up",
                "Following up on my application.",
                "text",
                THREAD_ID,
                inReplyToMessageId,
                null
        );
    }

    // -------------------------------------------------------------------------
    // Helper: build a DTO with threadId only (no inReplyToMessageId)
    // -------------------------------------------------------------------------

    private SendMessageDTO dtoWithThreadIdOnly(String threadId) {
        return new SendMessageDTO(
                List.of("recruiter@example.com"),
                null,
                null,
                "Re: Follow-up",
                "Following up on my application.",
                "text",
                threadId,
                null,   // no inReplyToMessageId
                null
        );
    }

    // =========================================================================
    // sendMessage — threading orchestration
    // =========================================================================

    // -------------------------------------------------------------------------
    // When inReplyToMessageId is non-null: getMessageHeaders IS called
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendMessage_withInReplyToMessageId_callsGetMessageHeadersOnce")
    void sendMessage_withInReplyToMessageId_callsGetMessageHeadersOnce() throws Exception {
        // Arrange
        SendMessageDTO dto = dtoWithInReplyTo(ORIGINAL_MSG_ID);
        OriginalMessageLookup lookup =
                new OriginalMessageLookup(ORIGINAL_MSG_ID, THREAD_ID, RFC_MSG_ID);
        MimeMessage mime = emptyMimeMessage();
        SentMessageResult result = new SentMessageResult(RETURNED_MSG_ID, THREAD_ID);

        when(gmailRepository.getMessageHeaders(USER_ID, ORIGINAL_MSG_ID)).thenReturn(lookup);
        when(mimeMessageBuilder.build(eq(dto), eq(lookup))).thenReturn(mime);
        when(mimeMessageBuilder.resolveThreadId(dto, lookup)).thenReturn(THREAD_ID);
        when(gmailRepository.sendMessage(eq(USER_ID), eq(mime), eq(THREAD_ID)))
                .thenReturn(result);

        // Act
        gmailService.sendMessage(USER_ID, dto);

        // Assert: lookup is called exactly once with correct userId and messageId
        verify(gmailRepository).getMessageHeaders(USER_ID, ORIGINAL_MSG_ID);
    }

    @Test
    @DisplayName("sendMessage_withInReplyToMessageId_passesFetchedLookupToMimeMessageBuilder")
    void sendMessage_withInReplyToMessageId_passesFetchedLookupToMimeMessageBuilder()
            throws Exception {
        // Arrange
        SendMessageDTO dto = dtoWithInReplyTo(ORIGINAL_MSG_ID);
        OriginalMessageLookup lookup =
                new OriginalMessageLookup(ORIGINAL_MSG_ID, THREAD_ID, RFC_MSG_ID);
        MimeMessage mime = emptyMimeMessage();
        SentMessageResult result = new SentMessageResult(RETURNED_MSG_ID, THREAD_ID);

        when(gmailRepository.getMessageHeaders(USER_ID, ORIGINAL_MSG_ID)).thenReturn(lookup);
        when(mimeMessageBuilder.build(eq(dto), eq(lookup))).thenReturn(mime);
        when(mimeMessageBuilder.resolveThreadId(dto, lookup)).thenReturn(THREAD_ID);
        when(gmailRepository.sendMessage(eq(USER_ID), eq(mime), eq(THREAD_ID)))
                .thenReturn(result);

        // Act
        gmailService.sendMessage(USER_ID, dto);

        // Assert: the lookup returned by the repository is forwarded to the builder
        verify(mimeMessageBuilder).build(eq(dto), eq(lookup));
    }

    // -------------------------------------------------------------------------
    // When threadId conflicts with fetched original's threadId: fetched value wins (FR-006)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendMessage_threadIdConflictWithFetchedOriginal_fetchedThreadIdWins")
    void sendMessage_threadIdConflictWithFetchedOriginal_fetchedThreadIdWins() throws Exception {
        // Arrange: caller supplies threadId="caller-thread" but original's actual
        // threadId from Gmail is "canonical-thread". The fetched value must win (FR-006).
        String callerThreadId    = "caller-supplied-thread-id";
        String canonicalThreadId = "canonical-thread-from-gmail";

        SendMessageDTO dto = new SendMessageDTO(
                List.of("recruiter@example.com"),
                null, null,
                "Re: Follow-up",
                "Body text", "text",
                callerThreadId,    // caller-supplied threadId
                ORIGINAL_MSG_ID,   // inReplyToMessageId present → triggers lookup
                null
        );

        // Lookup returns the canonical threadId from Gmail (different from caller's)
        OriginalMessageLookup lookup =
                new OriginalMessageLookup(ORIGINAL_MSG_ID, canonicalThreadId, RFC_MSG_ID);

        MimeMessage mime = emptyMimeMessage();
        SentMessageResult result = new SentMessageResult(RETURNED_MSG_ID, canonicalThreadId);

        when(gmailRepository.getMessageHeaders(USER_ID, ORIGINAL_MSG_ID)).thenReturn(lookup);
        when(mimeMessageBuilder.build(eq(dto), eq(lookup))).thenReturn(mime);
        // resolveThreadId must return the lookup's threadId (not the caller's)
        when(mimeMessageBuilder.resolveThreadId(dto, lookup)).thenReturn(canonicalThreadId);
        when(gmailRepository.sendMessage(eq(USER_ID), eq(mime), eq(canonicalThreadId)))
                .thenReturn(result);

        // Act
        SentMessageResult actual = gmailService.sendMessage(USER_ID, dto);

        // Assert: repository is called with the fetched canonical threadId, not the caller's
        verify(gmailRepository).sendMessage(eq(USER_ID), eq(mime), eq(canonicalThreadId));
        assertThat(actual.threadId()).isEqualTo(canonicalThreadId);
    }

    // -------------------------------------------------------------------------
    // When only threadId is supplied (no inReplyToMessageId): lookup NOT called (FR-007)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendMessage_withThreadIdOnlyAndNoInReplyTo_getMessageHeadersIsNeverCalled")
    void sendMessage_withThreadIdOnlyAndNoInReplyTo_getMessageHeadersIsNeverCalled()
            throws Exception {
        // Arrange: FR-007 — only threadId is provided; no lookup should be performed.
        // The message lands in the thread but without RFC 5322 threading headers.
        SendMessageDTO dto = dtoWithThreadIdOnly(THREAD_ID);
        MimeMessage mime = emptyMimeMessage();
        SentMessageResult result = new SentMessageResult(RETURNED_MSG_ID, THREAD_ID);

        // When lookup is null, build(dto, null) is called
        when(mimeMessageBuilder.build(eq(dto), isNull())).thenReturn(mime);
        when(mimeMessageBuilder.resolveThreadId(dto, null)).thenReturn(THREAD_ID);
        when(gmailRepository.sendMessage(eq(USER_ID), eq(mime), eq(THREAD_ID)))
                .thenReturn(result);

        // Act
        gmailService.sendMessage(USER_ID, dto);

        // Assert: getMessageHeaders is NEVER invoked (FR-007 — no inReplyToMessageId)
        verify(gmailRepository, never()).getMessageHeaders(any(), any());
    }

    // -------------------------------------------------------------------------
    // OriginalMessageNotFoundException from repository propagates to caller
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendMessage_repositoryGetMessageHeadersThrowsOriginalMessageNotFoundException_propagatesToCaller")
    void sendMessage_repositoryGetMessageHeadersThrowsOriginalMessageNotFoundException_propagatesToCaller()
            throws Exception {
        // Arrange: the message being replied to does not exist in the user's account
        SendMessageDTO dto = dtoWithInReplyTo(ORIGINAL_MSG_ID);

        when(gmailRepository.getMessageHeaders(USER_ID, ORIGINAL_MSG_ID))
                .thenThrow(new OriginalMessageNotFoundException(
                        "Original message not found (messageId=" + ORIGINAL_MSG_ID + ")"));

        // Act & Assert: the exception propagates up through the service without wrapping
        assertThatThrownBy(() -> gmailService.sendMessage(USER_ID, dto))
                .isInstanceOf(OriginalMessageNotFoundException.class);

        // Also verify the MIME builder is never invoked when lookup fails
        verify(mimeMessageBuilder, never()).build(any(), any());
        verify(gmailRepository, never()).sendMessage(any(), any(), any());
    }

    // =========================================================================
    // createDraft — threading orchestration (mirrors sendMessage tests)
    // =========================================================================

    @Test
    @DisplayName("createDraft_withInReplyToMessageId_callsGetMessageHeadersOnce")
    void createDraft_withInReplyToMessageId_callsGetMessageHeadersOnce() throws Exception {
        // Arrange
        SendMessageDTO dto = dtoWithInReplyTo(ORIGINAL_MSG_ID);
        OriginalMessageLookup lookup =
                new OriginalMessageLookup(ORIGINAL_MSG_ID, THREAD_ID, RFC_MSG_ID);
        MimeMessage mime = emptyMimeMessage();
        DraftCreationResult draftResult =
                new DraftCreationResult("r-draft-id", RETURNED_MSG_ID, THREAD_ID);

        when(gmailRepository.getMessageHeaders(USER_ID, ORIGINAL_MSG_ID)).thenReturn(lookup);
        when(mimeMessageBuilder.build(eq(dto), eq(lookup))).thenReturn(mime);
        when(mimeMessageBuilder.resolveThreadId(dto, lookup)).thenReturn(THREAD_ID);
        when(gmailRepository.createDraft(eq(USER_ID), eq(mime), eq(THREAD_ID)))
                .thenReturn(draftResult);

        // Act
        gmailService.createDraft(USER_ID, dto);

        // Assert
        verify(gmailRepository).getMessageHeaders(USER_ID, ORIGINAL_MSG_ID);
    }

    @Test
    @DisplayName("createDraft_withThreadIdOnlyAndNoInReplyTo_getMessageHeadersIsNeverCalled")
    void createDraft_withThreadIdOnlyAndNoInReplyTo_getMessageHeadersIsNeverCalled()
            throws Exception {
        // Arrange: FR-007 path for createDraft — only threadId, no lookup
        SendMessageDTO dto = dtoWithThreadIdOnly(THREAD_ID);
        MimeMessage mime = emptyMimeMessage();
        DraftCreationResult draftResult =
                new DraftCreationResult("r-draft-id", RETURNED_MSG_ID, THREAD_ID);

        when(mimeMessageBuilder.build(eq(dto), isNull())).thenReturn(mime);
        when(mimeMessageBuilder.resolveThreadId(dto, null)).thenReturn(THREAD_ID);
        when(gmailRepository.createDraft(eq(USER_ID), eq(mime), eq(THREAD_ID)))
                .thenReturn(draftResult);

        // Act
        gmailService.createDraft(USER_ID, dto);

        // Assert
        verify(gmailRepository, never()).getMessageHeaders(any(), any());
    }

    @Test
    @DisplayName("createDraft_repositoryGetMessageHeadersThrowsOriginalMessageNotFoundException_propagatesToCaller")
    void createDraft_repositoryGetMessageHeadersThrowsOriginalMessageNotFoundException_propagatesToCaller()
            throws Exception {
        // Arrange
        SendMessageDTO dto = dtoWithInReplyTo(ORIGINAL_MSG_ID);

        when(gmailRepository.getMessageHeaders(USER_ID, ORIGINAL_MSG_ID))
                .thenThrow(new OriginalMessageNotFoundException(
                        "Original message not found (messageId=" + ORIGINAL_MSG_ID + ")"));

        // Act & Assert: OriginalMessageNotFoundException propagates unchanged
        assertThatThrownBy(() -> gmailService.createDraft(USER_ID, dto))
                .isInstanceOf(OriginalMessageNotFoundException.class);

        verify(mimeMessageBuilder, never()).build(any(), any());
        verify(gmailRepository, never()).createDraft(any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // Baseline: no inReplyToMessageId, no threadId → non-threaded path unchanged
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendMessage_noThreadingFields_getMessageHeadersNotCalledAndNullThreadIdPassedToRepository")
    void sendMessage_noThreadingFields_getMessageHeadersNotCalledAndNullThreadIdPassedToRepository()
            throws Exception {
        // Arrange: standard non-threaded send — verifies FR-021 backward compatibility
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
        MimeMessage mime = emptyMimeMessage();
        SentMessageResult result = new SentMessageResult(RETURNED_MSG_ID, "new-thread-id");

        when(mimeMessageBuilder.build(eq(dto), isNull())).thenReturn(mime);
        when(mimeMessageBuilder.resolveThreadId(dto, null)).thenReturn(null);
        when(gmailRepository.sendMessage(eq(USER_ID), eq(mime), isNull()))
                .thenReturn(result);

        // Act
        SentMessageResult actual = gmailService.sendMessage(USER_ID, dto);

        // Assert: no lookup call, null threadId passed to repository
        verify(gmailRepository, never()).getMessageHeaders(any(), any());
        verify(gmailRepository).sendMessage(eq(USER_ID), eq(mime), isNull());
        assertThat(actual).isSameAs(result);
    }
}
