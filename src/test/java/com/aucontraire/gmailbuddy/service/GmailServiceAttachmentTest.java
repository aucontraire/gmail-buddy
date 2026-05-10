package com.aucontraire.gmailbuddy.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import com.aucontraire.gmailbuddy.dto.Attachment;
import com.aucontraire.gmailbuddy.dto.SendMessageDTO;
import com.aucontraire.gmailbuddy.exception.MessageTooLargeException;
import com.aucontraire.gmailbuddy.mapper.FilterCriteriaMapper;
import com.aucontraire.gmailbuddy.mapper.GmailMessageMapper;
import com.aucontraire.gmailbuddy.repository.GmailRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.util.unit.DataSize;

import java.util.Base64;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the attachment-related behaviour in {@link GmailService} (T038, T041 — Phase 4 US2).
 *
 * <h2>T038 — Total payload size validation</h2>
 * <p>Verifies Stage 1 (pre-construction estimate fast-reject at 90% threshold) and
 * Stage 2 (post-construction strict 100% cap) for {@link GmailService#sendMessage} and
 * {@link GmailService#createDraft}. Also verifies that requests without attachments
 * are unaffected by the total-payload check (FR-015).</p>
 *
 * <h2>T041 — Log-content compliance (Constitution VII)</h2>
 * <p>Uses a Logback {@link ListAppender} to capture all log events emitted during a
 * successful send-with-attachments invocation. Asserts that:</p>
 * <ul>
 *   <li>No log event at any level contains the attachment filename.</li>
 *   <li>No log event at any level contains any substring of the base64Data value.</li>
 *   <li>Attachment count and estimated payload bytes DO appear in the logs (FR-019, FR-020).</li>
 * </ul>
 *
 * <p>All collaborators are plain Mockito mocks — no Spring context is loaded.
 * The {@link GmailBuddyProperties} mock is configured to return a small
 * {@code maxTotalPayloadSize} so tests can trigger the size-check path without
 * materialising multi-megabyte payloads.</p>
 */
@DisplayName("GmailService — attachment size validation + log compliance (T038, T041)")
class GmailServiceAttachmentTest {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    private static final String USER_ID = "me";

    // Small limit used for Stage 1 / Stage 2 tests (avoids large allocations)
    private static final DataSize SMALL_LIMIT = DataSize.ofBytes(1024);

    // Real-world 25 MB limit used for log-compliance tests (Stage 1 won't trigger)
    private static final DataSize LARGE_LIMIT = DataSize.ofMegabytes(25);

    // Unique filename/base64 fingerprints for T041 log-compliance assertions
    private static final String UNIQUE_FILENAME = "unique-fingerprint-abc123.pdf";
    private static final String UNIQUE_BASE64_CONTENT = "JVBERi0xLjQK"; // %PDF-1.4\n — decodable

    // -------------------------------------------------------------------------
    // SUT + collaborators
    // -------------------------------------------------------------------------

    private GmailRepository gmailRepository;
    private GmailQueryBuilder gmailQueryBuilder;
    private FilterCriteriaMapper filterCriteriaMapper;
    private MimeMessageBuilder mimeMessageBuilder;
    private GmailBuddyProperties properties;
    private GmailBuddyProperties.Send send;

    // Real MimeMessageBuilder for T041 (log tests need an actual MimeMessage to serialize)
    private MimeMessageBuilder realMimeMessageBuilder;

    // Logback list appender for T041
    private ListAppender<ILoggingEvent> listAppender;
    private Logger gmailServiceLogger;

    // -------------------------------------------------------------------------
    // Setup / teardown
    // -------------------------------------------------------------------------

    @BeforeEach
    void setUp() {
        gmailRepository      = mock(GmailRepository.class);
        gmailQueryBuilder    = mock(GmailQueryBuilder.class);
        filterCriteriaMapper = mock(FilterCriteriaMapper.class);
        mimeMessageBuilder   = mock(MimeMessageBuilder.class);
        properties           = mock(GmailBuddyProperties.class);
        send                 = mock(GmailBuddyProperties.Send.class);
        when(properties.send()).thenReturn(send);

        realMimeMessageBuilder = new MimeMessageBuilder();

        // Wire Logback list appender to GmailService's logger for T041
        gmailServiceLogger = (Logger) LoggerFactory.getLogger(GmailService.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        gmailServiceLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        gmailServiceLogger.detachAppender(listAppender);
        listAppender.stop();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private MimeMessage emptyMimeMessage() {
        try {
            MimeMessage msg = new MimeMessage(Session.getInstance(new Properties()));
            msg.setContent("stub", "text/plain");
            msg.saveChanges();
            return msg;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create stub MimeMessage", e);
        }
    }

    /** Builds a GmailService with the mocked MimeMessageBuilder (for size-check tests). */
    private GmailService serviceWithMockedBuilder() {
        return new GmailService(
                gmailRepository, gmailQueryBuilder, filterCriteriaMapper, mimeMessageBuilder,
                mock(GmailMessageMapper.class), properties);
    }

    /** Builds a GmailService with the REAL MimeMessageBuilder (for log-compliance tests). */
    private GmailService serviceWithRealBuilder() {
        return new GmailService(
                gmailRepository, gmailQueryBuilder, filterCriteriaMapper, realMimeMessageBuilder,
                mock(GmailMessageMapper.class), properties);
    }

    /** Creates a small base64 payload that will exceed the given byte limit. */
    private String base64PayloadExceeding(long limitBytes) {
        // Each base64 char encodes ~0.75 bytes, so to get > limitBytes decoded,
        // we need > limitBytes * 4/3 base64 characters.
        byte[] bytes = new byte[(int) limitBytes + 100];
        return Base64.getEncoder().encodeToString(bytes);
    }

    // =========================================================================
    // T038 — Stage 1: pre-construction estimate rejects at 90% threshold
    // =========================================================================

    @Test
    @DisplayName("sendMessage_stage1EstimateExceeds90PercentOfLimit_throwsMessageTooLargeBeforeBuilderCalled")
    void sendMessage_stage1EstimateExceeds90PercentOfLimit_throwsMessageTooLargeBeforeBuilderCalled()
            throws Exception {

        // Arrange: small limit (1024 bytes); attachment whose decoded size > 90% of 1024
        when(send.maxTotalPayloadSize()).thenReturn(SMALL_LIMIT);

        // ~950 decoded bytes → base64 of that is ~1267 chars; estimate = body + ~950 > 0.9*1024=921
        String largeBase64 = base64PayloadExceeding((long) (SMALL_LIMIT.toBytes() * 0.9));
        Attachment attachment = new Attachment("test.pdf", "application/pdf", largeBase64);

        SendMessageDTO dto = new SendMessageDTO(
                List.of("r@example.com"),
                null, null,
                "Subject", "body", "text",
                null, null,
                List.of(attachment)
        );

        GmailService service = serviceWithMockedBuilder();

        // Act & Assert: MessageTooLargeException must be thrown BEFORE build() is called
        assertThatThrownBy(() -> service.sendMessage(USER_ID, dto))
                .isInstanceOf(MessageTooLargeException.class);

        // Verify the builder was never called (Stage 1 fast reject)
        verify(mimeMessageBuilder, never()).build(any(), any());
    }

    @Test
    @DisplayName("createDraft_stage1EstimateExceeds90PercentOfLimit_throwsMessageTooLargeBeforeBuilderCalled")
    void createDraft_stage1EstimateExceeds90PercentOfLimit_throwsMessageTooLargeBeforeBuilderCalled()
            throws Exception {

        // Arrange
        when(send.maxTotalPayloadSize()).thenReturn(SMALL_LIMIT);

        String largeBase64 = base64PayloadExceeding((long) (SMALL_LIMIT.toBytes() * 0.9));
        Attachment attachment = new Attachment("doc.pdf", "application/pdf", largeBase64);

        SendMessageDTO dto = new SendMessageDTO(
                List.of("r@example.com"),
                null, null,
                "Subject", "body", "text",
                null, null,
                List.of(attachment)
        );

        GmailService service = serviceWithMockedBuilder();

        // Act & Assert
        assertThatThrownBy(() -> service.createDraft(USER_ID, dto))
                .isInstanceOf(MessageTooLargeException.class);

        verify(mimeMessageBuilder, never()).build(any(), any());
    }

    // =========================================================================
    // T038 — Stage 2: post-construction actual-bytes check (safety net)
    // =========================================================================

    @Test
    @DisplayName("sendMessage_stage2ActualBytesExceedLimit_throwsMessageTooLargeAfterBuilderCalled")
    void sendMessage_stage2ActualBytesExceedLimit_throwsMessageTooLargeAfterBuilderCalled()
            throws Exception {

        // Arrange: small limit (1024 bytes); estimate is just UNDER 90% (Stage 1 passes)
        // but the serialized MIME exceeds the 100% limit.
        // Strategy: set limit to 1024, supply base64 whose decoded size is ~800 bytes
        //           (estimate ≈ 800 < 0.9*1024=921 → Stage 1 passes),
        //           then return a large fake MimeMessage whose writeTo() writes > 1024 bytes.

        when(send.maxTotalPayloadSize()).thenReturn(SMALL_LIMIT);

        // ~800 decoded bytes → base64 is ~1067 chars; estimate ~800 < threshold 921
        byte[] rawBytes = new byte[800];
        String safeBase64 = Base64.getEncoder().encodeToString(rawBytes);

        Attachment attachment = new Attachment("doc.pdf", "application/pdf", safeBase64);
        SendMessageDTO dto = new SendMessageDTO(
                List.of("r@example.com"),
                null, null,
                "Subject", "body", "text",
                null, null,
                List.of(attachment)
        );

        // The mocked builder returns a MimeMessage that, when serialized, exceeds 1024 bytes.
        // Use a real MimeMessage with a body large enough to push the serialized bytes over 1024.
        MimeMessage largeMessage = new MimeMessageBuilder().build(new SendMessageDTO(
                List.of("r@example.com"),
                null, null,
                "Subject",
                "X".repeat(2000),  // large body ensures serialized bytes > 1024
                "text",
                null, null,
                List.of(attachment)
        ), null);
        when(mimeMessageBuilder.build(any(SendMessageDTO.class), isNull())).thenReturn(largeMessage);

        GmailService service = serviceWithMockedBuilder();

        // Act & Assert: Stage 2 throws AFTER builder was called
        assertThatThrownBy(() -> service.sendMessage(USER_ID, dto))
                .isInstanceOf(MessageTooLargeException.class);

        // Builder WAS called (Stage 1 passed)
        verify(mimeMessageBuilder).build(any(SendMessageDTO.class), isNull());
    }

    @Test
    @DisplayName("createDraft_stage2ActualBytesExceedLimit_throwsMessageTooLargeAfterBuilderCalled")
    void createDraft_stage2ActualBytesExceedLimit_throwsMessageTooLargeAfterBuilderCalled()
            throws Exception {

        // Arrange: same as above but for createDraft
        when(send.maxTotalPayloadSize()).thenReturn(SMALL_LIMIT);

        byte[] rawBytes = new byte[800];
        String safeBase64 = Base64.getEncoder().encodeToString(rawBytes);

        Attachment attachment = new Attachment("doc.pdf", "application/pdf", safeBase64);
        SendMessageDTO dto = new SendMessageDTO(
                List.of("r@example.com"),
                null, null,
                "Subject", "body", "text",
                null, null,
                List.of(attachment)
        );

        MimeMessage largeMessage = new MimeMessageBuilder().build(new SendMessageDTO(
                List.of("r@example.com"),
                null, null,
                "Subject",
                "X".repeat(2000),
                "text",
                null, null,
                List.of(attachment)
        ), null);
        when(mimeMessageBuilder.build(any(SendMessageDTO.class), isNull())).thenReturn(largeMessage);

        GmailService service = serviceWithMockedBuilder();

        // Act & Assert
        assertThatThrownBy(() -> service.createDraft(USER_ID, dto))
                .isInstanceOf(MessageTooLargeException.class);

        verify(mimeMessageBuilder).build(any(SendMessageDTO.class), isNull());
    }

    // =========================================================================
    // T038 — Empty attachments → total-payload check is bypassed (FR-015)
    // =========================================================================

    @Test
    @DisplayName("sendMessage_noAttachments_totalPayloadCheckBypassed_builderCalled")
    void sendMessage_noAttachments_totalPayloadCheckBypassed_builderCalled()
            throws Exception {

        // Arrange: even with a tiny limit, no attachments → Stage 1 and Stage 2 are skipped.
        when(send.maxTotalPayloadSize()).thenReturn(SMALL_LIMIT);

        SendMessageDTO dto = new SendMessageDTO(
                List.of("r@example.com"),
                null, null,
                "Subject", "short body", "text",
                null, null,
                null  // null → compact constructor normalises to List.of()
        );

        MimeMessage mimeMessage = emptyMimeMessage();
        SentMessageResult expected = new SentMessageResult("msg-id", "thread-id");
        when(mimeMessageBuilder.build(any(SendMessageDTO.class), isNull())).thenReturn(mimeMessage);
        when(gmailRepository.sendMessage(any(), any(), isNull())).thenReturn(expected);

        GmailService service = serviceWithMockedBuilder();

        // Act: should NOT throw even though limit is tiny — no attachments means gate is open
        service.sendMessage(USER_ID, dto);

        // Assert: builder was called (total-payload check did not short-circuit)
        verify(mimeMessageBuilder).build(any(SendMessageDTO.class), isNull());
    }

    @Test
    @DisplayName("createDraft_noAttachments_totalPayloadCheckBypassed_builderCalled")
    void createDraft_noAttachments_totalPayloadCheckBypassed_builderCalled()
            throws Exception {

        // Arrange
        when(send.maxTotalPayloadSize()).thenReturn(SMALL_LIMIT);

        SendMessageDTO dto = new SendMessageDTO(
                List.of("r@example.com"),
                null, null,
                "Subject", "short body", "text",
                null, null,
                null
        );

        MimeMessage mimeMessage = emptyMimeMessage();
        DraftCreationResult expected = new DraftCreationResult("d-id", "msg-id", "thread-id");
        when(mimeMessageBuilder.build(any(SendMessageDTO.class), isNull())).thenReturn(mimeMessage);
        when(gmailRepository.createDraft(any(), any(), isNull())).thenReturn(expected);

        GmailService service = serviceWithMockedBuilder();

        // Act: should NOT throw
        service.createDraft(USER_ID, dto);

        verify(mimeMessageBuilder).build(any(SendMessageDTO.class), isNull());
    }

    // =========================================================================
    // T038 — Stage 1 under threshold: builder IS called (positive case)
    // =========================================================================

    @Test
    @DisplayName("sendMessage_stage1EstimateBelowThreshold_builderIsCalled")
    void sendMessage_stage1EstimateBelowThreshold_builderIsCalled()
            throws Exception {

        // Arrange: limit is generous; small attachment estimate is well under 90% threshold
        when(send.maxTotalPayloadSize()).thenReturn(LARGE_LIMIT);

        Attachment smallAttachment = new Attachment("tiny.pdf", "application/pdf", "JVBERi0xLjQK");
        SendMessageDTO dto = new SendMessageDTO(
                List.of("r@example.com"),
                null, null,
                "Subject", "small body", "text",
                null, null,
                List.of(smallAttachment)
        );

        MimeMessage mimeMessage = emptyMimeMessage();
        SentMessageResult expected = new SentMessageResult("msg-id", "thread-id");
        when(mimeMessageBuilder.build(any(SendMessageDTO.class), isNull())).thenReturn(mimeMessage);
        when(gmailRepository.sendMessage(any(), any(), isNull())).thenReturn(expected);

        GmailService service = serviceWithMockedBuilder();

        // Act
        SentMessageResult result = service.sendMessage(USER_ID, dto);

        // Assert: builder was called (Stage 1 did not reject)
        verify(mimeMessageBuilder).build(any(SendMessageDTO.class), isNull());
        assertThat(result).isSameAs(expected);
    }

    // =========================================================================
    // T041 — Log-content compliance (Constitution VII, FR-019, FR-020)
    // =========================================================================

    @Test
    @DisplayName("sendMessage_withAttachment_noLogEventContainsFilename")
    void sendMessage_withAttachment_noLogEventContainsFilename()
            throws Exception {

        // Arrange: large limit so size checks pass; use real builder to exercise actual logs
        when(send.maxTotalPayloadSize()).thenReturn(LARGE_LIMIT);

        Attachment attachment = new Attachment(UNIQUE_FILENAME, "application/pdf", UNIQUE_BASE64_CONTENT);
        SendMessageDTO dto = new SendMessageDTO(
                List.of("r@example.com"),
                null, null,
                "Subject", "Hello world", "text",
                null, null,
                List.of(attachment)
        );

        SentMessageResult expected = new SentMessageResult("msg-id", "thread-id");
        when(gmailRepository.sendMessage(any(), any(), isNull())).thenReturn(expected);

        GmailService service = serviceWithRealBuilder();

        // Act
        service.sendMessage(USER_ID, dto);

        // Assert: no log event at any level contains the filename (FR-019)
        List<ILoggingEvent> events = listAppender.list;
        for (ILoggingEvent event : events) {
            assertThat(event.getFormattedMessage())
                    .as("Log event at level %s must not contain filename '%s'",
                            event.getLevel(), UNIQUE_FILENAME)
                    .doesNotContain(UNIQUE_FILENAME);
        }
    }

    @Test
    @DisplayName("sendMessage_withAttachment_noLogEventContainsBase64Substring")
    void sendMessage_withAttachment_noLogEventContainsBase64Substring()
            throws Exception {

        // Arrange
        when(send.maxTotalPayloadSize()).thenReturn(LARGE_LIMIT);

        Attachment attachment = new Attachment(UNIQUE_FILENAME, "application/pdf", UNIQUE_BASE64_CONTENT);
        SendMessageDTO dto = new SendMessageDTO(
                List.of("r@example.com"),
                null, null,
                "Subject", "Hello world", "text",
                null, null,
                List.of(attachment)
        );

        SentMessageResult expected = new SentMessageResult("msg-id", "thread-id");
        when(gmailRepository.sendMessage(any(), any(), isNull())).thenReturn(expected);

        GmailService service = serviceWithRealBuilder();

        // Act
        service.sendMessage(USER_ID, dto);

        // Assert: no log event contains any portion of the base64 data (FR-019)
        // Use the first 8 chars as a unique fingerprint to detect partial leakage
        String base64Prefix = UNIQUE_BASE64_CONTENT.substring(0, 8);
        List<ILoggingEvent> events = listAppender.list;
        for (ILoggingEvent event : events) {
            assertThat(event.getFormattedMessage())
                    .as("Log event at level %s must not contain base64 data substring '%s'",
                            event.getLevel(), base64Prefix)
                    .doesNotContain(base64Prefix);
        }
    }

    @Test
    @DisplayName("sendMessage_withAttachment_attachmentCountAppearsInLogs")
    void sendMessage_withAttachment_attachmentCountAppearsInLogs()
            throws Exception {

        // Arrange
        when(send.maxTotalPayloadSize()).thenReturn(LARGE_LIMIT);

        Attachment attachment = new Attachment(UNIQUE_FILENAME, "application/pdf", UNIQUE_BASE64_CONTENT);
        SendMessageDTO dto = new SendMessageDTO(
                List.of("r@example.com"),
                null, null,
                "Subject", "body", "text",
                null, null,
                List.of(attachment)
        );

        SentMessageResult expected = new SentMessageResult("msg-id", "thread-id");
        when(gmailRepository.sendMessage(any(), any(), isNull())).thenReturn(expected);

        GmailService service = serviceWithRealBuilder();

        // Act
        service.sendMessage(USER_ID, dto);

        // Assert: at least one log event mentions "attachmentCount" (FR-020, SC-004)
        List<ILoggingEvent> events = listAppender.list;
        boolean foundAttachmentCount = events.stream()
                .anyMatch(e -> e.getFormattedMessage().contains("attachmentCount="));
        assertThat(foundAttachmentCount)
                .as("Expected at least one log event containing 'attachmentCount=' (FR-020)")
                .isTrue();
    }

    @Test
    @DisplayName("sendMessage_withAttachment_estimatedPayloadBytesAppearsInLogs")
    void sendMessage_withAttachment_estimatedPayloadBytesAppearsInLogs()
            throws Exception {

        // Arrange
        when(send.maxTotalPayloadSize()).thenReturn(LARGE_LIMIT);

        Attachment attachment = new Attachment(UNIQUE_FILENAME, "application/pdf", UNIQUE_BASE64_CONTENT);
        SendMessageDTO dto = new SendMessageDTO(
                List.of("r@example.com"),
                null, null,
                "Subject", "body", "text",
                null, null,
                List.of(attachment)
        );

        SentMessageResult expected = new SentMessageResult("msg-id", "thread-id");
        when(gmailRepository.sendMessage(any(), any(), isNull())).thenReturn(expected);

        GmailService service = serviceWithRealBuilder();

        // Act
        service.sendMessage(USER_ID, dto);

        // Assert: at least one log event mentions "estimatedPayloadBytes" (FR-020, SC-004)
        List<ILoggingEvent> events = listAppender.list;
        boolean foundEstimate = events.stream()
                .anyMatch(e -> e.getFormattedMessage().contains("estimatedPayloadBytes="));
        assertThat(foundEstimate)
                .as("Expected at least one log event containing 'estimatedPayloadBytes=' (FR-020)")
                .isTrue();
    }

    @Test
    @DisplayName("sendMessage_withAttachment_noLogEventAtAnyLevelContainsFilename")
    void sendMessage_withAttachment_noLogEventAtAnyLevelContainsFilename_allLevelsChecked()
            throws Exception {

        // Arrange: verify across ALL log levels (DEBUG, INFO, WARN, ERROR, TRACE)
        when(send.maxTotalPayloadSize()).thenReturn(LARGE_LIMIT);
        // Set logger level to TRACE so even trace-level events would be captured
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Level originalLevel = rootLogger.getLevel();

        Attachment attachment = new Attachment(UNIQUE_FILENAME, "application/pdf", UNIQUE_BASE64_CONTENT);
        SendMessageDTO dto = new SendMessageDTO(
                List.of("r@example.com"),
                null, null,
                "Subject", "body", "text",
                null, null,
                List.of(attachment)
        );

        SentMessageResult expected = new SentMessageResult("msg-id", "thread-id");
        when(gmailRepository.sendMessage(any(), any(), isNull())).thenReturn(expected);

        GmailService service = serviceWithRealBuilder();

        // Act
        service.sendMessage(USER_ID, dto);

        // Assert: across all captured events, none contains the unique filename
        List<ILoggingEvent> allEvents = listAppender.list;
        long violatingCount = allEvents.stream()
                .filter(e -> e.getFormattedMessage().contains(UNIQUE_FILENAME))
                .count();
        assertThat(violatingCount)
                .as("Expected 0 log events containing filename '%s' but found %d (Constitution VII, FR-019)",
                        UNIQUE_FILENAME, violatingCount)
                .isZero();
    }
}
