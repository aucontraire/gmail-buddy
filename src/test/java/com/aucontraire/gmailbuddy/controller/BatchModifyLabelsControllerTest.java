package com.aucontraire.gmailbuddy.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aucontraire.gmailbuddy.GmailBuddyApplication;
import com.aucontraire.gmailbuddy.config.TestTokenProviderConfiguration;
import com.aucontraire.gmailbuddy.dto.BatchModifyLabelsByIdRequest;
import com.aucontraire.gmailbuddy.dto.FilterCriteriaWithLabelsDTO;
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
 * Controller-slice contract tests for {@code POST /api/v1/gmail/messages/batchModifyLabels}
 * (feature 005 US2, T014).
 *
 * <p>{@link GmailService} is mocked so no Gmail API calls are made — these are
 * request/response contract tests only. Mirrors the {@code @SpringBootTest} +
 * {@code TestTokenProviderConfiguration} security harness used by
 * {@link BatchTrashControllerTest} and {@link BatchUntrashControllerTest}, so the
 * real {@code SecurityConfig} filter chain and {@code ResponseMapper} bean are
 * exercised rather than re-stubbed.</p>
 *
 * <p>Covers: add-only and combined add+remove success (200), an unknown-but-well-formed
 * label id surfacing as a per-message failure rather than a silent drop (FR-006), the
 * {@code @AssertTrue} no-op-modify rejection (both label lists empty/absent, FR-007),
 * an ill-formed label id, empty/oversized {@code messageIds}, and an unauthenticated
 * request.</p>
 */
@SpringBootTest(classes = GmailBuddyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestTokenProviderConfiguration.class)
@DisplayName("POST /api/v1/gmail/messages/batchModifyLabels — contract (T014)")
class BatchModifyLabelsControllerTest {

    private static final String BATCH_MODIFY_LABELS_ENDPOINT = "/api/v1/gmail/messages/batchModifyLabels";
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
    // (a) Add-only — labelIdsToAdd non-empty, labelIdsToRemove null -> 200 with
    //     successCount == N, ids in successfulOperations
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchModifyLabelsByIds_addOnly_returns200WithSuccessCountMatchingTotal")
    void batchModifyLabelsByIds_addOnly_returns200WithSuccessCountMatchingTotal() throws Exception {
        // Arrange
        List<String> messageIds = BatchOperationFixtures.validMessageIds(3);
        List<String> labelIdsToAdd = List.of("Label_42");
        BulkOperationResult bulkResult = BatchOperationFixtures.buildAllSuccessResult(messageIds);
        when(gmailService.batchModifyLabelsByIds(eq(USER_ID), eq(messageIds), eq(labelIdsToAdd), isNull()))
                .thenReturn(bulkResult);
        String requestBody =
                objectMapper.writeValueAsString(new BatchModifyLabelsByIdRequest(messageIds, labelIdsToAdd, null));

        // Act & Assert
        mockMvc.perform(post(BATCH_MODIFY_LABELS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(3))
                .andExpect(jsonPath("$.failureCount").value(0))
                .andExpect(jsonPath("$.successfulOperations", containsInAnyOrder(messageIds.toArray())));
    }

    // -------------------------------------------------------------------------
    // (b) Combined add + remove in one request -> 200
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchModifyLabelsByIds_addAndRemoveCombined_returns200WithSuccessCountMatchingTotal")
    void batchModifyLabelsByIds_addAndRemoveCombined_returns200WithSuccessCountMatchingTotal() throws Exception {
        // Arrange
        List<String> messageIds = BatchOperationFixtures.validMessageIds(2);
        List<String> labelIdsToAdd = List.of("Label_42");
        List<String> labelIdsToRemove = List.of("UNREAD");
        BulkOperationResult bulkResult = BatchOperationFixtures.buildAllSuccessResult(messageIds);
        when(gmailService.batchModifyLabelsByIds(eq(USER_ID), eq(messageIds), eq(labelIdsToAdd), eq(labelIdsToRemove)))
                .thenReturn(bulkResult);
        String requestBody = objectMapper.writeValueAsString(
                new BatchModifyLabelsByIdRequest(messageIds, labelIdsToAdd, labelIdsToRemove));

        // Act & Assert
        mockMvc.perform(post(BATCH_MODIFY_LABELS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.failureCount").value(0))
                .andExpect(jsonPath("$.successfulOperations", containsInAnyOrder(messageIds.toArray())));
    }

    // -------------------------------------------------------------------------
    // (b.1) X-Gmail-Quota-Used header (T031, updated WI-1 T009) — totalBatchesProcessed * 50
    //     (BATCH_MODIFY_QUOTA_PER_CHUNK, flat native batchModify per-chunk cost)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchModifyLabelsByIds_addOnly_setsGmailQuotaUsedHeaderToChunksTimesFifty")
    void batchModifyLabelsByIds_addOnly_setsGmailQuotaUsedHeaderToChunksTimesFifty() throws Exception {
        // Arrange: ResponseMapper#toBatchOperationResponse computes quotaUsed as
        // totalBatchesProcessed * 50 (native batchModify's flat per-chunk cost, WI-1/FR-008),
        // so 3 messages processed as 1 native chunk -> 50.
        List<String> messageIds = BatchOperationFixtures.validMessageIds(3);
        List<String> labelIdsToAdd = List.of("Label_42");
        BulkOperationResult bulkResult = BatchOperationFixtures.buildAllSuccessResult(messageIds);
        bulkResult.incrementBatchesProcessed(); // 1 native batchModify call for the chunk
        when(gmailService.batchModifyLabelsByIds(eq(USER_ID), eq(messageIds), eq(labelIdsToAdd), isNull()))
                .thenReturn(bulkResult);
        String requestBody =
                objectMapper.writeValueAsString(new BatchModifyLabelsByIdRequest(messageIds, labelIdsToAdd, null));

        // Act & Assert
        mockMvc.perform(post(BATCH_MODIFY_LABELS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Gmail-Quota-Used", "50"));
    }

    // -------------------------------------------------------------------------
    // (c) Unknown-but-well-formed label id -> per-message failure, not a silent
    //     drop (FR-006) — still HTTP 200 (best-effort semantics)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchModifyLabelsByIds_unknownLabelId_returns200WithFailureRecordedInFailedOperations")
    void batchModifyLabelsByIds_unknownLabelId_returns200WithFailureRecordedInFailedOperations() throws Exception {
        // Arrange: "Label_does_not_exist" is well-formed per @GmailLabelId but does not
        // correspond to a real Gmail label — Gmail's API rejects it per-message rather
        // than failing the whole batch, and this endpoint performs no name-to-id
        // resolution that could otherwise mask the failure (FR-006).
        List<String> messageIds = BatchOperationFixtures.validMessageIds(1);
        List<String> labelIdsToAdd = List.of("Label_does_not_exist");
        BulkOperationResult bulkResult = BatchOperationFixtures.buildAllFailResult(messageIds);
        when(gmailService.batchModifyLabelsByIds(eq(USER_ID), eq(messageIds), eq(labelIdsToAdd), isNull()))
                .thenReturn(bulkResult);
        String requestBody =
                objectMapper.writeValueAsString(new BatchModifyLabelsByIdRequest(messageIds, labelIdsToAdd, null));

        // Act & Assert
        mockMvc.perform(post(BATCH_MODIFY_LABELS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(0))
                .andExpect(jsonPath("$.failureCount").value(1))
                .andExpect(jsonPath("$.failedOperations." + messageIds.get(0)).exists());
    }

    // -------------------------------------------------------------------------
    // (d) Both labelIdsToAdd and labelIdsToRemove empty/absent -> 400
    //     (the @AssertTrue no-op-modify constraint, FR-007)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchModifyLabelsByIds_bothLabelListsEmpty_returns400WithValidationErrorProblemType")
    void batchModifyLabelsByIds_bothLabelListsEmpty_returns400WithValidationErrorProblemType() throws Exception {
        // Arrange: BatchModifyLabelsByIdRequest.isAtLeastOneLabelListNonEmpty() rejects
        // before the controller method body runs — no GmailService stubbing needed.
        String validId = BatchOperationFixtures.validMessageIds(1).get(0);
        String requestBody =
                String.format("{\"messageIds\": [\"%s\"], \"labelIdsToAdd\": [], \"labelIdsToRemove\": []}", validId);

        // Act & Assert
        mockMvc.perform(post(BATCH_MODIFY_LABELS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"));
    }

    // -------------------------------------------------------------------------
    // (e) Ill-formed label id -> 400
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchModifyLabelsByIds_illFormedLabelId_returns400WithValidationErrorProblemType")
    void batchModifyLabelsByIds_illFormedLabelId_returns400WithValidationErrorProblemType() throws Exception {
        // Arrange: "Label-42" contains a hyphen, which @GmailLabelId's
        // [A-Za-z0-9_]{1,128} pattern rejects.
        String validId = BatchOperationFixtures.validMessageIds(1).get(0);
        String requestBody = String.format("{\"messageIds\": [\"%s\"], \"labelIdsToAdd\": [\"Label-42\"]}", validId);

        // Act & Assert
        mockMvc.perform(post(BATCH_MODIFY_LABELS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"));
    }

    // -------------------------------------------------------------------------
    // (e.1) Null element in messageIds -> 400 (T036 — @NotNull on list element)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchModifyLabelsByIds_nullMessageIdElement_returns400WithValidationErrorProblemType")
    void batchModifyLabelsByIds_nullMessageIdElement_returns400WithValidationErrorProblemType() throws Exception {
        // Arrange: the @NotNull on BatchModifyLabelsByIdRequest.messageIds' list element
        // rejects a null entry before the controller method body runs — no GmailService
        // stubbing needed. labelIdsToAdd is well-formed so only the messageIds element
        // constraint is under test.
        String requestBody = "{\"messageIds\": [null], \"labelIdsToAdd\": [\"Label_42\"]}";

        // Act & Assert
        mockMvc.perform(post(BATCH_MODIFY_LABELS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"));
    }

    // -------------------------------------------------------------------------
    // (e.2) Null element in labelIdsToAdd -> 400 (T036 — @NotNull on list element)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchModifyLabelsByIds_nullLabelIdToAddElement_returns400WithValidationErrorProblemType")
    void batchModifyLabelsByIds_nullLabelIdToAddElement_returns400WithValidationErrorProblemType() throws Exception {
        // Arrange: the @NotNull on BatchModifyLabelsByIdRequest.labelIdsToAdd's list
        // element rejects a null entry before the controller method body runs — no
        // GmailService stubbing needed.
        String validId = BatchOperationFixtures.validMessageIds(1).get(0);
        String requestBody = String.format("{\"messageIds\": [\"%s\"], \"labelIdsToAdd\": [null]}", validId);

        // Act & Assert
        mockMvc.perform(post(BATCH_MODIFY_LABELS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"));
    }

    // -------------------------------------------------------------------------
    // (f) Empty messageIds -> 400
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchModifyLabelsByIds_emptyMessageIds_returns400WithValidationErrorProblemType")
    void batchModifyLabelsByIds_emptyMessageIds_returns400WithValidationErrorProblemType() throws Exception {
        // Arrange: @NotEmpty on BatchModifyLabelsByIdRequest.messageIds rejects before
        // the controller method body runs — no GmailService stubbing needed.
        String requestBody = "{\"messageIds\": [], \"labelIdsToAdd\": [\"Label_42\"]}";

        // Act & Assert
        mockMvc.perform(post(BATCH_MODIFY_LABELS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"));
    }

    // -------------------------------------------------------------------------
    // (g) Oversized messageIds (exceeds the configured batchDeleteMaxResults
    //     ceiling) -> 400
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchModifyLabelsByIds_batchSizeExceedsConfiguredMax_returns400WithValidationErrorProblemType")
    void batchModifyLabelsByIds_batchSizeExceedsConfiguredMax_returns400WithValidationErrorProblemType()
            throws Exception {
        // Arrange: GmailService is fully mocked here, so this test verifies the
        // controller's 400 contract when the service signals a batch-size violation —
        // real enforcement of the configured ceiling (validateBatchSize) is unit-tested
        // at the service layer, mirroring GmailServiceBatchTrashTest (T013) for US1.
        // No uniqueness constraint applies to messageIds, so a repeated valid id is
        // sufficient.
        String repeatedId = BatchOperationFixtures.validMessageIds(1).get(0);
        List<String> oversizedIds = Collections.nCopies(11, repeatedId);
        List<String> labelIdsToAdd = List.of("Label_42");
        when(gmailService.batchModifyLabelsByIds(eq(USER_ID), eq(oversizedIds), eq(labelIdsToAdd), isNull()))
                .thenThrow(new ValidationException("messageIds size (11) exceeds the configured maximum (10)"));
        String requestBody =
                objectMapper.writeValueAsString(new BatchModifyLabelsByIdRequest(oversizedIds, labelIdsToAdd, null));

        // Act & Assert
        mockMvc.perform(post(BATCH_MODIFY_LABELS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"));
    }

    // -------------------------------------------------------------------------
    // (g.1) FR-008 sibling check — the pre-existing, name-based
    //     POST /messages/filter/modifyLabels endpoint is unaffected by this
    //     feature and still returns its original LabelModificationResponse shape
    //     (T035). Housed here (rather than a new file) because this class already
    //     carries the real ResponseMapper bean needed to exercise the actual
    //     response-shaping logic, unlike ControllerValidationTest which mocks it.
    // -------------------------------------------------------------------------

    @Test
    @DisplayName(
            "modifyMessagesLabelsByFilter_existingByFilterEndpoint_returns200WithUnchangedLabelModificationResponseShape")
    void modifyMessagesLabelsByFilter_existingByFilterEndpoint_returns200WithUnchangedLabelModificationResponseShape()
            throws Exception {
        // Arrange: unlike batchModifyLabelsByIds, this endpoint resolves label names
        // (not raw ids) against filter-matched messages — its request/response contract
        // predates feature 005 and must be unchanged by it (FR-008).
        List<String> messageIds = BatchOperationFixtures.validMessageIds(2);
        BulkOperationResult bulkResult = BatchOperationFixtures.buildAllSuccessResult(messageIds);
        when(gmailService.modifyMessagesLabelsByFilterCriteria(eq(USER_ID), any(FilterCriteriaWithLabelsDTO.class)))
                .thenReturn(bulkResult);

        FilterCriteriaWithLabelsDTO dto = new FilterCriteriaWithLabelsDTO();
        dto.setFrom("test@example.com");
        dto.setLabelsToAdd(List.of("important"));
        String requestBody = objectMapper.writeValueAsString(dto);

        // Act & Assert
        mockMvc.perform(post("/api/v1/gmail/messages/filter/modifyLabels")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messagesModified").value(2))
                .andExpect(jsonPath("$.labelsAdded[0]").value("important"))
                .andExpect(jsonPath("$.affectedMessageIds", containsInAnyOrder(messageIds.toArray())));
    }

    // -------------------------------------------------------------------------
    // (h) Unauthenticated -> redirected to the OAuth2 login entry point
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchModifyLabelsByIds_unauthenticated_redirectsToOAuth2LoginEntryPoint")
    void batchModifyLabelsByIds_unauthenticated_redirectsToOAuth2LoginEntryPoint() throws Exception {
        // Arrange: SecurityConfig registers LoginUrlAuthenticationEntryPoint("/oauth2/authorization/google")
        // as the authenticationEntryPoint, so unauthenticated requests receive a 302 redirect
        // rather than a raw 401 — the same behavior BatchTrashControllerTest documents for
        // every other authenticated endpoint on this controller.
        SecurityContextHolder.clearContext();
        List<String> messageIds = BatchOperationFixtures.validMessageIds(1);
        String requestBody = objectMapper.writeValueAsString(
                new BatchModifyLabelsByIdRequest(messageIds, List.of("Label_42"), null));

        // Act & Assert
        mockMvc.perform(post(BATCH_MODIFY_LABELS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isFound());
    }
}
