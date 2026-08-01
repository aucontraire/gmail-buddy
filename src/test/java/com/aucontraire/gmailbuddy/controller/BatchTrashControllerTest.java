package com.aucontraire.gmailbuddy.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aucontraire.gmailbuddy.GmailBuddyApplication;
import com.aucontraire.gmailbuddy.config.TestTokenProviderConfiguration;
import com.aucontraire.gmailbuddy.dto.BatchMessageIdsRequest;
import com.aucontraire.gmailbuddy.exception.ValidationException;
import com.aucontraire.gmailbuddy.fixture.BatchOperationFixtures;
import com.aucontraire.gmailbuddy.repository.GmailRepository;
import com.aucontraire.gmailbuddy.service.BulkOperationResult;
import com.aucontraire.gmailbuddy.service.GmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Controller-slice contract tests for {@code POST /api/v1/gmail/messages/batchTrash}
 * (feature 005 US1, T005).
 *
 * <p>{@link GmailService} is mocked so no Gmail API calls are made — these are
 * request/response contract tests only. Uses the full {@code @SpringBootTest}
 * security context (mirroring {@code GmailControllerTest}) so the real
 * {@code SecurityConfig} filter chain and {@code ResponseMapper} bean are
 * exercised, rather than re-stubbing them.</p>
 *
 * <p>Covers: all-success, partial, fully-failed (still HTTP 200 per FR-003 —
 * best-effort semantics, not an error), empty/oversized/ill-formed
 * {@code messageIds} (400 {@code /problems/validation-error}), and an
 * unauthenticated request.</p>
 */
@SpringBootTest(classes = GmailBuddyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestTokenProviderConfiguration.class)
@DisplayName("POST /api/v1/gmail/messages/batchTrash — contract (T005)")
class BatchTrashControllerTest {

    private static final String BATCH_TRASH_ENDPOINT = "/api/v1/gmail/messages/batchTrash";
    private static final String USER_ID = "me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GmailService gmailService;

    @MockitoBean
    private GmailRepository gmailRepository;

    @BeforeEach
    void authenticateAsTestUser() {
        OAuth2AccessToken accessToken =
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token-value", null, null);
        OAuth2User principal = new DefaultOAuth2User(
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")),
                Collections.singletonMap("name", "testuser"),
                "name");
        OAuth2AuthenticationToken authentication =
                new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    // -------------------------------------------------------------------------
    // (a) All-success — 200 with successCount == N, ids in successfulOperations
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchTrashMessages_allMessagesSucceed_returns200WithSuccessCountMatchingTotal")
    void batchTrashMessages_allMessagesSucceed_returns200WithSuccessCountMatchingTotal() throws Exception {
        // Arrange
        List<String> messageIds = BatchOperationFixtures.validMessageIds(3);
        BulkOperationResult bulkResult = BatchOperationFixtures.buildAllSuccessResult(messageIds);
        when(gmailService.batchTrashMessages(eq(USER_ID), eq(messageIds))).thenReturn(bulkResult);
        String requestBody = objectMapper.writeValueAsString(new BatchMessageIdsRequest(messageIds));

        // Act & Assert
        mockMvc.perform(post(BATCH_TRASH_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(3))
                .andExpect(jsonPath("$.failureCount").value(0))
                .andExpect(jsonPath("$.successfulOperations", containsInAnyOrder(messageIds.toArray())));
    }

    // -------------------------------------------------------------------------
    // (a.1) X-Gmail-Quota-Used header (T031, updated WI-1 T009) — totalBatchesProcessed * 50
    //     (BATCH_MODIFY_QUOTA_PER_CHUNK, flat native batchModify per-chunk cost)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchTrashMessages_allMessagesSucceed_setsGmailQuotaUsedHeaderToChunksTimesFifty")
    void batchTrashMessages_allMessagesSucceed_setsGmailQuotaUsedHeaderToChunksTimesFifty() throws Exception {
        // Arrange: ResponseMapper#toBatchOperationResponse computes quotaUsed as
        // totalBatchesProcessed * 50 (native batchModify's flat per-chunk cost, WI-1/FR-008),
        // so 3 messages processed as 1 native chunk -> 50.
        List<String> messageIds = BatchOperationFixtures.validMessageIds(3);
        BulkOperationResult bulkResult = BatchOperationFixtures.buildAllSuccessResult(messageIds);
        bulkResult.incrementBatchesProcessed(); // 1 native batchModify call for the chunk
        when(gmailService.batchTrashMessages(eq(USER_ID), eq(messageIds))).thenReturn(bulkResult);
        String requestBody = objectMapper.writeValueAsString(new BatchMessageIdsRequest(messageIds));

        // Act & Assert
        mockMvc.perform(post(BATCH_TRASH_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Gmail-Quota-Used", "50"));
    }

    // -------------------------------------------------------------------------
    // (b) Partial — 200 with both successCount and failureCount > 0, plus the
    //     failed-id -> error map
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchTrashMessages_partialFailure_returns200WithSuccessAndFailureCountsAndErrorMap")
    void batchTrashMessages_partialFailure_returns200WithSuccessAndFailureCountsAndErrorMap() throws Exception {
        // Arrange
        List<String> messageIds = BatchOperationFixtures.validMessageIds(4);
        BulkOperationResult bulkResult = BatchOperationFixtures.buildPartialResult(messageIds, 2);
        when(gmailService.batchTrashMessages(eq(USER_ID), eq(messageIds))).thenReturn(bulkResult);
        String requestBody = objectMapper.writeValueAsString(new BatchMessageIdsRequest(messageIds));

        // Act & Assert
        mockMvc.perform(post(BATCH_TRASH_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.failureCount").value(2))
                .andExpect(jsonPath("$.failedOperations." + messageIds.get(2)).exists())
                .andExpect(jsonPath("$.failedOperations." + messageIds.get(3)).exists());
    }

    // -------------------------------------------------------------------------
    // (c) Fully-failed — 200 with successCount 0 and every id in failedOperations
    //     (FR-003: best-effort, NOT a 4xx)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchTrashMessages_allMessagesFail_returns200WithZeroSuccessCountPerFR003")
    void batchTrashMessages_allMessagesFail_returns200WithZeroSuccessCountPerFR003() throws Exception {
        // Arrange
        List<String> messageIds = BatchOperationFixtures.validMessageIds(3);
        BulkOperationResult bulkResult = BatchOperationFixtures.buildAllFailResult(messageIds);
        when(gmailService.batchTrashMessages(eq(USER_ID), eq(messageIds))).thenReturn(bulkResult);
        String requestBody = objectMapper.writeValueAsString(new BatchMessageIdsRequest(messageIds));

        // Act & Assert: HTTP 200, not a 4xx, even though every message failed (FR-003)
        mockMvc.perform(post(BATCH_TRASH_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(0))
                .andExpect(jsonPath("$.failureCount").value(3))
                .andExpect(jsonPath("$.failedOperations." + messageIds.get(0)).exists())
                .andExpect(jsonPath("$.failedOperations." + messageIds.get(1)).exists())
                .andExpect(jsonPath("$.failedOperations." + messageIds.get(2)).exists());
    }

    // -------------------------------------------------------------------------
    // (d) Empty messageIds -> 400 /problems/validation-error
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchTrashMessages_emptyMessageIds_returns400WithValidationErrorProblemType")
    void batchTrashMessages_emptyMessageIds_returns400WithValidationErrorProblemType() throws Exception {
        // Arrange: @NotEmpty on BatchMessageIdsRequest.messageIds rejects before the
        // controller method body runs — no GmailService stubbing needed.
        String requestBody = "{\"messageIds\": []}";

        // Act & Assert
        mockMvc.perform(post(BATCH_TRASH_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"));
    }

    // -------------------------------------------------------------------------
    // (d.1) Null element in messageIds -> 400 (T036 — @NotNull on list element)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchTrashMessages_nullMessageIdElement_returns400WithValidationErrorProblemType")
    void batchTrashMessages_nullMessageIdElement_returns400WithValidationErrorProblemType() throws Exception {
        // Arrange: the @NotNull on BatchMessageIdsRequest.messageIds' list element rejects
        // a null entry before the controller method body runs — no GmailService stubbing
        // needed. Distinct from the (d) empty-list case: this list is non-empty but its
        // sole element is null.
        String requestBody = "{\"messageIds\": [null]}";

        // Act & Assert
        mockMvc.perform(post(BATCH_TRASH_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"));
    }

    // -------------------------------------------------------------------------
    // (e) Oversized list (exceeds batchModifyMaxResults) -> 400
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchTrashMessages_batchSizeExceedsConfiguredMax_returns400WithValidationErrorProblemType")
    void batchTrashMessages_batchSizeExceedsConfiguredMax_returns400WithValidationErrorProblemType() throws Exception {
        // Arrange: application-test.properties sets
        // gmail-buddy.gmail-api.batch-modify-max-results=10 (decoupled from the
        // permanent-delete batch-delete-max-results cap per FR-009). Real enforcement of
        // that ceiling (validateBatchSize) is unit-tested in GmailServiceBatchTrashTest (T013);
        // here GmailService is fully mocked, so this test verifies the controller's 400
        // contract when the service signals a batch-size violation. No uniqueness
        // constraint applies to messageIds, so a repeated valid id is sufficient.
        String repeatedId = BatchOperationFixtures.validMessageIds(1).get(0);
        List<String> oversizedIds = Collections.nCopies(11, repeatedId);
        when(gmailService.batchTrashMessages(eq(USER_ID), eq(oversizedIds)))
                .thenThrow(new ValidationException("messageIds size (11) exceeds the configured maximum (10)"));
        String requestBody = objectMapper.writeValueAsString(new BatchMessageIdsRequest(oversizedIds));

        // Act & Assert
        mockMvc.perform(post(BATCH_TRASH_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"));
    }

    // -------------------------------------------------------------------------
    // (f) Ill-formed message id -> 400
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchTrashMessages_illFormedMessageId_returns400WithValidationErrorProblemType")
    void batchTrashMessages_illFormedMessageId_returns400WithValidationErrorProblemType() throws Exception {
        // Arrange: "not-valid-hex-id!" fails the @GmailMessageId hex-character pattern
        String requestBody = "{\"messageIds\": [\"not-valid-hex-id!\"]}";

        // Act & Assert
        mockMvc.perform(post(BATCH_TRASH_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"));
    }

    // -------------------------------------------------------------------------
    // (g) Unauthenticated -> redirected to the OAuth2 login entry point
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchTrashMessages_unauthenticated_redirectsToOAuth2LoginEntryPoint")
    void batchTrashMessages_unauthenticated_redirectsToOAuth2LoginEntryPoint() throws Exception {
        // Arrange: SecurityConfig registers LoginUrlAuthenticationEntryPoint("/oauth2/authorization/google")
        // as the authenticationEntryPoint, so unauthenticated requests receive a 302 redirect
        // rather than a raw 401 — the same behavior GmailControllerTest documents for every
        // other authenticated endpoint on this controller.
        SecurityContextHolder.clearContext();
        List<String> messageIds = BatchOperationFixtures.validMessageIds(1);
        String requestBody = objectMapper.writeValueAsString(new BatchMessageIdsRequest(messageIds));

        // Act & Assert
        mockMvc.perform(post(BATCH_TRASH_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isFound());
    }
}
