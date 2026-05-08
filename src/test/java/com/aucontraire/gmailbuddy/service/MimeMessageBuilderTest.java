package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.dto.SendMessageDTO;
import com.aucontraire.gmailbuddy.fixture.SendMessageRequestFixtures;
import com.aucontraire.gmailbuddy.util.MimeMessageTestUtil;
import jakarta.mail.Address;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.UnsupportedEncodingException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MimeMessageBuilder#build(SendMessageDTO)}.
 *
 * <p>The builder is a pure conversion utility with no external dependencies —
 * no Spring context is required. Each test constructs the builder directly,
 * invokes {@code build()}, and asserts on the resulting {@link MimeMessage}
 * using the JavaMail API and {@link MimeMessageTestUtil#getHeader}.</p>
 *
 * <p>{@link SendMessageRequestFixtures} is used wherever a pre-built DTO
 * matches the scenario; bespoke DTOs are constructed inline only when the
 * fixture doesn't cover the exact case.</p>
 *
 * <p>Test naming follows the project convention:
 * {@code methodName_stateUnderTest_expectedBehavior}.</p>
 */
class MimeMessageBuilderTest {

    private MimeMessageBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new MimeMessageBuilder();
    }

    // -------------------------------------------------------------------------
    // build — single primary recipient
    // -------------------------------------------------------------------------

    @Test
    void build_singlePrimaryRecipient_toHeaderContainsRecipientAddress()
            throws MessagingException, UnsupportedEncodingException {

        // Arrange
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();

        // Act
        MimeMessage message = builder.build(dto);

        // Assert
        Address[] toAddresses = message.getRecipients(RecipientType.TO);
        assertThat(toAddresses).isNotNull().hasSize(1);
        assertThat(toAddresses[0].toString()).isEqualTo("recruiter@example.com");
    }

    @Test
    void build_singlePrimaryRecipient_noUnexpectedCcOrBcc()
            throws MessagingException, UnsupportedEncodingException {

        // Arrange: the validSingleRecipient fixture has no cc or bcc.
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();

        // Act
        MimeMessage message = builder.build(dto);

        // Assert: recipient arrays for CC and BCC should be null when not set.
        assertThat(message.getRecipients(RecipientType.CC)).isNullOrEmpty();
        assertThat(message.getRecipients(RecipientType.BCC)).isNullOrEmpty();
    }

    // -------------------------------------------------------------------------
    // build — multiple recipients with cc and bcc
    // -------------------------------------------------------------------------

    @Test
    void build_multipleToRecipients_toHeaderContainsAllAddresses()
            throws MessagingException, UnsupportedEncodingException {

        // Arrange: fixture has alice and bob as primary recipients.
        SendMessageDTO dto = SendMessageRequestFixtures.validMultiRecipientWithCcAndBcc();

        // Act
        MimeMessage message = builder.build(dto);

        // Assert
        Address[] toAddresses = message.getRecipients(RecipientType.TO);
        assertThat(toAddresses).isNotNull().hasSize(2);
        assertThat(toAddresses[0].toString()).isEqualTo("alice@example.com");
        assertThat(toAddresses[1].toString()).isEqualTo("bob@example.com");
    }

    @Test
    void build_ccRecipientPresent_ccHeaderContainsAddress()
            throws MessagingException, UnsupportedEncodingException {

        // Arrange
        SendMessageDTO dto = SendMessageRequestFixtures.validMultiRecipientWithCcAndBcc();

        // Act
        MimeMessage message = builder.build(dto);

        // Assert
        Address[] ccAddresses = message.getRecipients(RecipientType.CC);
        assertThat(ccAddresses).isNotNull().hasSize(1);
        assertThat(ccAddresses[0].toString()).isEqualTo("cc-recipient@example.com");
    }

    @Test
    void build_bccRecipientPresent_bccHeaderContainsAddress()
            throws MessagingException, UnsupportedEncodingException {

        // Arrange
        SendMessageDTO dto = SendMessageRequestFixtures.validMultiRecipientWithCcAndBcc();

        // Act
        MimeMessage message = builder.build(dto);

        // Assert
        Address[] bccAddresses = message.getRecipients(RecipientType.BCC);
        assertThat(bccAddresses).isNotNull().hasSize(1);
        assertThat(bccAddresses[0].toString()).isEqualTo("bcc-recipient@example.com");
    }

    @Test
    void build_multipleToWithCcAndBcc_allThreeRecipientTypeArraysPopulated()
            throws MessagingException, UnsupportedEncodingException {

        // Arrange: fixture has to=[alice, bob], cc=[cc-recipient], bcc=[bcc-recipient]
        SendMessageDTO dto = SendMessageRequestFixtures.validMultiRecipientWithCcAndBcc();

        // Act
        MimeMessage message = builder.build(dto);

        // Assert: all three recipient arrays are non-null and sized as expected.
        assertThat(message.getRecipients(RecipientType.TO)).isNotNull().hasSize(2);
        assertThat(message.getRecipients(RecipientType.CC)).isNotNull().hasSize(1);
        assertThat(message.getRecipients(RecipientType.BCC)).isNotNull().hasSize(1);
    }

    // -------------------------------------------------------------------------
    // build — subject
    // -------------------------------------------------------------------------

    @Test
    void build_dtoWithSubject_subjectHeaderPreservedExactly()
            throws MessagingException, UnsupportedEncodingException {

        // Arrange
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();

        // Act
        MimeMessage message = builder.build(dto);

        // Assert: subject must round-trip without modification.
        assertThat(message.getSubject())
                .isEqualTo("Software Engineer – Application Follow-up");
    }

    @Test
    void build_subjectWithSpecialCharacters_subjectHeaderPreservedExactly()
            throws MessagingException, UnsupportedEncodingException {

        // Arrange: subject with non-ASCII (em-dash already in the fixture, but
        // here we explicitly test Unicode round-trip with accented chars).
        SendMessageDTO dto = new SendMessageDTO(
                java.util.List.of("someone@example.com"),
                null,
                null,
                "Réponse à votre annonce – Développeur",
                "Body text",
                "text",
                null,
                null,
                null
        );

        // Act
        MimeMessage message = builder.build(dto);

        // Assert
        assertThat(message.getSubject())
                .isEqualTo("Réponse à votre annonce – Développeur");
    }

    // -------------------------------------------------------------------------
    // build — content type: text/plain
    // -------------------------------------------------------------------------

    @Test
    void build_dtoWithTextBodyType_contentTypeStartsWithTextPlain()
            throws MessagingException, UnsupportedEncodingException {

        // Arrange
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();

        // Act
        MimeMessage message = builder.build(dto);

        // Assert: content type must begin with "text/plain" regardless of
        // trailing charset parameter ordering.
        String contentType = MimeMessageTestUtil.getHeader(message, "Content-Type");
        assertThat(contentType).startsWith("text/plain");
    }

    @Test
    void build_dtoWithTextBodyType_contentTypeIncludesUtf8Charset()
            throws MessagingException, UnsupportedEncodingException {

        // Arrange
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();

        // Act
        MimeMessage message = builder.build(dto);

        // Assert: charset must be UTF-8 so international characters survive encoding.
        String contentType = MimeMessageTestUtil.getHeader(message, "Content-Type");
        assertThat(contentType).containsIgnoringCase("charset=UTF-8");
    }

    // -------------------------------------------------------------------------
    // build — content type: text/html
    // -------------------------------------------------------------------------

    @Test
    void build_dtoWithHtmlBodyType_contentTypeStartsWithTextHtml()
            throws MessagingException, UnsupportedEncodingException {

        // Arrange
        SendMessageDTO dto = SendMessageRequestFixtures.validHtmlBody();

        // Act
        MimeMessage message = builder.build(dto);

        // Assert
        String contentType = MimeMessageTestUtil.getHeader(message, "Content-Type");
        assertThat(contentType).startsWith("text/html");
    }

    @Test
    void build_dtoWithHtmlBodyType_contentTypeIncludesUtf8Charset()
            throws MessagingException, UnsupportedEncodingException {

        // Arrange
        SendMessageDTO dto = SendMessageRequestFixtures.validHtmlBody();

        // Act
        MimeMessage message = builder.build(dto);

        // Assert
        String contentType = MimeMessageTestUtil.getHeader(message, "Content-Type");
        assertThat(contentType).containsIgnoringCase("charset=UTF-8");
    }

    // -------------------------------------------------------------------------
    // build — body content round-trip
    // -------------------------------------------------------------------------

    @Test
    void build_dtoWithTextBody_bodyContentRoundTrips()
            throws Exception {

        // Arrange
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
        String expectedBody = dto.body();

        // Act
        MimeMessage message = builder.build(dto);

        // Assert: getContent() returns the body as a String for simple text/plain
        // messages (Jakarta Mail decodes the transport encoding automatically).
        Object content = message.getContent();
        assertThat(content).isInstanceOf(String.class);
        assertThat((String) content).isEqualTo(expectedBody);
    }

    @Test
    void build_dtoWithHtmlBody_htmlBodyContentRoundTrips()
            throws Exception {

        // Arrange
        SendMessageDTO dto = SendMessageRequestFixtures.validHtmlBody();
        String expectedHtmlBody = dto.body();

        // Act
        MimeMessage message = builder.build(dto);

        // Assert: HTML body must be forwarded verbatim (Decision 7, no sanitization).
        Object content = message.getContent();
        assertThat(content).isInstanceOf(String.class);
        assertThat((String) content).isEqualTo(expectedHtmlBody);
    }

    // -------------------------------------------------------------------------
    // build — bodyType case insensitivity
    // -------------------------------------------------------------------------

    @Test
    void build_dtoWithUpperCaseHtmlBodyType_contentTypeStartsWithTextHtml()
            throws MessagingException, UnsupportedEncodingException {

        // Arrange: the builder uses equalsIgnoreCase for bodyType comparison.
        SendMessageDTO dto = new SendMessageDTO(
                java.util.List.of("someone@example.com"),
                null,
                null,
                "Test Subject",
                "<p>Hello</p>",
                "HTML",   // uppercase — should still map to text/html
                null,
                null,
                null
        );

        // Act
        MimeMessage message = builder.build(dto);

        // Assert
        String contentType = MimeMessageTestUtil.getHeader(message, "Content-Type");
        assertThat(contentType).startsWith("text/html");
    }

    @Test
    void build_dtoWithNullBodyTypeDefaultedToText_contentTypeStartsWithTextPlain()
            throws MessagingException, UnsupportedEncodingException {

        // Arrange: null bodyType is normalized to "text" by SendMessageDTO's compact
        // constructor, so the builder always receives a non-null value in practice.
        // Verify the builder handles the "text" value correctly.
        SendMessageDTO dto = new SendMessageDTO(
                java.util.List.of("someone@example.com"),
                null,
                null,
                "Test Subject",
                "Body text",
                null,  // compact constructor defaults to "text"
                null,
                null,
                null
        );

        // Act
        MimeMessage message = builder.build(dto);

        // Assert: default "text" bodyType must produce text/plain.
        String contentType = MimeMessageTestUtil.getHeader(message, "Content-Type");
        assertThat(contentType).startsWith("text/plain");
    }

    // =========================================================================
    // T030 — Threading-header path (Phase 3 US1)
    // =========================================================================

    // -------------------------------------------------------------------------
    // build(dto, lookup) — with non-null lookup: In-Reply-To and References are set
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("build_withNonNullLookup_inReplyToHeaderContainsRfcMessageId")
    void build_withNonNullLookup_inReplyToHeaderContainsRfcMessageId()
            throws MessagingException, UnsupportedEncodingException {

        // Arrange
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
        OriginalMessageLookup lookup = new OriginalMessageLookup(
                "1a2b3c4d5e6f7a8b",
                "thread-xyz",
                "<CABc123xyz@mail.gmail.com>"
        );

        // Act
        MimeMessage message = builder.build(dto, lookup);

        // Assert: In-Reply-To must be set to the rfcMessageId
        String[] inReplyToHeaders = message.getHeader("In-Reply-To");
        assertThat(inReplyToHeaders).isNotNull().isNotEmpty();
        assertThat(inReplyToHeaders[0]).isEqualTo("<CABc123xyz@mail.gmail.com>");
    }

    @Test
    @DisplayName("build_withNonNullLookup_referencesHeaderContainsRfcMessageId")
    void build_withNonNullLookup_referencesHeaderContainsRfcMessageId()
            throws MessagingException, UnsupportedEncodingException {

        // Arrange
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
        OriginalMessageLookup lookup = new OriginalMessageLookup(
                "1a2b3c4d5e6f7a8b",
                "thread-xyz",
                "<CABc123xyz@mail.gmail.com>"
        );

        // Act
        MimeMessage message = builder.build(dto, lookup);

        // Assert: References must also be set to the same rfcMessageId
        String[] referencesHeaders = message.getHeader("References");
        assertThat(referencesHeaders).isNotNull().isNotEmpty();
        assertThat(referencesHeaders[0]).isEqualTo("<CABc123xyz@mail.gmail.com>");
    }

    // -------------------------------------------------------------------------
    // build(dto, null) — backward compatibility: In-Reply-To and References are NOT set
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("build_withNullLookup_inReplyToHeaderIsAbsent")
    void build_withNullLookup_inReplyToHeaderIsAbsent()
            throws MessagingException, UnsupportedEncodingException {

        // Arrange: null lookup — non-threaded send path (FR-021 backward compatibility)
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();

        // Act
        MimeMessage message = builder.build(dto, null);

        // Assert: In-Reply-To must NOT be set when lookup is null
        String[] inReplyToHeaders = message.getHeader("In-Reply-To");
        assertThat(inReplyToHeaders).isNullOrEmpty();
    }

    @Test
    @DisplayName("build_withNullLookup_referencesHeaderIsAbsent")
    void build_withNullLookup_referencesHeaderIsAbsent()
            throws MessagingException, UnsupportedEncodingException {

        // Arrange: null lookup — non-threaded path
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();

        // Act
        MimeMessage message = builder.build(dto, null);

        // Assert: References must NOT be set when lookup is null
        String[] referencesHeaders = message.getHeader("References");
        assertThat(referencesHeaders).isNullOrEmpty();
    }

    @Test
    @DisplayName("build_singleArgForm_inReplyToHeaderIsAbsent")
    void build_singleArgForm_inReplyToHeaderIsAbsent()
            throws MessagingException, UnsupportedEncodingException {

        // Arrange: the single-arg form delegates to build(dto, null) — verify the delegation
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();

        // Act: use the 1-arg form
        MimeMessage message = builder.build(dto);

        // Assert: no threading headers (same as build(dto, null))
        assertThat(message.getHeader("In-Reply-To")).isNullOrEmpty();
        assertThat(message.getHeader("References")).isNullOrEmpty();
    }

    // =========================================================================
    // T030 — resolveThreadId helper (FR-005, FR-006, FR-007)
    // =========================================================================

    // -------------------------------------------------------------------------
    // resolveThreadId: lookup non-null → lookup.threadId() wins (FR-006)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("resolveThreadId_withNonNullLookup_returnsLookupThreadIdRegardlessOfDtoThreadId")
    void resolveThreadId_withNonNullLookup_returnsLookupThreadIdRegardlessOfDtoThreadId() {

        // Arrange: DTO has a different threadId; lookup's threadId should win (FR-006)
        SendMessageDTO dto = new SendMessageDTO(
                List.of("someone@example.com"),
                null, null,
                "Subject", "Body", "text",
                "caller-supplied-thread-id",  // dto.threadId() — should be IGNORED
                "1a2b3c4d",
                null
        );
        OriginalMessageLookup lookup = new OriginalMessageLookup(
                "1a2b3c4d",
                "canonical-thread-id-from-gmail",  // authoritative value
                "<msg@mail.gmail.com>"
        );

        // Act
        String resolved = builder.resolveThreadId(dto, lookup);

        // Assert: lookup's threadId wins unconditionally (FR-006)
        assertThat(resolved).isEqualTo("canonical-thread-id-from-gmail");
    }

    // -------------------------------------------------------------------------
    // resolveThreadId: lookup null, dto.threadId() non-null → dto.threadId() returned (FR-007)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("resolveThreadId_withNullLookupAndNonNullDtoThreadId_returnsDtoThreadId")
    void resolveThreadId_withNullLookupAndNonNullDtoThreadId_returnsDtoThreadId() {

        // Arrange: no lookup performed (no inReplyToMessageId); caller supplied threadId
        SendMessageDTO dto = new SendMessageDTO(
                List.of("someone@example.com"),
                null, null,
                "Subject", "Body", "text",
                "thread-from-dto",   // FR-007: pass this through
                null,                // no inReplyToMessageId
                null
        );

        // Act
        String resolved = builder.resolveThreadId(dto, null);

        // Assert: dto.threadId() is passed through unchanged (FR-007)
        assertThat(resolved).isEqualTo("thread-from-dto");
    }

    // -------------------------------------------------------------------------
    // resolveThreadId: both null → returns null (new thread)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("resolveThreadId_withNullLookupAndNullDtoThreadId_returnsNull")
    void resolveThreadId_withNullLookupAndNullDtoThreadId_returnsNull() {

        // Arrange: neither lookup nor dto.threadId() — standard non-threaded send
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();
        // validSingleRecipient has threadId=null and inReplyToMessageId=null

        // Act
        String resolved = builder.resolveThreadId(dto, null);

        // Assert: null means no thread specified → Gmail creates a new thread
        assertThat(resolved).isNull();
    }
}
