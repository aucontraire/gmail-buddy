package com.aucontraire.gmailbuddy.controller;

import com.aucontraire.gmailbuddy.GmailBuddyApplication;
import com.aucontraire.gmailbuddy.config.TestTokenProviderConfiguration;
import com.aucontraire.gmailbuddy.dto.FilterCriteriaDTO;
import com.aucontraire.gmailbuddy.dto.SendMessageDTO;
import com.aucontraire.gmailbuddy.exception.GmailApiException;
import com.aucontraire.gmailbuddy.exception.OriginalMessageNotFoundException;
import com.aucontraire.gmailbuddy.exception.ResourceNotFoundException;
import com.aucontraire.gmailbuddy.repository.GmailRepository;
import com.aucontraire.gmailbuddy.service.DraftDetailResult;
import com.aucontraire.gmailbuddy.service.DraftListResult;
import com.aucontraire.gmailbuddy.service.GmailService;
import com.aucontraire.gmailbuddy.service.MessageListResult;
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
}
