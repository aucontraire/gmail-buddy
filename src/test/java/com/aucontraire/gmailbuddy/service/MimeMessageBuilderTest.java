package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.dto.Attachment;
import com.aucontraire.gmailbuddy.dto.SendMessageDTO;
import com.aucontraire.gmailbuddy.fixture.AttachmentFixtures;
import com.aucontraire.gmailbuddy.fixture.SendMessageRequestFixtures;
import com.aucontraire.gmailbuddy.util.MimeMessageTestUtil;
import jakarta.mail.Address;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeUtility;
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

    // =========================================================================
    // T037 — Multipart/attachment path (Phase 4 US2)
    // =========================================================================

    // -------------------------------------------------------------------------
    // build(dto, null) with non-empty attachments → multipart/mixed content
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("build_withSingleAttachment_contentTypeIsMultipartMixed")
    void build_withSingleAttachment_contentTypeIsMultipartMixed()
            throws Exception {

        // Arrange: DTO with one valid PDF attachment
        SendMessageDTO dto = new SendMessageDTO(
                List.of("recruiter@example.com"),
                null, null,
                "Hello with attachment",
                "Please see the attached PDF.",
                "text",
                null, null,
                List.of(AttachmentFixtures.validSinglePdf())
        );

        // Act
        MimeMessage message = builder.build(dto);

        // Assert: Content-Type header starts with multipart/mixed
        String contentType = MimeMessageTestUtil.getHeader(message, "Content-Type");
        assertThat(contentType).startsWith("multipart/mixed");
    }

    @Test
    @DisplayName("build_withSingleAttachment_contentIsInstanceOfMimeMultipart")
    void build_withSingleAttachment_contentIsInstanceOfMimeMultipart()
            throws Exception {

        // Arrange
        SendMessageDTO dto = new SendMessageDTO(
                List.of("recruiter@example.com"),
                null, null,
                "Subject",
                "Body text",
                "text",
                null, null,
                List.of(AttachmentFixtures.validSinglePdf())
        );

        // Act
        MimeMessage message = builder.build(dto);

        // Assert: getContent() must return a MimeMultipart instance (FR-010)
        Object content = message.getContent();
        assertThat(content).isInstanceOf(MimeMultipart.class);
    }

    @Test
    @DisplayName("build_withSingleAttachment_multipartHasTwoParts")
    void build_withSingleAttachment_multipartHasTwoParts()
            throws Exception {

        // Arrange
        SendMessageDTO dto = new SendMessageDTO(
                List.of("recruiter@example.com"),
                null, null,
                "Subject",
                "Body text",
                "text",
                null, null,
                List.of(AttachmentFixtures.validSinglePdf())
        );

        // Act
        MimeMessage message = builder.build(dto);
        MimeMultipart multipart = (MimeMultipart) message.getContent();

        // Assert: 1 body part + 1 attachment = 2 total parts
        assertThat(multipart.getCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("build_withMultipleAttachments_multipartHasCorrectPartCount")
    void build_withMultipleAttachments_multipartHasCorrectPartCount()
            throws Exception {

        // Arrange: two attachments → 3 total parts (body + 2 attachments)
        SendMessageDTO dto = new SendMessageDTO(
                List.of("recruiter@example.com"),
                null, null,
                "Subject",
                "Body text",
                "text",
                null, null,
                AttachmentFixtures.validMultiAttachmentList()
        );

        // Act
        MimeMessage message = builder.build(dto);
        MimeMultipart multipart = (MimeMultipart) message.getContent();

        // Assert: 1 body + 2 attachments = 3 parts
        assertThat(multipart.getCount()).isEqualTo(3);
    }

    // -------------------------------------------------------------------------
    // First body part content and type
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("build_withAttachment_firstPartBodyTextMatchesDto")
    void build_withAttachment_firstPartBodyTextMatchesDto()
            throws Exception {

        // Arrange
        String expectedBody = "Please review the attached résumé.";
        SendMessageDTO dto = new SendMessageDTO(
                List.of("recruiter@example.com"),
                null, null,
                "Subject",
                expectedBody,
                "text",
                null, null,
                List.of(AttachmentFixtures.validSinglePdf())
        );

        // Act
        MimeMessage message = builder.build(dto);
        MimeMultipart multipart = (MimeMultipart) message.getContent();
        MimeBodyPart bodyPart = (MimeBodyPart) multipart.getBodyPart(0);

        // Assert: first part must carry the body text
        assertThat(bodyPart.getContent()).isEqualTo(expectedBody);
    }

    @Test
    @DisplayName("build_withTextBodyTypeAndAttachment_firstPartContentTypeIsTextPlain")
    void build_withTextBodyTypeAndAttachment_firstPartContentTypeIsTextPlain()
            throws Exception {

        // Arrange
        SendMessageDTO dto = new SendMessageDTO(
                List.of("recruiter@example.com"),
                null, null,
                "Subject",
                "Plain body",
                "text",
                null, null,
                List.of(AttachmentFixtures.validSinglePdf())
        );

        // Act
        MimeMessage message = builder.build(dto);
        MimeMultipart multipart = (MimeMultipart) message.getContent();
        MimeBodyPart bodyPart = (MimeBodyPart) multipart.getBodyPart(0);

        // Assert: bodyType="text" → text/plain; charset=UTF-8
        assertThat(bodyPart.getContentType()).startsWith("text/plain");
        assertThat(bodyPart.getContentType()).containsIgnoringCase("charset=UTF-8");
    }

    @Test
    @DisplayName("build_withHtmlBodyTypeAndAttachment_firstPartContentTypeIsTextHtml")
    void build_withHtmlBodyTypeAndAttachment_firstPartContentTypeIsTextHtml()
            throws Exception {

        // Arrange
        SendMessageDTO dto = new SendMessageDTO(
                List.of("recruiter@example.com"),
                null, null,
                "Subject",
                "<p>Hello</p>",
                "html",
                null, null,
                List.of(AttachmentFixtures.validSinglePdf())
        );

        // Act
        MimeMessage message = builder.build(dto);
        MimeMultipart multipart = (MimeMultipart) message.getContent();
        MimeBodyPart bodyPart = (MimeBodyPart) multipart.getBodyPart(0);

        // Assert: bodyType="html" → text/html; charset=UTF-8
        assertThat(bodyPart.getContentType()).startsWith("text/html");
        assertThat(bodyPart.getContentType()).containsIgnoringCase("charset=UTF-8");
    }

    // -------------------------------------------------------------------------
    // Attachment part: Content-Disposition must be "attachment" (FR-010a)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("build_withSingleAttachment_attachmentPartDispositionIsAttachment")
    void build_withSingleAttachment_attachmentPartDispositionIsAttachment()
            throws Exception {

        // Arrange
        SendMessageDTO dto = new SendMessageDTO(
                List.of("recruiter@example.com"),
                null, null,
                "Subject",
                "Body",
                "text",
                null, null,
                List.of(AttachmentFixtures.validSinglePdf())
        );

        // Act
        MimeMessage message = builder.build(dto);
        MimeMultipart multipart = (MimeMultipart) message.getContent();
        MimeBodyPart attachmentPart = (MimeBodyPart) multipart.getBodyPart(1);

        // Assert: Content-Disposition must be "attachment" — never "inline" (FR-010a)
        assertThat(attachmentPart.getDisposition()).isEqualToIgnoringCase("attachment");
    }

    @Test
    @DisplayName("build_withMultipleAttachments_allAttachmentPartsDispositionIsAttachment")
    void build_withMultipleAttachments_allAttachmentPartsDispositionIsAttachment()
            throws Exception {

        // Arrange: two attachments
        SendMessageDTO dto = new SendMessageDTO(
                List.of("recruiter@example.com"),
                null, null,
                "Subject",
                "Body",
                "text",
                null, null,
                AttachmentFixtures.validMultiAttachmentList()
        );

        // Act
        MimeMessage message = builder.build(dto);
        MimeMultipart multipart = (MimeMultipart) message.getContent();

        // Assert: parts 1 and 2 (index 1 and 2) are both attachment-disposition
        for (int i = 1; i < multipart.getCount(); i++) {
            MimeBodyPart part = (MimeBodyPart) multipart.getBodyPart(i);
            assertThat(part.getDisposition())
                    .as("Part %d disposition", i)
                    .isEqualToIgnoringCase("attachment");
        }
    }

    // -------------------------------------------------------------------------
    // Attachment part: Content-Type matches input mimeType
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("build_withPdfAttachment_attachmentPartContentTypeMatchesMimeType")
    void build_withPdfAttachment_attachmentPartContentTypeMatchesMimeType()
            throws Exception {

        // Arrange
        Attachment pdf = AttachmentFixtures.validSinglePdf();
        SendMessageDTO dto = new SendMessageDTO(
                List.of("recruiter@example.com"),
                null, null,
                "Subject",
                "Body",
                "text",
                null, null,
                List.of(pdf)
        );

        // Act
        MimeMessage message = builder.build(dto);
        MimeMultipart multipart = (MimeMultipart) message.getContent();
        MimeBodyPart attachmentPart = (MimeBodyPart) multipart.getBodyPart(1);

        // Assert: Content-Type should contain the mimeType value from the Attachment
        assertThat(attachmentPart.getContentType()).contains(pdf.mimeType());
    }

    @Test
    @DisplayName("build_withMultipleAttachments_eachPartContentTypeMatchesItsAttachment")
    void build_withMultipleAttachments_eachPartContentTypeMatchesItsAttachment()
            throws Exception {

        // Arrange: two attachments with different MIME types
        List<Attachment> attachments = AttachmentFixtures.validMultiAttachmentList();
        SendMessageDTO dto = new SendMessageDTO(
                List.of("recruiter@example.com"),
                null, null,
                "Subject",
                "Body",
                "text",
                null, null,
                attachments
        );

        // Act
        MimeMessage message = builder.build(dto);
        MimeMultipart multipart = (MimeMultipart) message.getContent();

        // Assert: part index 1 maps to attachments[0], part index 2 maps to attachments[1]
        for (int i = 0; i < attachments.size(); i++) {
            MimeBodyPart part = (MimeBodyPart) multipart.getBodyPart(i + 1);
            assertThat(part.getContentType())
                    .as("Part %d Content-Type", i + 1)
                    .contains(attachments.get(i).mimeType());
        }
    }

    // -------------------------------------------------------------------------
    // Attachment part: filename (RFC 2047 encoded for non-ASCII, decodable)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("build_withAsciiFilename_attachmentPartFilenameMatchesInput")
    void build_withAsciiFilename_attachmentPartFilenameMatchesInput()
            throws Exception {

        // Arrange: simple ASCII filename (no encoding required)
        Attachment pdf = AttachmentFixtures.validSinglePdf();
        SendMessageDTO dto = new SendMessageDTO(
                List.of("recruiter@example.com"),
                null, null,
                "Subject",
                "Body",
                "text",
                null, null,
                List.of(pdf)
        );

        // Act
        MimeMessage message = builder.build(dto);
        MimeMultipart multipart = (MimeMultipart) message.getContent();
        MimeBodyPart attachmentPart = (MimeBodyPart) multipart.getBodyPart(1);

        // Assert: getFileName() decodes RFC 2047 automatically — must equal original
        assertThat(MimeUtility.decodeText(attachmentPart.getFileName()))
                .isEqualTo(pdf.filename());
    }

    @Test
    @DisplayName("build_withNonAsciiFilename_attachmentPartFilenameDecodesToOriginal")
    void build_withNonAsciiFilename_attachmentPartFilenameDecodesToOriginal()
            throws Exception {

        // Arrange: non-ASCII filename triggers RFC 2047 B-encoding (research.md Decision 1)
        String nonAsciiFilename = "Résumé_2026.pdf";
        Attachment attachment = new Attachment(nonAsciiFilename, "application/pdf", "JVBERi0xLjQK");
        SendMessageDTO dto = new SendMessageDTO(
                List.of("recruiter@example.com"),
                null, null,
                "Subject",
                "Body",
                "text",
                null, null,
                List.of(attachment)
        );

        // Act
        MimeMessage message = builder.build(dto);
        MimeMultipart multipart = (MimeMultipart) message.getContent();
        MimeBodyPart attachmentPart = (MimeBodyPart) multipart.getBodyPart(1);

        // Assert: decoded filename must round-trip back to the original non-ASCII string
        String decodedFilename = MimeUtility.decodeText(attachmentPart.getFileName());
        assertThat(decodedFilename).isEqualTo(nonAsciiFilename);
    }

    // -------------------------------------------------------------------------
    // Backward compatibility: empty attachments → single-part path (FR-021)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("build_withEmptyAttachmentList_contentTypeIsNotMultipart")
    void build_withEmptyAttachmentList_contentTypeIsNotMultipart()
            throws Exception {

        // Arrange: explicit empty list — compact constructor normalises null→List.of(),
        // but we also verify that an explicitly supplied empty list hits the single-part path
        SendMessageDTO dto = new SendMessageDTO(
                List.of("recruiter@example.com"),
                null, null,
                "Subject",
                "Body text",
                "text",
                null, null,
                List.of()
        );

        // Act
        MimeMessage message = builder.build(dto);

        // Assert: single-part path — Content-Type must NOT be multipart/mixed
        String contentType = MimeMessageTestUtil.getHeader(message, "Content-Type");
        assertThat(contentType).doesNotStartWith("multipart");
        assertThat(contentType).startsWith("text/plain");
    }

    @Test
    @DisplayName("build_withEmptyAttachmentList_contentRoundTripsAsString")
    void build_withEmptyAttachmentList_contentRoundTripsAsString()
            throws Exception {

        // Arrange: omitting the attachments field (null → normalised to List.of())
        String expectedBody = "This is the body without attachments.";
        SendMessageDTO dto = SendMessageRequestFixtures.validSingleRecipient();

        // Act
        MimeMessage message = builder.build(dto);

        // Assert: getContent() returns the body String directly (not a MimeMultipart)
        Object content = message.getContent();
        assertThat(content).isInstanceOf(String.class);
    }

    // -------------------------------------------------------------------------
    // T037 — Combined path: attachments + threading headers (T046 anti-regression)
    // build(dto, lookup) with attachments → both multipart AND threading headers set
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("build_withAttachmentAndNonNullLookup_inReplyToHeaderPresent")
    void build_withAttachmentAndNonNullLookup_inReplyToHeaderPresent()
            throws Exception {

        // Arrange: DTO with attachment + threading lookup
        SendMessageDTO dto = new SendMessageDTO(
                List.of("recruiter@example.com"),
                null, null,
                "Re: Your resume",
                "Attached is my updated resume.",
                "text",
                "thread-abc123",
                "1a2b3c4d5e6f7a8b",
                List.of(AttachmentFixtures.validSinglePdf())
        );
        OriginalMessageLookup lookup = new OriginalMessageLookup(
                "1a2b3c4d5e6f7a8b",
                "thread-abc123",
                "<CABc123xyz@mail.gmail.com>"
        );

        // Act
        MimeMessage message = builder.build(dto, lookup);

        // Assert: threading headers are present on the outer MimeMessage
        String[] inReplyTo = message.getHeader("In-Reply-To");
        assertThat(inReplyTo).isNotNull().isNotEmpty();
        assertThat(inReplyTo[0]).isEqualTo("<CABc123xyz@mail.gmail.com>");
    }

    @Test
    @DisplayName("build_withAttachmentAndNonNullLookup_contentIsStillMultipartMixed")
    void build_withAttachmentAndNonNullLookup_contentIsStillMultipartMixed()
            throws Exception {

        // Arrange
        SendMessageDTO dto = new SendMessageDTO(
                List.of("recruiter@example.com"),
                null, null,
                "Re: Attachment + threading",
                "Please see attached.",
                "text",
                "thread-abc123",
                "1a2b3c4d5e6f7a8b",
                List.of(AttachmentFixtures.validSinglePdf())
        );
        OriginalMessageLookup lookup = new OriginalMessageLookup(
                "1a2b3c4d5e6f7a8b",
                "thread-abc123",
                "<CABc123xyz@mail.gmail.com>"
        );

        // Act
        MimeMessage message = builder.build(dto, lookup);

        // Assert: threading does NOT strip multipart structure (T046 anti-regression)
        Object content = message.getContent();
        assertThat(content).isInstanceOf(MimeMultipart.class);
        MimeMultipart multipart = (MimeMultipart) content;
        assertThat(multipart.getCount()).isEqualTo(2);  // body + 1 attachment
    }

    // -------------------------------------------------------------------------
    // Regression: MimeMessage.writeTo() must succeed for both text/* and binary
    // MIME types (caught by smoke test — prior implementation used
    // setContent(byte[], mimeType) which fails for text/* at writeTo() time
    // because JavaMail's text DataContentHandler expects a String, not a byte[]).
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("build_withTextPlainAttachment_writeToSucceeds")
    void build_withTextPlainAttachment_writeToSucceeds() throws Exception {
        // Arrange — text/plain attachment is the case that broke at writeTo() time
        SendMessageDTO dto = new SendMessageDTO(
                List.of("recipient@example.com"), List.of(), List.of(),
                "Subject", "Body", "html", null, null,
                List.of(new com.aucontraire.gmailbuddy.dto.Attachment(
                        "readme.txt",
                        "text/plain",
                        java.util.Base64.getEncoder().encodeToString("hello world".getBytes(java.nio.charset.StandardCharsets.UTF_8)))));

        // Act — build then serialize via writeTo, mirroring GmailService Stage 2
        MimeMessage message = builder.build(dto, null);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        message.writeTo(baos);

        // Assert — the serialized MIME contains the attachment text. For short ASCII
        // payloads JavaMail picks Content-Transfer-Encoding: 7bit (raw text), so the
        // body appears verbatim rather than base64-encoded. The key check is that
        // writeTo() did not throw — the prior implementation broke here for text/*.
        String serialized = baos.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(serialized).contains("Content-Type: text/plain");
        assertThat(serialized).contains("hello world");
    }

    @Test
    @DisplayName("build_withBinaryAttachment_writeToSucceeds")
    void build_withBinaryAttachment_writeToSucceeds() throws Exception {
        // Arrange — binary application/pdf attachment (the path that worked before)
        byte[] fakePdfBytes = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34};  // "%PDF-1.4"
        SendMessageDTO dto = new SendMessageDTO(
                List.of("recipient@example.com"), List.of(), List.of(),
                "Subject", "Body", "html", null, null,
                List.of(new com.aucontraire.gmailbuddy.dto.Attachment(
                        "fake.pdf",
                        "application/pdf",
                        java.util.Base64.getEncoder().encodeToString(fakePdfBytes))));

        // Act — build then serialize, both must succeed
        MimeMessage message = builder.build(dto, null);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        message.writeTo(baos);

        // Assert — non-empty serialized payload with the expected MIME type
        assertThat(baos.size()).isGreaterThan(0);
        String serialized = baos.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(serialized).contains("Content-Type: application/pdf");
    }

    // =========================================================================
    // T046 — US3 interaction anti-regression: multiple attachments + threading
    //
    // Checkpoint C already added two tests that partially cover T046's intent:
    //   - build_withAttachmentAndNonNullLookup_inReplyToHeaderPresent
    //   - build_withAttachmentAndNonNullLookup_contentIsStillMultipartMixed
    //
    // T046 documentation (per tasks.md instructions):
    //   Those two tests verify the single-attachment + single threading-header case.
    //   The test below is the sharper anti-regression demanded by T046: it uses THREE
    //   attachments (not one), verifies BOTH In-Reply-To AND References headers are
    //   present (not just one), asserts the exact part count, and exercises the full
    //   writeTo() round-trip — the same end-to-end serialization path that caught the
    //   text/* DataContentHandler bug before Checkpoint C. Together with the two
    //   Checkpoint C tests, T046 is fully covered.
    // =========================================================================

    /**
     * T046 — sharper anti-regression: 3 attachments + non-null lookup.
     *
     * <p>Verifies:
     * <ul>
     *   <li>BOTH {@code In-Reply-To} AND {@code References} headers are present
     *       (not just one) — guards against a partial-header regression.</li>
     *   <li>The multipart has exactly 4 parts: 1 body + 3 attachments — guards
     *       against a part-count regression under larger attachment counts.</li>
     *   <li>{@code writeTo()} succeeds end-to-end — the serialization safety net
     *       that caught the text/* DataContentHandler bug.</li>
     * </ul>
     */
    @Test
    @DisplayName("build_withThreeAttachmentsAndNonNullLookup_bothThreadingHeadersAndCorrectPartCount")
    void build_withThreeAttachmentsAndNonNullLookup_bothThreadingHeadersAndCorrectPartCount()
            throws Exception {

        // Arrange: three attachments covering different MIME types
        List<com.aucontraire.gmailbuddy.dto.Attachment> threeAttachments = List.of(
                new com.aucontraire.gmailbuddy.dto.Attachment("resume.pdf", "application/pdf", "JVBERi0xLjQK"),
                new com.aucontraire.gmailbuddy.dto.Attachment("cover.txt", "text/plain",
                        java.util.Base64.getEncoder().encodeToString(
                                "Cover letter content".getBytes(java.nio.charset.StandardCharsets.UTF_8))),
                new com.aucontraire.gmailbuddy.dto.Attachment("photo.png", "image/png", "iVBORw0KGgo=")
        );
        SendMessageDTO dto = new SendMessageDTO(
                List.of("recruiter@example.com"),
                null, null,
                "Re: Follow-up with materials",
                "Please find three files attached.",
                "text",
                "thread-xyz99",
                "1a2b3c4d5e6f7a8b",
                threeAttachments
        );
        OriginalMessageLookup lookup = new OriginalMessageLookup(
                "1a2b3c4d5e6f7a8b",
                "thread-xyz99",
                "<CAMultiple123@mail.gmail.com>"
        );

        // Act
        MimeMessage message = builder.build(dto, lookup);

        // Assert 1: BOTH In-Reply-To AND References are present (not just one)
        String[] inReplyTo = message.getHeader("In-Reply-To");
        assertThat(inReplyTo).isNotNull().isNotEmpty();
        assertThat(inReplyTo[0]).isEqualTo("<CAMultiple123@mail.gmail.com>");

        String[] references = message.getHeader("References");
        assertThat(references).isNotNull().isNotEmpty();
        assertThat(references[0]).isEqualTo("<CAMultiple123@mail.gmail.com>");

        // Assert 2: content is multipart/mixed with exactly 4 parts (1 body + 3 attachments)
        Object content = message.getContent();
        assertThat(content).isInstanceOf(MimeMultipart.class);
        MimeMultipart multipart = (MimeMultipart) content;
        assertThat(multipart.getCount()).isEqualTo(4);

        // Assert 3: writeTo() round-trip succeeds end-to-end (safety net for text/* bug)
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        message.writeTo(baos);
        assertThat(baos.size()).isGreaterThan(0);

        String serialized = baos.toString(java.nio.charset.StandardCharsets.UTF_8);
        // Both threading headers must survive serialisation
        assertThat(serialized).contains("In-Reply-To: <CAMultiple123@mail.gmail.com>");
        assertThat(serialized).contains("References: <CAMultiple123@mail.gmail.com>");
        // All three attachment MIME types must appear in the serialized output
        assertThat(serialized).contains("application/pdf");
        assertThat(serialized).contains("text/plain");
        assertThat(serialized).contains("image/png");
    }
}
