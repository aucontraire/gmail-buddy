package com.aucontraire.gmailbuddy.integration;

import com.aucontraire.gmailbuddy.repository.GmailRepository;
import com.aucontraire.gmailbuddy.service.GmailService;
import com.aucontraire.gmailbuddy.service.GoogleTokenValidator;
import com.aucontraire.gmailbuddy.service.SentMessageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code POST /api/v1/gmail/drafts/{draftId}/send} with
 * the full Spring application context.
 *
 * <p>Two {@code @Nested} classes exercise the two supported authentication paths:</p>
 * <ol>
 *   <li><strong>Bearer-token path</strong> — mirrors the service-to-service caller.
 *       {@code GoogleTokenValidator} is mocked to return a valid token info so
 *       {@code TokenAuthenticationFilter} allows the request through.</li>
 *   <li><strong>Session / {@code @WithMockUser} path</strong> — mirrors the
 *       browser-session caller. Spring Security Test injects a mock user.</li>
 * </ol>
 *
 * <p>Both paths assert the 200 OK status, correct body, and the explicit absence
 * of a Location header. {@code GmailService} is mocked so no Gmail API calls
 * are made.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("POST /api/v1/gmail/drafts/{draftId}/send — integration")
class SendDraftIntegrationTest {

    // -------------------------------------------------------------------------
    // Constants — shared across nested classes
    // -------------------------------------------------------------------------

    /**
     * Realistic-looking (but fake) Google OAuth2 access token.
     * The {@code ya29.} prefix triggers token validation in {@code TokenAuthenticationFilter}.
     */
    static final String VALID_GOOGLE_TOKEN = "ya29.a0ARrdaM-valid-google-token-for-send-draft";

    private static final String SEND_DRAFT_ENDPOINT = "/api/v1/gmail/drafts/{draftId}/send";
    private static final String DRAFT_ID             = "r-9876543210";
    private static final String MESSAGE_ID           = "19a2b3c4d5e6f7g8";
    private static final String THREAD_ID            = "thread-19a2b3c4d5e6f7g8";

    // -------------------------------------------------------------------------
    // Spring-managed beans
    // -------------------------------------------------------------------------

    @Autowired
    private MockMvc mockMvc;

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
    // Helper: mock a valid token validation response + stub GmailService
    // (mirrors mockValidTokenResponseForDraft() in CreateDraftIntegrationTest)
    // -------------------------------------------------------------------------

    private void mockValidTokenResponseForSendDraft() throws Exception {
        GoogleTokenValidator.TokenInfoResponse tokenInfo = createValidTokenInfo();
        when(tokenValidator.getTokenInfo(VALID_GOOGLE_TOKEN)).thenReturn(tokenInfo);
        when(tokenValidator.hasValidGmailScopes(tokenInfo.getScope())).thenReturn(true);

        SentMessageResult stubResult = new SentMessageResult(MESSAGE_ID, THREAD_ID);
        when(gmailService.sendDraft(anyString(), anyString())).thenReturn(stubResult);
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
        @DisplayName("postSendDraft_bearerTokenAuth_validDraftId_returns200WithSentBody")
        void postSendDraft_bearerTokenAuth_validDraftId_returns200WithSentBody()
                throws Exception {
            // Arrange
            mockValidTokenResponseForSendDraft();

            // Act & Assert: 200 OK with correct body.
            mockMvc.perform(post(SEND_DRAFT_ENDPOINT, DRAFT_ID)
                            .header("Authorization", "Bearer " + VALID_GOOGLE_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.messageId").value(MESSAGE_ID))
                    .andExpect(jsonPath("$.threadId").value(THREAD_ID))
                    .andExpect(jsonPath("$.status").value("SENT"));
        }

        @Test
        @DisplayName("postSendDraft_bearerTokenAuth_validDraftId_locationHeaderIsAbsent")
        void postSendDraft_bearerTokenAuth_validDraftId_locationHeaderIsAbsent()
                throws Exception {
            // Arrange
            mockValidTokenResponseForSendDraft();

            // Act & Assert: no Location header for a state transition
            // (per contracts/api-endpoints.md Endpoint 3 and Decision 3).
            mockMvc.perform(post(SEND_DRAFT_ENDPOINT, DRAFT_ID)
                            .header("Authorization", "Bearer " + VALID_GOOGLE_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist("Location"));
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
        @DisplayName("postSendDraft_sessionAuth_validDraftId_returns200WithSentBody")
        void postSendDraft_sessionAuth_validDraftId_returns200WithSentBody() throws Exception {
            // Arrange: no Bearer header; @WithMockUser injects a session principal.
            SentMessageResult stubResult = new SentMessageResult(MESSAGE_ID, THREAD_ID);
            when(gmailService.sendDraft(anyString(), anyString())).thenReturn(stubResult);

            // Act & Assert
            mockMvc.perform(post(SEND_DRAFT_ENDPOINT, DRAFT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.messageId").value(MESSAGE_ID))
                    .andExpect(jsonPath("$.threadId").value(THREAD_ID))
                    .andExpect(jsonPath("$.status").value("SENT"));
        }

        @Test
        @WithMockUser
        @DisplayName("postSendDraft_sessionAuth_validDraftId_locationHeaderIsAbsent")
        void postSendDraft_sessionAuth_validDraftId_locationHeaderIsAbsent() throws Exception {
            // Arrange
            SentMessageResult stubResult = new SentMessageResult(MESSAGE_ID, THREAD_ID);
            when(gmailService.sendDraft(anyString(), anyString())).thenReturn(stubResult);

            // Act & Assert: Location header must be absent in both auth paths.
            mockMvc.perform(post(SEND_DRAFT_ENDPOINT, DRAFT_ID))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist("Location"));
        }
    }
}
