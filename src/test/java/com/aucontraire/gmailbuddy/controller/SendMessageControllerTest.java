package com.aucontraire.gmailbuddy.controller;

import com.aucontraire.gmailbuddy.dto.SendMessageDTO;
import com.aucontraire.gmailbuddy.exception.ValidationException;
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
 *   <li>Validation failure (Gmail rejects recipient) → service throws
 *       {@link ValidationException} → 400 with problem type</li>
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
    // 400 — Gmail rejects the recipient (ValidationException from service layer)
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postSendMessage_gmailRejectsRecipient_returns400WithValidationErrorProblemType")
    void postSendMessage_gmailRejectsRecipient_returns400WithValidationErrorProblemType()
            throws Exception {
        // Arrange: service throws ValidationException when Gmail returns 400 invalidArgument
        // (mapped from mapGmailSendError in the repository layer and wrapped in GmailApiException
        // at the service layer — but since ValidationException is a RuntimeException it propagates
        // directly through the GmailApiException catch-block only for IOException).
        // The repository throws ValidationException directly (it's a RuntimeException),
        // which the service does NOT catch (only IOException is caught).
        // GlobalExceptionHandler catches ValidationException → 400 + /problems/validation-error.
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
        String requestBody = objectMapper.writeValueAsString(dto);

        when(gmailService.sendMessage(eq("me"), any(SendMessageDTO.class)))
                .thenThrow(new ValidationException(
                        "Gmail rejected one or more recipient addresses or message fields"));

        // Act & Assert
        mockMvc.perform(post(MESSAGES_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"));
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
