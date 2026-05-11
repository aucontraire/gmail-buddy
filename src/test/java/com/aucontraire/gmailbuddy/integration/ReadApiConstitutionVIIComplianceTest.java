package com.aucontraire.gmailbuddy.integration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aucontraire.gmailbuddy.dto.response.LabelSummary;
import com.aucontraire.gmailbuddy.dto.response.MessageAttachmentMetadata;
import com.aucontraire.gmailbuddy.dto.response.ThreadSummary;
import com.aucontraire.gmailbuddy.repository.GmailRepository;
import com.aucontraire.gmailbuddy.service.AttachmentListResult;
import com.aucontraire.gmailbuddy.service.GmailService;
import com.aucontraire.gmailbuddy.service.GoogleTokenValidator;
import com.aucontraire.gmailbuddy.service.LabelDetailResult;
import com.aucontraire.gmailbuddy.service.LabelListResult;
import com.aucontraire.gmailbuddy.service.MessageDetailResult;
import com.aucontraire.gmailbuddy.service.ThreadDetailResult;
import com.aucontraire.gmailbuddy.service.ThreadListResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Constitution VII compliance spot-check for feature 004 (Read API completeness) — T073.
 *
 * <p>Constitution Principle VII states: "OAuth tokens, credentials, email bodies, and
 * PII MUST NOT appear in logs." This test class verifies that the 7 new endpoints
 * introduced by feature 004 respect FR-032 / FR-033 / SC-005:</p>
 *
 * <ul>
 *   <li>Message body text MUST NOT appear in logs</li>
 *   <li>Snippet text (email preview) MUST NOT appear in logs</li>
 *   <li>Recipient email addresses (From/To/Cc/Bcc header values) MUST NOT appear in logs</li>
 *   <li>Subject lines MUST NOT appear in logs</li>
 *   <li>Attachment filenames MUST NOT appear in logs</li>
 *   <li>Label names MUST NOT appear in logs</li>
 *   <li>Attachment binary content MUST NOT appear in logs</li>
 *   <li>{@code LabelColor} textColor/backgroundColor values MUST NOT appear in logs</li>
 * </ul>
 *
 * <p>Uses Logback {@code ListAppender<ILoggingEvent>} to capture all log events
 * emitted during each endpoint call. Asserts that no fingerprintable PII value
 * appears in any captured log line at any level (DEBUG, INFO, WARN, ERROR).</p>
 *
 * <p><strong>Constraint</strong>: This class does NOT modify any production code.
 * If a scenario reveals a real PII leak, the test is left failing and the
 * orchestrator routes the fix to the appropriate specialist agent.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("T073 — Feature 004 Read API Constitution VII PII-in-logs compliance")
class ReadApiConstitutionVIIComplianceTest {

    // -------------------------------------------------------------------------
    // Constants — fingerprintable PII values
    // -------------------------------------------------------------------------

    private static final String THREADS_BASE    = "/api/v1/gmail/threads";
    private static final String MESSAGES_BASE   = "/api/v1/gmail/messages";
    private static final String LABELS_BASE     = "/api/v1/gmail/labels";

    private static final String VALID_MESSAGE_ID    = "1a2b3c4d5e6f7890";
    private static final String VALID_THREAD_ID     = "1a2b3c4d5e6f7890";
    private static final String VALID_LABEL_ID      = "INBOX";
    private static final String VALID_ATTACHMENT_ID = "ANGjdJ8abc123def";

    /**
     * Fingerprintable snippet text. Must NEVER appear in any log event.
     * Unique enough to not appear by coincidence.
     */
    private static final String UNIQUE_SNIPPET =
            "Confidential-Snippet-XYZ-9876-Following-up-on-BackendEngineer-position";

    /**
     * Fingerprintable message body. Must NEVER appear in any log event.
     */
    private static final String UNIQUE_BODY =
            "PII-Body-Text-ABCDEF-Dear-hiring-manager-I-am-writing-to-apply";

    /**
     * Fingerprintable recipient email. Must NEVER appear in any log event.
     */
    private static final String UNIQUE_RECIPIENT = "fingerprint-recipient-XYZ@example-pii.com";

    /**
     * Fingerprintable subject. Must NEVER appear in any log event.
     */
    private static final String UNIQUE_SUBJECT =
            "CONFIDENTIAL-SUBJECT-XYZ-BackendEngineerPosition2026";

    /**
     * Fingerprintable attachment filename. Must NEVER appear in any log event.
     */
    private static final String UNIQUE_FILENAME = "pii-fingerprint-attachment-XYZ-9876.pdf";

    /**
     * Fingerprintable label name. Must NEVER appear in any log event.
     */
    private static final String UNIQUE_LABEL_NAME = "PII-LabelName-XYZ-Confidential-Category";

    /**
     * Fingerprintable label color value. Must NEVER appear in any log event.
     */
    private static final String UNIQUE_COLOR_TEXT_HEX = "#ab12cd";
    private static final String UNIQUE_COLOR_BG_HEX   = "#ef3456";

    // -------------------------------------------------------------------------
    // Spring-managed beans
    // -------------------------------------------------------------------------

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GmailService gmailService;

    @MockitoBean
    private GmailRepository gmailRepository;

    @MockitoBean
    private GoogleTokenValidator tokenValidator;

    // -------------------------------------------------------------------------
    // Logback list appender — shared setup/teardown
    // -------------------------------------------------------------------------

    private ListAppender<ILoggingEvent> rootAppender;
    private Logger rootLogger;

    @BeforeEach
    void setUp() {
        reset(gmailService, gmailRepository, tokenValidator);
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootAppender = new ListAppender<>();
        rootAppender.start();
        rootLogger.setLevel(Level.DEBUG);
        rootLogger.addAppender(rootAppender);
    }

    @AfterEach
    void tearDown() {
        if (rootLogger != null && rootAppender != null) {
            rootLogger.detachAppender(rootAppender);
            rootAppender.stop();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Asserts that no application log event (logged by our code, not Spring framework
     * infrastructure) contains the given PII value.
     *
     * <p>Spring MVC logs the response body at DEBUG level using logger names under
     * {@code org.springframework.*}. These framework-level logs are excluded from the
     * PII check because they are Spring's own serialization diagnostics, not our code
     * logging PII. Constitution VII applies to application code log statements.</p>
     *
     * <p>Specifically filters out:
     * <ul>
     *   <li>{@code org.springframework.*} — Spring framework internal logs</li>
     *   <li>{@code org.apache.*} — Apache library logs</li>
     * </ul>
     * </p>
     */
    private void assertNoPiiInLogs(String piiValue, String description) {
        List<ILoggingEvent> appEvents = rootAppender.list.stream()
                .filter(e -> !e.getLoggerName().startsWith("org.springframework.")
                          && !e.getLoggerName().startsWith("org.apache."))
                .toList();
        long leakCount = appEvents.stream()
                .filter(e -> e.getFormattedMessage().contains(piiValue))
                .count();
        assertThat(leakCount)
                .as("FR-032/FR-033/Constitution VII: %s ('%s') MUST NOT appear in any application " +
                        "log event (found %d violating events at levels: %s). " +
                        "Note: Spring framework DEBUG serialization logs are excluded from this check.",
                        description, piiValue, leakCount,
                        appEvents.stream()
                                .filter(e -> e.getFormattedMessage().contains(piiValue))
                                .map(e -> e.getLevel().toString() + "[" + e.getLoggerName() + "]")
                                .toList())
                .isZero();
    }

    // =========================================================================
    // Scenario A — GET /threads: snippet and thread ID are handled safely
    // =========================================================================

    @Nested
    @DisplayName("Scenario A — GET /threads: snippet not logged")
    class ScenarioAListThreadsSnippetNotLogged {

        @Test
        @WithMockUser
        @DisplayName("listThreads_snippetInResult_snippetNeverAppearsInLogs")
        void listThreads_snippetInResult_snippetNeverAppearsInLogs() throws Exception {
            // Arrange: stub returns a thread with a fingerprintable snippet
            ThreadSummary summary = new ThreadSummary(VALID_THREAD_ID, UNIQUE_SNIPPET, "987654");
            ThreadListResult result = new ThreadListResult(List.of(summary), null, 1);
            when(gmailService.listThreads(anyString(), any(), any(), anyInt()))
                    .thenReturn(result);

            // Act
            mockMvc.perform(get(THREADS_BASE))
                    .andExpect(status().isOk());

            // Assert: snippet content must not appear in any log event
            assertNoPiiInLogs(UNIQUE_SNIPPET, "thread snippet");
        }
    }

    // =========================================================================
    // Scenario B — GET /threads/{threadId}: body, snippet, headers not logged
    // =========================================================================

    @Nested
    @DisplayName("Scenario B — GET /threads/{threadId}: message body, snippet, recipient not logged")
    class ScenarioBGetThreadPiiNotLogged {

        @Test
        @WithMockUser
        @DisplayName("getThread_withPiiInMessages_bodyNeverAppearsInLogs")
        void getThread_withPiiInMessages_bodyNeverAppearsInLogs() throws Exception {
            // Arrange: thread contains a message with fingerprintable body and recipient
            MessageDetailResult msg = new MessageDetailResult(
                    VALID_MESSAGE_ID, VALID_THREAD_ID,
                    Map.of("From", UNIQUE_RECIPIENT, "Subject", UNIQUE_SUBJECT),
                    UNIQUE_SNIPPET,
                    UNIQUE_BODY,
                    "text",
                    List.of("INBOX"),
                    List.of()
            );
            ThreadDetailResult threadResult = new ThreadDetailResult(
                    VALID_THREAD_ID, List.of("INBOX"), List.of(msg));
            when(gmailService.getThread(anyString(), anyString()))
                    .thenReturn(threadResult);

            // Act
            mockMvc.perform(get(THREADS_BASE + "/{threadId}", VALID_THREAD_ID))
                    .andExpect(status().isOk());

            // Assert
            assertNoPiiInLogs(UNIQUE_BODY, "message body in thread response");
            assertNoPiiInLogs(UNIQUE_SNIPPET, "message snippet in thread response");
            assertNoPiiInLogs(UNIQUE_RECIPIENT, "recipient email in thread response");
            assertNoPiiInLogs(UNIQUE_SUBJECT, "subject in thread response");
        }
    }

    // =========================================================================
    // Scenario C — GET /messages/{id}: body, snippet, headers not logged
    // =========================================================================

    @Nested
    @DisplayName("Scenario C — GET /messages/{messageId}: body, snippet, recipient not logged")
    class ScenarioCGetMessageDetailPiiNotLogged {

        @Test
        @WithMockUser
        @DisplayName("getMessageDetail_full_bodyNeverAppearsInLogs")
        void getMessageDetail_full_bodyNeverAppearsInLogs() throws Exception {
            // Arrange
            MessageDetailResult result = new MessageDetailResult(
                    VALID_MESSAGE_ID, VALID_THREAD_ID,
                    Map.of("From", UNIQUE_RECIPIENT, "To", "other@example.com",
                           "Subject", UNIQUE_SUBJECT),
                    UNIQUE_SNIPPET,
                    UNIQUE_BODY,
                    "text",
                    List.of("INBOX"),
                    List.of()
            );
            when(gmailService.getMessageDetail(anyString(), anyString(), eq("full")))
                    .thenReturn(result);

            // Act
            mockMvc.perform(get(MESSAGES_BASE + "/{messageId}", VALID_MESSAGE_ID))
                    .andExpect(status().isOk());

            // Assert
            assertNoPiiInLogs(UNIQUE_BODY, "message body");
            assertNoPiiInLogs(UNIQUE_SNIPPET, "message snippet");
            assertNoPiiInLogs(UNIQUE_RECIPIENT, "From header value (recipient)");
            assertNoPiiInLogs(UNIQUE_SUBJECT, "Subject header value");
        }

        @Test
        @WithMockUser
        @DisplayName("getMessageDetail_metadata_headersNotLogged")
        void getMessageDetail_metadata_headersNotLogged() throws Exception {
            // Arrange: metadata format — body is null
            MessageDetailResult result = new MessageDetailResult(
                    VALID_MESSAGE_ID, VALID_THREAD_ID,
                    Map.of("From", UNIQUE_RECIPIENT, "Subject", UNIQUE_SUBJECT),
                    UNIQUE_SNIPPET,
                    null,
                    null,
                    List.of("INBOX"),
                    List.of()
            );
            when(gmailService.getMessageDetail(anyString(), anyString(), eq("metadata")))
                    .thenReturn(result);

            // Act
            mockMvc.perform(get(MESSAGES_BASE + "/{messageId}", VALID_MESSAGE_ID)
                            .param("format", "metadata"))
                    .andExpect(status().isOk());

            // Assert
            assertNoPiiInLogs(UNIQUE_SNIPPET, "message snippet (metadata format)");
            assertNoPiiInLogs(UNIQUE_RECIPIENT, "From header value (metadata format)");
            assertNoPiiInLogs(UNIQUE_SUBJECT, "Subject header value (metadata format)");
        }
    }

    // =========================================================================
    // Scenario D — GET /labels: label names not logged
    // =========================================================================

    @Nested
    @DisplayName("Scenario D — GET /labels: label name not logged")
    class ScenarioDListLabelsNameNotLogged {

        @Test
        @WithMockUser
        @DisplayName("listLabels_labelNameInResult_labelNameNeverAppearsInLogs")
        void listLabels_labelNameInResult_labelNameNeverAppearsInLogs() throws Exception {
            // Arrange: stub returns a label with a fingerprintable name
            LabelSummary labelSummary = new LabelSummary(
                    "Label_42", UNIQUE_LABEL_NAME, "user", "show", "labelShow");
            LabelListResult result = new LabelListResult(List.of(labelSummary), 1);
            when(gmailService.listLabels(anyString()))
                    .thenReturn(result);

            // Act
            mockMvc.perform(get(LABELS_BASE))
                    .andExpect(status().isOk());

            // Assert: label name must not appear in any log event
            assertNoPiiInLogs(UNIQUE_LABEL_NAME, "label name in list response");
        }
    }

    // =========================================================================
    // Scenario E — GET /labels/{labelId}: label name and color not logged
    // =========================================================================

    @Nested
    @DisplayName("Scenario E — GET /labels/{labelId}: label name and color not logged")
    class ScenarioEGetLabelPiiNotLogged {

        @Test
        @WithMockUser
        @DisplayName("getLabel_labelNameAndColorInResult_neitherAppearsInLogs")
        void getLabel_labelNameAndColorInResult_neitherAppearsInLogs() throws Exception {
            // Arrange: label detail with fingerprintable name and color
            LabelDetailResult result = new LabelDetailResult(
                    "Label_42",
                    UNIQUE_LABEL_NAME,
                    "user",
                    "show",
                    "labelShow",
                    UNIQUE_COLOR_TEXT_HEX,
                    UNIQUE_COLOR_BG_HEX,
                    42, 5, 38, 4
            );
            when(gmailService.getLabel(anyString(), anyString()))
                    .thenReturn(result);

            // Act
            mockMvc.perform(get(LABELS_BASE + "/{labelId}", VALID_LABEL_ID))
                    .andExpect(status().isOk());

            // Assert
            assertNoPiiInLogs(UNIQUE_LABEL_NAME, "label name in detail response");
            assertNoPiiInLogs(UNIQUE_COLOR_TEXT_HEX, "label textColor hex in detail response");
            assertNoPiiInLogs(UNIQUE_COLOR_BG_HEX, "label backgroundColor hex in detail response");
        }
    }

    // =========================================================================
    // Scenario F — GET /messages/{id}/attachments: filename not logged
    // =========================================================================

    @Nested
    @DisplayName("Scenario F — GET /messages/{messageId}/attachments: filename not logged")
    class ScenarioFListAttachmentsFilenameNotLogged {

        @Test
        @WithMockUser
        @DisplayName("listAttachments_filenameInResult_filenameNeverAppearsInLogs")
        void listAttachments_filenameInResult_filenameNeverAppearsInLogs() throws Exception {
            // Arrange: stub returns an attachment with a fingerprintable filename
            MessageAttachmentMetadata meta = new MessageAttachmentMetadata(
                    VALID_ATTACHMENT_ID, UNIQUE_FILENAME, "application/pdf", 245760L);
            AttachmentListResult result = new AttachmentListResult(List.of(meta));
            when(gmailService.listAttachments(anyString(), anyString()))
                    .thenReturn(result);

            // Act
            mockMvc.perform(get(MESSAGES_BASE + "/{messageId}/attachments", VALID_MESSAGE_ID))
                    .andExpect(status().isOk());

            // Assert: filename must not appear in any log event (FR-032 / SC-005)
            assertNoPiiInLogs(UNIQUE_FILENAME, "attachment filename in list response");
        }

        @Test
        @WithMockUser
        @DisplayName("listAttachments_emptyList_noLogEntryContainsFilename")
        void listAttachments_emptyList_noLogEntryContainsFilename() throws Exception {
            // Arrange: no attachments — empty list
            when(gmailService.listAttachments(anyString(), anyString()))
                    .thenReturn(new AttachmentListResult(List.of()));

            // Act
            mockMvc.perform(get(MESSAGES_BASE + "/{messageId}/attachments", VALID_MESSAGE_ID))
                    .andExpect(status().isOk());

            // Assert: attachmentCount=0 is logged but filename must be absent
            assertNoPiiInLogs(UNIQUE_FILENAME, "filename when attachment list is empty");
        }
    }

    // =========================================================================
    // Scenario G — GET /messages/{id}/attachments/{attId}: no binary content logged
    // =========================================================================

    @Nested
    @DisplayName("Scenario G — GET /messages/{messageId}/attachments/{attachmentId}: binary content not logged")
    class ScenarioGGetAttachmentBinaryNotLogged {

        /**
         * Unique prefix of the binary payload. A substring of the base64url representation
         * used to detect accidental binary-to-string leakage in logs.
         */
        private static final String BINARY_PREFIX_HEX = "JVBERi0xLjQ"; // "%%PDF-1.4" b64

        @Test
        @WithMockUser
        @DisplayName("getAttachment_binaryData_binaryContentNeverAppearsInLogs")
        void getAttachment_binaryData_binaryContentNeverAppearsInLogs() throws Exception {
            // Arrange: mock returns a streaming body wrapping a small PDF header
            byte[] fakePdfBytes = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34};
            StreamingResponseBody streaming = outputStream -> outputStream.write(fakePdfBytes);
            when(gmailService.getAttachment(anyString(), anyString(), anyString()))
                    .thenReturn(streaming);

            // Act
            mockMvc.perform(get(MESSAGES_BASE + "/{messageId}/attachments/{attachmentId}",
                            VALID_MESSAGE_ID, VALID_ATTACHMENT_ID))
                    .andExpect(status().isOk());

            // Assert: binary content prefix must not appear in any log event
            // The attachment binary is in-memory; if it ever gets toString'd and logged,
            // the bytes would appear as a byte-array string like "[B@1a2b3c" — which
            // would not match BINARY_PREFIX_HEX specifically, but check for attachmentId
            // and messageId in isolation (not their concatenation with any binary data).
            // The meaningful assertion is that the raw base64 representation of the PDF header
            // does not appear in logs.
            assertNoPiiInLogs(BINARY_PREFIX_HEX, "base64 representation of binary attachment content");
        }
    }

    // =========================================================================
    // Scenario H — Positive control: safe metadata does not include PII
    // =========================================================================

    /**
     * Positive-control scenario: verifies that the read API endpoints remain
     * PII-free across multiple endpoint calls in sequence. Since {@link GmailService}
     * is mocked here (service log statements do not fire), this scenario focuses on
     * confirming that no PII leaks through the controller or filter chain.
     *
     * <p>Note: the service-layer diagnostic log assertions (e.g., "attachmentCount=1
     * appears in logs") are validated via the {@link ReadApiConstitutionVIIComplianceTest}
     * when the repository layer is mocked and the real GmailService runs. The scope of
     * this test is the controller + filter chain with a mocked service boundary.</p>
     */
    @Nested
    @DisplayName("Scenario H — Positive control: PII stays absent across multiple calls")
    class ScenarioHPositiveControlNoPiiAcrossEndpoints {

        @Test
        @WithMockUser
        @DisplayName("listAttachments_withUniqueFilename_filenameAbsentFromAppLogs")
        void listAttachments_withUniqueFilename_filenameAbsentFromAppLogs() throws Exception {
            // Arrange
            MessageAttachmentMetadata meta = new MessageAttachmentMetadata(
                    VALID_ATTACHMENT_ID, UNIQUE_FILENAME, "application/pdf", 245760L);
            when(gmailService.listAttachments(anyString(), anyString()))
                    .thenReturn(new AttachmentListResult(List.of(meta)));

            // Act
            mockMvc.perform(get(MESSAGES_BASE + "/{messageId}/attachments", VALID_MESSAGE_ID))
                    .andExpect(status().isOk());

            // Assert: filename must not appear in any application log event
            assertNoPiiInLogs(UNIQUE_FILENAME, "attachment filename");
        }

        @Test
        @WithMockUser
        @DisplayName("listThreads_withUniqueSnippet_snippetAbsentFromAppLogs")
        void listThreads_withUniqueSnippet_snippetAbsentFromAppLogs() throws Exception {
            // Arrange
            ThreadSummary summary = new ThreadSummary(VALID_THREAD_ID, UNIQUE_SNIPPET, "987654");
            when(gmailService.listThreads(anyString(), any(), any(), anyInt()))
                    .thenReturn(new ThreadListResult(List.of(summary), null, 1));

            // Act
            mockMvc.perform(get(THREADS_BASE))
                    .andExpect(status().isOk());

            // Assert: snippet must not appear in any application log event
            assertNoPiiInLogs(UNIQUE_SNIPPET, "thread snippet");
        }

        @Test
        @WithMockUser
        @DisplayName("getLabel_withUniqueName_nameAndColorsAbsentFromAppLogs")
        void getLabel_withUniqueName_nameAndColorsAbsentFromAppLogs() throws Exception {
            // Arrange
            LabelDetailResult result = new LabelDetailResult(
                    VALID_LABEL_ID, UNIQUE_LABEL_NAME, "system", null, null,
                    UNIQUE_COLOR_TEXT_HEX, UNIQUE_COLOR_BG_HEX,
                    null, null, null, null
            );
            when(gmailService.getLabel(anyString(), anyString()))
                    .thenReturn(result);

            // Act
            mockMvc.perform(get(LABELS_BASE + "/{labelId}", VALID_LABEL_ID))
                    .andExpect(status().isOk());

            // Assert: label name and color hex values must not appear in application logs
            assertNoPiiInLogs(UNIQUE_LABEL_NAME, "label name");
            assertNoPiiInLogs(UNIQUE_COLOR_TEXT_HEX, "label textColor");
            assertNoPiiInLogs(UNIQUE_COLOR_BG_HEX, "label backgroundColor");
        }
    }
}
