package com.aucontraire.gmailbuddy.integration;

import com.aucontraire.gmailbuddy.dto.SendMessageDTO;
import com.aucontraire.gmailbuddy.fixture.SendMessageRequestFixtures;
import com.aucontraire.gmailbuddy.repository.GmailRepository;
import com.aucontraire.gmailbuddy.service.DraftCreationResult;
import com.aucontraire.gmailbuddy.service.DraftDetailResult;
import com.aucontraire.gmailbuddy.service.DraftListResult;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests verifying FR-019 ({@code X-Gmail-Quota-Used}) and FR-019a
 * ({@code X-RateLimit-Limit}, {@code X-RateLimit-Remaining}, {@code X-RateLimit-Reset})
 * response headers are present on all 4 new draft CRUD endpoints (T042).
 *
 * <p>Uses the full Spring application context ({@code @SpringBootTest +
 * @AutoConfigureMockMvc}) so the real {@code RateLimitInterceptor} and
 * {@code ResponseHeaderFilter} participate in the filter chain — unlike
 * {@code @WebMvcTest} which loads only the web slice and would not wire those
 * components automatically.</p>
 *
 * <p>{@code GmailService} is mocked at the service boundary so no real Gmail
 * API calls are made. {@code GmailRepository} is also mocked to satisfy Spring
 * context wiring. The test uses {@code @WithMockUser} (browser-session path)
 * for simplicity — the header-verification concern is authentication-agnostic,
 * and the session path exercises the same filter chain.</p>
 *
 * <p>Quota values under test:</p>
 * <ul>
 *   <li>GET /drafts — pre-execution estimate {@code 1 + DEFAULT_LIMIT * 5};
 *       post-execution overwrite from controller: {@code 1 + N * 5} where N is
 *       the actual item count returned by the mocked service</li>
 *   <li>GET /drafts/{id} — 5 (from {@code GmailQuotaEstimator.estimateGetDraftQuota()})</li>
 *   <li>DELETE /drafts/{id} — 10 (from {@code estimateDeleteDraftQuota()})</li>
 *   <li>PUT /drafts/{id} — 15 (from {@code estimateUpdateDraftQuota()}, no threading)</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Draft CRUD endpoints — rate-limit and quota response headers (T042)")
class DraftsRateLimitHeadersIntegrationTest {

    // ---------------------------------------------------------------------------
    // Constants
    // ---------------------------------------------------------------------------

    private static final String DRAFTS_BASE     = "/api/v1/gmail/drafts";
    // Draft ID matching @Pattern(regexp = "[A-Za-z0-9_-]{1,128}") on the path variable.
    // Gmail draft IDs use letter-prefixed alphanumeric format (e.g., "r9068706262700056809").
    private static final String VALID_DRAFT_ID  = "abc123def456";
    private static final String DRAFT_MESSAGE_ID = "19a2b3c4d5e6f700";
    private static final String THREAD_ID        = "19a2b3c4d5e6f700";

    // ---------------------------------------------------------------------------
    // Spring-managed beans
    // ---------------------------------------------------------------------------

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    // Helpers — shared stub builders
    // ---------------------------------------------------------------------------

    /**
     * Returns a {@link DraftDetailResult} with all required fields populated.
     * Used both as the GET /drafts/{id} service stub and as the element type
     * inside the GET /drafts list stub.
     */
    private DraftDetailResult stubDraftDetail() {
        return new DraftDetailResult(
                VALID_DRAFT_ID,
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

    // ===========================================================================
    // Nested: GET /drafts — list endpoint
    // ===========================================================================

    @Nested
    @DisplayName("GET /api/v1/gmail/drafts — list drafts")
    class ListDraftsHeaderTests {

        @Test
        @WithMockUser
        @DisplayName("listDrafts_success_allFourRateLimitHeadersPresent")
        void listDrafts_success_allFourRateLimitHeadersPresent() throws Exception {
            // Arrange: service returns a single-item list. Controller rewrites quota to
            // 1 + 1*5 = 6 after execution.
            DraftDetailResult item = stubDraftDetail();
            DraftListResult listResult = new DraftListResult(List.of(item), null, 1);
            when(gmailService.listDrafts(anyString(), any(), anyInt())).thenReturn(listResult);

            // Act & Assert
            mockMvc.perform(get(DRAFTS_BASE))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("X-Gmail-Quota-Used"))
                    .andExpect(header().exists("X-RateLimit-Limit"))
                    .andExpect(header().exists("X-RateLimit-Remaining"))
                    .andExpect(header().exists("X-RateLimit-Reset"));
        }

        @Test
        @WithMockUser
        @DisplayName("listDrafts_oneItem_quotaHeaderReflectsActualOneItemCost")
        void listDrafts_oneItem_quotaHeaderReflectsActualOneItemCost() throws Exception {
            // Arrange: 1 draft returned → actual quota = 1 + 1*5 = 6
            DraftDetailResult item = stubDraftDetail();
            DraftListResult listResult = new DraftListResult(List.of(item), null, 1);
            when(gmailService.listDrafts(anyString(), any(), anyInt())).thenReturn(listResult);

            // Act
            MvcResult result = mockMvc.perform(get(DRAFTS_BASE))
                    .andExpect(status().isOk())
                    .andReturn();

            // Assert: controller updates ATTR_GMAIL_QUOTA_USED post-execution to 1 + N*5
            String quotaUsed = result.getResponse().getHeader("X-Gmail-Quota-Used");
            assertThat(quotaUsed).isNotNull();
            assertThat(Integer.parseInt(quotaUsed)).isEqualTo(6); // 1 + 1*5
        }

        @Test
        @WithMockUser
        @DisplayName("listDrafts_emptyList_quotaHeaderReflectsBaseListCostOnly")
        void listDrafts_emptyList_quotaHeaderReflectsBaseListCostOnly() throws Exception {
            // Arrange: 0 drafts returned → actual quota = 1 + 0*5 = 1
            DraftListResult emptyResult = new DraftListResult(List.of(), null, 0);
            when(gmailService.listDrafts(anyString(), any(), anyInt())).thenReturn(emptyResult);

            // Act
            MvcResult result = mockMvc.perform(get(DRAFTS_BASE))
                    .andExpect(status().isOk())
                    .andReturn();

            // Assert
            String quotaUsed = result.getResponse().getHeader("X-Gmail-Quota-Used");
            assertThat(quotaUsed).isNotNull();
            assertThat(Integer.parseInt(quotaUsed)).isEqualTo(1); // 1 + 0*5
        }

        @Test
        @WithMockUser
        @DisplayName("listDrafts_threeItems_quotaHeaderReflectsActualThreeItemCost")
        void listDrafts_threeItems_quotaHeaderReflectsActualThreeItemCost() throws Exception {
            // Arrange: 3 drafts returned → actual quota = 1 + 3*5 = 16
            DraftDetailResult item = stubDraftDetail();
            DraftListResult listResult = new DraftListResult(List.of(item, item, item), null, 3);
            when(gmailService.listDrafts(anyString(), any(), anyInt())).thenReturn(listResult);

            // Act
            MvcResult result = mockMvc.perform(get(DRAFTS_BASE))
                    .andExpect(status().isOk())
                    .andReturn();

            // Assert
            String quotaUsed = result.getResponse().getHeader("X-Gmail-Quota-Used");
            assertThat(quotaUsed).isNotNull();
            assertThat(Integer.parseInt(quotaUsed)).isEqualTo(16); // 1 + 3*5
        }

        @Test
        @WithMockUser
        @DisplayName("listDrafts_invalidLimit_400ErrorResponseStillHasRateLimitHeaders")
        void listDrafts_invalidLimit_400ErrorResponseStillHasRateLimitHeaders() throws Exception {
            // Arrange: limit=0 violates @Min(1) — service should never be called.
            // Act & Assert: even 400 error responses carry the rate-limit headers
            // because RateLimitInterceptor runs in preHandle (before validation).
            mockMvc.perform(get(DRAFTS_BASE).param("limit", "0"))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().exists("X-RateLimit-Limit"))
                    .andExpect(header().exists("X-RateLimit-Remaining"))
                    .andExpect(header().exists("X-RateLimit-Reset"));
        }
    }

    // ===========================================================================
    // Nested: GET /drafts/{draftId} — get endpoint
    // ===========================================================================

    @Nested
    @DisplayName("GET /api/v1/gmail/drafts/{draftId} — get draft detail")
    class GetDraftHeaderTests {

        @Test
        @WithMockUser
        @DisplayName("getDraft_success_allFourRateLimitHeadersPresent")
        void getDraft_success_allFourRateLimitHeadersPresent() throws Exception {
            // Arrange
            when(gmailService.getDraft(anyString(), anyString())).thenReturn(stubDraftDetail());

            // Act & Assert
            mockMvc.perform(get(DRAFTS_BASE + "/{draftId}", VALID_DRAFT_ID))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("X-Gmail-Quota-Used"))
                    .andExpect(header().exists("X-RateLimit-Limit"))
                    .andExpect(header().exists("X-RateLimit-Remaining"))
                    .andExpect(header().exists("X-RateLimit-Reset"));
        }

        @Test
        @WithMockUser
        @DisplayName("getDraft_success_quotaHeaderIs5")
        void getDraft_success_quotaHeaderIs5() throws Exception {
            // Arrange
            when(gmailService.getDraft(anyString(), anyString())).thenReturn(stubDraftDetail());

            // Act
            MvcResult result = mockMvc.perform(get(DRAFTS_BASE + "/{draftId}", VALID_DRAFT_ID))
                    .andExpect(status().isOk())
                    .andReturn();

            // Assert: GmailQuotaEstimator.estimateGetDraftQuota() == 5
            String quotaUsed = result.getResponse().getHeader("X-Gmail-Quota-Used");
            assertThat(quotaUsed).isNotNull();
            assertThat(Integer.parseInt(quotaUsed)).isEqualTo(5);
        }

        @Test
        @WithMockUser
        @DisplayName("getDraft_invalidIdFormat_400ErrorResponseStillHasRateLimitHeaders")
        void getDraft_invalidIdFormat_400ErrorResponseStillHasRateLimitHeaders() throws Exception {
            // Arrange: ID contains '.' which violates @Pattern([A-Za-z0-9_-]{1,128}).
            // Period is URL-safe (not stripped by URI parsing), so the validator sees the full string.
            // Act & Assert
            mockMvc.perform(get(DRAFTS_BASE + "/bad.id"))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().exists("X-RateLimit-Limit"))
                    .andExpect(header().exists("X-RateLimit-Remaining"))
                    .andExpect(header().exists("X-RateLimit-Reset"));
        }
    }

    // ===========================================================================
    // Nested: DELETE /drafts/{draftId} — delete endpoint
    // ===========================================================================

    @Nested
    @DisplayName("DELETE /api/v1/gmail/drafts/{draftId} — delete draft")
    class DeleteDraftHeaderTests {

        @Test
        @WithMockUser
        @DisplayName("deleteDraft_success_allFourRateLimitHeadersPresent")
        void deleteDraft_success_allFourRateLimitHeadersPresent() throws Exception {
            // Arrange: deleteDraft() is void — no stub return value needed.
            // The doNothing() default for void methods is automatically applied by Mockito.

            // Act & Assert
            mockMvc.perform(delete(DRAFTS_BASE + "/{draftId}", VALID_DRAFT_ID))
                    .andExpect(status().isNoContent())
                    .andExpect(header().exists("X-Gmail-Quota-Used"))
                    .andExpect(header().exists("X-RateLimit-Limit"))
                    .andExpect(header().exists("X-RateLimit-Remaining"))
                    .andExpect(header().exists("X-RateLimit-Reset"));
        }

        @Test
        @WithMockUser
        @DisplayName("deleteDraft_success_quotaHeaderIs10")
        void deleteDraft_success_quotaHeaderIs10() throws Exception {
            // Act
            MvcResult result = mockMvc.perform(delete(DRAFTS_BASE + "/{draftId}", VALID_DRAFT_ID))
                    .andExpect(status().isNoContent())
                    .andReturn();

            // Assert: GmailQuotaEstimator.estimateDeleteDraftQuota() == 10
            String quotaUsed = result.getResponse().getHeader("X-Gmail-Quota-Used");
            assertThat(quotaUsed).isNotNull();
            assertThat(Integer.parseInt(quotaUsed)).isEqualTo(10);
        }

        @Test
        @WithMockUser
        @DisplayName("deleteDraft_invalidIdFormat_400ErrorResponseStillHasRateLimitHeaders")
        void deleteDraft_invalidIdFormat_400ErrorResponseStillHasRateLimitHeaders() throws Exception {
            // Act & Assert
            mockMvc.perform(delete(DRAFTS_BASE + "/bad.id"))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().exists("X-RateLimit-Limit"))
                    .andExpect(header().exists("X-RateLimit-Remaining"))
                    .andExpect(header().exists("X-RateLimit-Reset"));
        }
    }

    // ===========================================================================
    // Nested: PUT /drafts/{draftId} — update endpoint
    // ===========================================================================

    @Nested
    @DisplayName("PUT /api/v1/gmail/drafts/{draftId} — update draft")
    class UpdateDraftHeaderTests {

        @Test
        @WithMockUser
        @DisplayName("updateDraft_noThreading_allFourRateLimitHeadersPresent")
        void updateDraft_noThreading_allFourRateLimitHeadersPresent() throws Exception {
            // Arrange
            when(gmailService.updateDraft(anyString(), anyString(), any(SendMessageDTO.class)))
                    .thenReturn(stubDraftDetail());

            SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
            String body = objectMapper.writeValueAsString(dto);

            // Act & Assert
            mockMvc.perform(put(DRAFTS_BASE + "/{draftId}", VALID_DRAFT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("X-Gmail-Quota-Used"))
                    .andExpect(header().exists("X-RateLimit-Limit"))
                    .andExpect(header().exists("X-RateLimit-Remaining"))
                    .andExpect(header().exists("X-RateLimit-Reset"));
        }

        @Test
        @WithMockUser
        @DisplayName("updateDraft_noThreading_quotaHeaderIs15")
        void updateDraft_noThreading_quotaHeaderIs15() throws Exception {
            // Arrange
            when(gmailService.updateDraft(anyString(), anyString(), any(SendMessageDTO.class)))
                    .thenReturn(stubDraftDetail());

            SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
            String body = objectMapper.writeValueAsString(dto);

            // Act
            MvcResult result = mockMvc.perform(put(DRAFTS_BASE + "/{draftId}", VALID_DRAFT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andReturn();

            // Assert: GmailQuotaEstimator.estimateUpdateDraftQuota() == 15 (no threading)
            String quotaUsed = result.getResponse().getHeader("X-Gmail-Quota-Used");
            assertThat(quotaUsed).isNotNull();
            assertThat(Integer.parseInt(quotaUsed)).isEqualTo(15);
        }

        @Test
        @WithMockUser
        @DisplayName("updateDraft_withInReplyToMessageId_quotaHeaderIs20")
        void updateDraft_withInReplyToMessageId_quotaHeaderIs20() throws Exception {
            // Arrange: inReplyToMessageId present → controller sets quota to 20 (15+5)
            // post-execution per data-model §13.
            when(gmailService.updateDraft(anyString(), anyString(), any(SendMessageDTO.class)))
                    .thenReturn(stubDraftDetail());

            // Build a DTO with inReplyToMessageId set to a valid hex message ID.
            SendMessageDTO threadedDto = new SendMessageDTO(
                    List.of("recruiter@example.com"),
                    null,
                    null,
                    "Re: Software Engineer Application",
                    "Following up on your message.",
                    "text",
                    THREAD_ID,
                    DRAFT_MESSAGE_ID,   // inReplyToMessageId — non-null triggers +5 quota
                    null
            );
            String body = objectMapper.writeValueAsString(threadedDto);

            // Act
            MvcResult result = mockMvc.perform(put(DRAFTS_BASE + "/{draftId}", VALID_DRAFT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andReturn();

            // Assert: controller sets ATTR_GMAIL_QUOTA_USED = 20 when inReplyToMessageId present.
            String quotaUsed = result.getResponse().getHeader("X-Gmail-Quota-Used");
            assertThat(quotaUsed).isNotNull();
            assertThat(Integer.parseInt(quotaUsed)).isEqualTo(20);
        }

        @Test
        @WithMockUser
        @DisplayName("updateDraft_missingToRecipient_400ErrorResponseStillHasRateLimitHeaders")
        void updateDraft_missingToRecipient_400ErrorResponseStillHasRateLimitHeaders() throws Exception {
            // Arrange: empty `to` list triggers @NotEmpty constraint → 400 before service is called.
            SendMessageDTO invalidDto = SendMessageRequestFixtures.invalidEmptyToList();
            String body = objectMapper.writeValueAsString(invalidDto);

            // Act & Assert: RateLimitInterceptor.preHandle() runs before DispatcherServlet binding,
            // so the rate-limit headers must still be present even on a 400 validation error.
            mockMvc.perform(put(DRAFTS_BASE + "/{draftId}", VALID_DRAFT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().exists("X-RateLimit-Limit"))
                    .andExpect(header().exists("X-RateLimit-Remaining"))
                    .andExpect(header().exists("X-RateLimit-Reset"));
        }
    }

    // ===========================================================================
    // Nested: Cross-cutting — RateLimit header value sanity
    // ===========================================================================

    @Nested
    @DisplayName("Rate-limit header value sanity across endpoints")
    class RateLimitHeaderValueSanityTests {

        @Test
        @WithMockUser
        @DisplayName("rateLimitHeaders_limitsAreNonNegativeIntegers_onListDrafts")
        void rateLimitHeaders_limitsAreNonNegativeIntegers_onListDrafts() throws Exception {
            // Arrange
            DraftListResult emptyResult = new DraftListResult(List.of(), null, 0);
            when(gmailService.listDrafts(anyString(), any(), anyInt())).thenReturn(emptyResult);

            // Act
            MvcResult result = mockMvc.perform(get(DRAFTS_BASE))
                    .andExpect(status().isOk())
                    .andReturn();

            // Assert: header values are parseable integers and non-negative
            String limit     = result.getResponse().getHeader("X-RateLimit-Limit");
            String remaining = result.getResponse().getHeader("X-RateLimit-Remaining");
            String reset     = result.getResponse().getHeader("X-RateLimit-Reset");

            assertThat(limit).isNotNull();
            assertThat(remaining).isNotNull();
            assertThat(reset).isNotNull();

            assertThat(Integer.parseInt(limit)).isGreaterThan(0);
            assertThat(Integer.parseInt(remaining)).isGreaterThanOrEqualTo(0);
            assertThat(Long.parseLong(reset)).isGreaterThan(0L);
        }

        @Test
        @WithMockUser
        @DisplayName("rateLimitHeaders_remainingDecrementsAcrossConsecutiveRequests")
        void rateLimitHeaders_remainingDecrementsAcrossConsecutiveRequests() throws Exception {
            // Arrange
            DraftListResult emptyResult = new DraftListResult(List.of(), null, 0);
            when(gmailService.listDrafts(anyString(), any(), anyInt())).thenReturn(emptyResult);

            // Act: first request
            MvcResult first = mockMvc.perform(get(DRAFTS_BASE))
                    .andExpect(status().isOk())
                    .andReturn();

            // Act: second request
            MvcResult second = mockMvc.perform(get(DRAFTS_BASE))
                    .andExpect(status().isOk())
                    .andReturn();

            // Assert: remaining on second request should be <= remaining on first request
            int remainingFirst  = Integer.parseInt(first.getResponse().getHeader("X-RateLimit-Remaining"));
            int remainingSecond = Integer.parseInt(second.getResponse().getHeader("X-RateLimit-Remaining"));

            assertThat(remainingSecond).isLessThanOrEqualTo(remainingFirst);
        }
    }
}
