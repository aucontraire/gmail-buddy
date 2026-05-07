package com.aucontraire.gmailbuddy.controller;

import com.aucontraire.gmailbuddy.dto.SendMessageDTO;
import com.aucontraire.gmailbuddy.exception.InvalidRecipientException;
import com.aucontraire.gmailbuddy.fixture.SendMessageRequestFixtures;
import com.aucontraire.gmailbuddy.mapper.ResponseMapper;
import com.aucontraire.gmailbuddy.ratelimit.GmailQuotaEstimator;
import com.aucontraire.gmailbuddy.ratelimit.RateLimitService;
import com.aucontraire.gmailbuddy.security.TokenReferenceService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-slice test for {@code POST /api/v1/gmail/messages} — direct send.
 *
 * <p>Focuses on the critical contract differences from {@code CreateDraftControllerTest}:</p>
 * <ul>
 *   <li>HTTP 201 Created (same as drafts, but with a different Location)</li>
 *   <li>Location header IS present and ends with {@code /body} per Decision 3 and
 *       contracts/api-endpoints.md Endpoint 1</li>
 *   <li>Response body: {@code {messageId, threadId, status: "SENT"}}</li>
 *   <li>Recipient rejection (Gmail rejects recipient) → service throws
 *       {@link InvalidRecipientException} → 422 with problem type
 *       {@code /problems/invalid-recipient}</li>
 * </ul>
 *
 * <p>Uses {@code @WebMvcTest} (web layer only) with {@code @MockitoBean GmailService}.
 * Mirrors the pattern established by {@code CreateDraftControllerTest}.</p>
 */
@WebMvcTest(GmailController.class)
@Import(TestGmailBuddyPropertiesConfiguration.class)
@DisplayName("POST /api/v1/gmail/messages — 201 direct-send success contract")
class SendMessageControllerTest {

    private static final String MESSAGES_ENDPOINT = "/api/v1/gmail/messages";

    private static final String MESSAGE_ID = "19a2b3c4d5e6f7g8";
    private static final String THREAD_ID  = "thread-19a2b3c4d5e6f7g8";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    // -------------------------------------------------------------------------
    // 201 Created — correct status code
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postSendMessage_validRequest_returns201Created")
    void postSendMessage_validRequest_returns201Created() throws Exception {
        // Arrange
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
        String requestBody = objectMapper.writeValueAsString(dto);

        SentMessageResult stubResult = new SentMessageResult(MESSAGE_ID, THREAD_ID);
        when(gmailService.sendMessage(eq("me"), any(SendMessageDTO.class)))
                .thenReturn(stubResult);

        // Act & Assert: must be 201 Created (new message resource created per Decision 3).
        mockMvc.perform(post(MESSAGES_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());
    }

    // -------------------------------------------------------------------------
    // 201 Created — Location header IS present and ends with /body
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postSendMessage_validRequest_returns201WithLocationHeaderEndingWithBody")
    void postSendMessage_validRequest_returns201WithLocationHeaderEndingWithBody() throws Exception {
        // Arrange
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
        String requestBody = objectMapper.writeValueAsString(dto);

        SentMessageResult stubResult = new SentMessageResult(MESSAGE_ID, THREAD_ID);
        when(gmailService.sendMessage(eq("me"), any(SendMessageDTO.class)))
                .thenReturn(stubResult);

        // Act & Assert: Location header must point to /api/v1/gmail/messages/{messageId}/body
        // per contracts/api-endpoints.md Endpoint 1 and Decision 3.
        mockMvc.perform(post(MESSAGES_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "/api/v1/gmail/messages/" + MESSAGE_ID + "/body"));
    }

    // -------------------------------------------------------------------------
    // 201 Created — response body contract: {messageId, threadId, status: "SENT"}
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postSendMessage_validRequest_returns201WithSendMessageResponseBody")
    void postSendMessage_validRequest_returns201WithSendMessageResponseBody() throws Exception {
        // Arrange
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
        String requestBody = objectMapper.writeValueAsString(dto);

        SentMessageResult stubResult = new SentMessageResult(MESSAGE_ID, THREAD_ID);
        when(gmailService.sendMessage(eq("me"), any(SendMessageDTO.class)))
                .thenReturn(stubResult);

        // Act & Assert: response body must contain messageId, threadId, and status="SENT"
        // per api-endpoints.md Endpoint 1 success-response example.
        mockMvc.perform(post(MESSAGES_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.messageId").value(MESSAGE_ID))
                .andExpect(jsonPath("$.threadId").value(THREAD_ID))
                .andExpect(jsonPath("$.status").value("SENT"));
    }

    // -------------------------------------------------------------------------
    // Contrast with sendDraft: Location header must NOT be absent (positive check)
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postSendMessage_validRequest_locationHeaderIsPresent")
    void postSendMessage_validRequest_locationHeaderIsPresent() throws Exception {
        // Arrange: explicit positive assertion complementing the sendDraft test that
        // asserts Location is absent.  This test would fail if the controller returned
        // 200 (like sendDraft) instead of 201 (which sets Location via ResponseEntity.created()).
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
        String requestBody = objectMapper.writeValueAsString(dto);

        SentMessageResult stubResult = new SentMessageResult(MESSAGE_ID, THREAD_ID);
        when(gmailService.sendMessage(eq("me"), any(SendMessageDTO.class)))
                .thenReturn(stubResult);

        // Act & Assert
        mockMvc.perform(post(MESSAGES_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"));
    }

    // -------------------------------------------------------------------------
    // 422 — Gmail rejects the recipient (InvalidRecipientException from service layer)
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postSendMessage_gmailRejectsRecipient_returns422WithInvalidRecipientProblemType")
    void postSendMessage_gmailRejectsRecipient_returns422WithInvalidRecipientProblemType()
            throws Exception {
        // Arrange: the repository maps Gmail's 400 invalidArgument reason to
        // InvalidRecipientException (not ValidationException), which propagates as a
        // RuntimeException through the service layer uncaught (only IOException is caught).
        // GlobalExceptionHandler.handleInvalidRecipientException catches it and returns
        // 422 Unprocessable Entity + /problems/invalid-recipient, distinguishing a
        // mailbox-provider semantic rejection from a Bean Validation structural failure (400).
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
        String requestBody = objectMapper.writeValueAsString(dto);

        when(gmailService.sendMessage(eq("me"), any(SendMessageDTO.class)))
                .thenThrow(new InvalidRecipientException(
                        "Gmail rejected one or more recipient addresses or message fields"));

        // Act & Assert
        mockMvc.perform(post(MESSAGES_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("/problems/invalid-recipient"));
    }

    // -------------------------------------------------------------------------
    // 201 Created — HTML body request also succeeds
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postSendMessage_htmlBodyRequest_returns201WithSentStatus")
    void postSendMessage_htmlBodyRequest_returns201WithSentStatus() throws Exception {
        // Arrange: HTML-body fixture to confirm the endpoint does not reject html bodyType.
        SendMessageDTO dto = SendMessageRequestFixtures.validHtmlBody();
        String requestBody = objectMapper.writeValueAsString(dto);

        SentMessageResult stubResult = new SentMessageResult(MESSAGE_ID, THREAD_ID);
        when(gmailService.sendMessage(eq("me"), any(SendMessageDTO.class)))
                .thenReturn(stubResult);

        // Act & Assert
        mockMvc.perform(post(MESSAGES_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SENT"));
    }
}
