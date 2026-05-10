package com.aucontraire.gmailbuddy.controller;

import com.aucontraire.gmailbuddy.exception.ResourceNotFoundException;
import com.aucontraire.gmailbuddy.mapper.GmailMessageMapper;
import com.aucontraire.gmailbuddy.mapper.ResponseMapper;
import com.aucontraire.gmailbuddy.ratelimit.GmailQuotaEstimator;
import com.aucontraire.gmailbuddy.ratelimit.RateLimitService;
import com.aucontraire.gmailbuddy.security.TokenReferenceService;
import com.aucontraire.gmailbuddy.service.GmailService;
import com.aucontraire.gmailbuddy.service.GoogleTokenValidator;
import com.aucontraire.gmailbuddy.service.SentMessageResult;
import com.aucontraire.gmailbuddy.validation.TestGmailBuddyPropertiesConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-slice test for {@code POST /api/v1/gmail/drafts/{draftId}/send}.
 *
 * <p>Focuses on the status-code contract (200 OK, NOT 201), the explicit
 * absence of a {@code Location} header (per Decision 3 — state transition),
 * and the 404 error path when the draft no longer exists.</p>
 *
 * <p>Uses {@code @WebMvcTest} (web layer only) with {@code @MockitoBean GmailService}
 * so the controller's dependency on the service is isolated. Mirrors the pattern
 * established by {@code CreateDraftControllerTest}.</p>
 */
@WebMvcTest(GmailController.class)
@Import(TestGmailBuddyPropertiesConfiguration.class)
@DisplayName("POST /api/v1/gmail/drafts/{draftId}/send — 200 success contract")
class SendDraftControllerTest {

    private static final String SEND_DRAFT_ENDPOINT = "/api/v1/gmail/drafts/{draftId}/send";

    private static final String DRAFT_ID   = "r-9876543210";
    private static final String MESSAGE_ID = "19a2b3c4d5e6f7g8";
    private static final String THREAD_ID  = "thread-19a2b3c4d5e6f7g8";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GmailService gmailService;

    @MockitoBean
    private OAuth2AuthorizedClientService authorizedClientService;

    @MockitoBean
    private GoogleTokenValidator tokenValidator;

    @MockitoBean
    private TokenReferenceService tokenReferenceService;

    @MockitoBean
    private ResponseMapper responseMapper;

    @MockitoBean
    private RateLimitService rateLimitService;

    @MockitoBean
    private GmailQuotaEstimator gmailQuotaEstimator;

    @MockitoBean
    private GmailMessageMapper gmailMessageMapper;

    // -------------------------------------------------------------------------
    // 200 OK — correct status code (NOT 201)
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postSendDraft_validDraftId_returns200NotCreated")
    void postSendDraft_validDraftId_returns200NotCreated() throws Exception {
        // Arrange
        SentMessageResult stubResult = new SentMessageResult(MESSAGE_ID, THREAD_ID);
        when(gmailService.sendDraft(eq("me"), eq(DRAFT_ID))).thenReturn(stubResult);

        // Act & Assert: must be 200 OK, not 201 Created — state transition, not creation.
        mockMvc.perform(post(SEND_DRAFT_ENDPOINT, DRAFT_ID).with(csrf()))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // 200 OK — Location header must be ABSENT
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postSendDraft_validDraftId_locationHeaderIsAbsent")
    void postSendDraft_validDraftId_locationHeaderIsAbsent() throws Exception {
        // Arrange
        SentMessageResult stubResult = new SentMessageResult(MESSAGE_ID, THREAD_ID);
        when(gmailService.sendDraft(eq("me"), eq(DRAFT_ID))).thenReturn(stubResult);

        // Act & Assert: per contracts/api-endpoints.md Endpoint 3 — no Location header
        // for a state transition (the draft is consumed; no new resource is created).
        mockMvc.perform(post(SEND_DRAFT_ENDPOINT, DRAFT_ID).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"));
    }

    // -------------------------------------------------------------------------
    // 200 OK — response body contract: {messageId, threadId, status: "SENT"}
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postSendDraft_validDraftId_returns200WithSendMessageResponseBody")
    void postSendDraft_validDraftId_returns200WithSendMessageResponseBody() throws Exception {
        // Arrange
        SentMessageResult stubResult = new SentMessageResult(MESSAGE_ID, THREAD_ID);
        when(gmailService.sendDraft(eq("me"), eq(DRAFT_ID))).thenReturn(stubResult);

        // Act & Assert: response body must contain messageId, threadId, and status="SENT"
        // per the api-endpoints.md Endpoint 3 success-response example.
        mockMvc.perform(post(SEND_DRAFT_ENDPOINT, DRAFT_ID).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageId").value(MESSAGE_ID))
                .andExpect(jsonPath("$.threadId").value(THREAD_ID))
                .andExpect(jsonPath("$.status").value("SENT"));
    }

    // -------------------------------------------------------------------------
    // 404 — draft not found / already sent or discarded
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postSendDraft_draftNotFound_returns404WithResourceNotFoundProblemType")
    void postSendDraft_draftNotFound_returns404WithResourceNotFoundProblemType() throws Exception {
        // Arrange: service throws ResourceNotFoundException when the draft no longer exists
        // (already sent, discarded, or invalid draftId).
        when(gmailService.sendDraft(eq("me"), eq(DRAFT_ID)))
                .thenThrow(new ResourceNotFoundException("Draft not found or already sent/discarded"));

        // Act & Assert: controller must surface 404 + RFC 7807 problem type.
        mockMvc.perform(post(SEND_DRAFT_ENDPOINT, DRAFT_ID).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("/problems/resource-not-found"));
    }
}
