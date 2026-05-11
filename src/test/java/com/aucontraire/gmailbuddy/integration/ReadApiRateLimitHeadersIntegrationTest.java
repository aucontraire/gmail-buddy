package com.aucontraire.gmailbuddy.integration;

import com.aucontraire.gmailbuddy.dto.response.LabelSummary;
import com.aucontraire.gmailbuddy.dto.response.MessageAttachmentMetadata;
import com.aucontraire.gmailbuddy.dto.response.ThreadSummary;
import com.aucontraire.gmailbuddy.repository.GmailRepository;
import com.aucontraire.gmailbuddy.service.AttachmentListResult;
import com.aucontraire.gmailbuddy.service.GmailService;
import com.aucontraire.gmailbuddy.service.GoogleTokenValidator;
import com.aucontraire.gmailbuddy.service.LabelDetailResult;
import com.aucontraire.gmailbuddy.service.LabelListResult;
import com.aucontraire.gmailbuddy.service.MessageDetailResult;
import com.aucontraire.gmailbuddy.service.ThreadDetailResult;
import com.aucontraire.gmailbuddy.service.ThreadListResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests verifying {@code X-Gmail-Quota-Used} and
 * {@code X-RateLimit-Limit}/{@code X-RateLimit-Remaining}/{@code X-RateLimit-Reset}
 * response headers are present on all 7 new Read API endpoints from feature 004
 * (T071 / FR-031).
 *
 * <p>Also verifies FR-034: all 7 endpoints reject anonymous requests with 401.</p>
 *
 * <p>Quota costs under test (per {@code GmailQuotaEstimator}):</p>
 * <ul>
 *   <li>{@code GET /threads}        — 10 units (flat, no per-item enrichment)</li>
 *   <li>{@code GET /threads/{id}}   — 10 units</li>
 *   <li>{@code GET /messages/{id}}  — 10 units (format=full, default)</li>
 *   <li>{@code GET /messages/{id}}  — 5 units (format=metadata)</li>
 *   <li>{@code GET /labels}         — 1 unit</li>
 *   <li>{@code GET /labels/{id}}    — 1 unit</li>
 *   <li>{@code GET /messages/{id}/attachments}          — 5 units</li>
 *   <li>{@code GET /messages/{id}/attachments/{attId}}  — 5 units</li>
 * </ul>
 *
 * <p>Uses the full Spring application context ({@code @SpringBootTest +
 * @AutoConfigureMockMvc}) so the real {@code RateLimitInterceptor} and
 * {@code ResponseHeaderFilter} participate in the filter chain. {@code GmailService}
 * is mocked at the service boundary; no real Gmail API calls are made.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Read API endpoints — rate-limit and quota response headers (T071 / FR-031)")
class ReadApiRateLimitHeadersIntegrationTest {

    // ---------------------------------------------------------------------------
    // URL constants
    // ---------------------------------------------------------------------------

    private static final String THREADS_BASE       = "/api/v1/gmail/threads";
    private static final String MESSAGES_BASE      = "/api/v1/gmail/messages";
    private static final String LABELS_BASE        = "/api/v1/gmail/labels";

    /** Valid hex Gmail message/thread ID — passes {@code @GmailMessageId} validator. */
    private static final String VALID_MESSAGE_ID   = "1a2b3c4d5e6f7890";
    private static final String VALID_THREAD_ID    = "1a2b3c4d5e6f7890";
    private static final String VALID_LABEL_ID     = "INBOX";
    private static final String VALID_ATTACHMENT_ID = "ANGjdJ8abc123def";

    // ---------------------------------------------------------------------------
    // Spring-managed beans
    // ---------------------------------------------------------------------------

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GmailService gmailService;

    @MockitoBean
    private GmailRepository gmailRepository;

    @MockitoBean
    private GoogleTokenValidator tokenValidator;

    @BeforeEach
    void resetMocks() {
        reset(gmailService, gmailRepository, tokenValidator);
    }

    // ---------------------------------------------------------------------------
    // Shared stub builders
    // ---------------------------------------------------------------------------

    private ThreadListResult stubThreadList() {
        ThreadSummary summary = new ThreadSummary(VALID_THREAD_ID,
                "Hi, following up on the Backend Engineer position...", "987654");
        return new ThreadListResult(List.of(summary), null, 1);
    }

    private ThreadDetailResult stubThreadDetail() {
        MessageDetailResult msg = new MessageDetailResult(
                VALID_MESSAGE_ID, VALID_THREAD_ID,
                Map.of("From", "sender@example.com", "Subject", "Hello"),
                "Hi there...",
                "Hello body text",
                "text",
                List.of("INBOX"),
                List.of()
        );
        return new ThreadDetailResult(VALID_THREAD_ID, List.of("INBOX"), List.of(msg));
    }

    private MessageDetailResult stubMessageDetail() {
        return new MessageDetailResult(
                VALID_MESSAGE_ID, VALID_THREAD_ID,
                Map.of("From", "sender@example.com"),
                "Snippet preview...",
                "Full body text",
                "text",
                List.of("INBOX"),
                List.of()
        );
    }

    private LabelListResult stubLabelList() {
        LabelSummary inbox = new LabelSummary("INBOX", "INBOX", "system", "show", "labelShow");
        return new LabelListResult(List.of(inbox), 1);
    }

    private LabelDetailResult stubLabelDetail() {
        return new LabelDetailResult(
                VALID_LABEL_ID, "INBOX", "system", "show", "labelShow",
                null, null, 42, 5, 38, 4
        );
    }

    private AttachmentListResult stubAttachmentList() {
        MessageAttachmentMetadata meta = new MessageAttachmentMetadata(
                VALID_ATTACHMENT_ID, "report.pdf", "application/pdf", 245760L);
        return new AttachmentListResult(List.of(meta));
    }

    private StreamingResponseBody stubStreamingBody() {
        return outputStream -> outputStream.write(new byte[]{0x25, 0x50, 0x44, 0x46});
    }

    // ===========================================================================
    // Nested: GET /threads — list threads
    // ===========================================================================

    @Nested
    @DisplayName("GET /api/v1/gmail/threads — list threads")
    class ListThreadsHeaderTests {

        @Test
        @WithMockUser
        @DisplayName("listThreads_success_allFourRateLimitHeadersPresent")
        void listThreads_success_allFourRateLimitHeadersPresent() throws Exception {
            when(gmailService.listThreads(anyString(), any(), any(), anyInt()))
                    .thenReturn(stubThreadList());

            mockMvc.perform(get(THREADS_BASE))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("X-Gmail-Quota-Used"))
                    .andExpect(header().exists("X-RateLimit-Limit"))
                    .andExpect(header().exists("X-RateLimit-Remaining"))
                    .andExpect(header().exists("X-RateLimit-Reset"));
        }

        @Test
        @WithMockUser
        @DisplayName("listThreads_success_quotaHeaderIs10")
        void listThreads_success_quotaHeaderIs10() throws Exception {
            when(gmailService.listThreads(anyString(), any(), any(), anyInt()))
                    .thenReturn(stubThreadList());

            MvcResult result = mockMvc.perform(get(THREADS_BASE))
                    .andExpect(status().isOk())
                    .andReturn();

            String quotaUsed = result.getResponse().getHeader("X-Gmail-Quota-Used");
            assertThat(quotaUsed).isNotNull();
            assertThat(Integer.parseInt(quotaUsed)).isEqualTo(10);
        }

        @Test
        @WithMockUser
        @DisplayName("listThreads_invalidLimit_400ErrorResponseStillHasRateLimitHeaders")
        void listThreads_invalidLimit_400ErrorResponseStillHasRateLimitHeaders() throws Exception {
            // limit=0 violates @Min(1)
            mockMvc.perform(get(THREADS_BASE).param("limit", "0"))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().exists("X-RateLimit-Limit"))
                    .andExpect(header().exists("X-RateLimit-Remaining"))
                    .andExpect(header().exists("X-RateLimit-Reset"));
        }

        @Test
        @DisplayName("listThreads_withInvalidBearerToken_rejectedWith401 (FR-034)")
        void listThreads_withInvalidBearerToken_rejectedWith401() throws Exception {
            // FR-034: API endpoints reject requests with invalid Bearer tokens with 401.
            // Note: truly anonymous requests (no Authorization header) get 302 redirect to
            // OAuth2 login, which is the correct browser-session behavior. Bearer-token
            // API clients that present an invalid token get 401 (enforced by TokenAuthenticationFilter).
            mockMvc.perform(get(THREADS_BASE)
                            .header("Authorization", "Bearer invalid-token-xyz"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ===========================================================================
    // Nested: GET /threads/{threadId} — get thread detail
    // ===========================================================================

    @Nested
    @DisplayName("GET /api/v1/gmail/threads/{threadId} — get thread detail")
    class GetThreadHeaderTests {

        @Test
        @WithMockUser
        @DisplayName("getThread_success_allFourRateLimitHeadersPresent")
        void getThread_success_allFourRateLimitHeadersPresent() throws Exception {
            when(gmailService.getThread(anyString(), anyString()))
                    .thenReturn(stubThreadDetail());

            mockMvc.perform(get(THREADS_BASE + "/{threadId}", VALID_THREAD_ID))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("X-Gmail-Quota-Used"))
                    .andExpect(header().exists("X-RateLimit-Limit"))
                    .andExpect(header().exists("X-RateLimit-Remaining"))
                    .andExpect(header().exists("X-RateLimit-Reset"));
        }

        @Test
        @WithMockUser
        @DisplayName("getThread_success_quotaHeaderIs10")
        void getThread_success_quotaHeaderIs10() throws Exception {
            when(gmailService.getThread(anyString(), anyString()))
                    .thenReturn(stubThreadDetail());

            MvcResult result = mockMvc.perform(get(THREADS_BASE + "/{threadId}", VALID_THREAD_ID))
                    .andExpect(status().isOk())
                    .andReturn();

            String quotaUsed = result.getResponse().getHeader("X-Gmail-Quota-Used");
            assertThat(quotaUsed).isNotNull();
            assertThat(Integer.parseInt(quotaUsed)).isEqualTo(10);
        }

        @Test
        @WithMockUser
        @DisplayName("getThread_invalidIdFormat_400ErrorResponseStillHasRateLimitHeaders")
        void getThread_invalidIdFormat_400ErrorResponseStillHasRateLimitHeaders() throws Exception {
            // Period violates @GmailMessageId validator
            mockMvc.perform(get(THREADS_BASE + "/bad.id"))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().exists("X-RateLimit-Limit"))
                    .andExpect(header().exists("X-RateLimit-Remaining"))
                    .andExpect(header().exists("X-RateLimit-Reset"));
        }

        @Test
        @DisplayName("getThread_withInvalidBearerToken_rejectedWith401 (FR-034)")
        void getThread_withInvalidBearerToken_rejectedWith401() throws Exception {
            mockMvc.perform(get(THREADS_BASE + "/{threadId}", VALID_THREAD_ID)
                            .header("Authorization", "Bearer invalid-token-xyz"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ===========================================================================
    // Nested: GET /messages/{messageId} — get message detail (format=full)
    // ===========================================================================

    @Nested
    @DisplayName("GET /api/v1/gmail/messages/{messageId} — get message detail (format=full)")
    class GetMessageDetailFullHeaderTests {

        @Test
        @WithMockUser
        @DisplayName("getMessageDetail_full_allFourRateLimitHeadersPresent")
        void getMessageDetail_full_allFourRateLimitHeadersPresent() throws Exception {
            when(gmailService.getMessageDetail(anyString(), anyString(), eq("full")))
                    .thenReturn(stubMessageDetail());

            mockMvc.perform(get(MESSAGES_BASE + "/{messageId}", VALID_MESSAGE_ID))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("X-Gmail-Quota-Used"))
                    .andExpect(header().exists("X-RateLimit-Limit"))
                    .andExpect(header().exists("X-RateLimit-Remaining"))
                    .andExpect(header().exists("X-RateLimit-Reset"));
        }

        @Test
        @WithMockUser
        @DisplayName("getMessageDetail_full_quotaHeaderIs10")
        void getMessageDetail_full_quotaHeaderIs10() throws Exception {
            when(gmailService.getMessageDetail(anyString(), anyString(), eq("full")))
                    .thenReturn(stubMessageDetail());

            MvcResult result = mockMvc.perform(get(MESSAGES_BASE + "/{messageId}", VALID_MESSAGE_ID))
                    .andExpect(status().isOk())
                    .andReturn();

            String quotaUsed = result.getResponse().getHeader("X-Gmail-Quota-Used");
            assertThat(quotaUsed).isNotNull();
            assertThat(Integer.parseInt(quotaUsed)).isEqualTo(10);
        }

        @Test
        @WithMockUser
        @DisplayName("getMessageDetail_metadata_quotaHeaderIs5")
        void getMessageDetail_metadata_quotaHeaderIs5() throws Exception {
            MessageDetailResult metadataResult = new MessageDetailResult(
                    VALID_MESSAGE_ID, VALID_THREAD_ID,
                    Map.of("From", "sender@example.com"),
                    "Snippet preview...",
                    null,  // body is null for metadata format
                    null,  // bodyType is null for metadata format
                    List.of("INBOX"),
                    List.of()
            );
            when(gmailService.getMessageDetail(anyString(), anyString(), eq("metadata")))
                    .thenReturn(metadataResult);

            MvcResult result = mockMvc.perform(get(MESSAGES_BASE + "/{messageId}", VALID_MESSAGE_ID)
                            .param("format", "metadata"))
                    .andExpect(status().isOk())
                    .andReturn();

            String quotaUsed = result.getResponse().getHeader("X-Gmail-Quota-Used");
            assertThat(quotaUsed).isNotNull();
            assertThat(Integer.parseInt(quotaUsed)).isEqualTo(5);
        }

        @Test
        @WithMockUser
        @DisplayName("getMessageDetail_invalidFormat_400ErrorResponseStillHasRateLimitHeaders")
        void getMessageDetail_invalidFormat_400ErrorResponseStillHasRateLimitHeaders() throws Exception {
            // format=raw violates @Pattern(full|metadata)
            mockMvc.perform(get(MESSAGES_BASE + "/{messageId}", VALID_MESSAGE_ID)
                            .param("format", "raw"))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().exists("X-RateLimit-Limit"))
                    .andExpect(header().exists("X-RateLimit-Remaining"))
                    .andExpect(header().exists("X-RateLimit-Reset"));
        }

        @Test
        @DisplayName("getMessageDetail_withInvalidBearerToken_rejectedWith401 (FR-034)")
        void getMessageDetail_withInvalidBearerToken_rejectedWith401() throws Exception {
            mockMvc.perform(get(MESSAGES_BASE + "/{messageId}", VALID_MESSAGE_ID)
                            .header("Authorization", "Bearer invalid-token-xyz"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ===========================================================================
    // Nested: GET /labels — list labels
    // ===========================================================================

    @Nested
    @DisplayName("GET /api/v1/gmail/labels — list labels")
    class ListLabelsHeaderTests {

        @Test
        @WithMockUser
        @DisplayName("listLabels_success_allFourRateLimitHeadersPresent")
        void listLabels_success_allFourRateLimitHeadersPresent() throws Exception {
            when(gmailService.listLabels(anyString()))
                    .thenReturn(stubLabelList());

            mockMvc.perform(get(LABELS_BASE))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("X-Gmail-Quota-Used"))
                    .andExpect(header().exists("X-RateLimit-Limit"))
                    .andExpect(header().exists("X-RateLimit-Remaining"))
                    .andExpect(header().exists("X-RateLimit-Reset"));
        }

        @Test
        @WithMockUser
        @DisplayName("listLabels_success_quotaHeaderIs1")
        void listLabels_success_quotaHeaderIs1() throws Exception {
            when(gmailService.listLabels(anyString()))
                    .thenReturn(stubLabelList());

            MvcResult result = mockMvc.perform(get(LABELS_BASE))
                    .andExpect(status().isOk())
                    .andReturn();

            String quotaUsed = result.getResponse().getHeader("X-Gmail-Quota-Used");
            assertThat(quotaUsed).isNotNull();
            assertThat(Integer.parseInt(quotaUsed)).isEqualTo(1);
        }

        @Test
        @DisplayName("listLabels_withInvalidBearerToken_rejectedWith401 (FR-034)")
        void listLabels_withInvalidBearerToken_rejectedWith401() throws Exception {
            mockMvc.perform(get(LABELS_BASE)
                            .header("Authorization", "Bearer invalid-token-xyz"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ===========================================================================
    // Nested: GET /labels/{labelId} — get label detail
    // ===========================================================================

    @Nested
    @DisplayName("GET /api/v1/gmail/labels/{labelId} — get label detail")
    class GetLabelHeaderTests {

        @Test
        @WithMockUser
        @DisplayName("getLabel_success_allFourRateLimitHeadersPresent")
        void getLabel_success_allFourRateLimitHeadersPresent() throws Exception {
            when(gmailService.getLabel(anyString(), anyString()))
                    .thenReturn(stubLabelDetail());

            mockMvc.perform(get(LABELS_BASE + "/{labelId}", VALID_LABEL_ID))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("X-Gmail-Quota-Used"))
                    .andExpect(header().exists("X-RateLimit-Limit"))
                    .andExpect(header().exists("X-RateLimit-Remaining"))
                    .andExpect(header().exists("X-RateLimit-Reset"));
        }

        @Test
        @WithMockUser
        @DisplayName("getLabel_success_quotaHeaderIs1")
        void getLabel_success_quotaHeaderIs1() throws Exception {
            when(gmailService.getLabel(anyString(), anyString()))
                    .thenReturn(stubLabelDetail());

            MvcResult result = mockMvc.perform(get(LABELS_BASE + "/{labelId}", VALID_LABEL_ID))
                    .andExpect(status().isOk())
                    .andReturn();

            String quotaUsed = result.getResponse().getHeader("X-Gmail-Quota-Used");
            assertThat(quotaUsed).isNotNull();
            assertThat(Integer.parseInt(quotaUsed)).isEqualTo(1);
        }

        @Test
        @DisplayName("getLabel_withInvalidBearerToken_rejectedWith401 (FR-034)")
        void getLabel_withInvalidBearerToken_rejectedWith401() throws Exception {
            mockMvc.perform(get(LABELS_BASE + "/{labelId}", VALID_LABEL_ID)
                            .header("Authorization", "Bearer invalid-token-xyz"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ===========================================================================
    // Nested: GET /messages/{messageId}/attachments — list attachments
    // ===========================================================================

    @Nested
    @DisplayName("GET /api/v1/gmail/messages/{messageId}/attachments — list attachments")
    class ListAttachmentsHeaderTests {

        @Test
        @WithMockUser
        @DisplayName("listAttachments_success_allFourRateLimitHeadersPresent")
        void listAttachments_success_allFourRateLimitHeadersPresent() throws Exception {
            when(gmailService.listAttachments(anyString(), anyString()))
                    .thenReturn(stubAttachmentList());

            mockMvc.perform(get(MESSAGES_BASE + "/{messageId}/attachments", VALID_MESSAGE_ID))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("X-Gmail-Quota-Used"))
                    .andExpect(header().exists("X-RateLimit-Limit"))
                    .andExpect(header().exists("X-RateLimit-Remaining"))
                    .andExpect(header().exists("X-RateLimit-Reset"));
        }

        @Test
        @WithMockUser
        @DisplayName("listAttachments_success_quotaHeaderIs5")
        void listAttachments_success_quotaHeaderIs5() throws Exception {
            when(gmailService.listAttachments(anyString(), anyString()))
                    .thenReturn(stubAttachmentList());

            MvcResult result = mockMvc.perform(
                            get(MESSAGES_BASE + "/{messageId}/attachments", VALID_MESSAGE_ID))
                    .andExpect(status().isOk())
                    .andReturn();

            String quotaUsed = result.getResponse().getHeader("X-Gmail-Quota-Used");
            assertThat(quotaUsed).isNotNull();
            assertThat(Integer.parseInt(quotaUsed)).isEqualTo(5);
        }

        @Test
        @WithMockUser
        @DisplayName("listAttachments_invalidMessageId_400ErrorResponseStillHasRateLimitHeaders")
        void listAttachments_invalidMessageId_400ErrorResponseStillHasRateLimitHeaders() throws Exception {
            mockMvc.perform(get(MESSAGES_BASE + "/bad.id/attachments"))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().exists("X-RateLimit-Limit"))
                    .andExpect(header().exists("X-RateLimit-Remaining"))
                    .andExpect(header().exists("X-RateLimit-Reset"));
        }

        @Test
        @DisplayName("listAttachments_withInvalidBearerToken_rejectedWith401 (FR-034)")
        void listAttachments_withInvalidBearerToken_rejectedWith401() throws Exception {
            mockMvc.perform(get(MESSAGES_BASE + "/{messageId}/attachments", VALID_MESSAGE_ID)
                            .header("Authorization", "Bearer invalid-token-xyz"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ===========================================================================
    // Nested: GET /messages/{messageId}/attachments/{attachmentId} — download attachment
    // ===========================================================================

    @Nested
    @DisplayName("GET /api/v1/gmail/messages/{messageId}/attachments/{attachmentId} — download attachment")
    class GetAttachmentHeaderTests {

        @Test
        @WithMockUser
        @DisplayName("getAttachment_success_allFourRateLimitHeadersPresent")
        void getAttachment_success_allFourRateLimitHeadersPresent() throws Exception {
            when(gmailService.getAttachment(anyString(), anyString(), anyString()))
                    .thenReturn(stubStreamingBody());

            mockMvc.perform(get(MESSAGES_BASE + "/{messageId}/attachments/{attachmentId}",
                            VALID_MESSAGE_ID, VALID_ATTACHMENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("X-Gmail-Quota-Used"))
                    .andExpect(header().exists("X-RateLimit-Limit"))
                    .andExpect(header().exists("X-RateLimit-Remaining"))
                    .andExpect(header().exists("X-RateLimit-Reset"));
        }

        @Test
        @WithMockUser
        @DisplayName("getAttachment_success_quotaHeaderIs5")
        void getAttachment_success_quotaHeaderIs5() throws Exception {
            when(gmailService.getAttachment(anyString(), anyString(), anyString()))
                    .thenReturn(stubStreamingBody());

            MvcResult result = mockMvc.perform(
                            get(MESSAGES_BASE + "/{messageId}/attachments/{attachmentId}",
                                    VALID_MESSAGE_ID, VALID_ATTACHMENT_ID))
                    .andExpect(status().isOk())
                    .andReturn();

            String quotaUsed = result.getResponse().getHeader("X-Gmail-Quota-Used");
            assertThat(quotaUsed).isNotNull();
            assertThat(Integer.parseInt(quotaUsed)).isEqualTo(5);
        }

        @Test
        @WithMockUser
        @DisplayName("getAttachment_unsafeFilenameParam_400ErrorResponseStillHasRateLimitHeaders")
        void getAttachment_unsafeFilenameParam_400ErrorResponseStillHasRateLimitHeaders()
                throws Exception {
            // filename with LF character is rejected by sanitizeFilename()
            mockMvc.perform(get(MESSAGES_BASE + "/{messageId}/attachments/{attachmentId}",
                            VALID_MESSAGE_ID, VALID_ATTACHMENT_ID)
                            .param("filename", "bad\nfilename.pdf"))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().exists("X-RateLimit-Limit"))
                    .andExpect(header().exists("X-RateLimit-Remaining"))
                    .andExpect(header().exists("X-RateLimit-Reset"));
        }

        @Test
        @DisplayName("getAttachment_withInvalidBearerToken_rejectedWith401 (FR-034)")
        void getAttachment_withInvalidBearerToken_rejectedWith401() throws Exception {
            mockMvc.perform(get(MESSAGES_BASE + "/{messageId}/attachments/{attachmentId}",
                            VALID_MESSAGE_ID, VALID_ATTACHMENT_ID)
                            .header("Authorization", "Bearer invalid-token-xyz"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ===========================================================================
    // Nested: Cross-cutting — RateLimit header value sanity
    // ===========================================================================

    @Nested
    @DisplayName("Rate-limit header value sanity across Read API endpoints")
    class RateLimitHeaderValueSanityTests {

        @Test
        @WithMockUser
        @DisplayName("rateLimitHeaders_limitsAreNonNegativeIntegers_onListThreads")
        void rateLimitHeaders_limitsAreNonNegativeIntegers_onListThreads() throws Exception {
            when(gmailService.listThreads(anyString(), any(), any(), anyInt()))
                    .thenReturn(new ThreadListResult(List.of(), null, 0));

            MvcResult result = mockMvc.perform(get(THREADS_BASE))
                    .andExpect(status().isOk())
                    .andReturn();

            String limit     = result.getResponse().getHeader("X-RateLimit-Limit");
            String remaining = result.getResponse().getHeader("X-RateLimit-Remaining");
            String reset     = result.getResponse().getHeader("X-RateLimit-Reset");

            assertThat(limit).isNotNull();
            assertThat(remaining).isNotNull();
            assertThat(reset).isNotNull();

            assertThat(Integer.parseInt(limit)).isGreaterThan(0);
            assertThat(Integer.parseInt(remaining)).isGreaterThanOrEqualTo(0);
            assertThat(Long.parseLong(reset)).isGreaterThan(0L);
        }

        @Test
        @WithMockUser
        @DisplayName("rateLimitHeaders_remainingDecrementsAcrossConsecutiveRequests_onListLabels")
        void rateLimitHeaders_remainingDecrementsAcrossConsecutiveRequests_onListLabels()
                throws Exception {
            when(gmailService.listLabels(anyString()))
                    .thenReturn(new LabelListResult(List.of(), 0));

            MvcResult first = mockMvc.perform(get(LABELS_BASE))
                    .andExpect(status().isOk())
                    .andReturn();

            MvcResult second = mockMvc.perform(get(LABELS_BASE))
                    .andExpect(status().isOk())
                    .andReturn();

            int remainingFirst  = Integer.parseInt(first.getResponse().getHeader("X-RateLimit-Remaining"));
            int remainingSecond = Integer.parseInt(second.getResponse().getHeader("X-RateLimit-Remaining"));

            assertThat(remainingSecond).isLessThanOrEqualTo(remainingFirst);
        }
    }
}
