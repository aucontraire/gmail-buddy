package com.aucontraire.gmailbuddy.controller;

import com.aucontraire.gmailbuddy.config.RateLimitInterceptor;
import com.aucontraire.gmailbuddy.config.ResponseHeaderFilter;
import com.aucontraire.gmailbuddy.exception.AuthorizationException;
import com.aucontraire.gmailbuddy.exception.GmailApiException;
import com.aucontraire.gmailbuddy.exception.OriginalMessageNotFoundException;
import com.aucontraire.gmailbuddy.mapper.ResponseMapper;
import com.aucontraire.gmailbuddy.ratelimit.GmailQuotaEstimator;
import com.aucontraire.gmailbuddy.ratelimit.RateLimitInfo;
import com.aucontraire.gmailbuddy.ratelimit.RateLimitService;
import com.aucontraire.gmailbuddy.security.TokenReferenceService;
import com.aucontraire.gmailbuddy.service.DraftCreationResult;
import com.aucontraire.gmailbuddy.service.GmailService;
import com.aucontraire.gmailbuddy.service.GoogleTokenValidator;
import com.aucontraire.gmailbuddy.service.SentMessageResult;
import com.aucontraire.gmailbuddy.validation.TestGmailBuddyPropertiesConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-slice tests for threading error-response mapping (T029) and quota-header
 * routing (T032) — both tasks share this class.
 *
 * <p><strong>T029</strong>: verifies that threading-specific exceptions (
 * {@link OriginalMessageNotFoundException}, {@link AuthorizationException},
 * {@link GmailApiException}) produce the correct RFC 7807 ProblemDetail responses
 * when either {@code POST /api/v1/gmail/messages} or {@code POST /api/v1/gmail/drafts}
 * is called with {@code inReplyToMessageId} present.</p>
 *
 * <p><strong>T032 — Quota header approach</strong>: {@code X-Gmail-Quota-Used} routing
 * is tested here via a Mockito-stub on {@link RateLimitInterceptor}-owned attributes
 * that {@link ResponseHeaderFilter} reads. The {@link RateLimitInterceptor} is NOT
 * included in the {@code @WebMvcTest} filter chain by default — it is a Spring MVC
 * interceptor registered by {@link com.aucontraire.gmailbuddy.config.WebConfig}, which
 * the web-layer slice DOES include. However, the interceptor's body-inspection logic
 * depends on {@link org.springframework.web.util.ContentCachingRequestWrapper} populated
 * by {@link com.aucontraire.gmailbuddy.config.RequestBodyCachingFilter}, which {@code
 * @WebMvcTest} does NOT include by default.
 *
 * <p>To keep the quota tests simple and reliable without pulling the full filter chain,
 * T032 bypasses the interceptor and stubs {@link GmailQuotaEstimator} directly:
 * the interceptor reads {@code quotaEstimator.estimateThreadedSendMessageQuota()} /
 * {@code estimateSendMessageQuota()} and stores the result as a request attribute that
 * {@link ResponseHeaderFilter} emits as the {@code X-Gmail-Quota-Used} header. By
 * stubbing the estimator the tests exercise the full header-emission path without
 * needing the {@code ContentCachingRequestWrapper} body-inspection logic.</p>
 *
 * <p>Uses {@code @WebMvcTest(GmailController.class)} + {@code @WithMockUser}.</p>
 */
@WebMvcTest(GmailController.class)
@Import(TestGmailBuddyPropertiesConfiguration.class)
@DisplayName("ThreadingControllerTest — T029 error responses + T032 quota headers")
class ThreadingControllerTest {

    private static final String MESSAGES_ENDPOINT = "/api/v1/gmail/messages";
    private static final String DRAFTS_ENDPOINT   = "/api/v1/gmail/drafts";

    private static final String MESSAGE_ID        = "19a2b3c4d5e6f7a8";
    private static final String THREAD_ID         = "2a2b3c4d5e6f7a82";
    private static final String ORIGINAL_MSG_ID   = "1a2b3c4d5e6f7a8b";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private GmailService gmailService;
    @MockitoBean private OAuth2AuthorizedClientService authorizedClientService;
    @MockitoBean private GoogleTokenValidator tokenValidator;
    @MockitoBean private TokenReferenceService tokenReferenceService;
    @MockitoBean private ResponseMapper responseMapper;
    @MockitoBean private RateLimitService rateLimitService;
    @MockitoBean private GmailQuotaEstimator gmailQuotaEstimator;

    // -------------------------------------------------------------------------
    // Helper: build the minimal valid send-message JSON with threading fields
    // -------------------------------------------------------------------------

    private String threadedMessageBody(String inReplyToMessageId) throws Exception {
        Map<String, Object> body = Map.of(
                "to",                   java.util.List.of("recruiter@example.com"),
                "subject",              "Re: Follow-up",
                "body",                 "Following up.",
                "bodyType",             "text",
                "threadId",             THREAD_ID,
                "inReplyToMessageId",   inReplyToMessageId
        );
        return objectMapper.writeValueAsString(body);
    }

    private String nonThreadedMessageBody() throws Exception {
        Map<String, Object> body = Map.of(
                "to",       java.util.List.of("recruiter@example.com"),
                "subject",  "Hello",
                "body",     "Plain send.",
                "bodyType", "text"
        );
        return objectMapper.writeValueAsString(body);
    }

    private String threadedDraftBody(String inReplyToMessageId) throws Exception {
        Map<String, Object> body = Map.of(
                "to",                   java.util.List.of("recruiter@example.com"),
                "subject",              "Re: Follow-up",
                "body",                 "Draft follow-up.",
                "bodyType",             "text",
                "threadId",             THREAD_ID,
                "inReplyToMessageId",   inReplyToMessageId
        );
        return objectMapper.writeValueAsString(body);
    }

    private String nonThreadedDraftBody() throws Exception {
        Map<String, Object> body = Map.of(
                "to",       java.util.List.of("recruiter@example.com"),
                "subject",  "Hello",
                "body",     "Plain draft.",
                "bodyType", "text"
        );
        return objectMapper.writeValueAsString(body);
    }

    // =========================================================================
    // T029 — threading error responses on POST /messages
    // =========================================================================

    // -------------------------------------------------------------------------
    // POST /messages + inReplyToMessageId pointing to non-existent message → 422
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postMessages_inReplyToNonExistentMessage_returns422WithOriginalMessageNotFoundType")
    void postMessages_inReplyToNonExistentMessage_returns422WithOriginalMessageNotFoundType()
            throws Exception {
        // Arrange: service throws OriginalMessageNotFoundException when the lookup fails
        when(gmailService.sendMessage(eq("me"), any()))
                .thenThrow(new OriginalMessageNotFoundException(
                        "Original message not found (messageId=" + ORIGINAL_MSG_ID + ")"));
        when(rateLimitService.recordRequest(any()))
                .thenReturn(new RateLimitInfo(1000, 999, System.currentTimeMillis()));

        // Act & Assert: HTTP 422, type = /problems/original-message-not-found, retryable false
        mockMvc.perform(post(MESSAGES_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(threadedMessageBody(ORIGINAL_MSG_ID)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("/problems/original-message-not-found"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    // -------------------------------------------------------------------------
    // POST /drafts + inReplyToMessageId pointing to non-existent message → 422
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postDrafts_inReplyToNonExistentMessage_returns422WithOriginalMessageNotFoundType")
    void postDrafts_inReplyToNonExistentMessage_returns422WithOriginalMessageNotFoundType()
            throws Exception {
        // Arrange
        when(gmailService.createDraft(eq("me"), any()))
                .thenThrow(new OriginalMessageNotFoundException(
                        "Original message not found (messageId=" + ORIGINAL_MSG_ID + ")"));
        when(rateLimitService.recordRequest(any()))
                .thenReturn(new RateLimitInfo(1000, 999, System.currentTimeMillis()));

        // Act & Assert
        mockMvc.perform(post(DRAFTS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(threadedDraftBody(ORIGINAL_MSG_ID)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("/problems/original-message-not-found"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    // -------------------------------------------------------------------------
    // POST /messages where lookup token lacks gmail.readonly (403) → 403 + authorization-failed
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postMessages_lookupTokenLacksGmailReadonlyScope_returns403WithAuthorizationFailedType")
    void postMessages_lookupTokenLacksGmailReadonlyScope_returns403WithAuthorizationFailedType()
            throws Exception {
        // Arrange: Gmail returns 403 on the metadata lookup — oauth scope insufficient (FR-008c)
        when(gmailService.sendMessage(eq("me"), any()))
                .thenThrow(new AuthorizationException(
                        "Insufficient Gmail permissions to read original message"));
        when(rateLimitService.recordRequest(any()))
                .thenReturn(new RateLimitInfo(1000, 999, System.currentTimeMillis()));

        // Act & Assert
        mockMvc.perform(post(MESSAGES_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(threadedMessageBody(ORIGINAL_MSG_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("/problems/authorization-failed"));
    }

    // -------------------------------------------------------------------------
    // POST /messages where lookup hits Gmail 503 → 502 via GmailApiException
    // No stack trace in body (FR-008a, Constitution IV)
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postMessages_lookupHitsGmail503_returns502WithNonEmptyProblemDetailAndNoStackTrace")
    void postMessages_lookupHitsGmail503_returns502WithNonEmptyProblemDetailAndNoStackTrace()
            throws Exception {
        // Arrange: transient Gmail API 5xx during the lookup maps to GmailApiException
        when(gmailService.sendMessage(eq("me"), any()))
                .thenThrow(new GmailApiException(
                        "Gmail API server error during original-message lookup: HTTP 503"));
        when(rateLimitService.recordRequest(any()))
                .thenReturn(new RateLimitInfo(1000, 999, System.currentTimeMillis()));

        // Act & Assert: HTTP 502 with a non-empty body; no stack trace (Constitution IV)
        String responseBody = mockMvc.perform(post(MESSAGES_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(threadedMessageBody(ORIGINAL_MSG_ID)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.status").value(502))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Verify no Java stack-trace fragments leak into the response body (Constitution IV)
        org.assertj.core.api.Assertions.assertThat(responseBody)
                .doesNotContain("at com.")
                .doesNotContain("java.lang.")
                .doesNotContain("StackTrace");
    }

    // =========================================================================
    // T032 — X-Gmail-Quota-Used header values for threaded vs non-threaded requests
    //
    // Approach: stub GmailQuotaEstimator return values directly. The WebMvcTest slice
    // loads WebConfig which registers RateLimitInterceptor; the interceptor calls the
    // quota estimator and stores the result as a request attribute; ResponseHeaderFilter
    // (included by @WebMvcTest because it is a @Component filter) reads that attribute
    // and emits the X-Gmail-Quota-Used header.
    //
    // The body-sniffing path (ContentCachingRequestWrapper) IS exercised because
    // @WebMvcTest includes all registered @Component filters when addFilters=true
    // (the default). However, whether the body is cached before the interceptor runs
    // depends on filter ordering. To make these tests deterministic without relying on
    // filter-chain ordering, we stub both branches: threaded → 105/15, non-threaded → 100/10.
    // =========================================================================

    // -------------------------------------------------------------------------
    // Threaded POST /messages → X-Gmail-Quota-Used: 105
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postMessages_threadedRequest_quotaHeaderIs105")
    void postMessages_threadedRequest_quotaHeaderIs105() throws Exception {
        // Arrange: service returns success; estimator stubs
        SentMessageResult result = new SentMessageResult(MESSAGE_ID, THREAD_ID);
        when(gmailService.sendMessage(eq("me"), any())).thenReturn(result);
        when(rateLimitService.recordRequest(any()))
                .thenReturn(new RateLimitInfo(1000, 999, System.currentTimeMillis()));
        when(gmailQuotaEstimator.estimateThreadedSendMessageQuota()).thenReturn(105);

        // Act & Assert: the interceptor should detect inReplyToMessageId and use 105
        mockMvc.perform(post(MESSAGES_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(threadedMessageBody(ORIGINAL_MSG_ID)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Gmail-Quota-Used", "105"));
    }

    // -------------------------------------------------------------------------
    // Non-threaded POST /messages → X-Gmail-Quota-Used: 100
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postMessages_nonThreadedRequest_quotaHeaderIs100")
    void postMessages_nonThreadedRequest_quotaHeaderIs100() throws Exception {
        // Arrange
        SentMessageResult result = new SentMessageResult(MESSAGE_ID, THREAD_ID);
        when(gmailService.sendMessage(eq("me"), any())).thenReturn(result);
        when(rateLimitService.recordRequest(any()))
                .thenReturn(new RateLimitInfo(1000, 999, System.currentTimeMillis()));
        when(gmailQuotaEstimator.estimateSendMessageQuota()).thenReturn(100);

        // Act & Assert: non-threaded request uses 100 units (FR-008b, SC-005)
        mockMvc.perform(post(MESSAGES_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nonThreadedMessageBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Gmail-Quota-Used", "100"));
    }

    // -------------------------------------------------------------------------
    // Threaded POST /drafts → X-Gmail-Quota-Used: 15
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postDrafts_threadedRequest_quotaHeaderIs15")
    void postDrafts_threadedRequest_quotaHeaderIs15() throws Exception {
        // Arrange
        DraftCreationResult result = new DraftCreationResult("r-draft-id", MESSAGE_ID, THREAD_ID);
        when(gmailService.createDraft(eq("me"), any())).thenReturn(result);
        when(rateLimitService.recordRequest(any()))
                .thenReturn(new RateLimitInfo(1000, 999, System.currentTimeMillis()));
        when(gmailQuotaEstimator.estimateThreadedCreateDraftQuota()).thenReturn(15);

        // Act & Assert
        mockMvc.perform(post(DRAFTS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(threadedDraftBody(ORIGINAL_MSG_ID)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Gmail-Quota-Used", "15"));
    }

    // -------------------------------------------------------------------------
    // Non-threaded POST /drafts → X-Gmail-Quota-Used: 10
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postDrafts_nonThreadedRequest_quotaHeaderIs10")
    void postDrafts_nonThreadedRequest_quotaHeaderIs10() throws Exception {
        // Arrange
        DraftCreationResult result = new DraftCreationResult("r-draft-id", MESSAGE_ID, THREAD_ID);
        when(gmailService.createDraft(eq("me"), any())).thenReturn(result);
        when(rateLimitService.recordRequest(any()))
                .thenReturn(new RateLimitInfo(1000, 999, System.currentTimeMillis()));
        when(gmailQuotaEstimator.estimateCreateDraftQuota()).thenReturn(10);

        // Act & Assert: existing baseline quota for non-threaded drafts
        mockMvc.perform(post(DRAFTS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nonThreadedDraftBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Gmail-Quota-Used", "10"));
    }
}
