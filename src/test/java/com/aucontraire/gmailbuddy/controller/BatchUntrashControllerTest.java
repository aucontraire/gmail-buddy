package com.aucontraire.gmailbuddy.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Controller-slice contract tests for {@code POST /api/v1/gmail/messages/batchUntrash}
 * (feature 005 US1, T006) — the inverse of {@link BatchTrashControllerTest}.
 *
 * <p>Per T006 scope: success, partial, and the same {@code messageIds} validation
 * 400s covered for batchTrash (empty, oversized, ill-formed). The fully-failed-200
 * (FR-003) and unauthenticated-request behaviors are identical HTTP-layer contracts
 * already proven for this controller in {@link BatchTrashControllerTest} and are not
 * duplicated here.</p>
 */
@SpringBootTest(classes = GmailBuddyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestTokenProviderConfiguration.class)
@DisplayName("POST /api/v1/gmail/messages/batchUntrash — contract (T006)")
class BatchUntrashControllerTest {

    private static final String BATCH_UNTRASH_ENDPOINT = "/api/v1/gmail/messages/batchUntrash";
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
    // Success — 200 with successCount == N, ids in successfulOperations
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchUntrashMessages_allMessagesSucceed_returns200WithSuccessCountMatchingTotal")
    void batchUntrashMessages_allMessagesSucceed_returns200WithSuccessCountMatchingTotal() throws Exception {
        // Arrange
        List<String> messageIds = BatchOperationFixtures.validMessageIds(3);
        BulkOperationResult bulkResult = BatchOperationFixtures.buildAllSuccessResult(messageIds);
        when(gmailService.batchUntrashMessages(eq(USER_ID), eq(messageIds))).thenReturn(bulkResult);
        String requestBody = objectMapper.writeValueAsString(new BatchMessageIdsRequest(messageIds));

        // Act & Assert
        mockMvc.perform(post(BATCH_UNTRASH_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(3))
                .andExpect(jsonPath("$.failureCount").value(0))
                .andExpect(jsonPath("$.successfulOperations", containsInAnyOrder(messageIds.toArray())));
    }

    // -------------------------------------------------------------------------
    // Partial — 200 with both successCount and failureCount > 0, plus the
    // failed-id -> error map
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchUntrashMessages_partialFailure_returns200WithSuccessAndFailureCountsAndErrorMap")
    void batchUntrashMessages_partialFailure_returns200WithSuccessAndFailureCountsAndErrorMap() throws Exception {
        // Arrange
        List<String> messageIds = BatchOperationFixtures.validMessageIds(4);
        BulkOperationResult bulkResult = BatchOperationFixtures.buildPartialResult(messageIds, 3);
        when(gmailService.batchUntrashMessages(eq(USER_ID), eq(messageIds))).thenReturn(bulkResult);
        String requestBody = objectMapper.writeValueAsString(new BatchMessageIdsRequest(messageIds));

        // Act & Assert
        mockMvc.perform(post(BATCH_UNTRASH_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(3))
                .andExpect(jsonPath("$.failureCount").value(1))
                .andExpect(jsonPath("$.failedOperations." + messageIds.get(3)).exists());
    }

    // -------------------------------------------------------------------------
    // Empty messageIds -> 400 /problems/validation-error
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchUntrashMessages_emptyMessageIds_returns400WithValidationErrorProblemType")
    void batchUntrashMessages_emptyMessageIds_returns400WithValidationErrorProblemType() throws Exception {
        // Arrange: @NotEmpty on BatchMessageIdsRequest.messageIds rejects before the
        // controller method body runs — no GmailService stubbing needed.
        String requestBody = "{\"messageIds\": []}";

        // Act & Assert
        mockMvc.perform(post(BATCH_UNTRASH_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"));
    }

    // -------------------------------------------------------------------------
    // Oversized list (exceeds batchModifyMaxResults) -> 400
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchUntrashMessages_batchSizeExceedsConfiguredMax_returns400WithValidationErrorProblemType")
    void batchUntrashMessages_batchSizeExceedsConfiguredMax_returns400WithValidationErrorProblemType()
            throws Exception {
        // Arrange: application-test.properties sets
        // gmail-buddy.gmail-api.batch-modify-max-results=10 (decoupled from the
        // permanent-delete batch-delete-max-results cap per FR-009). Real enforcement of
        // that ceiling (validateBatchSize) is unit-tested in GmailServiceBatchTrashTest (T013);
        // here GmailService is fully mocked, so this test verifies the controller's 400
        // contract when the service signals a batch-size violation. No uniqueness
        // constraint applies to messageIds, so a repeated valid id is sufficient.
        String repeatedId = BatchOperationFixtures.validMessageIds(1).get(0);
        List<String> oversizedIds = Collections.nCopies(11, repeatedId);
        when(gmailService.batchUntrashMessages(eq(USER_ID), eq(oversizedIds)))
                .thenThrow(new ValidationException("messageIds size (11) exceeds the configured maximum (10)"));
        String requestBody = objectMapper.writeValueAsString(new BatchMessageIdsRequest(oversizedIds));

        // Act & Assert
        mockMvc.perform(post(BATCH_UNTRASH_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"));
    }

    // -------------------------------------------------------------------------
    // Ill-formed message id -> 400
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchUntrashMessages_illFormedMessageId_returns400WithValidationErrorProblemType")
    void batchUntrashMessages_illFormedMessageId_returns400WithValidationErrorProblemType() throws Exception {
        // Arrange: "not-valid-hex-id!" fails the @GmailMessageId hex-character pattern
        String requestBody = "{\"messageIds\": [\"not-valid-hex-id!\"]}";

        // Act & Assert
        mockMvc.perform(post(BATCH_UNTRASH_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"));
    }
}
