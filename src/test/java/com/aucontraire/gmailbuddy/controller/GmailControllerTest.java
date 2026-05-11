package com.aucontraire.gmailbuddy.controller;

import com.aucontraire.gmailbuddy.GmailBuddyApplication;
import com.aucontraire.gmailbuddy.config.TestTokenProviderConfiguration;
import com.aucontraire.gmailbuddy.dto.FilterCriteriaDTO;
import com.aucontraire.gmailbuddy.dto.SendMessageDTO;
import com.aucontraire.gmailbuddy.exception.GmailApiException;
import com.aucontraire.gmailbuddy.exception.OriginalMessageNotFoundException;
import com.aucontraire.gmailbuddy.exception.ResourceNotFoundException;
import com.aucontraire.gmailbuddy.repository.GmailRepository;
import com.aucontraire.gmailbuddy.dto.response.ThreadSummary;
import com.aucontraire.gmailbuddy.service.AttachmentListResult;
import com.aucontraire.gmailbuddy.service.DraftDetailResult;
import com.aucontraire.gmailbuddy.service.DraftListResult;
import com.aucontraire.gmailbuddy.service.GmailService;
import com.aucontraire.gmailbuddy.service.MessageDetailResult;
import com.aucontraire.gmailbuddy.service.MessageListResult;
import com.aucontraire.gmailbuddy.service.ThreadDetailResult;
import com.aucontraire.gmailbuddy.service.ThreadListResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = GmailBuddyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestTokenProviderConfiguration.class)
public class GmailControllerTest {

    private static final Logger logger = LoggerFactory.getLogger(GmailControllerTest.class);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GmailService gmailService;

    @MockitoBean
    private GmailRepository gmailRepository;

    @BeforeEach
    public void setup() {
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token-value", null, null);
        OAuth2User principal = new DefaultOAuth2User(
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")),
                Collections.singletonMap("name", "testuser"),
                "name"
        );
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    public void testListMessagesByFilterCriteria() throws Exception {
        String jsonPayload = "{\n" +
                "  \"from\": \"jobalerts-noreply@linkedin.com\",\n" +
                "  \"to\": \"ocontreras.sf@gmail.com\",\n" +
                "  \"subject\": \"4 new jobs in United States\",\n" +
                "  \"hasAttachment\": false,\n" +
                "  \"query\": \"label:Inbox\",\n" +
                "  \"negatedQuery\": \"label:Spam\"\n" +
                "}";

        MessageListResult mockResult = new MessageListResult(Collections.emptyList(), null, 0);
        when(gmailService.listMessagesByFilterCriteriaWithPagination(eq("me"), any(FilterCriteriaDTO.class), any(), anyInt()))
                .thenReturn(mockResult);

        mockMvc.perform(post("/api/v1/gmail/messages/filter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.messages").isArray())
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    public void testListMessages() throws Exception {
        MessageListResult mockListResult = new MessageListResult(Collections.emptyList(), null, 0);
        when(gmailService.listMessagesWithPagination(eq("me"), any(), anyInt())).thenReturn(mockListResult);

        mockMvc.perform(get("/api/v1/gmail/messages")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.messages").isArray())
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    public void testGetMessageBody_ResourceNotFoundException() throws Exception {
        when(gmailService.getMessageBody("me", "12345"))
                .thenThrow(new ResourceNotFoundException("Message not found"));

        mockMvc.perform(get("/api/v1/gmail/messages/{messageId}/body", "12345")
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // T010 — GET /api/v1/gmail/drafts — list drafts
    // =========================================================================

    @Test
    public void listDrafts_200WithEmptyResults_returnsPaginatedShape() throws Exception {
        DraftListResult emptyResult = new DraftListResult(List.of(), null, 0);
        when(gmailService.listDrafts(eq("me"), any(), anyInt())).thenReturn(emptyResult);

        mockMvc.perform(get("/api/v1/gmail/drafts")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.results").isEmpty());
    }

    @Test
    public void listDrafts_200WithResults_returnsListItems() throws Exception {
        DraftDetailResult draft = new DraftDetailResult(
                "draft-1", "msg-1", null,
                List.of("to@example.com"), List.of(), List.of(),
                "Test Subject", "snippet", "body", "text", null, List.of()
        );
        DraftListResult listResult = new DraftListResult(List.of(draft), "next-token", 1);
        when(gmailService.listDrafts(eq("me"), any(), anyInt())).thenReturn(listResult);

        mockMvc.perform(get("/api/v1/gmail/drafts")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.results[0].id").value("draft-1"))
                .andExpect(jsonPath("$.nextPageToken").value("next-token"))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    public void listDrafts_400ForLimitExceedingMax() throws Exception {
        mockMvc.perform(get("/api/v1/gmail/drafts")
                        .param("limit", "51")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void listDrafts_400ForLimitZero() throws Exception {
        mockMvc.perform(get("/api/v1/gmail/drafts")
                        .param("limit", "0")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void listDrafts_401WhenUnauthenticated() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/gmail/drafts")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isFound());
    }

    @Test
    public void listDrafts_502WhenGmailApiExceptionThrown() throws Exception {
        when(gmailService.listDrafts(eq("me"), any(), anyInt()))
                .thenThrow(new GmailApiException("Gmail API down", new java.io.IOException("timeout")));

        mockMvc.perform(get("/api/v1/gmail/drafts")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isBadGateway());
    }

    // =========================================================================
    // T010 — GET /api/v1/gmail/drafts/{draftId} — get draft
    // =========================================================================

    @Test
    public void getDraft_200WithFullDraftDetailResponse() throws Exception {
        DraftDetailResult detail = new DraftDetailResult(
                "abc123", "msg-abc", "thread-1",
                List.of("to@example.com"), List.of(), List.of(),
                "Subject", "snippet", "<p>HTML body</p>", "html",
                "in-reply-to", List.of()
        );
        when(gmailService.getDraft(eq("me"), eq("abc123"))).thenReturn(detail);

        mockMvc.perform(get("/api/v1/gmail/drafts/{draftId}", "abc123")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("abc123"))
                .andExpect(jsonPath("$.subject").value("Subject"))
                .andExpect(jsonPath("$.bodyType").value("html"))
                .andExpect(jsonPath("$.threadId").value("thread-1"))
                .andExpect(jsonPath("$.inReplyToMessageId").value("in-reply-to"));
    }

    @Test
    public void getDraft_404ForUnknownDraftId() throws Exception {
        when(gmailService.getDraft(eq("me"), eq("deadbeef")))
                .thenThrow(new ResourceNotFoundException("Draft not found"));

        mockMvc.perform(get("/api/v1/gmail/drafts/{draftId}", "deadbeef")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    public void getDraft_400ForNonHexDraftId() throws Exception {
        mockMvc.perform(get("/api/v1/gmail/drafts/{draftId}", "invalid_id!!!")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void getDraft_401WhenUnauthenticated() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/gmail/drafts/{draftId}", "abc123")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isFound());
    }

    @Test
    public void getDraft_502WhenGmailApiException() throws Exception {
        when(gmailService.getDraft(eq("me"), eq("abc123")))
                .thenThrow(new GmailApiException("Gmail error", new java.io.IOException("io")));

        mockMvc.perform(get("/api/v1/gmail/drafts/{draftId}", "abc123")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isBadGateway());
    }

    // =========================================================================
    // T023 — DELETE /api/v1/gmail/drafts/{draftId} — delete draft
    // =========================================================================

    @Test
    public void deleteDraft_204NoContentOnSuccess() throws Exception {
        // doNothing is default for void mocks
        mockMvc.perform(delete("/api/v1/gmail/drafts/{draftId}", "abc123")
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    public void deleteDraft_404ForUnknownDraftId() throws Exception {
        doThrow(new ResourceNotFoundException("Draft not found"))
                .when(gmailService).deleteDraft(eq("me"), eq("deadbeef"));

        mockMvc.perform(delete("/api/v1/gmail/drafts/{draftId}", "deadbeef")
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    public void deleteDraft_400ForInvalidDraftId() throws Exception {
        mockMvc.perform(delete("/api/v1/gmail/drafts/{draftId}", "invalid!!!")
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void deleteDraft_401WhenUnauthenticated() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(delete("/api/v1/gmail/drafts/{draftId}", "abc123")
                        .with(csrf()))
                .andExpect(status().isFound());
    }

    @Test
    public void deleteDraft_502WhenGmailApiException() throws Exception {
        doThrow(new GmailApiException("Gmail error", new java.io.IOException("io")))
                .when(gmailService).deleteDraft(eq("me"), eq("abc123"));

        mockMvc.perform(delete("/api/v1/gmail/drafts/{draftId}", "abc123")
                        .with(csrf()))
                .andExpect(status().isBadGateway());
    }

    // =========================================================================
    // T033 — PUT /api/v1/gmail/drafts/{draftId} — update draft
    // =========================================================================

    @Test
    public void updateDraft_200WithUpdatedDraftDetailResponse() throws Exception {
        DraftDetailResult updated = new DraftDetailResult(
                "abc123", "msg-1", null,
                List.of("to@example.com"), List.of(), List.of(),
                "Updated Subject", "snippet", "Updated body", "text",
                null, List.of()
        );
        when(gmailService.updateDraft(eq("me"), eq("abc123"), any(SendMessageDTO.class)))
                .thenReturn(updated);

        String requestBody = """
                {
                  "to": ["to@example.com"],
                  "cc": [],
                  "bcc": [],
                  "subject": "Updated Subject",
                  "body": "Updated body",
                  "attachments": []
                }
                """;

        mockMvc.perform(put("/api/v1/gmail/drafts/{draftId}", "abc123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("abc123"))
                .andExpect(jsonPath("$.subject").value("Updated Subject"));
    }

    @Test
    public void updateDraft_404ForUnknownDraftId() throws Exception {
        when(gmailService.updateDraft(eq("me"), eq("deadbeef"), any(SendMessageDTO.class)))
                .thenThrow(new ResourceNotFoundException("Draft not found"));

        String requestBody = """
                {
                  "to": ["to@example.com"],
                  "cc": [],
                  "bcc": [],
                  "subject": "Subject",
                  "body": "Body",
                  "attachments": []
                }
                """;

        mockMvc.perform(put("/api/v1/gmail/drafts/{draftId}", "deadbeef")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    public void updateDraft_400ForInvalidDraftId() throws Exception {
        String requestBody = """
                {
                  "to": ["to@example.com"],
                  "cc": [],
                  "bcc": [],
                  "subject": "Subject",
                  "body": "Body",
                  "attachments": []
                }
                """;

        mockMvc.perform(put("/api/v1/gmail/drafts/{draftId}", "invalid!!!")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void updateDraft_422ForOriginalMessageNotFound() throws Exception {
        when(gmailService.updateDraft(eq("me"), eq("abc123"), any(SendMessageDTO.class)))
                .thenThrow(new OriginalMessageNotFoundException("inReplyTo target not found"));

        String requestBody = """
                {
                  "to": ["to@example.com"],
                  "cc": [],
                  "bcc": [],
                  "subject": "Re: Subject",
                  "body": "Reply body",
                  "attachments": [],
                  "inReplyToMessageId": "1a2b3c4d5e6f7890"
                }
                """;

        mockMvc.perform(put("/api/v1/gmail/drafts/{draftId}", "abc123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    public void updateDraft_401WhenUnauthenticated() throws Exception {
        SecurityContextHolder.clearContext();

        String requestBody = """
                {
                  "to": ["to@example.com"],
                  "cc": [],
                  "bcc": [],
                  "subject": "Subject",
                  "body": "Body",
                  "attachments": []
                }
                """;

        mockMvc.perform(put("/api/v1/gmail/drafts/{draftId}", "abc123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isFound());
    }

    @Test
    public void updateDraft_502WhenGmailApiException() throws Exception {
        when(gmailService.updateDraft(eq("me"), eq("abc123"), any(SendMessageDTO.class)))
                .thenThrow(new GmailApiException("Gmail API error", new java.io.IOException("io")));

        String requestBody = """
                {
                  "to": ["to@example.com"],
                  "cc": [],
                  "bcc": [],
                  "subject": "Subject",
                  "body": "Body",
                  "attachments": []
                }
                """;

        mockMvc.perform(put("/api/v1/gmail/drafts/{draftId}", "abc123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isBadGateway());
    }

    // ---------------------------------------------------------------------------
    // R-023 gap-fill: pre-existing path variables that previously had no @Pattern
    // validation now reject malformed IDs at the controller layer (HTTP 400)
    // before the request reaches the service. Uses "bad.id" — period is URL-safe
    // (passes through URI parsing) but rejected by both [0-9a-fA-F]{1,32} and
    // [A-Za-z0-9_-]{1,128}.
    // ---------------------------------------------------------------------------

    @Test
    public void deleteMessage_400ForMalformedMessageId() throws Exception {
        mockMvc.perform(delete("/api/v1/gmail/messages/{messageId}", "bad.id")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void getMessageBody_400ForMalformedMessageId() throws Exception {
        mockMvc.perform(get("/api/v1/gmail/messages/{messageId}/body", "bad.id")
                        .accept(MediaType.TEXT_HTML)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void markMessageAsRead_400ForMalformedMessageId() throws Exception {
        mockMvc.perform(put("/api/v1/gmail/messages/{messageId}/read", "bad.id")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void sendDraft_400ForMalformedDraftId() throws Exception {
        mockMvc.perform(post("/api/v1/gmail/drafts/{draftId}/send", "bad.id")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // T014 — GET /api/v1/gmail/threads — list threads
    // =========================================================================

    @Test
    public void listThreads_200WithEmptyResults_returnsPaginatedShape() throws Exception {
        ThreadListResult emptyResult = new ThreadListResult(List.of(), null, 0);
        when(gmailService.listThreads(eq("me"), any(FilterCriteriaDTO.class), any(), anyInt()))
                .thenReturn(emptyResult);

        mockMvc.perform(get("/api/v1/gmail/threads")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.results").isEmpty());
    }

    @Test
    public void listThreads_200WithResults_returnsThreadSummaries() throws Exception {
        ThreadSummary s1 = new ThreadSummary("thread-aaa", "Snippet A", "12345");
        ThreadSummary s2 = new ThreadSummary("thread-bbb", "Snippet B", null);
        ThreadListResult listResult = new ThreadListResult(List.of(s1, s2), "next-token", 2);
        when(gmailService.listThreads(eq("me"), any(FilterCriteriaDTO.class), any(), anyInt()))
                .thenReturn(listResult);

        mockMvc.perform(get("/api/v1/gmail/threads")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.results[0].id").value("thread-aaa"))
                .andExpect(jsonPath("$.results[0].snippet").value("Snippet A"))
                .andExpect(jsonPath("$.results[0].historyId").value("12345"))
                .andExpect(jsonPath("$.results[1].id").value("thread-bbb"))
                .andExpect(jsonPath("$.nextPageToken").value("next-token"))
                .andExpect(jsonPath("$.totalCount").value(2));
    }

    @Test
    public void listThreads_200WithPageTokenParam_honoredInResponse() throws Exception {
        ThreadSummary s = new ThreadSummary("thread-page2", "Page 2 snippet", null);
        ThreadListResult page2 = new ThreadListResult(List.of(s), "token-page-3", 1);
        when(gmailService.listThreads(eq("me"), any(FilterCriteriaDTO.class), eq("token-page-2"), anyInt()))
                .thenReturn(page2);

        mockMvc.perform(get("/api/v1/gmail/threads")
                        .param("pageToken", "token-page-2")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].id").value("thread-page2"))
                .andExpect(jsonPath("$.nextPageToken").value("token-page-3"));
    }

    @Test
    public void listThreads_200WithFilterParams_returnsResults() throws Exception {
        ThreadListResult result = new ThreadListResult(List.of(), null, 0);
        when(gmailService.listThreads(eq("me"), any(FilterCriteriaDTO.class), any(), anyInt()))
                .thenReturn(result);

        mockMvc.perform(get("/api/v1/gmail/threads")
                        .param("from", "sender@example.com")
                        .param("hasAttachment", "true")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isArray());
    }

    @Test
    public void listThreads_200WithRawQueryParam_passedThrough() throws Exception {
        ThreadListResult result = new ThreadListResult(List.of(), null, 0);
        when(gmailService.listThreads(eq("me"), any(FilterCriteriaDTO.class), any(), anyInt()))
                .thenReturn(result);

        mockMvc.perform(get("/api/v1/gmail/threads")
                        .param("query", "label:INBOX is:unread")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isArray());
    }

    @Test
    public void listThreads_400ForLimitZero() throws Exception {
        mockMvc.perform(get("/api/v1/gmail/threads")
                        .param("limit", "0")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void listThreads_400ForLimitExceedingMax() throws Exception {
        mockMvc.perform(get("/api/v1/gmail/threads")
                        .param("limit", "101")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void listThreads_400ForPageTokenTooLong() throws Exception {
        String oversizedToken = "a".repeat(256);
        mockMvc.perform(get("/api/v1/gmail/threads")
                        .param("pageToken", oversizedToken)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void listThreads_401WhenUnauthenticated() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/gmail/threads")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isFound());
    }

    @Test
    public void listThreads_502WhenGmailApiExceptionThrown() throws Exception {
        when(gmailService.listThreads(eq("me"), any(FilterCriteriaDTO.class), any(), anyInt()))
                .thenThrow(new GmailApiException("Gmail API down", new java.io.IOException("timeout")));

        mockMvc.perform(get("/api/v1/gmail/threads")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isBadGateway());
    }

    @Test
    public void listThreads_quotaHeaderIs10_flatRegardlessOfPageSize() throws Exception {
        ThreadSummary s1 = new ThreadSummary("t1", "s1", null);
        ThreadSummary s2 = new ThreadSummary("t2", "s2", null);
        ThreadSummary s3 = new ThreadSummary("t3", "s3", null);
        ThreadListResult result = new ThreadListResult(List.of(s1, s2, s3), null, 3);
        when(gmailService.listThreads(eq("me"), any(FilterCriteriaDTO.class), any(), anyInt()))
                .thenReturn(result);

        mockMvc.perform(get("/api/v1/gmail/threads")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Gmail-Quota-Used", "10"));
    }

    // =========================================================================
    // T014 — GET /api/v1/gmail/threads/{threadId} — get thread detail
    // =========================================================================

    @Test
    public void getThread_200WithFullThreadDetailResponse() throws Exception {
        MessageDetailResult msg = new MessageDetailResult(
                "msg-1", "thread-abc123",
                Map.of("From", "sender@example.com", "Subject", "Hello"),
                "snippet text", "<p>body</p>", "html",
                List.of("INBOX", "CATEGORY_PERSONAL"), List.of()
        );
        ThreadDetailResult detail = new ThreadDetailResult(
                "thread-abc123", List.of("INBOX", "CATEGORY_PERSONAL"), List.of(msg)
        );
        when(gmailService.getThread(eq("me"), eq("1a2b3c4d5e6f7890")))
                .thenReturn(detail);

        mockMvc.perform(get("/api/v1/gmail/threads/{threadId}", "1a2b3c4d5e6f7890")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.threadId").value("thread-abc123"))
                .andExpect(jsonPath("$.labelIds").isArray())
                .andExpect(jsonPath("$.labelIds[0]").value("INBOX"))
                .andExpect(jsonPath("$.messages").isArray())
                .andExpect(jsonPath("$.messages[0].id").value("msg-1"))
                .andExpect(jsonPath("$.messages[0].headers.From").value("sender@example.com"));
    }

    @Test
    public void getThread_200WithSingleMessageThread_returnsOneMessage() throws Exception {
        MessageDetailResult msg = new MessageDetailResult(
                "msg-only", "thread-single",
                Map.of(), "only message snippet", "body text", "text",
                List.of("INBOX"), List.of()
        );
        ThreadDetailResult singleMsg = new ThreadDetailResult(
                "thread-single", List.of("INBOX"), List.of(msg)
        );
        when(gmailService.getThread(eq("me"), eq("1a2b3c4d5e6f7891")))
                .thenReturn(singleMsg);

        mockMvc.perform(get("/api/v1/gmail/threads/{threadId}", "1a2b3c4d5e6f7891")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages").isArray())
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.messages[0].id").value("msg-only"));
    }

    @Test
    public void getThread_200WithMultipleMessages_preservesChronologicalOrder() throws Exception {
        MessageDetailResult msg1 = new MessageDetailResult(
                "msg-first", "thread-multi",
                Map.of(), "first", "body1", "text", List.of("INBOX"), List.of()
        );
        MessageDetailResult msg2 = new MessageDetailResult(
                "msg-second", "thread-multi",
                Map.of(), "second", "body2", "text", List.of("INBOX"), List.of()
        );
        MessageDetailResult msg3 = new MessageDetailResult(
                "msg-third", "thread-multi",
                Map.of(), "third", "body3", "text", List.of("INBOX", "UNREAD"), List.of()
        );
        ThreadDetailResult multiMsg = new ThreadDetailResult(
                "thread-multi", List.of("INBOX", "UNREAD"), List.of(msg1, msg2, msg3)
        );
        when(gmailService.getThread(eq("me"), eq("1a2b3c4d5e6f7892")))
                .thenReturn(multiMsg);

        mockMvc.perform(get("/api/v1/gmail/threads/{threadId}", "1a2b3c4d5e6f7892")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(3))
                .andExpect(jsonPath("$.messages[0].id").value("msg-first"))
                .andExpect(jsonPath("$.messages[1].id").value("msg-second"))
                .andExpect(jsonPath("$.messages[2].id").value("msg-third"));
    }

    @Test
    public void getThread_200WithMixedLabelMessages_returnsLabelUnion() throws Exception {
        MessageDetailResult msg1 = new MessageDetailResult(
                "msg-a", "thread-labels",
                Map.of(), "s1", "b1", "text", List.of("INBOX"), List.of()
        );
        MessageDetailResult msg2 = new MessageDetailResult(
                "msg-b", "thread-labels",
                Map.of(), "s2", "b2", "text", List.of("INBOX", "UNREAD", "Label_42"), List.of()
        );
        ThreadDetailResult mixedLabels = new ThreadDetailResult(
                "thread-labels", List.of("INBOX", "UNREAD", "Label_42"), List.of(msg1, msg2)
        );
        when(gmailService.getThread(eq("me"), eq("1a2b3c4d5e6f7893")))
                .thenReturn(mixedLabels);

        mockMvc.perform(get("/api/v1/gmail/threads/{threadId}", "1a2b3c4d5e6f7893")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labelIds.length()").value(3))
                .andExpect(jsonPath("$.labelIds[2]").value("Label_42"));
    }

    @Test
    public void getThread_404ForUnknownThreadId() throws Exception {
        when(gmailService.getThread(eq("me"), eq("1a2b3c4d5e6f7894")))
                .thenThrow(new ResourceNotFoundException("Thread not found"));

        mockMvc.perform(get("/api/v1/gmail/threads/{threadId}", "1a2b3c4d5e6f7894")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    public void getThread_400ForNonHexThreadId() throws Exception {
        mockMvc.perform(get("/api/v1/gmail/threads/{threadId}", "bad.id")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void getThread_400ForOversizeThreadId() throws Exception {
        // @GmailMessageId pattern: [0-9a-fA-F]{1,32} — 33 hex chars rejected
        String oversizeId = "a".repeat(33);
        mockMvc.perform(get("/api/v1/gmail/threads/{threadId}", oversizeId)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void getThread_401WhenUnauthenticated() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/gmail/threads/{threadId}", "1a2b3c4d5e6f7890")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isFound());
    }

    @Test
    public void getThread_502WhenGmailApiException() throws Exception {
        when(gmailService.getThread(eq("me"), eq("1a2b3c4d5e6f7890")))
                .thenThrow(new GmailApiException("Gmail error", new java.io.IOException("io")));

        mockMvc.perform(get("/api/v1/gmail/threads/{threadId}", "1a2b3c4d5e6f7890")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isBadGateway());
    }

    @Test
    public void getThread_quotaHeaderIs10() throws Exception {
        ThreadDetailResult detail = new ThreadDetailResult("thread-q", List.of(), List.of());
        when(gmailService.getThread(eq("me"), eq("1a2b3c4d5e6f7890")))
                .thenReturn(detail);

        mockMvc.perform(get("/api/v1/gmail/threads/{threadId}", "1a2b3c4d5e6f7890")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Gmail-Quota-Used", "10"));
    }

    // =========================================================================
    // T030 — Phase 4 US2: GET /api/v1/gmail/messages/{messageId} — get message detail
    // =========================================================================

    @Test
    public void getMessageDetail_200WithFullFormatReturnsCompleteResponse() throws Exception {
        com.aucontraire.gmailbuddy.dto.response.MessageAttachmentMetadata att =
                new com.aucontraire.gmailbuddy.dto.response.MessageAttachmentMetadata(
                        "att-id-1", "report.pdf", "application/pdf", 12345L);
        MessageDetailResult detail = new MessageDetailResult(
                "1a2b3c4d5e6f7890", "thread-abc123",
                Map.of("From", "sender@example.com", "Subject", "Hello",
                        "To", "me@example.com", "Date", "Mon, 1 Jan 2026 00:00:00 +0000"),
                "preview snippet text",
                "<p>body content here</p>",
                "html",
                List.of("INBOX", "CATEGORY_PERSONAL"),
                List.of(att)
        );
        when(gmailService.getMessageDetail(eq("me"), eq("1a2b3c4d5e6f7890"), eq("full")))
                .thenReturn(detail);

        mockMvc.perform(get("/api/v1/gmail/messages/{messageId}", "1a2b3c4d5e6f7890")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1a2b3c4d5e6f7890"))
                .andExpect(jsonPath("$.threadId").value("thread-abc123"))
                .andExpect(jsonPath("$.headers.From").value("sender@example.com"))
                .andExpect(jsonPath("$.headers.Subject").value("Hello"))
                .andExpect(jsonPath("$.snippet").value("preview snippet text"))
                .andExpect(jsonPath("$.body").value("<p>body content here</p>"))
                .andExpect(jsonPath("$.bodyType").value("html"))
                .andExpect(jsonPath("$.labelIds").isArray())
                .andExpect(jsonPath("$.labelIds[0]").value("INBOX"))
                .andExpect(jsonPath("$.attachments").isArray())
                .andExpect(jsonPath("$.attachments[0].attachmentId").value("att-id-1"))
                .andExpect(jsonPath("$.attachments[0].filename").value("report.pdf"))
                .andExpect(jsonPath("$.attachments[0].mimeType").value("application/pdf"))
                .andExpect(jsonPath("$.attachments[0].sizeBytes").value(12345));
    }

    @Test
    public void getMessageDetail_200WithMetadataFormat_bodyIsNull_quotaIs5() throws Exception {
        MessageDetailResult detail = new MessageDetailResult(
                "1a2b3c4d5e6f7890", "thread-meta",
                Map.of("From", "sender@example.com", "Subject", "Hi"),
                "snippet", null, null,
                List.of("INBOX"), List.of()
        );
        when(gmailService.getMessageDetail(eq("me"), eq("1a2b3c4d5e6f7890"), eq("metadata")))
                .thenReturn(detail);

        mockMvc.perform(get("/api/v1/gmail/messages/{messageId}", "1a2b3c4d5e6f7890")
                        .param("format", "metadata")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1a2b3c4d5e6f7890"))
                .andExpect(jsonPath("$.body").doesNotExist())
                .andExpect(jsonPath("$.headers.From").value("sender@example.com"))
                .andExpect(header().string("X-Gmail-Quota-Used", "5"));
    }

    @Test
    public void getMessageDetail_200WithFormatFull_caseInsensitive_Full() throws Exception {
        MessageDetailResult detail = new MessageDetailResult(
                "1a2b3c4d5e6f7890", "thread-ci",
                Map.of("Subject", "Case test"),
                "snippet", "body text", "text",
                List.of("INBOX"), List.of()
        );
        // Controller normalizes "Full" → "full" before service call
        when(gmailService.getMessageDetail(eq("me"), eq("1a2b3c4d5e6f7890"), eq("full")))
                .thenReturn(detail);

        mockMvc.perform(get("/api/v1/gmail/messages/{messageId}", "1a2b3c4d5e6f7890")
                        .param("format", "Full")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1a2b3c4d5e6f7890"))
                .andExpect(header().string("X-Gmail-Quota-Used", "10"));
    }

    @Test
    public void getMessageDetail_200WithFormatFull_caseInsensitive_FULL() throws Exception {
        MessageDetailResult detail = new MessageDetailResult(
                "1a2b3c4d5e6f7890", "thread-ci2",
                Map.of("Subject", "FULL test"),
                "snippet2", "body2", "text",
                List.of("INBOX"), List.of()
        );
        when(gmailService.getMessageDetail(eq("me"), eq("1a2b3c4d5e6f7890"), eq("full")))
                .thenReturn(detail);

        mockMvc.perform(get("/api/v1/gmail/messages/{messageId}", "1a2b3c4d5e6f7890")
                        .param("format", "FULL")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1a2b3c4d5e6f7890"))
                .andExpect(header().string("X-Gmail-Quota-Used", "10"));
    }

    @Test
    public void getMessageDetail_400ForFormatRaw_doesNotEchoValue() throws Exception {
        // FR-014: format=raw must return 400
        // FR-035a: the 'detail' field value specifically must NOT echo the literal "raw" input
        mockMvc.perform(get("/api/v1/gmail/messages/{messageId}", "1a2b3c4d5e6f7890")
                        .param("format", "raw")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    com.fasterxml.jackson.databind.JsonNode root =
                            new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
                    com.fasterxml.jackson.databind.JsonNode detailNode = root.get("detail");
                    if (detailNode != null) {
                        String detailValue = detailNode.asText();
                        org.junit.jupiter.api.Assertions.assertFalse(
                                detailValue.equalsIgnoreCase("raw"),
                                "detail field must not echo the literal 'raw' user-provided value (FR-035a), but was: " + detailValue);
                    }
                });
    }

    @Test
    public void getMessageDetail_400ForFormatMinimal() throws Exception {
        mockMvc.perform(get("/api/v1/gmail/messages/{messageId}", "1a2b3c4d5e6f7890")
                        .param("format", "minimal")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void getMessageDetail_400ForNonHexMessageId_doesNotEchoValue() throws Exception {
        // @GmailMessageId validates format; FR-035a: the 'detail' field specifically must NOT
        // echo the malformed messageId back. The 'instance' field may contain the URL path.
        mockMvc.perform(get("/api/v1/gmail/messages/{messageId}", "bad.id")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                // detail field value must not contain the literal malformed input
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    com.fasterxml.jackson.databind.JsonNode root =
                            new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
                    com.fasterxml.jackson.databind.JsonNode detailNode = root.get("detail");
                    if (detailNode != null) {
                        String detailValue = detailNode.asText();
                        org.junit.jupiter.api.Assertions.assertFalse(
                                detailValue.contains("bad.id"),
                                "detail field must not echo the malformed messageId 'bad.id' (FR-035a), but was: " + detailValue);
                    }
                });
    }

    @Test
    public void getMessageDetail_400ForOversizeMessageId() throws Exception {
        // @GmailMessageId rejects > 32 hex chars
        String oversizeId = "a".repeat(33);
        mockMvc.perform(get("/api/v1/gmail/messages/{messageId}", oversizeId)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void getMessageDetail_404ForUnknownMessageId() throws Exception {
        when(gmailService.getMessageDetail(eq("me"), eq("1a2b3c4d5e6f7890"), eq("full")))
                .thenThrow(new ResourceNotFoundException("Message not found"));

        mockMvc.perform(get("/api/v1/gmail/messages/{messageId}", "1a2b3c4d5e6f7890")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    public void getMessageDetail_401WhenUnauthenticated() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/gmail/messages/{messageId}", "1a2b3c4d5e6f7890")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isFound());
    }

    @Test
    public void getMessageDetail_502WhenGmailApiException() throws Exception {
        when(gmailService.getMessageDetail(eq("me"), eq("1a2b3c4d5e6f7890"), eq("full")))
                .thenThrow(new GmailApiException("Gmail error", new java.io.IOException("io")));

        mockMvc.perform(get("/api/v1/gmail/messages/{messageId}", "1a2b3c4d5e6f7890")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isBadGateway());
    }

    @Test
    public void getMessageDetail_quotaHeaderIs10ForFormatFull() throws Exception {
        MessageDetailResult detail = new MessageDetailResult(
                "1a2b3c4d5e6f7890", "thread-quota",
                Map.of(), "snippet", "body", "text",
                List.of(), List.of()
        );
        when(gmailService.getMessageDetail(eq("me"), eq("1a2b3c4d5e6f7890"), eq("full")))
                .thenReturn(detail);

        mockMvc.perform(get("/api/v1/gmail/messages/{messageId}", "1a2b3c4d5e6f7890")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Gmail-Quota-Used", "10"));
    }

    @Test
    public void getMessageDetail_quotaHeaderIs5ForFormatMetadata() throws Exception {
        MessageDetailResult detail = new MessageDetailResult(
                "1a2b3c4d5e6f7890", "thread-quota-meta",
                Map.of(), "snippet", null, null,
                List.of(), List.of()
        );
        when(gmailService.getMessageDetail(eq("me"), eq("1a2b3c4d5e6f7890"), eq("metadata")))
                .thenReturn(detail);

        mockMvc.perform(get("/api/v1/gmail/messages/{messageId}", "1a2b3c4d5e6f7890")
                        .param("format", "metadata")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Gmail-Quota-Used", "5"));
    }

    // =========================================================================
    // T040 — Phase 5 US3: GET /api/v1/gmail/labels and GET /api/v1/gmail/labels/{id}
    // =========================================================================

    @Test
    public void listLabels_200WithSystemAndUserLabels() throws Exception {
        com.aucontraire.gmailbuddy.service.LabelListResult listResult =
                new com.aucontraire.gmailbuddy.service.LabelListResult(
                        List.of(
                                new com.aucontraire.gmailbuddy.dto.response.LabelSummary(
                                        "INBOX", "INBOX", "system", "show", "labelShow"),
                                new com.aucontraire.gmailbuddy.dto.response.LabelSummary(
                                        "Label_42", "Recruiters", "user", "show", "labelShow")
                        ),
                        2
                );
        when(gmailService.listLabels(eq("me"))).thenReturn(listResult);

        mockMvc.perform(get("/api/v1/gmail/labels")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.results[0].id").value("INBOX"))
                .andExpect(jsonPath("$.results[0].type").value("system"))
                .andExpect(jsonPath("$.results[1].id").value("Label_42"))
                .andExpect(jsonPath("$.results[1].type").value("user"));
    }

    @Test
    public void listLabels_200WithEmptyUserLabels_returnsOnlySystemLabels() throws Exception {
        com.aucontraire.gmailbuddy.service.LabelListResult listResult =
                new com.aucontraire.gmailbuddy.service.LabelListResult(
                        List.of(
                                new com.aucontraire.gmailbuddy.dto.response.LabelSummary(
                                        "INBOX", "INBOX", "system", "show", "labelShow")
                        ),
                        1
                );
        when(gmailService.listLabels(eq("me"))).thenReturn(listResult);

        mockMvc.perform(get("/api/v1/gmail/labels")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.results[0].id").value("INBOX"));
    }

    @Test
    public void listLabels_quotaHeaderIs1() throws Exception {
        com.aucontraire.gmailbuddy.service.LabelListResult listResult =
                new com.aucontraire.gmailbuddy.service.LabelListResult(List.of(), 0);
        when(gmailService.listLabels(eq("me"))).thenReturn(listResult);

        mockMvc.perform(get("/api/v1/gmail/labels")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Gmail-Quota-Used", "1"));
    }

    @Test
    public void listLabels_401WhenUnauthenticated() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/gmail/labels")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isFound());
    }

    @Test
    public void listLabels_502WhenGmailApiException() throws Exception {
        when(gmailService.listLabels(eq("me")))
                .thenThrow(new GmailApiException("Gmail error", new java.io.IOException("io")));

        mockMvc.perform(get("/api/v1/gmail/labels")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isBadGateway());
    }

    @Test
    public void getLabel_200WithColorSet() throws Exception {
        com.aucontraire.gmailbuddy.service.LabelDetailResult detailResult =
                new com.aucontraire.gmailbuddy.service.LabelDetailResult(
                        "Label_42", "Recruiters", "user",
                        "show", "labelShow",
                        "#222222", "#16a766",
                        42, 5, 38, 4
                );
        when(gmailService.getLabel(eq("me"), eq("Label_42"))).thenReturn(detailResult);

        mockMvc.perform(get("/api/v1/gmail/labels/{labelId}", "Label_42")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("Label_42"))
                .andExpect(jsonPath("$.name").value("Recruiters"))
                .andExpect(jsonPath("$.type").value("user"))
                .andExpect(jsonPath("$.color.textColor").value("#222222"))
                .andExpect(jsonPath("$.color.backgroundColor").value("#16a766"))
                .andExpect(jsonPath("$.messagesTotal").value(42))
                .andExpect(jsonPath("$.messagesUnread").value(5))
                .andExpect(jsonPath("$.threadsTotal").value(38))
                .andExpect(jsonPath("$.threadsUnread").value(4));
    }

    @Test
    public void getLabel_200SystemLabelWithoutColor() throws Exception {
        com.aucontraire.gmailbuddy.service.LabelDetailResult detailResult =
                new com.aucontraire.gmailbuddy.service.LabelDetailResult(
                        "INBOX", "INBOX", "system",
                        "show", "labelShow",
                        null, null,
                        100, 10, 80, 8
                );
        when(gmailService.getLabel(eq("me"), eq("INBOX"))).thenReturn(detailResult);

        mockMvc.perform(get("/api/v1/gmail/labels/{labelId}", "INBOX")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("INBOX"))
                .andExpect(jsonPath("$.type").value("system"))
                .andExpect(jsonPath("$.color").doesNotExist());
    }

    @Test
    public void getLabel_200WithFullCounts() throws Exception {
        com.aucontraire.gmailbuddy.service.LabelDetailResult detailResult =
                new com.aucontraire.gmailbuddy.service.LabelDetailResult(
                        "SENT", "SENT", "system",
                        "hide", "labelHide",
                        null, null,
                        500, 0, 400, 0
                );
        when(gmailService.getLabel(eq("me"), eq("SENT"))).thenReturn(detailResult);

        mockMvc.perform(get("/api/v1/gmail/labels/{labelId}", "SENT")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messagesTotal").value(500))
                .andExpect(jsonPath("$.messagesUnread").value(0))
                .andExpect(jsonPath("$.threadsTotal").value(400))
                .andExpect(jsonPath("$.threadsUnread").value(0));
    }

    @Test
    public void getLabel_400ForIllFormedLabelIdWithHyphen() throws Exception {
        // "label-with-hyphen" is rejected by @GmailLabelId (hyphens not in [A-Za-z0-9_]{1,128})
        mockMvc.perform(get("/api/v1/gmail/labels/{labelId}", "label-with-hyphen")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void getLabel_400ForLabelIdWithSlash() throws Exception {
        // Slash in path would not reach the controller but test URL encoding rejection
        // Use a double-encoded slash that reaches the handler
        mockMvc.perform(get("/api/v1/gmail/labels/label%2Ffoo")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void getLabel_404ForUnknownLabelId() throws Exception {
        when(gmailService.getLabel(eq("me"), eq("Label_9999")))
                .thenThrow(new ResourceNotFoundException("Label not found: Label_9999"));

        mockMvc.perform(get("/api/v1/gmail/labels/{labelId}", "Label_9999")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    public void getLabel_401WhenUnauthenticated() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/gmail/labels/{labelId}", "INBOX")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isFound());
    }

    @Test
    public void getLabel_502WhenGmailApiException() throws Exception {
        when(gmailService.getLabel(eq("me"), eq("INBOX")))
                .thenThrow(new GmailApiException("Gmail error", new java.io.IOException("io")));

        mockMvc.perform(get("/api/v1/gmail/labels/{labelId}", "INBOX")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isBadGateway());
    }

    @Test
    public void getLabel_quotaHeaderIs1() throws Exception {
        com.aucontraire.gmailbuddy.service.LabelDetailResult detailResult =
                new com.aucontraire.gmailbuddy.service.LabelDetailResult(
                        "INBOX", "INBOX", "system",
                        "show", "labelShow",
                        null, null,
                        0, 0, 0, 0
                );
        when(gmailService.getLabel(eq("me"), eq("INBOX"))).thenReturn(detailResult);

        mockMvc.perform(get("/api/v1/gmail/labels/{labelId}", "INBOX")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Gmail-Quota-Used", "1"));
    }

    // =========================================================================
    // T057 — Phase 6 US4: GET /messages/{id}/attachments and
    //                      GET /messages/{id}/attachments/{attachmentId}
    // =========================================================================

    @Test
    public void listAttachments_200WithNItems() throws Exception {
        com.aucontraire.gmailbuddy.dto.response.MessageAttachmentMetadata att1 =
                new com.aucontraire.gmailbuddy.dto.response.MessageAttachmentMetadata(
                        "att-id-1", "report.pdf", "application/pdf", 12345L);
        com.aucontraire.gmailbuddy.dto.response.MessageAttachmentMetadata att2 =
                new com.aucontraire.gmailbuddy.dto.response.MessageAttachmentMetadata(
                        "att-id-2", "photo.jpg", "image/jpeg", 67890L);
        AttachmentListResult result = new AttachmentListResult(List.of(att1, att2));
        when(gmailService.listAttachments(eq("me"), eq("1a2b3c4d5e6f7890"))).thenReturn(result);

        mockMvc.perform(get("/api/v1/gmail/messages/{messageId}/attachments", "1a2b3c4d5e6f7890")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.results[0].attachmentId").value("att-id-1"))
                .andExpect(jsonPath("$.results[0].filename").value("report.pdf"))
                .andExpect(jsonPath("$.results[0].mimeType").value("application/pdf"))
                .andExpect(jsonPath("$.results[0].sizeBytes").value(12345))
                .andExpect(jsonPath("$.results[1].attachmentId").value("att-id-2"));
    }

    @Test
    public void listAttachments_200WithEmptyListWhenNoAttachments() throws Exception {
        AttachmentListResult result = new AttachmentListResult(List.of());
        when(gmailService.listAttachments(eq("me"), eq("1a2b3c4d5e6f7890"))).thenReturn(result);

        mockMvc.perform(get("/api/v1/gmail/messages/{messageId}/attachments", "1a2b3c4d5e6f7890")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.results.length()").value(0))
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    public void listAttachments_404ForUnknownMessageId() throws Exception {
        when(gmailService.listAttachments(eq("me"), eq("1a2b3c4d5e6f7890")))
                .thenThrow(new ResourceNotFoundException("Message not found: 1a2b3c4d5e6f7890"));

        mockMvc.perform(get("/api/v1/gmail/messages/{messageId}/attachments", "1a2b3c4d5e6f7890")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    public void listAttachments_400ForNonHexMessageId() throws Exception {
        mockMvc.perform(get("/api/v1/gmail/messages/{messageId}/attachments", "not-a-valid-id!")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void listAttachments_quotaHeaderIs5() throws Exception {
        AttachmentListResult result = new AttachmentListResult(List.of());
        when(gmailService.listAttachments(eq("me"), eq("1a2b3c4d5e6f7890"))).thenReturn(result);

        mockMvc.perform(get("/api/v1/gmail/messages/{messageId}/attachments", "1a2b3c4d5e6f7890")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Gmail-Quota-Used", "5"));
    }

    @Test
    public void getAttachment_200BinaryDownloadWithCorrectHeaders() throws Exception {
        byte[] attachmentBytes = "PDF content bytes".getBytes();
        StreamingResponseBody stream = outputStream -> outputStream.write(attachmentBytes);
        when(gmailService.getAttachment(eq("me"), eq("1a2b3c4d5e6f7890"), eq("ANGjdJ8BwFpn3nQ0oFQ7wPjVLfRx")))
                .thenReturn(stream);

        mockMvc.perform(get("/api/v1/gmail/messages/{messageId}/attachments/{attachmentId}",
                        "1a2b3c4d5e6f7890", "ANGjdJ8BwFpn3nQ0oFQ7wPjVLfRx")
                        .param("filename", "report.pdf")
                        .param("mimeType", "application/pdf")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"report.pdf\""))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().contentType("application/pdf"));
    }

    @Test
    public void getAttachment_200WithDefaultsWhenQueryParamsAbsent() throws Exception {
        byte[] attachmentBytes = "binary data".getBytes();
        StreamingResponseBody stream = outputStream -> outputStream.write(attachmentBytes);
        when(gmailService.getAttachment(eq("me"), eq("1a2b3c4d5e6f7890"), eq("ANGjdJ8BwFpn3nQ0oFQ7wPjVLfRx")))
                .thenReturn(stream);

        mockMvc.perform(get("/api/v1/gmail/messages/{messageId}/attachments/{attachmentId}",
                        "1a2b3c4d5e6f7890", "ANGjdJ8BwFpn3nQ0oFQ7wPjVLfRx")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"attachment\""))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().contentType("application/octet-stream"));
    }

    @Test
    public void getAttachment_404ForUnknownMessageOrAttachment() throws Exception {
        when(gmailService.getAttachment(eq("me"), eq("1a2b3c4d5e6f7890"), eq("ANGjdJ8BwFpn3nQ0oFQ7wPjVLfRx")))
                .thenThrow(new ResourceNotFoundException("Attachment not found"));

        mockMvc.perform(get("/api/v1/gmail/messages/{messageId}/attachments/{attachmentId}",
                        "1a2b3c4d5e6f7890", "ANGjdJ8BwFpn3nQ0oFQ7wPjVLfRx")
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    public void getAttachment_400ForInvalidMessageId() throws Exception {
        mockMvc.perform(get("/api/v1/gmail/messages/{messageId}/attachments/{attachmentId}",
                        "not-valid!", "ANGjdJ8BwFpn3nQ0oFQ7wPjVLfRx")
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void getAttachment_400ForFilenameWithLineFeed() throws Exception {
        byte[] bytes = "data".getBytes();
        StreamingResponseBody stream = outputStream -> outputStream.write(bytes);
        when(gmailService.getAttachment(eq("me"), eq("1a2b3c4d5e6f7890"), eq("ANGjdJ8BwFpn3nQ0oFQ7wPjVLfRx")))
                .thenReturn(stream);

        // LF in filename triggers FR-026a sanitization → HTTP 400
        mockMvc.perform(get("/api/v1/gmail/messages/{messageId}/attachments/{attachmentId}",
                        "1a2b3c4d5e6f7890", "ANGjdJ8BwFpn3nQ0oFQ7wPjVLfRx")
                        .param("filename", "evil\nContent-Type: text/html")
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void getAttachment_400ForFilenameWithCarriageReturn() throws Exception {
        byte[] bytes = "data".getBytes();
        StreamingResponseBody stream = outputStream -> outputStream.write(bytes);
        when(gmailService.getAttachment(eq("me"), eq("1a2b3c4d5e6f7890"), eq("ANGjdJ8BwFpn3nQ0oFQ7wPjVLfRx")))
                .thenReturn(stream);

        // CR in filename triggers FR-026a sanitization → HTTP 400
        mockMvc.perform(get("/api/v1/gmail/messages/{messageId}/attachments/{attachmentId}",
                        "1a2b3c4d5e6f7890", "ANGjdJ8BwFpn3nQ0oFQ7wPjVLfRx")
                        .param("filename", "evil\rfile.pdf")
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void getAttachment_400ForMalformedMimeType() throws Exception {
        byte[] bytes = "data".getBytes();
        StreamingResponseBody stream = outputStream -> outputStream.write(bytes);
        when(gmailService.getAttachment(eq("me"), eq("1a2b3c4d5e6f7890"), eq("ANGjdJ8BwFpn3nQ0oFQ7wPjVLfRx")))
                .thenReturn(stream);

        // mimeType with whitespace inside the subtype is not a valid RFC 9110 media type.
        // (Real-world trigger: caller passes `image/svg+xml` un-encoded; the `+` decodes
        // to a space in the query string → arrives here as `image/svg xml`.)
        // MediaType.parseMediaType throws InvalidMediaTypeException; controller surfaces
        // it as HTTP 400 instead of letting it bubble to the catch-all 500 handler.
        mockMvc.perform(get("/api/v1/gmail/messages/{messageId}/attachments/{attachmentId}",
                        "1a2b3c4d5e6f7890", "ANGjdJ8BwFpn3nQ0oFQ7wPjVLfRx")
                        .param("mimeType", "image/svg xml")
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void getAttachment_quotaHeaderIs5() throws Exception {
        byte[] bytes = "data".getBytes();
        StreamingResponseBody stream = outputStream -> outputStream.write(bytes);
        when(gmailService.getAttachment(eq("me"), eq("1a2b3c4d5e6f7890"), eq("ANGjdJ8BwFpn3nQ0oFQ7wPjVLfRx")))
                .thenReturn(stream);

        mockMvc.perform(get("/api/v1/gmail/messages/{messageId}/attachments/{attachmentId}",
                        "1a2b3c4d5e6f7890", "ANGjdJ8BwFpn3nQ0oFQ7wPjVLfRx")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Gmail-Quota-Used", "5"));
    }
}
