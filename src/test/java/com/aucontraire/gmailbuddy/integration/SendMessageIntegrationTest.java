package com.aucontraire.gmailbuddy.integration;

import com.aucontraire.gmailbuddy.dto.SendMessageDTO;
import com.aucontraire.gmailbuddy.fixture.SendMessageRequestFixtures;
import com.aucontraire.gmailbuddy.repository.GmailRepository;
import com.aucontraire.gmailbuddy.service.GmailService;
import com.aucontraire.gmailbuddy.service.GoogleTokenValidator;
import com.aucontraire.gmailbuddy.service.SentMessageResult;
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
 * Integration tests for {@code POST /api/v1/gmail/messages} (direct send)
 * with the full Spring application context.
 *
 * <p>Two {@code @Nested} classes exercise the two supported authentication paths:</p>
 * <ol>
 *   <li><strong>Bearer-token path</strong> — mirrors the service-to-service caller.
 *       {@code GoogleTokenValidator} is mocked to return a valid token info so
 *       {@code TokenAuthenticationFilter} allows the request through without a real
 *       Google token. Matches the pattern in {@code ApiClientAuthenticationIntegrationTest}.</li>
 *   <li><strong>Session / {@code @WithMockUser} path</strong> — mirrors the
 *       browser-session caller. Spring Security Test injects a mock user.</li>
 * </ol>
 *
 * <p>Both paths assert the 201 Created status, {@code SendMessageResponse} body
 * contract, and the Location header ending with {@code /body}. {@code GmailService}
 * is mocked so no Gmail API calls are made.</p>
 *
 * <p>An additional test exercises the at-least-once contract documentation: the
 * endpoint MUST NOT include any idempotency-key header in its response, confirming
 * the API makes no idempotency guarantee for {@code POST /messages} (Decision 6).</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("POST /api/v1/gmail/messages — integration")
class SendMessageIntegrationTest {

    // -------------------------------------------------------------------------
    // Constants — shared across nested classes
    // -------------------------------------------------------------------------

    /**
     * Realistic-looking (but fake) Google OAuth2 access token.
     * The {@code ya29.} prefix triggers token validation in {@code TokenAuthenticationFilter}.
     */
    static final String VALID_GOOGLE_TOKEN = "ya29.a0ARrdaM-valid-google-token-for-send-msg";

    private static final String MESSAGES_ENDPOINT = "/api/v1/gmail/messages";
    private static final String MESSAGE_ID        = "19a2b3c4d5e6f7g8";
    private static final String THREAD_ID         = "thread-19a2b3c4d5e6f7g8";

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
    // Helper: mock a valid token validation response + stub GmailService
    // (mirrors mockValidTokenResponseForDraft() in CreateDraftIntegrationTest)
    // -------------------------------------------------------------------------

    private void mockValidTokenResponseForSendMessage() throws Exception {
        GoogleTokenValidator.TokenInfoResponse tokenInfo = createValidTokenInfo();
        when(tokenValidator.getTokenInfo(VALID_GOOGLE_TOKEN)).thenReturn(tokenInfo);
        when(tokenValidator.hasValidGmailScopes(tokenInfo.getScope())).thenReturn(true);

        SentMessageResult stubResult = new SentMessageResult(MESSAGE_ID, THREAD_ID);
        when(gmailService.sendMessage(anyString(), any(SendMessageDTO.class)))
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
        @DisplayName("postSendMessage_bearerTokenAuth_validRequest_returns201WithSentResponseBody")
        void postSendMessage_bearerTokenAuth_validRequest_returns201WithSentResponseBody()
                throws Exception {
            // Arrange
            mockValidTokenResponseForSendMessage();
            SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
            String requestBody = objectMapper.writeValueAsString(dto);

            // Act & Assert
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .header("Authorization", "Bearer " + VALID_GOOGLE_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.messageId").value(MESSAGE_ID))
                    .andExpect(jsonPath("$.threadId").value(THREAD_ID))
                    .andExpect(jsonPath("$.status").value("SENT"));
        }

        @Test
        @DisplayName("postSendMessage_bearerTokenAuth_validRequest_returns201WithLocationHeaderEndingWithBody")
        void postSendMessage_bearerTokenAuth_validRequest_returns201WithLocationHeaderEndingWithBody()
                throws Exception {
            // Arrange
            mockValidTokenResponseForSendMessage();
            SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
            String requestBody = objectMapper.writeValueAsString(dto);

            // Act & Assert: Location header must end with /body per Decision 3 and
            // contracts/api-endpoints.md Endpoint 1.
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .header("Authorization", "Bearer " + VALID_GOOGLE_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location",
                            "/api/v1/gmail/messages/" + MESSAGE_ID + "/body"));
        }

        @Test
        @DisplayName("postSendMessage_bearerTokenAuth_htmlBodyRequest_returns201")
        void postSendMessage_bearerTokenAuth_htmlBodyRequest_returns201() throws Exception {
            // Arrange: verify HTML bodyType is accepted end-to-end via bearer auth.
            mockValidTokenResponseForSendMessage();
            SendMessageDTO dto = SendMessageRequestFixtures.validHtmlBody();
            String requestBody = objectMapper.writeValueAsString(dto);

            // Act & Assert
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .header("Authorization", "Bearer " + VALID_GOOGLE_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("SENT"));
        }

        @Test
        @DisplayName("postSendMessage_bearerTokenAuth_responseDoesNotIncludeIdempotencyKeyHeader")
        void postSendMessage_bearerTokenAuth_responseDoesNotIncludeIdempotencyKeyHeader()
                throws Exception {
            // Arrange: documents the at-least-once contract from Decision 6 / FR-023.
            // The endpoint makes no idempotency guarantee; the response must NOT carry
            // any Idempotency-Key or X-Idempotency-Key header that would imply one.
            mockValidTokenResponseForSendMessage();
            SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
            String requestBody = objectMapper.writeValueAsString(dto);

            // Act & Assert
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .header("Authorization", "Bearer " + VALID_GOOGLE_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(header().doesNotExist("Idempotency-Key"))
                    .andExpect(header().doesNotExist("X-Idempotency-Key"));
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
        @DisplayName("postSendMessage_sessionAuth_validRequest_returns201WithSentResponseBody")
        void postSendMessage_sessionAuth_validRequest_returns201WithSentResponseBody()
                throws Exception {
            // Arrange: no Bearer header; @WithMockUser injects a session principal.
            SentMessageResult stubResult = new SentMessageResult(MESSAGE_ID, THREAD_ID);
            when(gmailService.sendMessage(anyString(), any(SendMessageDTO.class)))
                    .thenReturn(stubResult);

            SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
            String requestBody = objectMapper.writeValueAsString(dto);

            // Act & Assert
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.messageId").value(MESSAGE_ID))
                    .andExpect(jsonPath("$.threadId").value(THREAD_ID))
                    .andExpect(jsonPath("$.status").value("SENT"));
        }

        @Test
        @WithMockUser
        @DisplayName("postSendMessage_sessionAuth_validRequest_returns201WithLocationHeaderEndingWithBody")
        void postSendMessage_sessionAuth_validRequest_returns201WithLocationHeaderEndingWithBody()
                throws Exception {
            // Arrange
            SentMessageResult stubResult = new SentMessageResult(MESSAGE_ID, THREAD_ID);
            when(gmailService.sendMessage(anyString(), any(SendMessageDTO.class)))
                    .thenReturn(stubResult);

            SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
            String requestBody = objectMapper.writeValueAsString(dto);

            // Act & Assert
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location",
                            "/api/v1/gmail/messages/" + MESSAGE_ID + "/body"));
        }
    }
}
