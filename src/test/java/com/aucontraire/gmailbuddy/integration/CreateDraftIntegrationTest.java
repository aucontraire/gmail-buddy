package com.aucontraire.gmailbuddy.integration;

import com.aucontraire.gmailbuddy.dto.SendMessageDTO;
import com.aucontraire.gmailbuddy.fixture.SendMessageRequestFixtures;
import com.aucontraire.gmailbuddy.repository.GmailRepository;
import com.aucontraire.gmailbuddy.service.DraftCreationResult;
import com.aucontraire.gmailbuddy.service.GmailService;
import com.aucontraire.gmailbuddy.service.GoogleTokenValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code POST /api/v1/gmail/drafts} with the full Spring
 * application context.
 *
 * <p>Two {@code @Nested} classes exercise the two supported authentication paths:</p>
 * <ol>
 *   <li><strong>Bearer-token path</strong> — mirrors the service-to-service caller.
 *       The {@code GoogleTokenValidator} is mocked to return a valid token info, so
 *       {@code TokenAuthenticationFilter} allows the request through without a real
 *       Google token. This matches the pattern in
 *       {@code ApiClientAuthenticationIntegrationTest}.</li>
 *   <li><strong>Session / {@code @WithMockUser} path</strong> — mirrors the
 *       browser-session caller. Spring Security Test injects a mock user into the
 *       security context so the OAuth2 redirect is bypassed.</li>
 * </ol>
 *
 * <p>Both paths assert the 201 Created status + {@code DraftResponse} body contract.
 * {@code GmailService} is mocked so no Gmail API calls are made.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("POST /api/v1/gmail/drafts — integration")
class CreateDraftIntegrationTest {

    // -------------------------------------------------------------------------
    // Constants — shared across nested classes
    // -------------------------------------------------------------------------

    /**
     * A realistic-looking (but fake) Google OAuth2 access token.
     * The token starts with the {@code ya29.} prefix that the real Google tokens use,
     * which is what the {@code TokenAuthenticationFilter} looks for when deciding
     * whether to invoke {@code GoogleTokenValidator}.
     */
    static final String VALID_GOOGLE_TOKEN = "ya29.a0ARrdaM-valid-google-token-for-draft";

    private static final String DRAFTS_ENDPOINT = "/api/v1/gmail/drafts";
    private static final String DRAFT_ID        = "r-9876543210";
    private static final String MESSAGE_ID      = "19a2b3c4d5e6f7g8";
    private static final String THREAD_ID       = "19a2b3c4d5e6f7g8";

    // -------------------------------------------------------------------------
    // Spring-managed beans
    // -------------------------------------------------------------------------

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GoogleTokenValidator tokenValidator;

    @MockitoBean
    private GmailService gmailService;

    @MockitoBean
    private GmailRepository gmailRepository;

    @BeforeEach
    void resetMocks() {
        reset(tokenValidator, gmailService, gmailRepository);
    }

    // -------------------------------------------------------------------------
    // Helper: mock a valid token validation response (mirrors ApiClientAuthenticationIntegrationTest)
    // -------------------------------------------------------------------------

    /**
     * Sets up the {@link GoogleTokenValidator} mock to behave as if the token is
     * valid with required Gmail scopes, and stubs {@code GmailService.createDraft}
     * to return a canned {@link DraftCreationResult}. This helper is the
     * create-draft analogue of {@code mockValidTokenResponse()} in
     * {@code ApiClientAuthenticationIntegrationTest}.
     */
    private void mockValidTokenResponseForDraft() throws Exception {
        GoogleTokenValidator.TokenInfoResponse tokenInfo =
                createValidTokenInfo();
        when(tokenValidator.getTokenInfo(VALID_GOOGLE_TOKEN)).thenReturn(tokenInfo);
        when(tokenValidator.hasValidGmailScopes(tokenInfo.getScope())).thenReturn(true);

        DraftCreationResult stubResult =
                new DraftCreationResult(DRAFT_ID, MESSAGE_ID, THREAD_ID);
        when(gmailService.createDraft(anyString(), any(SendMessageDTO.class)))
                .thenReturn(stubResult);
    }

    private GoogleTokenValidator.TokenInfoResponse createValidTokenInfo() {
        GoogleTokenValidator.TokenInfoResponse tokenInfo =
                new GoogleTokenValidator.TokenInfoResponse();
        tokenInfo.setEmail("api-user@example.com");
        tokenInfo.setUserId("123456789");
        tokenInfo.setScope(
                "https://www.googleapis.com/auth/gmail.readonly "
                + "https://www.googleapis.com/auth/gmail.modify "
                + "https://www.googleapis.com/auth/gmail.send");
        tokenInfo.setAudience("test-client-id");
        tokenInfo.setExpiresIn("3600");
        tokenInfo.setAccessType("offline");
        return tokenInfo;
    }

    // =========================================================================
    // Nested class 1: Bearer-token authentication path
    // =========================================================================

    @Nested
    @DisplayName("Bearer-token auth path")
    class BearerTokenAuthTests {

        @Test
        @DisplayName("postDraft_bearerTokenAuth_validRequest_returns201WithDraftResponseBody")
        void postDraft_bearerTokenAuth_validRequest_returns201WithDraftResponseBody()
                throws Exception {
            // Arrange
            mockValidTokenResponseForDraft();
            SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
            String requestBody = objectMapper.writeValueAsString(dto);

            // Act & Assert
            mockMvc.perform(post(DRAFTS_ENDPOINT)
                            .header("Authorization", "Bearer " + VALID_GOOGLE_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.draftId").value(DRAFT_ID))
                    .andExpect(jsonPath("$.messageId").value(MESSAGE_ID))
                    .andExpect(jsonPath("$.threadId").value(THREAD_ID))
                    .andExpect(jsonPath("$.status").value("DRAFT"));
        }

        @Test
        @DisplayName("postDraft_bearerTokenAuth_validRequest_returns201WithLocationHeader")
        void postDraft_bearerTokenAuth_validRequest_returns201WithLocationHeader()
                throws Exception {
            // Arrange
            mockValidTokenResponseForDraft();
            SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
            String requestBody = objectMapper.writeValueAsString(dto);

            // Act & Assert: Location header must point to /api/v1/gmail/drafts/{draftId}.
            mockMvc.perform(post(DRAFTS_ENDPOINT)
                            .header("Authorization", "Bearer " + VALID_GOOGLE_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location",
                            "/api/v1/gmail/drafts/" + DRAFT_ID));
        }

        @Test
        @DisplayName("postDraft_bearerTokenAuth_htmlBodyRequest_returns201")
        void postDraft_bearerTokenAuth_htmlBodyRequest_returns201() throws Exception {
            // Arrange: verify HTML bodyType is accepted end-to-end via bearer auth.
            mockValidTokenResponseForDraft();
            SendMessageDTO dto = SendMessageRequestFixtures.validHtmlBody();
            String requestBody = objectMapper.writeValueAsString(dto);

            // Act & Assert
            mockMvc.perform(post(DRAFTS_ENDPOINT)
                            .header("Authorization", "Bearer " + VALID_GOOGLE_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("DRAFT"));
        }
    }

    // =========================================================================
    // Nested class 2: Browser session / @WithMockUser authentication path
    // =========================================================================

    @Nested
    @DisplayName("Browser session auth path (@WithMockUser)")
    class SessionAuthTests {

        @Test
        @WithMockUser
        @DisplayName("postDraft_sessionAuth_validRequest_returns201WithDraftResponseBody")
        void postDraft_sessionAuth_validRequest_returns201WithDraftResponseBody()
                throws Exception {
            // Arrange: no Bearer header; @WithMockUser injects a session principal.
            DraftCreationResult stubResult =
                    new DraftCreationResult(DRAFT_ID, MESSAGE_ID, THREAD_ID);
            when(gmailService.createDraft(anyString(), any(SendMessageDTO.class)))
                    .thenReturn(stubResult);

            SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
            String requestBody = objectMapper.writeValueAsString(dto);

            // Act & Assert
            mockMvc.perform(post(DRAFTS_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.draftId").value(DRAFT_ID))
                    .andExpect(jsonPath("$.messageId").value(MESSAGE_ID))
                    .andExpect(jsonPath("$.threadId").value(THREAD_ID))
                    .andExpect(jsonPath("$.status").value("DRAFT"));
        }

        @Test
        @WithMockUser
        @DisplayName("postDraft_sessionAuth_validRequest_returns201WithLocationHeader")
        void postDraft_sessionAuth_validRequest_returns201WithLocationHeader()
                throws Exception {
            // Arrange
            DraftCreationResult stubResult =
                    new DraftCreationResult(DRAFT_ID, MESSAGE_ID, THREAD_ID);
            when(gmailService.createDraft(anyString(), any(SendMessageDTO.class)))
                    .thenReturn(stubResult);

            SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
            String requestBody = objectMapper.writeValueAsString(dto);

            // Act & Assert
            mockMvc.perform(post(DRAFTS_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location",
                            "/api/v1/gmail/drafts/" + DRAFT_ID));
        }
    }
}
