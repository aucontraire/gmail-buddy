package com.aucontraire.gmailbuddy.controller;

import com.aucontraire.gmailbuddy.dto.SendMessageDTO;
import com.aucontraire.gmailbuddy.fixture.SendMessageRequestFixtures;
import com.aucontraire.gmailbuddy.mapper.GmailMessageMapper;
import com.aucontraire.gmailbuddy.mapper.ResponseMapper;
import com.aucontraire.gmailbuddy.ratelimit.GmailQuotaEstimator;
import com.aucontraire.gmailbuddy.ratelimit.RateLimitService;
import com.aucontraire.gmailbuddy.security.TokenReferenceService;
import com.aucontraire.gmailbuddy.service.DraftCreationResult;
import com.aucontraire.gmailbuddy.service.GmailService;
import com.aucontraire.gmailbuddy.service.GoogleTokenValidator;
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
 * Controller-slice test for {@code POST /api/v1/gmail/drafts} — success contract.
 *
 * <p>Focuses exclusively on the 201 Created response shape per the API contract
 * in {@code specs/001-send-draft-emails/contracts/api-endpoints.md § Endpoint 2}.
 * Validation failure cases are covered separately in
 * {@code SendMessageValidationTest} (T033); this test does not duplicate them.</p>
 *
 * <p>Uses {@code @WebMvcTest} (web layer only) with {@code @MockitoBean GmailService}
 * so the controller's dependency on the service is isolated. The
 * {@code TestGmailBuddyPropertiesConfiguration} supplies a correctly wired
 * {@link com.aucontraire.gmailbuddy.config.GmailBuddyProperties} bean matching
 * the existing {@code ControllerValidationTest} import pattern.</p>
 */
@WebMvcTest(GmailController.class)
@Import(TestGmailBuddyPropertiesConfiguration.class)
@DisplayName("POST /api/v1/gmail/drafts — 201 success contract")
class CreateDraftControllerTest {

    private static final String DRAFTS_ENDPOINT = "/api/v1/gmail/drafts";

    // Test-fixture draft identifiers — chosen to be unambiguous.
    private static final String DRAFT_ID   = "r-9876543210";
    private static final String MESSAGE_ID = "19a2b3c4d5e6f7g8";
    private static final String THREAD_ID  = "19a2b3c4d5e6f7g8";

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

    @MockitoBean
    private GmailMessageMapper gmailMessageMapper;

    // -------------------------------------------------------------------------
    // 201 Created — response body contract
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postDraft_validRequest_returns201WithDraftIdMessageIdThreadIdAndDraftStatus")
    void postDraft_validRequest_returns201WithDraftIdMessageIdThreadIdAndDraftStatus()
            throws Exception {
        // Arrange
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
        String requestBody = objectMapper.writeValueAsString(dto);

        DraftCreationResult stubResult =
                new DraftCreationResult(DRAFT_ID, MESSAGE_ID, THREAD_ID);

        // The controller uses properties.gmailApi().defaultUserId() = "me" from the
        // TestGmailBuddyPropertiesConfiguration bean.
        when(gmailService.createDraft(eq("me"), any(SendMessageDTO.class)))
                .thenReturn(stubResult);

        // Act & Assert
        mockMvc.perform(post(DRAFTS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.draftId").value(DRAFT_ID))
                .andExpect(jsonPath("$.messageId").value(MESSAGE_ID))
                .andExpect(jsonPath("$.threadId").value(THREAD_ID))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    // -------------------------------------------------------------------------
    // 201 Created — Location header contract
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postDraft_validRequest_returns201WithLocationHeaderPointingToDraftId")
    void postDraft_validRequest_returns201WithLocationHeaderPointingToDraftId() throws Exception {
        // Arrange
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
        String requestBody = objectMapper.writeValueAsString(dto);

        DraftCreationResult stubResult =
                new DraftCreationResult(DRAFT_ID, MESSAGE_ID, THREAD_ID);

        when(gmailService.createDraft(eq("me"), any(SendMessageDTO.class)))
                .thenReturn(stubResult);

        // Act & Assert: Location header must point to /api/v1/gmail/drafts/{draftId}
        // per the api-endpoints.md § Endpoint 2 success-response example.
        mockMvc.perform(post(DRAFTS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/gmail/drafts/" + DRAFT_ID));
    }

    // -------------------------------------------------------------------------
    // 201 Created — HTML body request also succeeds
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postDraft_htmlBodyRequest_returns201WithDraftStatus")
    void postDraft_htmlBodyRequest_returns201WithDraftStatus() throws Exception {
        // Arrange: HTML-body fixture to confirm the endpoint does not reject html bodyType.
        SendMessageDTO dto = SendMessageRequestFixtures.validHtmlBody();
        String requestBody = objectMapper.writeValueAsString(dto);

        DraftCreationResult stubResult =
                new DraftCreationResult(DRAFT_ID, MESSAGE_ID, THREAD_ID);

        when(gmailService.createDraft(eq("me"), any(SendMessageDTO.class)))
                .thenReturn(stubResult);

        // Act & Assert
        mockMvc.perform(post(DRAFTS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }
}
