package com.aucontraire.gmailbuddy.validation;

import com.aucontraire.gmailbuddy.controller.GmailController;
import com.aucontraire.gmailbuddy.dto.SendMessageDTO;
import com.aucontraire.gmailbuddy.fixture.SendMessageRequestFixtures;
import com.aucontraire.gmailbuddy.mapper.ResponseMapper;
import com.aucontraire.gmailbuddy.ratelimit.GmailQuotaEstimator;
import com.aucontraire.gmailbuddy.ratelimit.RateLimitService;
import com.aucontraire.gmailbuddy.security.TokenReferenceService;
import com.aucontraire.gmailbuddy.service.GmailService;
import com.aucontraire.gmailbuddy.service.GoogleTokenValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Validation-slice tests for {@code POST /api/v1/gmail/drafts}.
 *
 * <p>Covers all Bean Validation failure scenarios for the
 * {@link com.aucontraire.gmailbuddy.dto.SendMessageDTO} payload when submitted to
 * the create-draft endpoint. The happy-path 201 contract is tested separately in
 * {@code CreateDraftControllerTest} (T036).</p>
 *
 * <p>Uses {@code @WebMvcTest} to load only the web layer (controller +
 * {@code GlobalExceptionHandler}) so tests run fast without a full Spring context.
 * All service-layer dependencies are mocked with {@code @MockitoBean}.</p>
 *
 * <p>Key conventions:</p>
 * <ul>
 *   <li>Test method names follow {@code methodName_stateUnderTest_expectedBehavior}.</li>
 *   <li>Arrange-Act-Assert sections are clearly separated.</li>
 *   <li>Each test validates exactly one logical validation behaviour.</li>
 * </ul>
 */
@WebMvcTest(GmailController.class)
@Import(TestGmailBuddyPropertiesConfiguration.class)
@DisplayName("POST /api/v1/gmail/drafts — validation slice")
class SendMessageValidationTest {

    private static final String DRAFTS_ENDPOINT = "/api/v1/gmail/drafts";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GmailService gmailService;

    @MockitoBean
    private OAuth2AuthorizedClientService authorizedClientService;

    @MockitoBean
    private GoogleTokenValidator tokenValidator;

    @MockitoBean
    private TokenReferenceService tokenReferenceService;

    @MockitoBean
    private ResponseMapper responseMapper;

    @MockitoBean
    private RateLimitService rateLimitService;

    @MockitoBean
    private GmailQuotaEstimator gmailQuotaEstimator;

    // -------------------------------------------------------------------------
    // Recipient list — empty / missing
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postDraft_emptyToList_returns400WithFieldExtension")
    void postDraft_emptyToList_returns400WithFieldExtension() throws Exception {
        // Arrange: a DTO whose "to" list is explicitly empty; @NotEmpty must reject it.
        SendMessageDTO dto = SendMessageRequestFixtures.invalidEmptyToList();
        String body = objectMapper.writeValueAsString(dto);

        // Act & Assert
        mockMvc.perform(post(DRAFTS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.extensions['field:to']").exists());
    }

    @Test
    @WithMockUser
    @DisplayName("postDraft_missingToField_returns400WithFieldExtension")
    void postDraft_missingToField_returns400WithFieldExtension() throws Exception {
        // Arrange: explicit null "to" field to verify null/absent list produces same error.
        // The compact constructor normalises null→empty-list, so the @NotEmpty fires.
        SendMessageDTO dto = new SendMessageDTO(
                null,
                null,
                null,
                "Valid Subject",
                "Valid body content",
                "text",
                null,
                null,
                null
        );
        String body = objectMapper.writeValueAsString(dto);

        // Act & Assert
        mockMvc.perform(post(DRAFTS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                .andExpect(jsonPath("$.extensions['field:to']").exists());
    }

    // -------------------------------------------------------------------------
    // Recipient list — malformed email at element index
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postDraft_malformedEmailAtToIndex1_returns400WithConstraintExtension")
    void postDraft_malformedEmailAtToIndex1_returns400WithConstraintExtension() throws Exception {
        // Arrange: first recipient is valid; second is malformed.
        // The constraint path reported by Bean Validation for a TYPE_USE constraint
        // on a list element follows the pattern "to[1].<list element>".
        SendMessageDTO dto = new SendMessageDTO(
                List.of("valid@example.com", "not-an-email"),
                null,
                null,
                "Valid Subject",
                "Valid body content",
                "text",
                null,
                null,
                null
        );
        String body = objectMapper.writeValueAsString(dto);

        // Act & Assert
        mockMvc.perform(post(DRAFTS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                .andExpect(jsonPath("$.status").value(400))
                // The GlobalExceptionHandler puts constraint-path keys under "constraint:" prefix
                // for ConstraintViolationException; for MethodArgumentNotValidException it uses
                // "field:" prefix with the field name only. We assert the error exists for "to".
                .andExpect(jsonPath("$.extensions").exists());
    }

    // -------------------------------------------------------------------------
    // Subject — oversized (> 998 chars)
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postDraft_oversizedSubject_returns400WithFieldExtension")
    void postDraft_oversizedSubject_returns400WithFieldExtension() throws Exception {
        // Arrange: subject of 999 characters exceeds the @Size(max=998) constraint.
        String oversizedSubject = "A".repeat(999);
        SendMessageDTO dto = new SendMessageDTO(
                List.of("recruiter@example.com"),
                null,
                null,
                oversizedSubject,
                "Valid body content",
                "text",
                null,
                null,
                null
        );
        String body = objectMapper.writeValueAsString(dto);

        // Act & Assert
        mockMvc.perform(post(DRAFTS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.extensions['field:subject']").exists());
    }

    // -------------------------------------------------------------------------
    // Body — oversized (> 10 MB)
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postDraft_oversizedBody_returns400WithFieldExtension")
    void postDraft_oversizedBody_returns400WithFieldExtension() throws Exception {
        // Arrange: body that exceeds the 10 MB @MaxBodySize limit.
        // SendMessageRequestFixtures.invalidOversizedBody() produces ~10 MB + 1 KB of ASCII.
        SendMessageDTO dto = SendMessageRequestFixtures.invalidOversizedBody();
        String body = objectMapper.writeValueAsString(dto);

        // Act & Assert
        mockMvc.perform(post(DRAFTS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.extensions['field:body']").exists());
    }

    // -------------------------------------------------------------------------
    // Missing required fields — no body / no subject
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postDraft_missingSubject_returns400WithFieldExtension")
    void postDraft_missingSubject_returns400WithFieldExtension() throws Exception {
        // Arrange: no subject provided; @NotBlank must reject it.
        String rawJson = """
                {
                  "to": ["recruiter@example.com"],
                  "body": "Hello"
                }
                """;

        // Act & Assert
        mockMvc.perform(post(DRAFTS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                .andExpect(jsonPath("$.extensions['field:subject']").exists());
    }

    @Test
    @WithMockUser
    @DisplayName("postDraft_missingBody_returns400WithFieldExtension")
    void postDraft_missingBody_returns400WithFieldExtension() throws Exception {
        // Arrange: no body provided; @NotBlank must reject it.
        String rawJson = """
                {
                  "to": ["recruiter@example.com"],
                  "subject": "Hello"
                }
                """;

        // Act & Assert
        mockMvc.perform(post(DRAFTS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                .andExpect(jsonPath("$.extensions['field:body']").exists());
    }

    // -------------------------------------------------------------------------
    // Header injection — CRLF in subject
    //
    // Per FR-015 and FR-018 (SC-005), @NoHeaderInjection violations must return
    // "/problems/header-injection-detected" so security-relevant attempts can be
    // triaged separately from ordinary validation failures. The handler inspects
    // FieldError codes for "NoHeaderInjection" and switches the problem type
    // accordingly while keeping HTTP status 400 and field-level extensions intact.
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postDraft_crlfInSubject_returns400HeaderInjectionDetected")
    void postDraft_crlfInSubject_returns400ValidationError() throws Exception {
        // Arrange: subject contains a CRLF injection sequence; @NoHeaderInjection must
        // reject it with the distinct /problems/header-injection-detected problem type.
        SendMessageDTO dto = SendMessageRequestFixtures.invalidCrlfInSubject();
        String body = objectMapper.writeValueAsString(dto);

        // Act & Assert
        mockMvc.perform(post(DRAFTS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/header-injection-detected"))
                .andExpect(jsonPath("$.title").value("Header Injection Detected"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.extensions['field:subject']").exists());
    }

    @Test
    @WithMockUser
    @DisplayName("postDraft_crlfInRecipient_returns400HeaderInjectionDetected")
    void postDraft_crlfInRecipient_returns400ValidationError() throws Exception {
        // Arrange: a "to" recipient contains a CRLF injection sequence; @NoHeaderInjection
        // must reject it with the distinct /problems/header-injection-detected problem type.
        SendMessageDTO dto = new SendMessageDTO(
                List.of("recruiter@example.com\r\nBcc: attacker@evil.com"),
                null,
                null,
                "Valid Subject",
                "Valid body content",
                "text",
                null,
                null,
                null
        );
        String body = objectMapper.writeValueAsString(dto);

        // Act & Assert
        mockMvc.perform(post(DRAFTS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/header-injection-detected"))
                .andExpect(jsonPath("$.title").value("Header Injection Detected"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.extensions").exists());
    }

    // -------------------------------------------------------------------------
    // Invalid bodyType
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postDraft_invalidBodyType_returns400WithFieldExtension")
    void postDraft_invalidBodyType_returns400WithFieldExtension() throws Exception {
        // Arrange: bodyType value "markdown" is not in the allowed set {text, html}.
        SendMessageDTO dto = new SendMessageDTO(
                List.of("recruiter@example.com"),
                null,
                null,
                "Valid Subject",
                "Valid body content",
                "markdown",
                null,
                null,
                null
        );
        String body = objectMapper.writeValueAsString(dto);

        // Act & Assert
        mockMvc.perform(post(DRAFTS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                .andExpect(jsonPath("$.extensions['field:bodyType']").exists());
    }
}
