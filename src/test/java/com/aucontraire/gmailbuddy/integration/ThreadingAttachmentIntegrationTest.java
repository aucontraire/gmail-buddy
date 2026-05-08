package com.aucontraire.gmailbuddy.integration;

import com.aucontraire.gmailbuddy.dto.Attachment;
import com.aucontraire.gmailbuddy.dto.SendMessageDTO;
import com.aucontraire.gmailbuddy.fixture.AttachmentFixtures;
import com.aucontraire.gmailbuddy.fixture.SendMessageRequestFixtures;
import com.aucontraire.gmailbuddy.repository.GmailRepository;
import com.aucontraire.gmailbuddy.service.GoogleTokenValidator;
import com.aucontraire.gmailbuddy.service.OriginalMessageLookup;
import com.aucontraire.gmailbuddy.service.SentMessageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for User Story 3 — Threaded reply with attachments (Phase 5).
 *
 * <p><strong>T045</strong>: Verifies that a request containing BOTH
 * {@code inReplyToMessageId} AND a non-empty {@code attachments} array produces
 * HTTP 201, triggers a {@code getMessageHeaders} repository call, calls the
 * 3-arg {@code sendMessage} with a non-null {@code threadId}, emits
 * {@code X-Gmail-Quota-Used: 105}, and produces a MIME payload that contains
 * both threading headers ({@code In-Reply-To}, {@code References}) and a
 * {@code multipart/mixed} body with the correct number of parts.</p>
 *
 * <p><strong>T047</strong>: FR-021 backward-compatibility regression — proves
 * that a request with all new fields absent (no {@code threadId}, no
 * {@code inReplyToMessageId}, empty {@code attachments}) produces the same
 * HTTP 201 response shape, emits {@code X-Gmail-Quota-Used: 100}, does NOT call
 * {@code getMessageHeaders}, and produces a single-part MIME with no threading
 * headers.</p>
 *
 * <p>The full Spring application context is loaded ({@code @SpringBootTest} +
 * {@code @AutoConfigureMockMvc}). Both the {@link GmailRepository} and
 * {@link com.aucontraire.gmailbuddy.service.GmailService} are mocked at the
 * repository boundary so no real Gmail API call is made. The test exercises the
 * real {@link com.aucontraire.gmailbuddy.service.GmailService} and
 * {@link com.aucontraire.gmailbuddy.service.MimeMessageBuilder} path.</p>
 *
 * <p>Test naming follows the project convention:
 * {@code methodName_stateUnderTest_expectedBehavior}.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("POST /api/v1/gmail/messages — threading + attachment integration (T045, T047)")
class ThreadingAttachmentIntegrationTest {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    private static final String MESSAGES_ENDPOINT = "/api/v1/gmail/messages";

    private static final String MESSAGE_ID          = "19a2b3c4d5e6f7g8";
    private static final String THREAD_ID           = "2a3b4c5d6e7f8a9b";
    private static final String ORIGINAL_MSG_ID     = "1a2b3c4d5e6f7a8b";
    private static final String RFC_MESSAGE_ID      = "<CABc123xyz@mail.gmail.com>";

    /**
     * Small but valid PDF base64 payload: {@code %PDF-1.4\n}.
     * Accepted by {@code Base64.getDecoder()} without throwing.
     */
    private static final String VALID_PDF_BASE64 = "JVBERi0xLjQK";

    // -------------------------------------------------------------------------
    // Spring-managed beans
    // -------------------------------------------------------------------------

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GoogleTokenValidator tokenValidator;

    /**
     * Mock at the repository boundary so the real {@link com.aucontraire.gmailbuddy.service.GmailService}
     * and {@link com.aucontraire.gmailbuddy.service.MimeMessageBuilder} execute for real.
     * This differs from {@link SendMessageIntegrationTest} which also mocks GmailService —
     * here we need to exercise the real service to validate the MIME structure and
     * verify that getMessageHeaders is (or is not) called.
     */
    @MockitoBean
    private GmailRepository gmailRepository;

    @BeforeEach
    void resetMocks() {
        reset(tokenValidator, gmailRepository);
    }

    // -------------------------------------------------------------------------
    // Helper: build a valid JSON body with threading + attachment
    // -------------------------------------------------------------------------

    private String threadedWithAttachmentBody() throws Exception {
        Map<String, Object> body = Map.of(
                "to",                   List.of("recruiter@example.com"),
                "subject",              "Re: Your inquiry",
                "body",                 "Please find my resume attached.",
                "bodyType",             "text",
                "inReplyToMessageId",   ORIGINAL_MSG_ID,
                "attachments",          List.of(
                        Map.of(
                                "filename",   "resume.pdf",
                                "mimeType",   "application/pdf",
                                "base64Data", VALID_PDF_BASE64
                        )
                )
        );
        return objectMapper.writeValueAsString(body);
    }

    /**
     * A request that matches the v0.2.0 baseline — no threading fields, no attachments.
     * Used by T047 to verify FR-021 backward compatibility.
     */
    private String baselineV020Body() throws Exception {
        Map<String, Object> body = Map.of(
                "to",      List.of("recruiter@example.com"),
                "subject", "Hello",
                "body",    "Reaching out.",
                "bodyType", "text"
        );
        return objectMapper.writeValueAsString(body);
    }

    // =========================================================================
    // T045 — Threading + attachment combination
    // =========================================================================

    /**
     * T045: Full happy-path integration test for US3 (threaded reply with attachment).
     *
     * <p>Verifies:
     * <ul>
     *   <li>HTTP 201 Created</li>
     *   <li>{@code getMessageHeaders} is called exactly once with the correct userId and messageId</li>
     *   <li>The 3-arg {@code sendMessage(userId, mimeMessage, threadId)} is called with a non-null threadId</li>
     *   <li>{@code X-Gmail-Quota-Used: 105} (threading cost; attachments add no extra quota)</li>
     *   <li>The captured {@link MimeMessage} has {@code In-Reply-To} and {@code References} headers</li>
     *   <li>The captured {@link MimeMessage} content is {@code multipart/mixed}</li>
     *   <li>The multipart has 2 parts: body + attachment</li>
     * </ul>
     */
    @Nested
    @DisplayName("T045 — Session auth: threading + attachment produces HTTP 201")
    class ThreadingAttachmentHappyPath {

        @Test
        @WithMockUser
        @DisplayName("postMessages_threadedWithAttachment_returns201")
        void postMessages_threadedWithAttachment_returns201() throws Exception {
            // Arrange: mock getMessageHeaders to return a valid lookup
            OriginalMessageLookup lookup = new OriginalMessageLookup(
                    ORIGINAL_MSG_ID, THREAD_ID, RFC_MESSAGE_ID);
            when(gmailRepository.getMessageHeaders(anyString(), eq(ORIGINAL_MSG_ID)))
                    .thenReturn(lookup);

            SentMessageResult stubResult = new SentMessageResult(MESSAGE_ID, THREAD_ID);
            when(gmailRepository.sendMessage(anyString(), any(MimeMessage.class), anyString()))
                    .thenReturn(stubResult);

            // Act & Assert: HTTP 201
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(threadedWithAttachmentBody()))
                    .andExpect(status().isCreated());
        }

        @Test
        @WithMockUser
        @DisplayName("postMessages_threadedWithAttachment_getMessageHeadersCalledOnce")
        void postMessages_threadedWithAttachment_getMessageHeadersCalledOnce() throws Exception {
            // Arrange
            OriginalMessageLookup lookup = new OriginalMessageLookup(
                    ORIGINAL_MSG_ID, THREAD_ID, RFC_MESSAGE_ID);
            when(gmailRepository.getMessageHeaders(anyString(), eq(ORIGINAL_MSG_ID)))
                    .thenReturn(lookup);
            when(gmailRepository.sendMessage(anyString(), any(MimeMessage.class), anyString()))
                    .thenReturn(new SentMessageResult(MESSAGE_ID, THREAD_ID));

            // Act
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(threadedWithAttachmentBody()))
                    .andExpect(status().isCreated());

            // Assert: getMessageHeaders called exactly once with the correct message ID
            verify(gmailRepository).getMessageHeaders(anyString(), eq(ORIGINAL_MSG_ID));
        }

        @Test
        @WithMockUser
        @DisplayName("postMessages_threadedWithAttachment_sendMessageCalledWith3ArgAndNonNullThreadId")
        void postMessages_threadedWithAttachment_sendMessageCalledWith3ArgAndNonNullThreadId()
                throws Exception {
            // Arrange
            OriginalMessageLookup lookup = new OriginalMessageLookup(
                    ORIGINAL_MSG_ID, THREAD_ID, RFC_MESSAGE_ID);
            when(gmailRepository.getMessageHeaders(anyString(), eq(ORIGINAL_MSG_ID)))
                    .thenReturn(lookup);

            ArgumentCaptor<String> threadIdCaptor = ArgumentCaptor.forClass(String.class);
            when(gmailRepository.sendMessage(anyString(), any(MimeMessage.class), threadIdCaptor.capture()))
                    .thenReturn(new SentMessageResult(MESSAGE_ID, THREAD_ID));

            // Act
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(threadedWithAttachmentBody()))
                    .andExpect(status().isCreated());

            // Assert: the 3-arg sendMessage was called and the captured threadId is non-null
            assertThat(threadIdCaptor.getValue()).isNotNull();
            assertThat(threadIdCaptor.getValue()).isEqualTo(THREAD_ID);
        }

        @Test
        @WithMockUser
        @DisplayName("postMessages_threadedWithAttachment_quotaHeaderIs105")
        void postMessages_threadedWithAttachment_quotaHeaderIs105() throws Exception {
            // Arrange
            OriginalMessageLookup lookup = new OriginalMessageLookup(
                    ORIGINAL_MSG_ID, THREAD_ID, RFC_MESSAGE_ID);
            when(gmailRepository.getMessageHeaders(anyString(), eq(ORIGINAL_MSG_ID)))
                    .thenReturn(lookup);
            when(gmailRepository.sendMessage(anyString(), any(MimeMessage.class), anyString()))
                    .thenReturn(new SentMessageResult(MESSAGE_ID, THREAD_ID));

            // Act & Assert: X-Gmail-Quota-Used must be 105 (5 lookup + 100 send)
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(threadedWithAttachmentBody()))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("X-Gmail-Quota-Used", "105"));
        }

        @Test
        @WithMockUser
        @DisplayName("postMessages_threadedWithAttachment_capturedMimeHasInReplyToHeader")
        void postMessages_threadedWithAttachment_capturedMimeHasInReplyToHeader() throws Exception {
            // Arrange
            OriginalMessageLookup lookup = new OriginalMessageLookup(
                    ORIGINAL_MSG_ID, THREAD_ID, RFC_MESSAGE_ID);
            when(gmailRepository.getMessageHeaders(anyString(), eq(ORIGINAL_MSG_ID)))
                    .thenReturn(lookup);

            ArgumentCaptor<MimeMessage> mimeCaptor = ArgumentCaptor.forClass(MimeMessage.class);
            when(gmailRepository.sendMessage(anyString(), mimeCaptor.capture(), anyString()))
                    .thenReturn(new SentMessageResult(MESSAGE_ID, THREAD_ID));

            // Act
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(threadedWithAttachmentBody()))
                    .andExpect(status().isCreated());

            // Assert: In-Reply-To header contains the rfcMessageId from the lookup
            MimeMessage captured = mimeCaptor.getValue();
            String[] inReplyTo = captured.getHeader("In-Reply-To");
            assertThat(inReplyTo).isNotNull().isNotEmpty();
            assertThat(inReplyTo[0]).isEqualTo(RFC_MESSAGE_ID);
        }

        @Test
        @WithMockUser
        @DisplayName("postMessages_threadedWithAttachment_capturedMimeHasReferencesHeader")
        void postMessages_threadedWithAttachment_capturedMimeHasReferencesHeader() throws Exception {
            // Arrange
            OriginalMessageLookup lookup = new OriginalMessageLookup(
                    ORIGINAL_MSG_ID, THREAD_ID, RFC_MESSAGE_ID);
            when(gmailRepository.getMessageHeaders(anyString(), eq(ORIGINAL_MSG_ID)))
                    .thenReturn(lookup);

            ArgumentCaptor<MimeMessage> mimeCaptor = ArgumentCaptor.forClass(MimeMessage.class);
            when(gmailRepository.sendMessage(anyString(), mimeCaptor.capture(), anyString()))
                    .thenReturn(new SentMessageResult(MESSAGE_ID, THREAD_ID));

            // Act
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(threadedWithAttachmentBody()))
                    .andExpect(status().isCreated());

            // Assert: References header contains the same rfcMessageId
            MimeMessage captured = mimeCaptor.getValue();
            String[] references = captured.getHeader("References");
            assertThat(references).isNotNull().isNotEmpty();
            assertThat(references[0]).isEqualTo(RFC_MESSAGE_ID);
        }

        @Test
        @WithMockUser
        @DisplayName("postMessages_threadedWithAttachment_capturedMimeContentIsMultipartMixed")
        void postMessages_threadedWithAttachment_capturedMimeContentIsMultipartMixed()
                throws Exception {
            // Arrange
            OriginalMessageLookup lookup = new OriginalMessageLookup(
                    ORIGINAL_MSG_ID, THREAD_ID, RFC_MESSAGE_ID);
            when(gmailRepository.getMessageHeaders(anyString(), eq(ORIGINAL_MSG_ID)))
                    .thenReturn(lookup);

            ArgumentCaptor<MimeMessage> mimeCaptor = ArgumentCaptor.forClass(MimeMessage.class);
            when(gmailRepository.sendMessage(anyString(), mimeCaptor.capture(), anyString()))
                    .thenReturn(new SentMessageResult(MESSAGE_ID, THREAD_ID));

            // Act
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(threadedWithAttachmentBody()))
                    .andExpect(status().isCreated());

            // Assert: threading does NOT strip multipart structure (FR-018, US3 acceptance scenario 2)
            MimeMessage captured = mimeCaptor.getValue();
            assertThat(captured.getContentType()).startsWith("multipart/mixed");
            Object content = captured.getContent();
            assertThat(content).isInstanceOf(MimeMultipart.class);
        }

        @Test
        @WithMockUser
        @DisplayName("postMessages_threadedWithAttachment_capturedMultipartHas2Parts")
        void postMessages_threadedWithAttachment_capturedMultipartHas2Parts() throws Exception {
            // Arrange
            OriginalMessageLookup lookup = new OriginalMessageLookup(
                    ORIGINAL_MSG_ID, THREAD_ID, RFC_MESSAGE_ID);
            when(gmailRepository.getMessageHeaders(anyString(), eq(ORIGINAL_MSG_ID)))
                    .thenReturn(lookup);

            ArgumentCaptor<MimeMessage> mimeCaptor = ArgumentCaptor.forClass(MimeMessage.class);
            when(gmailRepository.sendMessage(anyString(), mimeCaptor.capture(), anyString()))
                    .thenReturn(new SentMessageResult(MESSAGE_ID, THREAD_ID));

            // Act
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(threadedWithAttachmentBody()))
                    .andExpect(status().isCreated());

            // Assert: 2 parts — body (text/plain) + attachment (application/pdf)
            MimeMessage captured = mimeCaptor.getValue();
            MimeMultipart multipart = (MimeMultipart) captured.getContent();
            assertThat(multipart.getCount()).isEqualTo(2);
        }

        @Test
        @WithMockUser
        @DisplayName("postMessages_threadedWithAttachment_attachmentPartHasCorrectFilenameAndMimeType")
        void postMessages_threadedWithAttachment_attachmentPartHasCorrectFilenameAndMimeType()
                throws Exception {
            // Arrange
            OriginalMessageLookup lookup = new OriginalMessageLookup(
                    ORIGINAL_MSG_ID, THREAD_ID, RFC_MESSAGE_ID);
            when(gmailRepository.getMessageHeaders(anyString(), eq(ORIGINAL_MSG_ID)))
                    .thenReturn(lookup);

            ArgumentCaptor<MimeMessage> mimeCaptor = ArgumentCaptor.forClass(MimeMessage.class);
            when(gmailRepository.sendMessage(anyString(), mimeCaptor.capture(), anyString()))
                    .thenReturn(new SentMessageResult(MESSAGE_ID, THREAD_ID));

            // Act
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(threadedWithAttachmentBody()))
                    .andExpect(status().isCreated());

            // Assert: attachment part (index 1) must match the fixture's filename and MIME type
            MimeMessage captured = mimeCaptor.getValue();
            MimeMultipart multipart = (MimeMultipart) captured.getContent();
            jakarta.mail.BodyPart attachmentPart = multipart.getBodyPart(1);
            assertThat(attachmentPart.getContentType()).contains("application/pdf");
            assertThat(jakarta.mail.internet.MimeUtility.decodeText(attachmentPart.getFileName()))
                    .isEqualTo("resume.pdf");
        }
    }

    // =========================================================================
    // T047 — FR-021 backward-compatibility regression
    // =========================================================================

    /**
     * T047: Proves that a v0.2.0 caller (no threading, no attachments) is completely
     * unaffected by the US1 + US2 implementation. This is the explicit FR-021
     * backward-compatibility verification.
     *
     * <p>Verifies:
     * <ul>
     *   <li>HTTP 201 Created (same as v0.2.0)</li>
     *   <li>{@code getMessageHeaders} is NOT called</li>
     *   <li>The 3-arg {@code sendMessage} is called with {@code threadId == null}</li>
     *   <li>{@code X-Gmail-Quota-Used: 100} (no threading overhead)</li>
     *   <li>The captured {@link MimeMessage} content is NOT a {@link MimeMultipart}</li>
     *   <li>The captured {@link MimeMessage} has NO {@code In-Reply-To} or {@code References} headers</li>
     * </ul>
     */
    @Nested
    @DisplayName("T047 — FR-021: v0.2.0 caller (no threading, no attachments) is unaffected")
    class BackwardCompatibilityRegression {

        @Test
        @WithMockUser
        @DisplayName("postMessages_baselineV020Request_returns201")
        void postMessages_baselineV020Request_returns201() throws Exception {
            // Arrange: mock only the 3-arg sendMessage path (resolveThreadId returns null)
            when(gmailRepository.sendMessage(anyString(), any(MimeMessage.class), isNull()))
                    .thenReturn(new SentMessageResult(MESSAGE_ID, THREAD_ID));

            // Act & Assert: same HTTP 201 as v0.2.0
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(baselineV020Body()))
                    .andExpect(status().isCreated());
        }

        @Test
        @WithMockUser
        @DisplayName("postMessages_baselineV020Request_getMessageHeadersIsNeverCalled")
        void postMessages_baselineV020Request_getMessageHeadersIsNeverCalled() throws Exception {
            // Arrange
            when(gmailRepository.sendMessage(anyString(), any(MimeMessage.class), isNull()))
                    .thenReturn(new SentMessageResult(MESSAGE_ID, THREAD_ID));

            // Act
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(baselineV020Body()))
                    .andExpect(status().isCreated());

            // Assert: no threading lookup should have happened (FR-021)
            verify(gmailRepository, never()).getMessageHeaders(anyString(), anyString());
        }

        @Test
        @WithMockUser
        @DisplayName("postMessages_baselineV020Request_sendMessageCalledWithNullThreadId")
        void postMessages_baselineV020Request_sendMessageCalledWithNullThreadId() throws Exception {
            // Arrange
            ArgumentCaptor<String> threadIdCaptor = ArgumentCaptor.forClass(String.class);
            when(gmailRepository.sendMessage(anyString(), any(MimeMessage.class), threadIdCaptor.capture()))
                    .thenReturn(new SentMessageResult(MESSAGE_ID, THREAD_ID));

            // Act
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(baselineV020Body()))
                    .andExpect(status().isCreated());

            // Assert: resolveThreadId(dto, null) returns null when both threadId and lookup are null
            assertThat(threadIdCaptor.getValue()).isNull();
        }

        @Test
        @WithMockUser
        @DisplayName("postMessages_baselineV020Request_quotaHeaderIs100")
        void postMessages_baselineV020Request_quotaHeaderIs100() throws Exception {
            // Arrange
            when(gmailRepository.sendMessage(anyString(), any(MimeMessage.class), isNull()))
                    .thenReturn(new SentMessageResult(MESSAGE_ID, THREAD_ID));

            // Act & Assert: non-threaded request retains v0.2.0 quota of 100 (FR-008b, SC-005)
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(baselineV020Body()))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("X-Gmail-Quota-Used", "100"));
        }

        @Test
        @WithMockUser
        @DisplayName("postMessages_baselineV020Request_capturedMimeIsNotMultipart")
        void postMessages_baselineV020Request_capturedMimeIsNotMultipart() throws Exception {
            // Arrange
            ArgumentCaptor<MimeMessage> mimeCaptor = ArgumentCaptor.forClass(MimeMessage.class);
            when(gmailRepository.sendMessage(anyString(), mimeCaptor.capture(), isNull()))
                    .thenReturn(new SentMessageResult(MESSAGE_ID, THREAD_ID));

            // Act
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(baselineV020Body()))
                    .andExpect(status().isCreated());

            // Assert: single-part MIME path (FR-021) — content must NOT be a MimeMultipart
            MimeMessage captured = mimeCaptor.getValue();
            Object content = captured.getContent();
            assertThat(content).isNotInstanceOf(MimeMultipart.class);
            assertThat(content).isInstanceOf(String.class);
        }

        @Test
        @WithMockUser
        @DisplayName("postMessages_baselineV020Request_capturedMimeHasNoInReplyToHeader")
        void postMessages_baselineV020Request_capturedMimeHasNoInReplyToHeader() throws Exception {
            // Arrange
            ArgumentCaptor<MimeMessage> mimeCaptor = ArgumentCaptor.forClass(MimeMessage.class);
            when(gmailRepository.sendMessage(anyString(), mimeCaptor.capture(), isNull()))
                    .thenReturn(new SentMessageResult(MESSAGE_ID, THREAD_ID));

            // Act
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(baselineV020Body()))
                    .andExpect(status().isCreated());

            // Assert: no In-Reply-To header (FR-021, backward compatibility)
            MimeMessage captured = mimeCaptor.getValue();
            assertThat(captured.getHeader("In-Reply-To")).isNullOrEmpty();
        }

        @Test
        @WithMockUser
        @DisplayName("postMessages_baselineV020Request_capturedMimeHasNoReferencesHeader")
        void postMessages_baselineV020Request_capturedMimeHasNoReferencesHeader() throws Exception {
            // Arrange
            ArgumentCaptor<MimeMessage> mimeCaptor = ArgumentCaptor.forClass(MimeMessage.class);
            when(gmailRepository.sendMessage(anyString(), mimeCaptor.capture(), isNull()))
                    .thenReturn(new SentMessageResult(MESSAGE_ID, THREAD_ID));

            // Act
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(baselineV020Body()))
                    .andExpect(status().isCreated());

            // Assert: no References header (FR-021, backward compatibility)
            MimeMessage captured = mimeCaptor.getValue();
            assertThat(captured.getHeader("References")).isNullOrEmpty();
        }
    }
}
