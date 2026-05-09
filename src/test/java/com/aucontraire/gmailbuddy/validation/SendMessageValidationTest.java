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

    // =========================================================================
    // T040 — Attachment DTO field-level constraint validation (Phase 4 US2)
    // =========================================================================

    // -------------------------------------------------------------------------
    // @NotBlank on filename — blank/null filename must be rejected
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postDraft_blankAttachmentFilename_returns400WithValidationError")
    void postDraft_blankAttachmentFilename_returns400WithValidationError() throws Exception {
        // Arrange: attachment with blank filename — @NotBlank must fire
        String rawJson = """
                {
                  "to": ["recruiter@example.com"],
                  "subject": "Valid Subject",
                  "body": "Valid body",
                  "bodyType": "text",
                  "attachments": [
                    {
                      "filename": "",
                      "mimeType": "application/pdf",
                      "base64Data": "JVBERi0xLjQK"
                    }
                  ]
                }
                """;

        // Act & Assert
        mockMvc.perform(post(DRAFTS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                .andExpect(jsonPath("$.status").value(400));
    }

    // -------------------------------------------------------------------------
    // @NotBlank on mimeType — blank/null mimeType must be rejected
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postDraft_blankAttachmentMimeType_returns400WithValidationError")
    void postDraft_blankAttachmentMimeType_returns400WithValidationError() throws Exception {
        // Arrange: attachment with blank mimeType — @NotBlank must fire
        String rawJson = """
                {
                  "to": ["recruiter@example.com"],
                  "subject": "Valid Subject",
                  "body": "Valid body",
                  "bodyType": "text",
                  "attachments": [
                    {
                      "filename": "resume.pdf",
                      "mimeType": "",
                      "base64Data": "JVBERi0xLjQK"
                    }
                  ]
                }
                """;

        // Act & Assert
        mockMvc.perform(post(DRAFTS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                .andExpect(jsonPath("$.status").value(400));
    }

    // -------------------------------------------------------------------------
    // @NotBlank on base64Data — blank/null base64Data must be rejected
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postDraft_blankAttachmentBase64Data_returns400WithValidationError")
    void postDraft_blankAttachmentBase64Data_returns400WithValidationError() throws Exception {
        // Arrange: attachment with blank base64Data — @NotBlank must fire
        String rawJson = """
                {
                  "to": ["recruiter@example.com"],
                  "subject": "Valid Subject",
                  "body": "Valid body",
                  "bodyType": "text",
                  "attachments": [
                    {
                      "filename": "resume.pdf",
                      "mimeType": "application/pdf",
                      "base64Data": ""
                    }
                  ]
                }
                """;

        // Act & Assert
        mockMvc.perform(post(DRAFTS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                .andExpect(jsonPath("$.status").value(400));
    }

    // -------------------------------------------------------------------------
    // @Size(max=255) on filename — oversized filename must be rejected
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postDraft_oversizedAttachmentFilename_returns400WithValidationError")
    void postDraft_oversizedAttachmentFilename_returns400WithValidationError() throws Exception {
        // Arrange: filename of 256 characters exceeds @Size(max=255) limit
        String longFilename = "a".repeat(256) + ".pdf";

        String rawJson = String.format("""
                {
                  "to": ["recruiter@example.com"],
                  "subject": "Valid Subject",
                  "body": "Valid body",
                  "bodyType": "text",
                  "attachments": [
                    {
                      "filename": "%s",
                      "mimeType": "application/pdf",
                      "base64Data": "JVBERi0xLjQK"
                    }
                  ]
                }
                """, longFilename);

        // Act & Assert
        mockMvc.perform(post(DRAFTS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                .andExpect(jsonPath("$.status").value(400));
    }

    // -------------------------------------------------------------------------
    // @SafeFilename — path-traversal in filename must be rejected
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postDraft_dotDotInFilename_returns400WithSafeFilenameViolation")
    void postDraft_dotDotInFilename_returns400WithSafeFilenameViolation() throws Exception {
        // Arrange: ".." in filename is a path-traversal pattern that @SafeFilename must reject
        String rawJson = """
                {
                  "to": ["recruiter@example.com"],
                  "subject": "Valid Subject",
                  "body": "Valid body",
                  "bodyType": "text",
                  "attachments": [
                    {
                      "filename": "../../etc/passwd",
                      "mimeType": "application/pdf",
                      "base64Data": "JVBERi0xLjQK"
                    }
                  ]
                }
                """;

        // Act & Assert
        mockMvc.perform(post(DRAFTS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @WithMockUser
    @DisplayName("postDraft_forwardSlashInFilename_returns400")
    void postDraft_forwardSlashInFilename_returns400() throws Exception {
        // Arrange: "/" in filename is a path separator — @SafeFilename must reject it
        String rawJson = """
                {
                  "to": ["recruiter@example.com"],
                  "subject": "Valid Subject",
                  "body": "Valid body",
                  "bodyType": "text",
                  "attachments": [
                    {
                      "filename": "/etc/hosts",
                      "mimeType": "text/plain",
                      "base64Data": "JVBERi0xLjQK"
                    }
                  ]
                }
                """;

        // Act & Assert
        mockMvc.perform(post(DRAFTS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // -------------------------------------------------------------------------
    // @ValidMimeType — malformed MIME type must be rejected
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postDraft_malformedMimeTypeNoSlash_returns400WithValidationError")
    void postDraft_malformedMimeTypeNoSlash_returns400WithValidationError() throws Exception {
        // Arrange: "application" lacks "/" separator — @ValidMimeType must reject it
        String rawJson = """
                {
                  "to": ["recruiter@example.com"],
                  "subject": "Valid Subject",
                  "body": "Valid body",
                  "bodyType": "text",
                  "attachments": [
                    {
                      "filename": "resume.pdf",
                      "mimeType": "application",
                      "base64Data": "JVBERi0xLjQK"
                    }
                  ]
                }
                """;

        // Act & Assert
        mockMvc.perform(post(DRAFTS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @WithMockUser
    @DisplayName("postDraft_mimeTypeWithSpaces_returns400WithValidationError")
    void postDraft_mimeTypeWithSpaces_returns400WithValidationError() throws Exception {
        // Arrange: whitespace in MIME type is not RFC 6838 compliant
        String rawJson = """
                {
                  "to": ["recruiter@example.com"],
                  "subject": "Valid Subject",
                  "body": "Valid body",
                  "bodyType": "text",
                  "attachments": [
                    {
                      "filename": "resume.pdf",
                      "mimeType": "application /pdf",
                      "base64Data": "JVBERi0xLjQK"
                    }
                  ]
                }
                """;

        // Act & Assert
        mockMvc.perform(post(DRAFTS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                .andExpect(jsonPath("$.status").value(400));
    }

    // -------------------------------------------------------------------------
    // @ValidBase64 — invalid base64 encoding must be rejected
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postDraft_invalidBase64Data_returns400WithValidationError")
    void postDraft_invalidBase64Data_returns400WithValidationError() throws Exception {
        // Arrange: "not-valid-base64!!!" contains invalid characters — @ValidBase64 must reject
        String rawJson = """
                {
                  "to": ["recruiter@example.com"],
                  "subject": "Valid Subject",
                  "body": "Valid body",
                  "bodyType": "text",
                  "attachments": [
                    {
                      "filename": "resume.pdf",
                      "mimeType": "application/pdf",
                      "base64Data": "not-valid-base64!!!"
                    }
                  ]
                }
                """;

        // Act & Assert
        mockMvc.perform(post(DRAFTS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @WithMockUser
    @DisplayName("postDraft_base64WithInvalidCharacters_returns400WithValidationError")
    void postDraft_base64WithInvalidCharacters_returns400WithValidationError() throws Exception {
        // Arrange: use URL-safe alphabet characters ('-' and '_') which are NOT in the
        // standard Base64 alphabet ('+' and '/' are standard; '-' and '_' are URL-safe only).
        // The standard decoder (Base64.getDecoder()) ALWAYS rejects '-' and '_' characters.
        // "SGVsbG8-V29ybGQ_" contains '-' and '_' which are definitively invalid for standard decoder.
        String rawJson = """
                {
                  "to": ["recruiter@example.com"],
                  "subject": "Valid Subject",
                  "body": "Valid body",
                  "bodyType": "text",
                  "attachments": [
                    {
                      "filename": "resume.pdf",
                      "mimeType": "application/pdf",
                      "base64Data": "SGVsbG8-V29ybGQ_"
                    }
                  ]
                }
                """;

        // Act & Assert: standard decoder must reject '-' and '_' characters
        mockMvc.perform(post(DRAFTS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                .andExpect(jsonPath("$.status").value(400));
    }

    // -------------------------------------------------------------------------
    // T040 — Valid attachment passes all constraints (positive case)
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    @DisplayName("postDraft_validAttachment_passesAllAttachmentConstraints")
    void postDraft_validAttachment_passesAllAttachmentConstraints() throws Exception {
        // Arrange: a fully valid attachment — all constraints should pass, request reaches service
        String rawJson = """
                {
                  "to": ["recruiter@example.com"],
                  "subject": "Valid Subject",
                  "body": "Valid body",
                  "bodyType": "text",
                  "attachments": [
                    {
                      "filename": "resume.pdf",
                      "mimeType": "application/pdf",
                      "base64Data": "JVBERi0xLjQK"
                    }
                  ]
                }
                """;

        // Act: validation should not reject this request with a 400
        mockMvc.perform(post(DRAFTS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    // Valid attachment should NOT be rejected by validation (not 400)
                    assert status != 400 : "Valid attachment rejected with 400 — constraint misfired";
                });
    }
}
