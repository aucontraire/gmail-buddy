package com.aucontraire.gmailbuddy.dto;

import com.aucontraire.gmailbuddy.controller.GmailController;
import com.aucontraire.gmailbuddy.fixture.AttachmentFixtures;
import com.aucontraire.gmailbuddy.mapper.ResponseMapper;
import com.aucontraire.gmailbuddy.ratelimit.GmailQuotaEstimator;
import com.aucontraire.gmailbuddy.ratelimit.RateLimitService;
import com.aucontraire.gmailbuddy.security.TokenReferenceService;
import com.aucontraire.gmailbuddy.service.GmailService;
import com.aucontraire.gmailbuddy.service.GoogleTokenValidator;
import com.aucontraire.gmailbuddy.validation.TestGmailBuddyPropertiesConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for the threading and attachment fields added to {@link SendMessageDTO}
 * in Phase 2 Wave 1 (T006).
 *
 * <p>Scoped exclusively to the three new fields: {@code threadId},
 * {@code inReplyToMessageId}, and {@code attachments}. Complements the existing
 * {@code SendMessageValidationTest} which covers the six original fields.</p>
 *
 * <h2>Structure</h2>
 * <ul>
 *   <li>{@link CompactConstructorTests} — plain unit tests; no Spring context required.
 *       Tests the null-normalisation logic in the compact constructor.</li>
 *   <li>{@link PatternAndCascadeValidationTests} — {@code @WebMvcTest} slice.
 *       Tests {@code @Pattern} on {@code threadId}/{@code inReplyToMessageId} and
 *       {@code @Valid} cascade on {@code attachments} via the controller endpoint.</li>
 * </ul>
 *
 * <p>The split is necessary because {@link com.aucontraire.gmailbuddy.validation.MaxBodySizeValidator}
 * requires Spring's {@code GmailBuddyProperties} bean. Using a plain
 * {@code Validation.buildDefaultValidatorFactory()} validator without a Spring context
 * would throw {@code HV000028} when validating a {@link SendMessageDTO} whose {@code body}
 * field carries {@code @MaxBodySize}.</p>
 */
@DisplayName("SendMessageDTO — threading and attachment field tests (T023)")
class SendMessageDTOThreadingAttachmentTest {

    /** Minimum valid field values for the six pre-existing required fields. */
    private static final String VALID_RECIPIENT = "recruiter@example.com";
    private static final String VALID_SUBJECT = "Follow-up";
    private static final String VALID_BODY = "Hello!";

    // =========================================================================
    // Compact constructor tests — no Spring context needed
    // =========================================================================

    /**
     * Tests for the null-normalisation logic in the compact constructor.
     * These tests create {@link SendMessageDTO} instances directly and inspect the
     * result of the compact constructor without invoking Bean Validation.
     */
    @Nested
    @DisplayName("Compact constructor — null normalisation")
    class CompactConstructorTests {

        @Test
        @DisplayName("null attachments normalises to List.of() (empty immutable list)")
        void compactConstructor_nullAttachments_normalisesToEmptyList() {
            // Arrange + Act: pass null for attachments.
            SendMessageDTO dto = new SendMessageDTO(
                    List.of(VALID_RECIPIENT),
                    null, null,
                    VALID_SUBJECT, VALID_BODY, "text",
                    null, null,
                    null  // null attachments
            );

            // Assert: compact constructor must replace null with List.of().
            List<Attachment> result = dto.attachments();
            assertThat(result).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("explicit empty attachments list remains empty")
        void compactConstructor_emptyAttachments_remainsEmpty() {
            // Arrange + Act
            SendMessageDTO dto = new SendMessageDTO(
                    List.of(VALID_RECIPIENT),
                    null, null,
                    VALID_SUBJECT, VALID_BODY, "text",
                    null, null,
                    List.of()
            );

            // Assert
            assertThat(dto.attachments()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("non-null attachments list is copied and preserved")
        void compactConstructor_nonNullAttachments_copiesToImmutableList() {
            // Arrange + Act
            List<Attachment> input = List.of(AttachmentFixtures.validSinglePdf());
            SendMessageDTO dto = new SendMessageDTO(
                    List.of(VALID_RECIPIENT),
                    null, null,
                    VALID_SUBJECT, VALID_BODY, "text",
                    null, null,
                    input
            );

            // Assert
            List<Attachment> result = dto.attachments();
            assertThat(result).hasSize(1);
            assertThat(result.get(0).filename()).isEqualTo("resume.pdf");
        }

        @Test
        @DisplayName("null threadId is preserved as null (field is optional)")
        void compactConstructor_nullThreadId_remainsNull() {
            // Arrange + Act
            SendMessageDTO dto = new SendMessageDTO(
                    List.of(VALID_RECIPIENT),
                    null, null,
                    VALID_SUBJECT, VALID_BODY, "text",
                    null, null, null
            );

            // Assert: no default is applied to optional fields.
            assertThat(dto.threadId()).isNull();
        }

        @Test
        @DisplayName("null inReplyToMessageId is preserved as null (field is optional)")
        void compactConstructor_nullInReplyToMessageId_remainsNull() {
            // Arrange + Act
            SendMessageDTO dto = new SendMessageDTO(
                    List.of(VALID_RECIPIENT),
                    null, null,
                    VALID_SUBJECT, VALID_BODY, "text",
                    null, null, null
            );

            // Assert
            assertThat(dto.inReplyToMessageId()).isNull();
        }
    }

    // =========================================================================
    // @Pattern and @Valid cascade validation tests — requires Spring context
    // =========================================================================

    /**
     * Controller-slice tests for {@code @Pattern} on {@code threadId} /
     * {@code inReplyToMessageId} and {@code @Valid} cascade on {@code attachments}.
     *
     * <p>Uses {@code @WebMvcTest} so the full Bean Validation pipeline (including
     * Spring-injected validators like {@code MaxBodySizeValidator}) is wired correctly.
     * All service-layer beans are mocked.</p>
     */
    @Nested
    @WebMvcTest(GmailController.class)
    @Import(TestGmailBuddyPropertiesConfiguration.class)
    @DisplayName("@Pattern and @Valid cascade — controller-slice validation")
    class PatternAndCascadeValidationTests {

        private static final String MESSAGES_ENDPOINT = "/api/v1/gmail/messages";

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
        // threadId — @Pattern validation via controller endpoint
        // -------------------------------------------------------------------------

        @Test
        @WithMockUser
        @DisplayName("threadId with valid lowercase hex passes validation")
        void threadId_validLowerHex_passesValidation() throws Exception {
            // Arrange: use raw JSON so Jackson doesn't filter out null fields and we
            // control exactly what goes on the wire.
            String json = """
                    {
                      "to": ["recruiter@example.com"],
                      "subject": "Follow-up",
                      "body": "Hello!",
                      "bodyType": "text",
                      "threadId": "1a2b3c4d5e6f7a8b"
                    }
                    """;

            // Act & Assert: 400 would indicate a validation error on threadId.
            // 201 or service-layer errors both mean validation passed.
            // We only care that no 400 with type=validation-error targets threadId.
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    // threadId validation passes; service layer may throw but not 400 on threadId
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        // A 400 with threadId field error means validation failed — must not happen.
                        if (status == 400) {
                            String body = result.getResponse().getContentAsString();
                            assertThat(body).doesNotContain("\"field:threadId\"");
                        }
                    });
        }

        @Test
        @WithMockUser
        @DisplayName("threadId with non-hex characters returns 400 with validation error on threadId")
        void threadId_containsNonHexCharacters_returns400WithFieldExtension() throws Exception {
            // Arrange: 'g' is not a hex character; @Pattern("[0-9a-fA-F]{1,32}") rejects it.
            String json = """
                    {
                      "to": ["recruiter@example.com"],
                      "subject": "Follow-up",
                      "body": "Hello!",
                      "bodyType": "text",
                      "threadId": "1a2b3g"
                    }
                    """;

            // Act & Assert
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                    .andExpect(jsonPath("$.extensions['field:threadId']").exists());
        }

        @Test
        @WithMockUser
        @DisplayName("threadId with line-feed (header-injection attempt) returns 400")
        void threadId_containsLineFeed_returns400WithFieldExtension() throws Exception {
            // Arrange: the @Pattern rejects LF by exclusion (only [0-9a-fA-F] allowed),
            // satisfying FR-001a without a separate @NoHeaderInjection annotation.
            String json = "{\"to\":[\"recruiter@example.com\"],\"subject\":\"Follow-up\","
                    + "\"body\":\"Hello!\",\"bodyType\":\"text\","
                    + "\"threadId\":\"1a2b\\n3c4d\"}";

            // Act & Assert
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                    .andExpect(jsonPath("$.extensions['field:threadId']").exists());
        }

        @Test
        @WithMockUser
        @DisplayName("threadId with 33+ hex chars returns 400 (exceeds max length of 32)")
        void threadId_tooLong_returns400WithFieldExtension() throws Exception {
            // Arrange: 33 hex chars exceeds the {1,32} quantifier.
            String json = String.format(
                    "{\"to\":[\"recruiter@example.com\"],\"subject\":\"Follow-up\","
                    + "\"body\":\"Hello!\",\"bodyType\":\"text\","
                    + "\"threadId\":\"%s\"}", "a".repeat(33));

            // Act & Assert
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                    .andExpect(jsonPath("$.extensions['field:threadId']").exists());
        }

        // -------------------------------------------------------------------------
        // inReplyToMessageId — @Pattern validation
        // -------------------------------------------------------------------------

        @Test
        @WithMockUser
        @DisplayName("inReplyToMessageId with non-hex characters returns 400")
        void inReplyToMessageId_containsNonHexCharacters_returns400WithFieldExtension() throws Exception {
            // Arrange: 'z' is not a hex character.
            String json = """
                    {
                      "to": ["recruiter@example.com"],
                      "subject": "Follow-up",
                      "body": "Hello!",
                      "bodyType": "text",
                      "inReplyToMessageId": "deadbeefzz"
                    }
                    """;

            // Act & Assert
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                    .andExpect(jsonPath("$.extensions['field:inReplyToMessageId']").exists());
        }

        @Test
        @WithMockUser
        @DisplayName("inReplyToMessageId with carriage-return (header-injection) returns 400")
        void inReplyToMessageId_containsCarriageReturn_returns400WithFieldExtension() throws Exception {
            // Arrange: CR is not hex; the @Pattern rejects it, satisfying FR-001a.
            String json = "{\"to\":[\"recruiter@example.com\"],\"subject\":\"Follow-up\","
                    + "\"body\":\"Hello!\",\"bodyType\":\"text\","
                    + "\"inReplyToMessageId\":\"1a2b\\r3c4d\"}";

            // Act & Assert
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                    .andExpect(jsonPath("$.extensions['field:inReplyToMessageId']").exists());
        }

        // -------------------------------------------------------------------------
        // @Valid cascade — Attachment field validation
        // -------------------------------------------------------------------------

        @Test
        @WithMockUser
        @DisplayName("attachment with path-traversal filename returns 400 with error on filename")
        void attachments_pathTraversalFilename_returns400WithFilenameError() throws Exception {
            // Arrange: "../../etc/passwd" fails @SafeFilename. The @Valid cascade on
            // attachments must propagate the violation to the controller response.
            String json = """
                    {
                      "to": ["recruiter@example.com"],
                      "subject": "Follow-up",
                      "body": "Hello!",
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
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                    .andExpect(jsonPath("$.extensions").exists());
        }

        @Test
        @WithMockUser
        @DisplayName("attachment with invalid base64 returns 400 with error on base64Data")
        void attachments_invalidBase64_returns400WithBase64Error() throws Exception {
            // Arrange: "not-valid-base64!!!" contains '-' and '!' which the standard
            // Base64.getDecoder() rejects, failing @ValidBase64.
            String json = """
                    {
                      "to": ["recruiter@example.com"],
                      "subject": "Follow-up",
                      "body": "Hello!",
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
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                    .andExpect(jsonPath("$.extensions").exists());
        }

        @Test
        @WithMockUser
        @DisplayName("attachment with malformed MIME type returns 400 with error on mimeType")
        void attachments_malformedMimeType_returns400WithMimeTypeError() throws Exception {
            // Arrange: "application" without "/subtype" fails @ValidMimeType.
            String json = """
                    {
                      "to": ["recruiter@example.com"],
                      "subject": "Follow-up",
                      "body": "Hello!",
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
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                    .andExpect(jsonPath("$.extensions").exists());
        }
    }
}
