package com.aucontraire.gmailbuddy.integration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aucontraire.gmailbuddy.repository.GmailRepository;
import com.aucontraire.gmailbuddy.service.DraftCreationResult;
import com.aucontraire.gmailbuddy.service.GoogleTokenValidator;
import com.aucontraire.gmailbuddy.service.OriginalMessageLookup;
import com.aucontraire.gmailbuddy.service.SentMessageResult;
import com.aucontraire.gmailbuddy.util.CrlfSanitizingMessageConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Constitution VII compliance spot-check for the threading-attachments feature (T058).
 *
 * <p>Constitution Principle VII states: "OAuth tokens, credentials, email bodies, and
 * PII MUST NOT appear in logs." This test class verifies that the three new
 * user-controlled fields introduced by feature 002-threading-attachments respect the
 * following log-discipline requirements:</p>
 *
 * <ul>
 *   <li><strong>FR-019a</strong>: User-supplied values containing CR/LF are sanitized
 *       by {@link CrlfSanitizingMessageConverter} before emission, preventing log-line
 *       injection attacks.</li>
 *   <li><strong>FR-019</strong>: Attachment filenames, attachment content, and message
 *       body MUST NOT appear in any log statement at any level.</li>
 *   <li><strong>FR-020</strong>: Attachment count, total payload estimate bytes, and
 *       the list of MIME types MAY appear in diagnostic logs.</li>
 *   <li><strong>SC-004</strong>: Zero occurrences of attachment content or filenames
 *       in logs (Constitution VII compliance audit requirement).</li>
 * </ul>
 *
 * <h2>Test structure</h2>
 * <ul>
 *   <li>{@link Scenario1CrlfLogInjection}: verifies FR-019a — the
 *       {@link CrlfSanitizingMessageConverter} escapes {@code \r\n} in formatted
 *       messages, and that the validation error path does not echo back the raw
 *       injected value.</li>
 *   <li>{@link Scenario2RfcMessageIdNotLogged}: verifies that the server-fetched
 *       {@code rfcMessageId} from an {@link OriginalMessageLookup} never appears in
 *       any log event (FR-019).</li>
 *   <li>{@link Scenario3FilenameNotLogged}: verifies that the caller-supplied
 *       attachment {@code filename} never appears in logs, while diagnostic fields
 *       ({@code attachmentCount}, {@code mimeTypes}) do appear (FR-019, FR-020,
 *       SC-004).</li>
 * </ul>
 *
 * <p><strong>Constraint</strong>: This class does NOT modify any production code. If a
 * scenario reveals a real leak, the test is left failing and the orchestrator routes the
 * fix to the appropriate specialist agent.</p>
 *
 * <p>Test naming follows the project convention:
 * {@code methodName_stateUnderTest_expectedBehavior}.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("T058 — Constitution VII PII-in-logs compliance spot-check")
class ConstitutionVIIComplianceTest {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    private static final String MESSAGES_ENDPOINT = "/api/v1/gmail/messages";
    private static final String DRAFTS_ENDPOINT   = "/api/v1/gmail/drafts";

    /** A valid Gmail thread ID used in threading scenarios. */
    private static final String VALID_THREAD_ID   = "1a2b3c4d5e6f7a8b";

    /** A valid Gmail message ID used as the inReplyToMessageId. */
    private static final String VALID_IN_REPLY_TO = "2b3c4d5e6f7a8b9c";

    /**
     * Fingerprintable RFC 5322 Message-ID used in Scenario 2.
     * This value is unique enough that it will not appear in logs by coincidence.
     * It must NEVER appear in any log event (FR-019, Constitution VII).
     */
    private static final String RFC_MESSAGE_ID_FINGERPRINT =
            "<unique-fingerprint-rfc-id@mail.gmail.com>";

    /**
     * Fingerprintable attachment filename used in Scenario 3.
     * Unique enough to detect accidental leakage. Must not appear in any log event
     * (FR-019, SC-004).
     */
    private static final String UNIQUE_FILENAME = "unique-fingerprint-12345.pdf";

    /**
     * Small but valid base64 payload: {@code %PDF-1.4\n}.
     * Standard decoder accepts this without throwing {@code IllegalArgumentException}.
     */
    private static final String VALID_PDF_BASE64 = "JVBERi0xLjQK";

    private static final String STUB_MESSAGE_ID = "msg-stub-id-001";
    private static final String STUB_THREAD_ID  = "thread-stub-001";
    private static final String STUB_DRAFT_ID   = "draft-stub-001";

    // -------------------------------------------------------------------------
    // Spring-managed beans
    // -------------------------------------------------------------------------

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GoogleTokenValidator tokenValidator;

    /**
     * Mocked at the repository boundary so the real {@code GmailService} and
     * {@code MimeMessageBuilder} execute, exercising the actual log statements
     * under Constitution VII scrutiny.
     */
    @MockitoBean
    private GmailRepository gmailRepository;

    // -------------------------------------------------------------------------
    // Logback list appender — shared setup/teardown for Scenario 2 and 3
    // -------------------------------------------------------------------------

    /**
     * Captures ALL log events at ALL levels from the root logger during tests that
     * require cross-logger log scanning. Attached in {@link #attachRootAppender()} and
     * detached in {@link #detachRootAppender()} to avoid cross-test contamination.
     *
     * <p>Not used in Scenario 1 (which tests the converter directly and via HTTP response
     * code only, without needing cross-logger event capture).</p>
     */
    private ListAppender<ILoggingEvent> rootAppender;
    private Logger rootLogger;

    @BeforeEach
    void resetMocks() {
        reset(tokenValidator, gmailRepository);
    }

    private void attachRootAppender() {
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootAppender = new ListAppender<>();
        rootAppender.start();
        // Set the root logger to DEBUG so all levels are captured, including DEBUG
        // statements from GmailRepositoryImpl.getMessageHeaders() and GmailService
        rootLogger.setLevel(Level.DEBUG);
        rootLogger.addAppender(rootAppender);
    }

    @AfterEach
    void detachRootAppender() {
        if (rootLogger != null && rootAppender != null) {
            rootLogger.detachAppender(rootAppender);
            rootAppender.stop();
        }
    }

    // =========================================================================
    // Scenario 1 — FR-019a: CRLF log-injection sanitization
    // =========================================================================

    /**
     * Scenario 1: Log-injection sanitization on threading IDs.
     *
     * <p>Verifies FR-019a in two complementary ways:</p>
     * <ol>
     *   <li>Direct unit-test of {@link CrlfSanitizingMessageConverter#convert}: any message
     *       containing literal {@code \r} or {@code \n} characters has them replaced with
     *       the four-character escape sequences {@code \\r} and {@code \\n} respectively.</li>
     *   <li>HTTP-layer gate: a {@code threadId} containing {@code \r\n} is rejected by the
     *       {@code @Pattern("[0-9a-fA-F]{1,32}")} Bean Validation annotation with HTTP 400
     *       before any service code executes — no log statement in {@code GmailService} or
     *       {@code GmailRepositoryImpl} ever sees the offending value, so the injection
     *       attempt never reaches the application's log pipeline in the first place.</li>
     * </ol>
     *
     * <p>The converter test exercises the sanitization mechanism at the unit level
     * (independent of the HTTP test), verifying the Logback conversion rule that would fire
     * if an attacker-controlled string ever reached a log statement at the appender level.</p>
     */
    @Nested
    @DisplayName("Scenario 1 — FR-019a: CRLF sanitization via CrlfSanitizingMessageConverter")
    class Scenario1CrlfLogInjection {

        /**
         * Direct unit test of {@link CrlfSanitizingMessageConverter}: LF character
         * ({@code \n}, U+000A) is replaced with the four-character sequence {@code \\n}.
         *
         * <p>This is the primary sanitization path for log-injection via
         * newline-containing user input (FR-019a). The converter is the mechanism that
         * prevents log-line injection — this test verifies the mechanism itself works.</p>
         */
        @Test
        @DisplayName("converter_messageContainsLF_replacesWithEscapeSequence")
        void converter_messageContainsLF_replacesWithEscapeSequence() {
            // Arrange: build a real LoggingEvent carrying a message with an embedded LF
            CrlfSanitizingMessageConverter converter = new CrlfSanitizingMessageConverter();
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            converter.setContext(context);
            converter.start();

            // Simulate a log message that contains the CRLF-injected threadId value.
            // In practice, the @Pattern annotation blocks this from reaching GmailService,
            // but the converter must still handle it defensively.
            String rawMessage = "threadId=1234567890abcdef\nFAKE_LOG_LINE injected by attacker";
            LoggingEvent event = new LoggingEvent();
            event.setMessage(rawMessage);
            event.setLoggerName("com.aucontraire.gmailbuddy.service.GmailService");

            // Act
            String sanitized = converter.convert(event);

            // Assert: LF is replaced with the literal 4-char sequence \n (escaped), not a newline
            assertThat(sanitized)
                    .as("FR-019a: LF character must be replaced by '\\\\n' to prevent log injection")
                    .doesNotContain("\n")
                    .contains("\\n");
        }

        /**
         * Direct unit test of {@link CrlfSanitizingMessageConverter}: CR character
         * ({@code \r}, U+000D) is replaced with the four-character sequence {@code \\r}.
         */
        @Test
        @DisplayName("converter_messageContainsCR_replacesWithEscapeSequence")
        void converter_messageContainsCR_replacesWithEscapeSequence() {
            // Arrange
            CrlfSanitizingMessageConverter converter = new CrlfSanitizingMessageConverter();
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            converter.setContext(context);
            converter.start();

            String rawMessage = "threadId=1234567890abcdef\rinjected";
            LoggingEvent event = new LoggingEvent();
            event.setMessage(rawMessage);
            event.setLoggerName("com.aucontraire.gmailbuddy.service.GmailService");

            // Act
            String sanitized = converter.convert(event);

            // Assert: CR is replaced with the literal 4-char sequence \r
            assertThat(sanitized)
                    .as("FR-019a: CR character must be replaced by '\\\\r' to prevent log injection")
                    .doesNotContain("\r")
                    .contains("\\r");
        }

        /**
         * Direct unit test for the combined CRLF sequence: both {@code \r} and {@code \n}
         * are replaced when they appear together (the typical HTTP header injection pattern).
         */
        @Test
        @DisplayName("converter_messageContainsCRLF_replacesBothCharacters")
        void converter_messageContainsCRLF_replacesBothCharacters() {
            // Arrange
            CrlfSanitizingMessageConverter converter = new CrlfSanitizingMessageConverter();
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            converter.setContext(context);
            converter.start();

            String rawMessage = "1234567890abcdef\r\nFAKE_LOG_LINE";
            LoggingEvent event = new LoggingEvent();
            event.setMessage(rawMessage);
            event.setLoggerName("com.aucontraire.gmailbuddy.test");

            // Act
            String sanitized = converter.convert(event);

            // Assert: no literal CRLF sequence remains; both escape sequences are present
            assertThat(sanitized)
                    .as("FR-019a: CRLF sequence must be fully escaped — no literal \\r or \\n")
                    .doesNotContain("\r\n")
                    .doesNotContain("\r")
                    .doesNotContain("\n")
                    .contains("\\r")
                    .contains("\\n");
        }

        /**
         * HTTP-gate test: {@code POST /api/v1/gmail/messages} with a {@code threadId}
         * containing {@code \r\n} is rejected by Bean Validation with HTTP 400.
         *
         * <p>The {@code @Pattern(regexp = "[0-9a-fA-F]{1,32}")} annotation on
         * {@code SendMessageDTO.threadId} rejects all non-hexadecimal characters including
         * CR and LF, satisfying FR-001a. This gate means the CRLF-injected value never
         * reaches any service or repository log statement, providing defense-in-depth on top
         * of the converter-level sanitization tested above.</p>
         */
        @Test
        @WithMockUser
        @DisplayName("postMessages_threadIdContainsCRLF_rejectedWith400BeforeServiceCode")
        void postMessages_threadIdContainsCRLF_rejectedWith400BeforeServiceCode() throws Exception {
            // Arrange: CRLF embedded in threadId; this string fails @Pattern([0-9a-fA-F]{1,32})
            Map<String, Object> body = Map.of(
                    "to",       List.of("recruiter@example.com"),
                    "subject",  "Follow-up",
                    "body",     "Reaching out.",
                    "threadId", "1234567890abcdef\r\nFAKE_LOG_LINE"
            );
            String json = objectMapper.writeValueAsString(body);

            // Act & Assert: Bean Validation rejects the request; GmailService never runs
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        /**
         * HTTP-gate test for {@code inReplyToMessageId}: same CRLF-in-field rejection
         * as {@code threadId}, because both carry the same {@code @Pattern} annotation.
         */
        @Test
        @WithMockUser
        @DisplayName("postMessages_inReplyToMessageIdContainsCRLF_rejectedWith400")
        void postMessages_inReplyToMessageIdContainsCRLF_rejectedWith400() throws Exception {
            // Arrange
            Map<String, Object> body = Map.of(
                    "to",                  List.of("recruiter@example.com"),
                    "subject",             "Follow-up",
                    "body",                "Reaching out.",
                    "inReplyToMessageId",  "1234567890abcdef\r\nFAKE_LOG_LINE"
            );
            String json = objectMapper.writeValueAsString(body);

            // Act & Assert
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Verifies that a valid (clean) {@code threadId} is NOT rejected by the
         * {@code @Pattern} validator. This is a positive control for the above tests,
         * confirming the hex pattern itself is not over-restrictive.
         */
        @Test
        @WithMockUser
        @DisplayName("postMessages_validHexThreadId_notRejectedByPatternValidator")
        void postMessages_validHexThreadId_notRejectedByPatternValidator() throws Exception {
            // Arrange: mock repository so the request can complete
            when(gmailRepository.sendMessage(anyString(), any(MimeMessage.class), anyString()))
                    .thenReturn(new SentMessageResult(STUB_MESSAGE_ID, STUB_THREAD_ID));

            Map<String, Object> body = Map.of(
                    "to",      List.of("recruiter@example.com"),
                    "subject", "Follow-up",
                    "body",    "Reaching out.",
                    "threadId", VALID_THREAD_ID   // clean hex — must pass @Pattern
            );
            String json = objectMapper.writeValueAsString(body);

            // Act & Assert: 201 Created (validation passes)
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated());
        }
    }

    // =========================================================================
    // Scenario 2 — FR-019: rfcMessageId must not appear in any log event
    // =========================================================================

    /**
     * Scenario 2: The server-fetched {@code rfcMessageId} from
     * {@link OriginalMessageLookup} must never appear in any log event.
     *
     * <p>The {@code rfcMessageId} is extracted from Gmail's {@code users.messages.get}
     * response and passed directly into {@code MimeMessageBuilder} to set {@code In-Reply-To}
     * and {@code References} MIME headers. It must not be logged at any level — it is a
     * server-side value derived from the original message's metadata, but its exposure in
     * logs would violate Constitution VII (PII/tracking-ID leak). The spec explicitly
     * documents this constraint in {@code OriginalMessageLookup}'s Javadoc ("No fields in
     * this record are ever logged at any level").</p>
     *
     * <p>The {@code inReplyToMessageId} (the caller-supplied Gmail short ID) IS permitted
     * to appear in logs because it is an opaque hexadecimal identifier analogous to a
     * message sequence number (FR-019b spirit — no sensitive content is revealed).</p>
     */
    @Nested
    @DisplayName("Scenario 2 — FR-019: rfcMessageId must not appear in any log event")
    class Scenario2RfcMessageIdNotLogged {

        @BeforeEach
        void setUp() {
            attachRootAppender();
        }

        /**
         * Submit a threaded send request with a mocked {@code getMessageHeaders} that
         * returns a lookup containing the fingerprintable {@code RFC_MESSAGE_ID_FINGERPRINT}.
         * After the request completes, assert that no captured log event at any level
         * contains the literal {@code rfcMessageId} value.
         */
        @Test
        @WithMockUser
        @DisplayName("postMessages_withInReplyToMessageId_rfcMessageIdNeverAppearsInLogs")
        void postMessages_withInReplyToMessageId_rfcMessageIdNeverAppearsInLogs() throws Exception {
            // Arrange: mock getMessageHeaders to return a lookup with the fingerprintable rfcMessageId
            OriginalMessageLookup lookup = new OriginalMessageLookup(
                    VALID_IN_REPLY_TO, VALID_THREAD_ID, RFC_MESSAGE_ID_FINGERPRINT);
            when(gmailRepository.getMessageHeaders(anyString(), eq(VALID_IN_REPLY_TO)))
                    .thenReturn(lookup);
            when(gmailRepository.sendMessage(anyString(), any(MimeMessage.class), anyString()))
                    .thenReturn(new SentMessageResult(STUB_MESSAGE_ID, STUB_THREAD_ID));

            Map<String, Object> body = Map.of(
                    "to",                  List.of("recruiter@example.com"),
                    "subject",             "Re: Your inquiry",
                    "body",                "Following up on our conversation.",
                    "inReplyToMessageId",  VALID_IN_REPLY_TO
            );

            // Act
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated());

            // Assert: no log event at any level contains the rfcMessageId fingerprint
            List<ILoggingEvent> allEvents = rootAppender.list;
            long leakingEvents = allEvents.stream()
                    .filter(e -> e.getFormattedMessage().contains(RFC_MESSAGE_ID_FINGERPRINT))
                    .count();
            assertThat(leakingEvents)
                    .as("FR-019 / Constitution VII: rfcMessageId '%s' MUST NOT appear in any log event " +
                        "(found %d violating events)", RFC_MESSAGE_ID_FINGERPRINT, leakingEvents)
                    .isZero();
        }

        /**
         * Confirm that the request DOES log the caller-supplied {@code inReplyToMessageId}
         * (the short hex Gmail ID), which is acceptable per FR-019b's spirit — the hex ID
         * is an opaque identifier with no PII content. This is the positive control that
         * distinguishes "nothing logged from threading" (too strict) from "rfcMessageId
         * not logged" (the actual requirement).
         */
        @Test
        @WithMockUser
        @DisplayName("postMessages_withInReplyToMessageId_inReplyToMessageIdMayAppearInLogs")
        void postMessages_withInReplyToMessageId_inReplyToMessageIdMayAppearInLogs() throws Exception {
            // Arrange
            OriginalMessageLookup lookup = new OriginalMessageLookup(
                    VALID_IN_REPLY_TO, VALID_THREAD_ID, RFC_MESSAGE_ID_FINGERPRINT);
            when(gmailRepository.getMessageHeaders(anyString(), eq(VALID_IN_REPLY_TO)))
                    .thenReturn(lookup);
            when(gmailRepository.sendMessage(anyString(), any(MimeMessage.class), anyString()))
                    .thenReturn(new SentMessageResult(STUB_MESSAGE_ID, STUB_THREAD_ID));

            Map<String, Object> body = Map.of(
                    "to",                  List.of("recruiter@example.com"),
                    "subject",             "Re: Your inquiry",
                    "body",                "Following up on our conversation.",
                    "inReplyToMessageId",  VALID_IN_REPLY_TO
            );

            // Act
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated());

            // Assert: the opaque hex inReplyToMessageId DOES appear in at least one log event
            // (GmailService logs it as "Threading lookup: inReplyToMessageId=..., userId=...")
            List<ILoggingEvent> allEvents = rootAppender.list;
            boolean inReplyToLogged = allEvents.stream()
                    .anyMatch(e -> e.getFormattedMessage().contains(VALID_IN_REPLY_TO));
            assertThat(inReplyToLogged)
                    .as("FR-019b spirit: inReplyToMessageId (opaque hex ID) should appear in diagnostic " +
                        "log to confirm threading lookup occurred. If absent, the threading log statement " +
                        "may have been removed — check GmailService#sendMessage threading log.")
                    .isTrue();
        }

        /**
         * Same rfcMessageId non-logging assertion for the draft creation path
         * ({@code POST /api/v1/gmail/drafts}), since both {@code sendMessage} and
         * {@code createDraft} share the same threading orchestration in {@code GmailService}.
         */
        @Test
        @WithMockUser
        @DisplayName("postDrafts_withInReplyToMessageId_rfcMessageIdNeverAppearsInLogs")
        void postDrafts_withInReplyToMessageId_rfcMessageIdNeverAppearsInLogs() throws Exception {
            // Arrange
            OriginalMessageLookup lookup = new OriginalMessageLookup(
                    VALID_IN_REPLY_TO, VALID_THREAD_ID, RFC_MESSAGE_ID_FINGERPRINT);
            when(gmailRepository.getMessageHeaders(anyString(), eq(VALID_IN_REPLY_TO)))
                    .thenReturn(lookup);
            when(gmailRepository.createDraft(anyString(), any(MimeMessage.class), anyString()))
                    .thenReturn(new DraftCreationResult(STUB_DRAFT_ID, STUB_MESSAGE_ID, STUB_THREAD_ID));

            Map<String, Object> body = Map.of(
                    "to",                  List.of("recruiter@example.com"),
                    "subject",             "Re: Your inquiry",
                    "body",                "Following up on our conversation.",
                    "inReplyToMessageId",  VALID_IN_REPLY_TO
            );

            // Act
            mockMvc.perform(post(DRAFTS_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated());

            // Assert: rfcMessageId must not appear in logs for the draft path either
            List<ILoggingEvent> allEvents = rootAppender.list;
            long leakingEvents = allEvents.stream()
                    .filter(e -> e.getFormattedMessage().contains(RFC_MESSAGE_ID_FINGERPRINT))
                    .count();
            assertThat(leakingEvents)
                    .as("FR-019 / Constitution VII: rfcMessageId '%s' MUST NOT appear in any log event " +
                        "for the draft creation path (found %d violating events)",
                        RFC_MESSAGE_ID_FINGERPRINT, leakingEvents)
                    .isZero();
        }
    }

    // =========================================================================
    // Scenario 3 — FR-019 / FR-020: filename not logged; count + mimeTypes are
    // =========================================================================

    /**
     * Scenario 3: The caller-supplied attachment {@code filename} must not appear in any
     * log event; diagnostic fields ({@code attachmentCount} and {@code mimeTypes}) MUST
     * appear.
     *
     * <p>FR-019 forbids logging attachment content, attachment filenames, or message body.
     * FR-020 explicitly permits (and encourages) logging attachment count, total payload
     * bytes, and the list of MIME types for diagnostic purposes.</p>
     *
     * <p>SC-004 requires zero occurrences of attachment content or filenames in logs over
     * a representative period — this test provides the automated enforcement of that
     * success criterion.</p>
     *
     * <p>Positive assertions for FR-020 are included in
     * {@link #postMessages_withAttachment_diagnosticLogContainsAttachmentCountAndMimeTypes}
     * to verify that Constitution VII compliance does not sacrifice observability.</p>
     */
    @Nested
    @DisplayName("Scenario 3 — FR-019/FR-020: filename not logged; count + mimeTypes are")
    class Scenario3FilenameNotLogged {

        @BeforeEach
        void setUp() {
            attachRootAppender();
        }

        /**
         * Primary assertion: no log event at any level (DEBUG, INFO, WARN, ERROR)
         * contains the literal {@code UNIQUE_FILENAME} string after a successful send
         * with an attachment.
         *
         * <p>Uses a fingerprintable filename value that is highly unlikely to appear
         * in any log message except via an unintended leak.</p>
         */
        @Test
        @WithMockUser
        @DisplayName("postMessages_withAttachment_noLogEventContainsFilename")
        void postMessages_withAttachment_noLogEventContainsFilename() throws Exception {
            // Arrange
            when(gmailRepository.sendMessage(anyString(), any(MimeMessage.class), any()))
                    .thenReturn(new SentMessageResult(STUB_MESSAGE_ID, STUB_THREAD_ID));

            String json = singleAttachmentBody(UNIQUE_FILENAME, "application/pdf", VALID_PDF_BASE64);

            // Act
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated());

            // Assert: no log event at any level contains the filename
            List<ILoggingEvent> allEvents = rootAppender.list;
            long leakingEvents = allEvents.stream()
                    .filter(e -> e.getFormattedMessage().contains(UNIQUE_FILENAME))
                    .count();
            assertThat(leakingEvents)
                    .as("FR-019 / SC-004 / Constitution VII: filename '%s' MUST NOT appear in any log event " +
                        "(found %d violating events at levels: %s)",
                        UNIQUE_FILENAME, leakingEvents,
                        allEvents.stream()
                                .filter(e -> e.getFormattedMessage().contains(UNIQUE_FILENAME))
                                .map(e -> e.getLevel().toString())
                                .toList())
                    .isZero();
        }

        /**
         * No log event should contain any substring of the base64Data value either.
         * Uses a known base64 token ({@code JVBERi0x}) that is the start of the
         * {@code VALID_PDF_BASE64} string.
         */
        @Test
        @WithMockUser
        @DisplayName("postMessages_withAttachment_noLogEventContainsBase64DataSubstring")
        void postMessages_withAttachment_noLogEventContainsBase64DataSubstring() throws Exception {
            // Arrange
            when(gmailRepository.sendMessage(anyString(), any(MimeMessage.class), any()))
                    .thenReturn(new SentMessageResult(STUB_MESSAGE_ID, STUB_THREAD_ID));

            String json = singleAttachmentBody(UNIQUE_FILENAME, "application/pdf", VALID_PDF_BASE64);

            // Act
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated());

            // Assert: no log event contains any part of the base64Data
            // Check both the full string and the first 8 characters (a unique prefix of VALID_PDF_BASE64)
            String base64Prefix = VALID_PDF_BASE64.substring(0, 8); // "JVBERi0x"
            List<ILoggingEvent> allEvents = rootAppender.list;

            long fullBase64Leaks = allEvents.stream()
                    .filter(e -> e.getFormattedMessage().contains(VALID_PDF_BASE64))
                    .count();
            long prefixLeaks = allEvents.stream()
                    .filter(e -> e.getFormattedMessage().contains(base64Prefix))
                    .count();

            assertThat(fullBase64Leaks)
                    .as("FR-019: Full base64Data value MUST NOT appear in any log event")
                    .isZero();
            assertThat(prefixLeaks)
                    .as("FR-019: Prefix of base64Data ('%s') MUST NOT appear in any log event", base64Prefix)
                    .isZero();
        }

        /**
         * Positive assertion for FR-020: the diagnostic log statement MUST emit
         * {@code attachmentCount=1} and {@code mimeTypes=} so operators can diagnose
         * attachment processing issues without seeing content.
         *
         * <p>This is the "bonus" FR-020 check from the T058 task description. If this
         * assertion fails, the diagnostic log statement has been removed or changed —
         * check {@code GmailService#sendMessage} around the FR-020 comment block.</p>
         */
        @Test
        @WithMockUser
        @DisplayName("postMessages_withAttachment_diagnosticLogContainsAttachmentCountAndMimeTypes")
        void postMessages_withAttachment_diagnosticLogContainsAttachmentCountAndMimeTypes()
                throws Exception {
            // Arrange
            when(gmailRepository.sendMessage(anyString(), any(MimeMessage.class), any()))
                    .thenReturn(new SentMessageResult(STUB_MESSAGE_ID, STUB_THREAD_ID));

            String json = singleAttachmentBody(UNIQUE_FILENAME, "application/pdf", VALID_PDF_BASE64);

            // Act
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated());

            // Assert: FR-020 positive assertions
            List<ILoggingEvent> allEvents = rootAppender.list;

            boolean foundAttachmentCount = allEvents.stream()
                    .anyMatch(e -> e.getFormattedMessage().contains("attachmentCount=1"));
            assertThat(foundAttachmentCount)
                    .as("FR-020: diagnostic log MUST contain 'attachmentCount=1' after sending 1 attachment. " +
                        "Check GmailService#sendMessage FR-019a/FR-020 log statement.")
                    .isTrue();

            boolean foundMimeTypes = allEvents.stream()
                    .anyMatch(e -> e.getFormattedMessage().contains("mimeTypes="));
            assertThat(foundMimeTypes)
                    .as("FR-020: diagnostic log MUST contain 'mimeTypes=' for observability. " +
                        "Check GmailService#sendMessage FR-019a/FR-020 log statement.")
                    .isTrue();

            boolean foundMimeTypeValue = allEvents.stream()
                    .anyMatch(e -> e.getFormattedMessage().contains("application/pdf"));
            assertThat(foundMimeTypeValue)
                    .as("FR-020: 'mimeTypes=' log field MUST include the actual MIME type 'application/pdf'. " +
                        "Check GmailService#sendMessage — mimeTypes list should include Attachment::mimeType.")
                    .isTrue();
        }

        /**
         * Confirms that the diagnostic log does NOT contain the filename even when
         * examining the mimeTypes field format — the mimeTypes list must contain only
         * MIME types, not filenames.
         */
        @Test
        @WithMockUser
        @DisplayName("postMessages_withAttachment_mimeTypesFieldDoesNotContainFilename")
        void postMessages_withAttachment_mimeTypesFieldDoesNotContainFilename() throws Exception {
            // Arrange
            when(gmailRepository.sendMessage(anyString(), any(MimeMessage.class), any()))
                    .thenReturn(new SentMessageResult(STUB_MESSAGE_ID, STUB_THREAD_ID));

            // Use a MIME type that has no lexical overlap with the filename to make
            // the assertion deterministic
            String json = singleAttachmentBody(UNIQUE_FILENAME, "application/pdf", VALID_PDF_BASE64);

            // Act
            mockMvc.perform(post(MESSAGES_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated());

            // Find the mimeTypes log line specifically and verify it does not contain the filename
            List<ILoggingEvent> allEvents = rootAppender.list;
            List<String> mimeTypeLogLines = allEvents.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(msg -> msg.contains("mimeTypes="))
                    .toList();

            // At least one mimeTypes log line must exist (FR-020)
            assertThat(mimeTypeLogLines)
                    .as("FR-020: at least one log line containing 'mimeTypes=' must exist")
                    .isNotEmpty();

            // None of the mimeTypes log lines may contain the filename
            mimeTypeLogLines.forEach(line ->
                    assertThat(line)
                            .as("FR-019: the mimeTypes= log line must NOT contain the attachment filename '%s'. " +
                                "This would indicate the filename is being logged alongside MIME types.",
                                UNIQUE_FILENAME)
                            .doesNotContain(UNIQUE_FILENAME)
            );
        }

        /**
         * Draft path equivalence: same filename-not-logged assertion for
         * {@code POST /api/v1/gmail/drafts} to confirm FR-019 is enforced in both
         * the send and draft creation code paths (both go through the same
         * {@code GmailService#createDraft} log statement).
         */
        @Test
        @WithMockUser
        @DisplayName("postDrafts_withAttachment_noLogEventContainsFilename")
        void postDrafts_withAttachment_noLogEventContainsFilename() throws Exception {
            // Arrange
            when(gmailRepository.createDraft(anyString(), any(MimeMessage.class), any()))
                    .thenReturn(new DraftCreationResult(STUB_DRAFT_ID, STUB_MESSAGE_ID, STUB_THREAD_ID));

            String json = singleAttachmentBody(UNIQUE_FILENAME, "application/pdf", VALID_PDF_BASE64);

            // Act
            mockMvc.perform(post(DRAFTS_ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated());

            // Assert
            List<ILoggingEvent> allEvents = rootAppender.list;
            long leakingEvents = allEvents.stream()
                    .filter(e -> e.getFormattedMessage().contains(UNIQUE_FILENAME))
                    .count();
            assertThat(leakingEvents)
                    .as("FR-019 / SC-004: filename '%s' MUST NOT appear in any log event for the draft path " +
                        "(found %d violating events)", UNIQUE_FILENAME, leakingEvents)
                    .isZero();
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Builds the JSON body for a single-attachment send/draft request.
     *
     * @param filename  the attachment filename (may be the fingerprintable unique value)
     * @param mimeType  the attachment MIME type
     * @param base64Data the base64-encoded attachment content
     * @return JSON string suitable for {@code MockMvc} request body
     */
    private String singleAttachmentBody(String filename, String mimeType, String base64Data)
            throws Exception {
        Map<String, Object> body = Map.of(
                "to",          List.of("recruiter@example.com"),
                "subject",     "Outreach with attachment",
                "body",        "Please find the attachment enclosed.",
                "attachments", List.of(
                        Map.of(
                                "filename",   filename,
                                "mimeType",   mimeType,
                                "base64Data", base64Data
                        )
                )
        );
        return objectMapper.writeValueAsString(body);
    }
}
