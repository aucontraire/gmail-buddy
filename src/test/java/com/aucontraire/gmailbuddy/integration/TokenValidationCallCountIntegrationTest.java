package com.aucontraire.gmailbuddy.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aucontraire.gmailbuddy.client.GmailBatchClient;
import com.aucontraire.gmailbuddy.client.GmailClient;
import com.aucontraire.gmailbuddy.dto.BatchMessageIdsRequest;
import com.aucontraire.gmailbuddy.fixture.BatchOperationFixtures;
import com.aucontraire.gmailbuddy.service.BulkOperationResult;
import com.aucontraire.gmailbuddy.service.GoogleTokenValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ModifyMessageRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * WI-2 cross-cutting proof (feature 007, T017) that a single authenticated API request performs
 * at most one live token-validation round-trip to Google, down from two (FR-003, SC-001;
 * contracts/auth-token-transport.md invariant #1 + verification obligation "Call-count").
 *
 * <p>Before WI-2, an authenticated request paid two live validations: (1) {@code
 * TokenAuthenticationFilter} calling {@code GoogleTokenValidator.getTokenInfo(token)} at the
 * security boundary, and (2) the post-authentication path re-deriving/re-validating the token
 * (the deprecated {@code GoogleTokenValidator.isValidGoogleToken}) while fetching it for the
 * Gmail call. WI-2's {@code OAuth2TokenProvider.getTokenFromContext()} instead retrieves the
 * token via the {@code SecurityContext} + encrypted token-reference path the filter already
 * established (ADR-002 hard constraint, FR-005) — performing zero additional live validation.</p>
 *
 * <p>Wires the real Spring stack (real {@code TokenAuthenticationFilter}, real {@code
 * OAuth2TokenProvider}, real {@code TokenReferenceService}, real {@code GmailService}/{@code
 * GmailRepositoryImpl}) and mocks only {@link GoogleTokenValidator} (the live Google edge),
 * {@link GmailClient} (the Gmail service factory), and {@link GmailBatchClient} (the native batch
 * call) — mirroring {@code AuthErrorTaxonomyIntegrationTest} and {@code
 * ApiClientAuthenticationIntegrationTest}.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("WI-2 token-validation call count per authenticated request (2 -> <=1) — T017")
class TokenValidationCallCountIntegrationTest {

    private static final String API_BASE_PATH = "/api/v1/gmail";
    private static final String VALID_SCOPE = "https://www.googleapis.com/auth/gmail.modify";
    private static final String TOKEN = "ya29.single-validation-token";

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

    private Gmail gmailServiceStub;

    @BeforeEach
    void setUp() throws Exception {
        reset(tokenValidator, gmailClient, gmailBatchClient);
        gmailServiceStub = mock(Gmail.class);
        when(gmailClient.createGmailService(anyString())).thenReturn(gmailServiceStub);
    }

    @Test
    @DisplayName(
            "batchTrash_singleAuthenticatedRequest_performsAtMostOneLiveTokenValidationAndNeverReDerivesViaDeprecatedPath")
    void batchTrash_singleAuthenticatedRequest_performsAtMostOneLiveTokenValidationAndNeverReDerivesViaDeprecatedPath()
            throws Exception {
        // Arrange: a valid, sufficiently-scoped token. Only the filter's getTokenInfo call is
        // stubbed — isValidGoogleToken is deliberately left unstubbed (defaults to false) so that
        // if the post-authentication path ever fell back to it, the Gmail operation would fail
        // and the assertions below would catch it.
        GoogleTokenValidator.TokenInfoResponse tokenInfo = new GoogleTokenValidator.TokenInfoResponse();
        tokenInfo.setEmail("bulk-caller@example.com");
        tokenInfo.setScope(VALID_SCOPE);
        tokenInfo.setExpiresIn("3600");
        when(tokenValidator.getTokenInfo(TOKEN)).thenReturn(tokenInfo);
        when(tokenValidator.hasValidGmailScopes(VALID_SCOPE)).thenReturn(true);

        List<String> messageIds = BatchOperationFixtures.validMessageIds(3);
        BulkOperationResult successResult = BatchOperationFixtures.buildAllSuccessResult(messageIds);
        when(gmailBatchClient.batchModifyLabels(
                        any(Gmail.class), anyString(), eq(messageIds), any(ModifyMessageRequest.class)))
                .thenReturn(successResult);

        String requestBody = objectMapper.writeValueAsString(new BatchMessageIdsRequest(messageIds));

        // Act: a single authenticated request that reaches Gmail-calling code end-to-end (real
        // filter -> real OAuth2TokenProvider -> real GmailService/GmailRepositoryImpl).
        mockMvc.perform(post(API_BASE_PATH + "/messages/batchTrash")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(messageIds.size()));

        // Assert (SC-001): exactly one live tokeninfo round-trip for the whole request — the
        // filter's — not two.
        verify(tokenValidator, times(1)).getTokenInfo(TOKEN);

        // Assert (FR-003/FR-005, ADR-002): the post-authentication path never falls back to the
        // deprecated re-validation entry point to fetch the token; it was retrieved solely via the
        // authenticated SecurityContext + token-reference path the filter already created.
        verify(tokenValidator, never()).isValidGoogleToken(any());

        // Assert (decoupling correctness): the token actually used to call Gmail is the same raw
        // token the filter validated — proving the reference correctly round-tripped the original
        // token rather than some other value or a fallback path silently substituting one.
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(gmailClient).createGmailService(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue()).isEqualTo(TOKEN);

        // Assert the Gmail operation genuinely happened end-to-end (not skipped/bypassed).
        verify(gmailBatchClient)
                .batchModifyLabels(eq(gmailServiceStub), anyString(), eq(messageIds), any(ModifyMessageRequest.class));
    }
}
