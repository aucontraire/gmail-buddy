package com.aucontraire.gmailbuddy.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aucontraire.gmailbuddy.client.GmailBatchClient;
import com.aucontraire.gmailbuddy.client.GmailClient;
import com.aucontraire.gmailbuddy.dto.BatchMessageIdsRequest;
import com.aucontraire.gmailbuddy.exception.AuthorizationException;
import com.aucontraire.gmailbuddy.fixture.BatchOperationFixtures;
import com.aucontraire.gmailbuddy.service.GoogleTokenValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ModifyMessageRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * WI-2 cross-cutting proof (feature 007, T016) that the 401 (boundary auth) vs 403 (Gmail
 * under-scope) error taxonomy is preserved (FR-014, SC-008; contracts/auth-token-transport.md
 * invariant #7).
 *
 * <p>Wires the real Spring stack — the real {@link com.aucontraire.gmailbuddy.config.TokenAuthenticationFilter},
 * the real {@link com.aucontraire.gmailbuddy.service.OAuth2TokenProvider}, the real {@code GmailService}/
 * {@code GmailRepositoryImpl} — and mocks only the three edges the app has no control over: the
 * live Google tokeninfo call ({@link GoogleTokenValidator}), the Gmail service factory
 * ({@link GmailClient}), and the native batch call ({@link GmailBatchClient}). This mirrors the
 * pattern established by {@code ApiClientAuthenticationIntegrationTest} and
 * {@code BatchTrashRoundTripIntegrationTest}.</p>
 *
 * <p><strong>Scenario under test:</strong> a token whose scope is <em>readonly-only</em>
 * ({@code https://www.googleapis.com/auth/gmail.readonly}) satisfies the coarse boundary
 * scope-gate ({@code GoogleTokenValidator.hasValidGmailScopes} accepts any token carrying at
 * least one of {@code gmail.readonly}/{@code gmail.modify}/{@code gmail.send}/{@code mail.google.com}
 * — see {@code GoogleTokenValidator.REQUIRED_GMAIL_SCOPES}) but genuinely lacks permission for a
 * Gmail <em>write</em>. The boundary therefore lets the request through (no 401); the write then
 * fails downstream at Gmail with {@code insufficientPermissions}, which
 * {@code GmailRepositoryImpl} maps to {@link AuthorizationException} (HTTP 403) and
 * {@code GlobalExceptionHandler} renders as an RFC 7807 403 response. The memo/scope-gate MUST
 * NOT collapse this into a boundary 401, nor silently swallow it as a 200.</p>
 *
 * <p><strong>Approximation note:</strong> {@link GoogleTokenValidator} is fully mocked here (as
 * in the existing {@code ApiClientAuthenticationIntegrationTest}/{@code DualAuthenticationIntegrationTest}
 * suites) rather than exercising its real HTTP call to Google — that call, and the real
 * {@code hasValidGmailScopes} scope-matching logic, are unit-tested separately
 * ({@code GoogleTokenValidatorTest}). What this test proves — and what those unit tests cannot —
 * is that once the boundary accepts a coarse-gate-passing token, the rest of the authenticated
 * request path (filter → controller → service → repository → exception mapping) correctly
 * distinguishes a downstream Gmail authorization failure (403) from a boundary auth failure
 * (401), across the real Spring wiring.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("WI-2 auth error taxonomy (401 boundary vs 403 Gmail under-scope) — T016")
class AuthErrorTaxonomyIntegrationTest {

    private static final String API_BASE_PATH = "/api/v1/gmail";
    private static final String READONLY_ONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly";
    private static final String NO_GMAIL_SCOPE = "openid email profile";
    private static final String READONLY_TOKEN = "ya29.readonly-only-token";
    private static final String NO_SCOPE_TOKEN = "ya29.no-gmail-scope-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GoogleTokenValidator tokenValidator;

    @MockitoBean
    private GmailClient gmailClient;

    @MockitoBean
    private GmailBatchClient gmailBatchClient;

    @BeforeEach
    void setUp() throws Exception {
        reset(tokenValidator, gmailClient, gmailBatchClient);
        Gmail gmailServiceStub = mock(Gmail.class);
        when(gmailClient.createGmailService(anyString())).thenReturn(gmailServiceStub);
    }

    @Test
    @DisplayName("batchTrash_readonlyOnlyTokenPassesCoarseGate_gmail403SurfacesAsForbiddenNotBoundaryUnauthorized")
    void batchTrash_readonlyOnlyTokenPassesCoarseGate_gmail403SurfacesAsForbiddenNotBoundaryUnauthorized()
            throws Exception {
        // Arrange: a readonly-only token — coarse-gate-passing per REQUIRED_GMAIL_SCOPES, but
        // lacking permission for a write. The downstream Gmail call fails with insufficientPermissions,
        // which the repository maps to AuthorizationException (HTTP 403).
        GoogleTokenValidator.TokenInfoResponse tokenInfo = new GoogleTokenValidator.TokenInfoResponse();
        tokenInfo.setEmail("readonly-user@example.com");
        tokenInfo.setScope(READONLY_ONLY_SCOPE);
        tokenInfo.setExpiresIn("3600");
        when(tokenValidator.getTokenInfo(READONLY_TOKEN)).thenReturn(tokenInfo);
        when(tokenValidator.hasValidGmailScopes(READONLY_ONLY_SCOPE)).thenReturn(true);

        List<String> messageIds = BatchOperationFixtures.validMessageIds(2);
        when(gmailBatchClient.batchModifyLabels(
                        any(Gmail.class), anyString(), eq(messageIds), any(ModifyMessageRequest.class)))
                .thenThrow(new AuthorizationException(
                        "Insufficient Gmail permissions to modify message labels (insufficientPermissions)"));

        String requestBody = objectMapper.writeValueAsString(new BatchMessageIdsRequest(messageIds));

        // Act & Assert: the Gmail-layer authorization failure surfaces as HTTP 403 — never a
        // boundary 401, never a silent 200 bypass.
        mockMvc.perform(post(API_BASE_PATH + "/messages/batchTrash")
                        .header("Authorization", "Bearer " + READONLY_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        // The boundary scope-gate accepted the readonly-only token (no 401 short-circuit) ...
        verify(tokenValidator).getTokenInfo(READONLY_TOKEN);
        verify(tokenValidator).hasValidGmailScopes(READONLY_ONLY_SCOPE);
        // ... so the 403 genuinely originates from the downstream Gmail call, not a boundary reject.
        verify(gmailBatchClient)
                .batchModifyLabels(any(Gmail.class), anyString(), eq(messageIds), any(ModifyMessageRequest.class));
    }

    @Test
    @DisplayName("batchTrash_tokenWithNoAcceptedGmailScope_rejectedAtBoundaryWithUnauthorizedNeverForbidden")
    void batchTrash_tokenWithNoAcceptedGmailScope_rejectedAtBoundaryWithUnauthorizedNeverForbidden() throws Exception {
        // Contrast/negative control: a token carrying NONE of the accepted Gmail scopes fails the
        // coarse boundary gate outright. This must remain a 401 (TokenAuthenticationFilter), and
        // the request must never reach the Gmail-calling code at all — proving the previous test's
        // 403 was not an artifact of the boundary always letting requests through.
        GoogleTokenValidator.TokenInfoResponse tokenInfo = new GoogleTokenValidator.TokenInfoResponse();
        tokenInfo.setEmail("no-scope-user@example.com");
        tokenInfo.setScope(NO_GMAIL_SCOPE);
        tokenInfo.setExpiresIn("3600");
        when(tokenValidator.getTokenInfo(NO_SCOPE_TOKEN)).thenReturn(tokenInfo);
        when(tokenValidator.hasValidGmailScopes(NO_GMAIL_SCOPE)).thenReturn(false);

        List<String> messageIds = BatchOperationFixtures.validMessageIds(2);
        String requestBody = objectMapper.writeValueAsString(new BatchMessageIdsRequest(messageIds));

        mockMvc.perform(post(API_BASE_PATH + "/messages/batchTrash")
                        .header("Authorization", "Bearer " + NO_SCOPE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());

        verify(tokenValidator).getTokenInfo(NO_SCOPE_TOKEN);
        verify(tokenValidator).hasValidGmailScopes(NO_GMAIL_SCOPE);
        // The request never reached Gmail-calling code — the boundary rejected it outright.
        verifyNoInteractions(gmailBatchClient);
    }
}
