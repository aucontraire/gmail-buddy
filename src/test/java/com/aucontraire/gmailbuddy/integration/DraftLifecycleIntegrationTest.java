package com.aucontraire.gmailbuddy.integration;

import com.aucontraire.gmailbuddy.exception.ResourceNotFoundException;
import com.aucontraire.gmailbuddy.repository.GmailRepository;
import com.aucontraire.gmailbuddy.service.DraftDetailResult;
import com.aucontraire.gmailbuddy.service.DraftListResult;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests verifying FR-001a (sent draft absent from list) and the
 * analogous delete-then-list scenario end-to-end through the controller layer
 * (T043).
 *
 * <p>Two nested classes correspond to the two lifecycle scenarios:</p>
 * <ol>
 *   <li><strong>Send lifecycle</strong>: list (present) → get (200) → send (200)
 *       → list (absent) → get (404). Verifies FR-001a: a draft is permanently
 *       removed from the drafts list after it is sent.</li>
 *   <li><strong>Delete lifecycle</strong>: list (present) → delete (204)
 *       → list (absent) → get (404). Verifies the symmetric guarantee for the
 *       DELETE endpoint — a hard-deleted draft is absent from subsequent list
 *       and get calls.</li>
 * </ol>
 *
 * <p>The full Spring application context is loaded ({@code @SpringBootTest +
 * @AutoConfigureMockMvc}) so the real controller routing, filter chain, and
 * exception handling ({@code GlobalExceptionHandler}) run. {@code GmailService}
 * is mocked at the service boundary — the mocked service is configured with
 * sequential stubs (using Mockito's {@code thenReturn(...).thenReturn(...)})
 * to simulate state changes across HTTP calls without a real Gmail API.</p>
 *
 * <p>Draft identifiers must match {@code [A-Za-z0-9_-]{1,128}} (Gmail's opaque
 * format — letter-prefix + alphanumeric, e.g., {@code "r9068706262700056809"}).
 * The {@code @Pattern} constraint on the {@code {draftId}} path variable rejects
 * path-traversal characters before reaching the service.</p>
 *
 * <p>The {@code @WithMockUser} annotation injects a mock Spring Security
 * principal so the OAuth2 redirect is bypassed. This mirrors the browser-session
 * path used in {@code CreateDraftIntegrationTest} and
 * {@code SendDraftIntegrationTest}.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Draft CRUD endpoints — lifecycle integration (FR-001a) (T043)")
class DraftLifecycleIntegrationTest {

    // ---------------------------------------------------------------------------
    // Constants
    // ---------------------------------------------------------------------------

    private static final String DRAFTS_BASE       = "/api/v1/gmail/drafts";
    // IDs matching @Pattern(regexp = "[A-Za-z0-9_-]{1,128}") — Gmail's opaque draft format
    private static final String DRAFT_ID          = "abc123def456";
    private static final String DRAFT_MESSAGE_ID  = "19a2b3c4d5e60001";
    private static final String SENT_MESSAGE_ID   = "19a2b3c4d5e60002";
    private static final String THREAD_ID         = "19a2b3c4d5e60001";

    // ---------------------------------------------------------------------------
    // Spring-managed beans
    // ---------------------------------------------------------------------------

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GmailService gmailService;

    @MockitoBean
    private GmailRepository gmailRepository;

    @MockitoBean
    private GoogleTokenValidator tokenValidator;

    @BeforeEach
    void resetMocks() {
        reset(gmailService, gmailRepository, tokenValidator);
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Returns a realistic {@link DraftDetailResult} with all fields populated,
     * representing a draft that exists before any state-changing operation.
     */
    private DraftDetailResult existingDraft() {
        return new DraftDetailResult(
                DRAFT_ID,
                DRAFT_MESSAGE_ID,
                THREAD_ID,
                List.of("recruiter@example.com"),
                List.of(),
                List.of(),
                "Re: Software Engineer Application",
                "Hi there, following up on my application...",
                "Hi there, following up on my application for the role.",
                "text",
                null,
                List.of()
        );
    }

    /**
     * Returns a {@link DraftListResult} containing the single draft — models the
     * state before the send or delete operation.
     */
    private DraftListResult listWithDraft() {
        return new DraftListResult(List.of(existingDraft()), null, 1);
    }

    /**
     * Returns a {@link DraftListResult} with an empty list — models the state
     * AFTER the draft has been sent or deleted.
     */
    private DraftListResult emptyList() {
        return new DraftListResult(List.of(), null, 0);
    }

    // ===========================================================================
    // Nested: Send lifecycle (FR-001a)
    // ===========================================================================

    @Nested
    @DisplayName("Send lifecycle: list → get → send → list (absent) → get (404)")
    class SendLifecycleTests {

        /**
         * Full send lifecycle sequence exercised in a single test method to verify
         * the causal chain: after {@code POST /drafts/{id}/send} succeeds, the draft
         * is absent from a subsequent {@code GET /drafts} and yields 404 on
         * {@code GET /drafts/{id}}.
         *
         * <p>Mock strategy:</p>
         * <ul>
         *   <li>{@code listDrafts}: first call returns the draft; second call (post-send)
         *       returns an empty list.</li>
         *   <li>{@code getDraft}: first call returns the full draft; second call (post-send)
         *       throws {@code ResourceNotFoundException}.</li>
         *   <li>{@code sendDraft}: single call returns a {@link SentMessageResult}.</li>
         * </ul>
         */
        @Test
        @WithMockUser
        @DisplayName("sendDraft_thenList_draftIsAbsentFromList_FR001a")
        void sendDraft_thenList_draftIsAbsentFromList_FR001a() throws Exception {
            // Arrange — sequential stub: first listDrafts returns the draft, second returns empty.
            when(gmailService.listDrafts(anyString(), isNull(), anyInt()))
                    .thenReturn(listWithDraft())
                    .thenReturn(emptyList());

            // Arrange — sequential stub: first getDraft returns the draft, second throws 404.
            when(gmailService.getDraft(anyString(), anyString()))
                    .thenReturn(existingDraft())
                    .thenThrow(new ResourceNotFoundException(
                            "Draft not found: " + DRAFT_ID, "DRAFT_NOT_FOUND"));

            // Arrange — sendDraft stub.
            when(gmailService.sendDraft(anyString(), anyString()))
                    .thenReturn(new SentMessageResult(SENT_MESSAGE_ID, THREAD_ID));

            // Step 1: GET /drafts → 200, draft-001 present in results
            mockMvc.perform(get(DRAFTS_BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results").isArray())
                    .andExpect(jsonPath("$.results.length()").value(1))
                    .andExpect(jsonPath("$.results[0].id").value(DRAFT_ID));

            // Step 2: GET /drafts/{id} → 200, full DraftDetailResponse
            mockMvc.perform(get(DRAFTS_BASE + "/{draftId}", DRAFT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(DRAFT_ID))
                    .andExpect(jsonPath("$.to[0]").value("recruiter@example.com"))
                    .andExpect(jsonPath("$.subject").value("Re: Software Engineer Application"));

            // Step 3: POST /drafts/{id}/send → 200 (state transition, no Location header)
            mockMvc.perform(post(DRAFTS_BASE + "/{draftId}/send", DRAFT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.messageId").value(SENT_MESSAGE_ID))
                    .andExpect(jsonPath("$.status").value("SENT"));

            // Step 4: GET /drafts → 200, draft absent (FR-001a: sent draft removed from list)
            mockMvc.perform(get(DRAFTS_BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results").isArray())
                    .andExpect(jsonPath("$.results.length()").value(0));

            // Step 5: GET /drafts/{id} → 404 with /problems/resource-not-found
            mockMvc.perform(get(DRAFTS_BASE + "/{draftId}", DRAFT_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.type").value("/problems/resource-not-found"));
        }

        @Test
        @WithMockUser
        @DisplayName("sendDraft_thenGetById_returns404WithCorrectProblemType")
        void sendDraft_thenGetById_returns404WithCorrectProblemType() throws Exception {
            // Arrange: getDraft is already absent (draft was sent before this request)
            when(gmailService.getDraft(anyString(), anyString()))
                    .thenThrow(new ResourceNotFoundException(
                            "Draft not found: " + DRAFT_ID, "DRAFT_NOT_FOUND"));

            // Act & Assert: 404 response carries the RFC 7807 problem type
            mockMvc.perform(get(DRAFTS_BASE + "/{draftId}", DRAFT_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.type").value("/problems/resource-not-found"))
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @WithMockUser
        @DisplayName("listDrafts_afterSend_returnsEmptyResultsNotA404")
        void listDrafts_afterSend_returnsEmptyResultsNotA404() throws Exception {
            // Arrange: after send, list returns empty — NOT a 404. An empty queue is
            // represented as 200 OK with results=[] per spec (FR-001a note).
            when(gmailService.listDrafts(anyString(), isNull(), anyInt()))
                    .thenReturn(emptyList());

            // Act & Assert
            mockMvc.perform(get(DRAFTS_BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results").isArray())
                    .andExpect(jsonPath("$.results.length()").value(0))
                    .andExpect(jsonPath("$.nextPageToken").doesNotExist());
        }
    }

    // ===========================================================================
    // Nested: Delete lifecycle (FR-001a analogue)
    // ===========================================================================

    @Nested
    @DisplayName("Delete lifecycle: list → delete → list (absent) → get (404)")
    class DeleteLifecycleTests {

        /**
         * Full delete lifecycle sequence: list confirms presence, delete succeeds,
         * subsequent list confirms absence, subsequent get returns 404.
         *
         * <p>Mock strategy:</p>
         * <ul>
         *   <li>{@code listDrafts}: first call returns the draft; second call (post-delete)
         *       returns an empty list.</li>
         *   <li>{@code deleteDraft}: void method — doNothing() on first call.</li>
         *   <li>{@code getDraft}: call after delete throws {@code ResourceNotFoundException}.</li>
         * </ul>
         */
        @Test
        @WithMockUser
        @DisplayName("deleteDraft_thenList_draftIsAbsentFromList")
        void deleteDraft_thenList_draftIsAbsentFromList() throws Exception {
            // Arrange — sequential listDrafts: first returns the draft, second returns empty.
            when(gmailService.listDrafts(anyString(), isNull(), anyInt()))
                    .thenReturn(listWithDraft())
                    .thenReturn(emptyList());

            // Arrange — deleteDraft: void method; Mockito default is doNothing().
            doNothing().when(gmailService).deleteDraft(anyString(), anyString());

            // Arrange — getDraft after delete throws 404.
            when(gmailService.getDraft(anyString(), anyString()))
                    .thenThrow(new ResourceNotFoundException(
                            "Draft not found: " + DRAFT_ID, "DRAFT_NOT_FOUND"));

            // Step 1: GET /drafts → 200, draft present
            mockMvc.perform(get(DRAFTS_BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.length()").value(1))
                    .andExpect(jsonPath("$.results[0].id").value(DRAFT_ID));

            // Step 2: DELETE /drafts/{id} → 204 No Content, empty response body
            mockMvc.perform(delete(DRAFTS_BASE + "/{draftId}", DRAFT_ID))
                    .andExpect(status().isNoContent());

            // Step 3: GET /drafts → 200, draft absent
            mockMvc.perform(get(DRAFTS_BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results").isArray())
                    .andExpect(jsonPath("$.results.length()").value(0));

            // Step 4: GET /drafts/{id} → 404 with /problems/resource-not-found
            mockMvc.perform(get(DRAFTS_BASE + "/{draftId}", DRAFT_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.type").value("/problems/resource-not-found"));
        }

        @Test
        @WithMockUser
        @DisplayName("deleteDraft_success_returns204WithEmptyBody")
        void deleteDraft_success_returns204WithEmptyBody() throws Exception {
            // Arrange: Mockito default for void is doNothing()
            doNothing().when(gmailService).deleteDraft(anyString(), anyString());

            // Act & Assert: 204 No Content — no body
            mockMvc.perform(delete(DRAFTS_BASE + "/{draftId}", DRAFT_ID))
                    .andExpect(status().isNoContent());
        }

        @Test
        @WithMockUser
        @DisplayName("deleteDraft_alreadyDeleted_returns404WithCorrectProblemType")
        void deleteDraft_alreadyDeleted_returns404WithCorrectProblemType() throws Exception {
            // Arrange: deleteDraft throws ResourceNotFoundException (idempotent per FR-011 —
            // same 404 response whether never-existed or already-deleted).
            doThrow(new ResourceNotFoundException(
                    "Draft not found: " + DRAFT_ID, "DRAFT_NOT_FOUND"))
                    .when(gmailService).deleteDraft(anyString(), anyString());

            // Act & Assert
            mockMvc.perform(delete(DRAFTS_BASE + "/{draftId}", DRAFT_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.type").value("/problems/resource-not-found"))
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @WithMockUser
        @DisplayName("listDrafts_afterDelete_returnsEmptyResultsNotA404")
        void listDrafts_afterDelete_returnsEmptyResultsNotA404() throws Exception {
            // Arrange: empty queue after delete is 200 OK with results=[], not 404
            when(gmailService.listDrafts(anyString(), isNull(), anyInt()))
                    .thenReturn(emptyList());

            // Act & Assert
            mockMvc.perform(get(DRAFTS_BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results").isArray())
                    .andExpect(jsonPath("$.results.length()").value(0));
        }
    }

    // ===========================================================================
    // Nested: Cross-lifecycle — combined send + delete scenarios
    // ===========================================================================

    @Nested
    @DisplayName("Cross-lifecycle: sequential state verification")
    class CrossLifecycleTests {

        @Test
        @WithMockUser
        @DisplayName("listDrafts_multipleItems_thenDeleteOne_listShrinks")
        void listDrafts_multipleItems_thenDeleteOne_listShrinks() throws Exception {
            // Arrange: initial list has two drafts; after delete one remains.
            DraftDetailResult draft1 = new DraftDetailResult(
                    DRAFT_ID, DRAFT_MESSAGE_ID, THREAD_ID,
                    List.of("alice@example.com"), List.of(), List.of(),
                    "Draft 1", "snippet 1", "body 1", "text", null, List.of()
            );
            String secondDraftId = "cafe0000beef0001";
            DraftDetailResult draft2 = new DraftDetailResult(
                    secondDraftId, "cafe0000beef0002", "cafe0000beef0001",
                    List.of("bob@example.com"), List.of(), List.of(),
                    "Draft 2", "snippet 2", "body 2", "text", null, List.of()
            );

            DraftListResult twoItems  = new DraftListResult(List.of(draft1, draft2), null, 2);
            DraftListResult oneItem   = new DraftListResult(List.of(draft2), null, 1);

            when(gmailService.listDrafts(anyString(), isNull(), anyInt()))
                    .thenReturn(twoItems)
                    .thenReturn(oneItem);
            doNothing().when(gmailService).deleteDraft(anyString(), anyString());

            // Step 1: list → 2 items
            mockMvc.perform(get(DRAFTS_BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.length()").value(2));

            // Step 2: delete the first draft
            mockMvc.perform(delete(DRAFTS_BASE + "/{draftId}", DRAFT_ID))
                    .andExpect(status().isNoContent());

            // Step 3: list → 1 item (the second draft remains)
            mockMvc.perform(get(DRAFTS_BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.length()").value(1))
                    .andExpect(jsonPath("$.results[0].id").value(secondDraftId));
        }

        @Test
        @WithMockUser
        @DisplayName("getDraft_beforeAndAfterSend_200ThenCorrect404ProblemType")
        void getDraft_beforeAndAfterSend_200ThenCorrect404ProblemType() throws Exception {
            // Arrange: first getDraft succeeds; second (post-send) throws 404.
            when(gmailService.getDraft(anyString(), anyString()))
                    .thenReturn(existingDraft())
                    .thenThrow(new ResourceNotFoundException(
                            "Draft not found: " + DRAFT_ID, "DRAFT_NOT_FOUND"));
            when(gmailService.sendDraft(anyString(), anyString()))
                    .thenReturn(new SentMessageResult(SENT_MESSAGE_ID, THREAD_ID));

            // Step 1: GET /drafts/{id} → 200
            mockMvc.perform(get(DRAFTS_BASE + "/{draftId}", DRAFT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(DRAFT_ID));

            // Step 2: POST /drafts/{id}/send → 200
            mockMvc.perform(post(DRAFTS_BASE + "/{draftId}/send", DRAFT_ID))
                    .andExpect(status().isOk());

            // Step 3: GET /drafts/{id} → 404 with /problems/resource-not-found
            mockMvc.perform(get(DRAFTS_BASE + "/{draftId}", DRAFT_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.type").value("/problems/resource-not-found"))
                    .andExpect(jsonPath("$.status").value(404));
        }
    }
}
