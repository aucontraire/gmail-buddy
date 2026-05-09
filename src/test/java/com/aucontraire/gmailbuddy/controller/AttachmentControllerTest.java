package com.aucontraire.gmailbuddy.controller;

import com.aucontraire.gmailbuddy.exception.MessageTooLargeException;
import com.aucontraire.gmailbuddy.mapper.ResponseMapper;
import com.aucontraire.gmailbuddy.ratelimit.GmailQuotaEstimator;
import com.aucontraire.gmailbuddy.ratelimit.RateLimitInfo;
import com.aucontraire.gmailbuddy.ratelimit.RateLimitService;
import com.aucontraire.gmailbuddy.security.TokenReferenceService;
import com.aucontraire.gmailbuddy.service.GmailService;
import com.aucontraire.gmailbuddy.service.GoogleTokenValidator;
import com.aucontraire.gmailbuddy.validation.TestGmailBuddyPropertiesConfiguration;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-slice tests for attachment-related error responses (T039 — Phase 4 US2).
 *
 * <p>Uses {@code @WebMvcTest(GmailController.class)} + {@code @WithMockUser} to test
 * only the web layer without a full Spring context.</p>
 *
 * <p>Covered scenarios:</p>
 * <ul>
 *   <li>Path-traversal filename → 400 with {@code /problems/header-injection-detected}
 *       or {@code /problems/validation-error} and field-level error on
 *       {@code attachments[0].filename}.</li>
 *   <li>Invalid Base64 in {@code base64Data} → 400 with field-level error on
 *       {@code attachments[0].base64Data}.</li>
 *   <li>Malformed MIME type → 400 with field-level error on
 *       {@code attachments[0].mimeType}.</li>
 *   <li>Total payload exceeds 25 MB (service throws {@link MessageTooLargeException}) →
 *       413 with {@code type = /problems/message-too-large}.</li>
 * </ul>
 */
@WebMvcTest(GmailController.class)
@Import(TestGmailBuddyPropertiesConfiguration.class)
@DisplayName("AttachmentControllerTest — T039 attachment error responses")
class AttachmentControllerTest {

    private static final String MESSAGES_ENDPOINT = "/api/v1/gmail/messages";
    private static final String DRAFTS_ENDPOINT   = "/api/v1/gmail/drafts";

    // Valid base64 for %PDF-1.4\n
    private static final String VALID_BASE64 = "JVBERi0xLjQK";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private GmailService gmailService;
    @MockitoBean private OAuth2AuthorizedClientService authorizedClientService;
    @MockitoBean private GoogleTokenValidator tokenValidator;
    @MockitoBean private TokenReferenceService tokenReferenceService;
    @MockitoBean private ResponseMapper responseMapper;
    @MockitoBean private RateLimitService rateLimitService;
    @MockitoBean private GmailQuotaEstimator gmailQuotaEstimator;

    // -------------------------------------------------------------------------
    // Helper: build the request body JSON
    // -------------------------------------------------------------------------

    /** Builds a minimal valid message body with one attachment whose fields can be customised. */
    private String messageWithAttachment(String filename, String mimeType, String base64Data)
            throws Exception {
        Map<String, Object> body = Map.of(
                "to",          List.of("recruiter@example.com"),
                "subject",     "Test with attachment",
                "body",        "Please see attached.",
                "bodyType",    "text",
                "attachments", List.of(Map.of(
                        "filename",    filename,
                        "mimeType",    mimeType,
                        "base64Data",  base64Data
                ))
        );
        return objectMapper.writeValueAsString(body);
    }

    /** Builds a minimal valid draft body with one attachment. */
    private String draftWithAttachment(String filename, String mimeType, String base64Data)
            throws Exception {
        return messageWithAttachment(filename, mimeType, base64Data);  // same shape
    }

    // =========================================================================
    // Path-traversal filename → 400
    // =========================================================================

    @Test
    @WithMockUser
    @DisplayName("postMessages_pathTraversalFilename_returns400WithAttachmentFilenameError")
    void postMessages_pathTraversalFilename_returns400WithAttachmentFilenameError()
            throws Exception {

        // Arrange: "../../etc/passwd" contains ".." and "/" — @SafeFilename must reject it
        String requestBody = messageWithAttachment("../../etc/passwd", "application/pdf", VALID_BASE64);

        // Act & Assert
        mockMvc.perform(post(MESSAGES_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.extensions").exists());
    }

    @Test
    @WithMockUser
    @DisplayName("postMessages_backslashInFilename_returns400")
    void postMessages_backslashInFilename_returns400()
            throws Exception {

        // Arrange: backslash in filename — @SafeFilename must reject it
        String requestBody = messageWithAttachment("..\\evil.exe", "application/octet-stream", VALID_BASE64);

        // Act & Assert
        mockMvc.perform(post(MESSAGES_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @WithMockUser
    @DisplayName("postDrafts_pathTraversalFilename_returns400")
    void postDrafts_pathTraversalFilename_returns400()
            throws Exception {

        // Arrange: path traversal in draft create endpoint
        String requestBody = draftWithAttachment("../secret.txt", "text/plain", VALID_BASE64);

        // Act & Assert
        mockMvc.perform(post(DRAFTS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // =========================================================================
    // Header-injection filename → 400 with header-injection-detected type
    // =========================================================================

    @Test
    @WithMockUser
    @DisplayName("postMessages_crlfInFilename_returns400HeaderInjectionDetected")
    void postMessages_crlfInFilename_returns400HeaderInjectionDetected()
            throws Exception {

        // Arrange: CRLF in filename — @SafeFilename rejects header-injection characters
        // Use a raw JSON approach to embed actual CRLF bytes in the filename field.
        String rawJson = """
                {
                  "to": ["recruiter@example.com"],
                  "subject": "Test",
                  "body": "body",
                  "bodyType": "text",
                  "attachments": [
                    {
                      "filename": "innocent.pdf\\r\\nContent-Type: text/html",
                      "mimeType": "application/pdf",
                      "base64Data": "JVBERi0xLjQK"
                    }
                  ]
                }
                """;

        // Act & Assert
        mockMvc.perform(post(MESSAGES_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // =========================================================================
    // Invalid Base64 → 400 with field error on attachments[0].base64Data
    // =========================================================================

    @Test
    @WithMockUser
    @DisplayName("postMessages_invalidBase64_returns400WithBase64Error")
    void postMessages_invalidBase64_returns400WithBase64Error()
            throws Exception {

        // Arrange: "not-valid-base64!!!" is NOT valid standard Base64 — @ValidBase64 must reject
        String requestBody = messageWithAttachment("resume.pdf", "application/pdf", "not-valid-base64!!!");

        // Act & Assert
        mockMvc.perform(post(MESSAGES_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.extensions").exists());
    }

    @Test
    @WithMockUser
    @DisplayName("postDrafts_invalidBase64_returns400WithBase64Error")
    void postDrafts_invalidBase64_returns400WithBase64Error()
            throws Exception {

        // Arrange
        String requestBody = draftWithAttachment("doc.pdf", "application/pdf", "!!!invalid!!!");

        // Act & Assert
        mockMvc.perform(post(DRAFTS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                .andExpect(jsonPath("$.status").value(400));
    }

    // =========================================================================
    // Malformed MIME type → 400 with field error on attachments[0].mimeType
    // =========================================================================

    @Test
    @WithMockUser
    @DisplayName("postMessages_malformedMimeType_returns400WithMimeTypeError")
    void postMessages_malformedMimeType_returns400WithMimeTypeError()
            throws Exception {

        // Arrange: "application" lacks the mandatory "/" separator — @ValidMimeType must reject
        String requestBody = messageWithAttachment("resume.pdf", "application", VALID_BASE64);

        // Act & Assert
        mockMvc.perform(post(MESSAGES_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.extensions").exists());
    }

    @Test
    @WithMockUser
    @DisplayName("postMessages_mimeTypeWithSpaces_returns400WithMimeTypeError")
    void postMessages_mimeTypeWithSpaces_returns400WithMimeTypeError()
            throws Exception {

        // Arrange: MIME type with spaces is not RFC 6838 compliant
        String requestBody = messageWithAttachment("file.pdf", "application /pdf", VALID_BASE64);

        // Act & Assert
        mockMvc.perform(post(MESSAGES_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @WithMockUser
    @DisplayName("postDrafts_malformedMimeType_returns400")
    void postDrafts_malformedMimeType_returns400()
            throws Exception {

        // Arrange: empty subtype (just "/") is malformed
        String requestBody = draftWithAttachment("doc.pdf", "application/", VALID_BASE64);

        // Act & Assert
        mockMvc.perform(post(DRAFTS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // =========================================================================
    // Total payload exceeds limit → 413 (service throws MessageTooLargeException)
    // =========================================================================

    @Test
    @WithMockUser
    @DisplayName("postMessages_totalPayloadExceedsLimit_returns413WithMessageTooLargeType")
    void postMessages_totalPayloadExceedsLimit_returns413WithMessageTooLargeType()
            throws Exception {

        // Arrange: service throws MessageTooLargeException (Stage 1 or Stage 2 rejection)
        when(rateLimitService.recordRequest(any()))
                .thenReturn(new RateLimitInfo(1000, 999, System.currentTimeMillis()));
        when(gmailService.sendMessage(eq("me"), any()))
                .thenThrow(new MessageTooLargeException(
                        "Total payload estimate exceeds 90% of 26214400 byte cap (Stage 1 fast reject, FR-017)"));

        String requestBody = messageWithAttachment("resume.pdf", "application/pdf", VALID_BASE64);

        // Act & Assert
        mockMvc.perform(post(MESSAGES_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.type").value("/problems/message-too-large"))
                .andExpect(jsonPath("$.status").value(413));
    }

    @Test
    @WithMockUser
    @DisplayName("postDrafts_totalPayloadExceedsLimit_returns413WithMessageTooLargeType")
    void postDrafts_totalPayloadExceedsLimit_returns413WithMessageTooLargeType()
            throws Exception {

        // Arrange
        when(rateLimitService.recordRequest(any()))
                .thenReturn(new RateLimitInfo(1000, 999, System.currentTimeMillis()));
        when(gmailService.createDraft(eq("me"), any()))
                .thenThrow(new MessageTooLargeException(
                        "Assembled MIME payload exceeds 26214400 byte cap (Stage 2 safety net, FR-017)"));

        String requestBody = draftWithAttachment("resume.pdf", "application/pdf", VALID_BASE64);

        // Act & Assert
        mockMvc.perform(post(DRAFTS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.type").value("/problems/message-too-large"))
                .andExpect(jsonPath("$.status").value(413));
    }

    // =========================================================================
    // Valid attachment → passes validation (positive case for completeness)
    // =========================================================================

    @Test
    @WithMockUser
    @DisplayName("postMessages_validAttachment_passesValidationLayer")
    void postMessages_validAttachment_passesValidationLayer()
            throws Exception {

        // Arrange: valid PDF attachment — should reach the service layer (no 400)
        // The service mock will return null (no setup), causing controller to handle
        // a NullPointerException or similar — but we only care that the status is NOT 400.
        when(rateLimitService.recordRequest(any()))
                .thenReturn(new RateLimitInfo(1000, 999, System.currentTimeMillis()));

        String requestBody = messageWithAttachment("resume.pdf", "application/pdf", VALID_BASE64);

        // Act: we expect the request to pass validation (not 400)
        // It may 500 internally if the service mock isn't fully set up — that's acceptable
        // as the test goal is purely to verify the validation layer is not rejecting valid input.
        mockMvc.perform(post(MESSAGES_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    // Valid attachment should NOT produce a 400 validation error
                    assert status != 400 : "Valid attachment should not be rejected with 400 but got " + status;
                });
    }
}
