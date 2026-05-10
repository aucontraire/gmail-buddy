package com.aucontraire.gmailbuddy.mapper;

import com.aucontraire.gmailbuddy.dto.response.AttachmentMetadata;
import com.aucontraire.gmailbuddy.dto.response.DraftDetailResponse;
import com.aucontraire.gmailbuddy.dto.response.DraftListItem;
import com.aucontraire.gmailbuddy.dto.response.DraftListResponse;
import com.aucontraire.gmailbuddy.service.DraftCreationResult;
import com.aucontraire.gmailbuddy.service.DraftDetailResult;
import com.aucontraire.gmailbuddy.service.DraftListResult;
import com.aucontraire.gmailbuddy.service.SentMessageResult;
import com.google.api.services.gmail.model.Draft;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link GmailMessageMapper}.
 *
 * <p>The mapper is a pure conversion utility with no external dependencies.
 * Gmail SDK types ({@link Message}, {@link Draft}) are SDK POJOs — they need
 * no mocking; instances are constructed directly using their setter API and
 * asserted against the resulting domain records.</p>
 *
 * <p>Test naming follows the project convention:
 * {@code methodName_stateUnderTest_expectedBehavior}.</p>
 */
class GmailMessageMapperTest {

    private GmailMessageMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new GmailMessageMapper();
    }

    // -------------------------------------------------------------------------
    // toSentMessageResult — happy path
    // -------------------------------------------------------------------------

    @Test
    void toSentMessageResult_messageWithIdAndThreadId_returnsResultWithBothFields() {
        // Arrange
        Message gmailMessage = new Message();
        gmailMessage.setId("msg-abc-123");
        gmailMessage.setThreadId("thread-xyz-456");

        // Act
        SentMessageResult result = mapper.toSentMessageResult(gmailMessage);

        // Assert
        assertThat(result.messageId()).isEqualTo("msg-abc-123");
        assertThat(result.threadId()).isEqualTo("thread-xyz-456");
    }

    @Test
    void toSentMessageResult_messageWithDifferentIds_resultReflectsCorrectValues() {
        // Arrange: verify no field swap — messageId maps to getId(), threadId to getThreadId().
        Message gmailMessage = new Message();
        gmailMessage.setId("message-id-sentinel");
        gmailMessage.setThreadId("thread-id-sentinel");

        // Act
        SentMessageResult result = mapper.toSentMessageResult(gmailMessage);

        // Assert
        assertThat(result.messageId()).isEqualTo("message-id-sentinel");
        assertThat(result.threadId()).isEqualTo("thread-id-sentinel");
    }

    @Test
    void toSentMessageResult_messageWithNullThreadId_resultHasNullThreadId() {
        // Arrange: Gmail may omit threadId in some edge-case responses;
        // the mapper must not add a null-guard that replaces it with a default.
        Message gmailMessage = new Message();
        gmailMessage.setId("msg-no-thread");
        // threadId intentionally not set — defaults to null

        // Act
        SentMessageResult result = mapper.toSentMessageResult(gmailMessage);

        // Assert
        assertThat(result.messageId()).isEqualTo("msg-no-thread");
        assertThat(result.threadId()).isNull();
    }

    @Test
    void toSentMessageResult_nullMessage_throwsNullPointerException() {
        // Arrange: the Javadoc contracts that null is disallowed;
        // passing null must propagate as NullPointerException (per @throws).

        // Act + Assert
        assertThatNullPointerException()
                .isThrownBy(() -> mapper.toSentMessageResult(null));
    }

    // -------------------------------------------------------------------------
    // toDraftCreationResult — happy path
    // -------------------------------------------------------------------------

    @Test
    void toDraftCreationResult_draftWithAllFields_returnsResultWithAllThreeFields() {
        // Arrange
        Message nestedMessage = new Message();
        nestedMessage.setId("msg-nested-789");
        nestedMessage.setThreadId("thread-nested-012");

        Draft draft = new Draft();
        draft.setId("draft-abc-001");
        draft.setMessage(nestedMessage);

        // Act
        DraftCreationResult result = mapper.toDraftCreationResult(draft);

        // Assert
        assertThat(result.draftId()).isEqualTo("draft-abc-001");
        assertThat(result.messageId()).isEqualTo("msg-nested-789");
        assertThat(result.threadId()).isEqualTo("thread-nested-012");
    }

    @Test
    void toDraftCreationResult_draftWithDifferentIds_resultReflectsCorrectValues() {
        // Arrange: verify no field swap between draftId, messageId, threadId.
        Message nestedMessage = new Message();
        nestedMessage.setId("message-id-sentinel");
        nestedMessage.setThreadId("thread-id-sentinel");

        Draft draft = new Draft();
        draft.setId("draft-id-sentinel");
        draft.setMessage(nestedMessage);

        // Act
        DraftCreationResult result = mapper.toDraftCreationResult(draft);

        // Assert
        assertThat(result.draftId()).isEqualTo("draft-id-sentinel");
        assertThat(result.messageId()).isEqualTo("message-id-sentinel");
        assertThat(result.threadId()).isEqualTo("thread-id-sentinel");
    }

    // -------------------------------------------------------------------------
    // toDraftCreationResult — null nested Message (null-safety)
    // -------------------------------------------------------------------------

    @Test
    void toDraftCreationResult_draftWithNullNestedMessage_messageIdIsNull() {
        // Arrange: per the Javadoc, a null nested Message results in null messageId
        // and threadId. This covers the defensive null-safety branch in the mapper.
        Draft draft = new Draft();
        draft.setId("draft-no-message");
        // draft.setMessage() not called — nested Message is null

        // Act
        DraftCreationResult result = mapper.toDraftCreationResult(draft);

        // Assert
        assertThat(result.draftId()).isEqualTo("draft-no-message");
        assertThat(result.messageId()).isNull();
    }

    @Test
    void toDraftCreationResult_draftWithNullNestedMessage_threadIdIsNull() {
        // Arrange
        Draft draft = new Draft();
        draft.setId("draft-no-message");
        // draft.setMessage() not called — nested Message is null

        // Act
        DraftCreationResult result = mapper.toDraftCreationResult(draft);

        // Assert
        assertThat(result.threadId()).isNull();
    }

    @Test
    void toDraftCreationResult_draftWithNullNestedMessage_draftIdStillPopulated() {
        // Arrange: even with a null nested message, the draft's own ID must
        // be present in the result — the null guard must not wipe the draftId.
        Draft draft = new Draft();
        draft.setId("draft-with-null-message");

        // Act
        DraftCreationResult result = mapper.toDraftCreationResult(draft);

        // Assert
        assertThat(result.draftId()).isEqualTo("draft-with-null-message");
    }

    @Test
    void toDraftCreationResult_nullDraft_throwsNullPointerException() {
        // Arrange: the Javadoc contracts that null draft is disallowed.

        // Act + Assert
        assertThatNullPointerException()
                .isThrownBy(() -> mapper.toDraftCreationResult(null));
    }

    // -------------------------------------------------------------------------
    // toDraftCreationResult — nested Message with null threadId
    // -------------------------------------------------------------------------

    @Test
    void toDraftCreationResult_nestedMessageWithNullThreadId_resultHasNullThreadId() {
        // Arrange: Gmail may omit threadId on the nested message in some responses.
        Message nestedMessage = new Message();
        nestedMessage.setId("msg-no-thread");
        // threadId intentionally not set

        Draft draft = new Draft();
        draft.setId("draft-msg-no-thread");
        draft.setMessage(nestedMessage);

        // Act
        DraftCreationResult result = mapper.toDraftCreationResult(draft);

        // Assert
        assertThat(result.draftId()).isEqualTo("draft-msg-no-thread");
        assertThat(result.messageId()).isEqualTo("msg-no-thread");
        assertThat(result.threadId()).isNull();
    }

    // =========================================================================
    // T007 — toDraftDetailResult(Draft) tests
    // =========================================================================

    // Helper: encode string to base64url for building test payloads
    private String encodeBase64Url(String text) {
        return Base64.getUrlEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    // Helper: build a MessagePart with optional mimeType, body data, filename
    private MessagePart buildBodyPart(String mimeType, String bodyText) {
        MessagePart part = new MessagePart();
        part.setMimeType(mimeType);
        MessagePartBody body = new MessagePartBody();
        body.setData(encodeBase64Url(bodyText));
        part.setBody(body);
        return part;
    }

    private MessagePart buildAttachmentPart(String filename, String mimeType, long sizeBytes) {
        MessagePart part = new MessagePart();
        part.setMimeType(mimeType);
        part.setFilename(filename);
        MessagePartBody body = new MessagePartBody();
        body.setSize((int) sizeBytes);
        part.setBody(body);
        return part;
    }

    private MessagePartHeader header(String name, String value) {
        MessagePartHeader h = new MessagePartHeader();
        h.setName(name);
        h.setValue(value);
        return h;
    }

    private Draft buildFullDraft(String draftId, String messageId, String threadId,
                                  String snippet, List<MessagePartHeader> headers,
                                  List<MessagePart> subParts) {
        MessagePart payload = new MessagePart();
        payload.setMimeType("multipart/mixed");
        payload.setHeaders(headers);
        payload.setParts(subParts);

        Message message = new Message();
        message.setId(messageId);
        message.setThreadId(threadId);
        message.setSnippet(snippet);
        message.setPayload(payload);

        Draft draft = new Draft();
        draft.setId(draftId);
        draft.setMessage(message);
        return draft;
    }

    @Test
    void toDraftDetailResult_fullDraft_allFieldsPopulated() {
        String bodyText = "Hello, Sarah!";
        Draft draft = buildFullDraft(
                "draft-001", "msg-001", "thread-001", "Hello, Sarah!",
                List.of(
                        header("To", "sarah@example.com"),
                        header("Cc", "cc1@example.com"),
                        header("Bcc", "bcc1@example.com"),
                        header("Subject", "Test Subject")
                ),
                List.of(buildBodyPart("text/plain", bodyText))
        );

        DraftDetailResult result = mapper.toDraftDetailResult(draft);

        assertThat(result.draftId()).isEqualTo("draft-001");
        assertThat(result.messageId()).isEqualTo("msg-001");
        assertThat(result.threadId()).isEqualTo("thread-001");
        assertThat(result.snippet()).isEqualTo("Hello, Sarah!");
        assertThat(result.to()).containsExactly("sarah@example.com");
        assertThat(result.cc()).containsExactly("cc1@example.com");
        assertThat(result.bcc()).containsExactly("bcc1@example.com");
        assertThat(result.subject()).isEqualTo("Test Subject");
        assertThat(result.body()).isEqualTo(bodyText);
        assertThat(result.bodyType()).isEqualTo("text");
        assertThat(result.inReplyToMessageId()).isNull();
        assertThat(result.attachments()).isEmpty();
    }

    @Test
    void toDraftDetailResult_missingSubject_subjectIsNull() {
        Draft draft = buildFullDraft(
                "draft-002", "msg-002", null, null,
                List.of(header("To", "test@example.com")),
                List.of(buildBodyPart("text/plain", "Body text"))
        );

        DraftDetailResult result = mapper.toDraftDetailResult(draft);

        assertThat(result.subject()).isNull();
    }

    @Test
    void toDraftDetailResult_missingRecipients_returnsEmptyLists() {
        Draft draft = buildFullDraft(
                "draft-003", "msg-003", null, null,
                List.of(header("Subject", "No recipients")),
                List.of(buildBodyPart("text/plain", "body"))
        );

        DraftDetailResult result = mapper.toDraftDetailResult(draft);

        assertThat(result.to()).isNotNull().isEmpty();
        assertThat(result.cc()).isNotNull().isEmpty();
        assertThat(result.bcc()).isNotNull().isEmpty();
    }

    @Test
    void toDraftDetailResult_threadedDraft_setsThreadIdAndInReplyToMessageId() {
        Draft draft = buildFullDraft(
                "draft-004", "msg-004", "thread-abc", null,
                List.of(
                        header("To", "test@example.com"),
                        header("In-Reply-To", "<original-msg-id@mail.gmail.com>")
                ),
                List.of(buildBodyPart("text/plain", "reply body"))
        );

        DraftDetailResult result = mapper.toDraftDetailResult(draft);

        assertThat(result.threadId()).isEqualTo("thread-abc");
        // Angle brackets must be stripped
        assertThat(result.inReplyToMessageId()).isEqualTo("original-msg-id@mail.gmail.com");
    }

    @Test
    void toDraftDetailResult_htmlBodyPart_bodyTypeIsHtml() {
        String htmlContent = "<p>Hello <b>World</b></p>";
        Draft draft = buildFullDraft(
                "draft-005", "msg-005", null, null,
                List.of(header("To", "test@example.com")),
                List.of(buildBodyPart("text/html", htmlContent))
        );

        DraftDetailResult result = mapper.toDraftDetailResult(draft);

        assertThat(result.bodyType()).isEqualTo("html");
        assertThat(result.body()).isEqualTo(htmlContent);
    }

    @Test
    void toDraftDetailResult_textBodyPart_bodyTypeIsText() {
        String plainContent = "Plain text body";
        Draft draft = buildFullDraft(
                "draft-006", "msg-006", null, null,
                List.of(header("To", "test@example.com")),
                List.of(buildBodyPart("text/plain", plainContent))
        );

        DraftDetailResult result = mapper.toDraftDetailResult(draft);

        assertThat(result.bodyType()).isEqualTo("text");
        assertThat(result.body()).isEqualTo(plainContent);
    }

    @Test
    void toDraftDetailResult_htmlWinsOverPlain_whenBothPresent() {
        String htmlContent = "<p>HTML body</p>";
        String plainContent = "Plain body";

        MessagePart payload = new MessagePart();
        payload.setMimeType("multipart/alternative");
        payload.setHeaders(List.of(header("To", "test@example.com")));
        payload.setParts(List.of(
                buildBodyPart("text/plain", plainContent),
                buildBodyPart("text/html", htmlContent)
        ));

        Message message = new Message();
        message.setId("msg-007");
        message.setPayload(payload);

        Draft draft = new Draft();
        draft.setId("draft-007");
        draft.setMessage(message);

        DraftDetailResult result = mapper.toDraftDetailResult(draft);

        assertThat(result.bodyType()).isEqualTo("html");
        assertThat(result.body()).isEqualTo(htmlContent);
    }

    @Test
    void toDraftDetailResult_withAttachments_attachmentCountMatchesAndMetadataCorrect() {
        Draft draft = buildFullDraft(
                "draft-008", "msg-008", null, null,
                List.of(header("To", "test@example.com")),
                List.of(
                        buildBodyPart("text/plain", "body"),
                        buildAttachmentPart("resume.pdf", "application/pdf", 245760L),
                        buildAttachmentPart("cover-letter.docx",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                102400L)
                )
        );

        DraftDetailResult result = mapper.toDraftDetailResult(draft);

        assertThat(result.attachments()).hasSize(2);
        assertThat(result.attachments().get(0).filename()).isEqualTo("resume.pdf");
        assertThat(result.attachments().get(0).mimeType()).isEqualTo("application/pdf");
        assertThat(result.attachments().get(0).sizeBytes()).isEqualTo(245760L);
        assertThat(result.attachments().get(1).filename()).isEqualTo("cover-letter.docx");
    }

    @Test
    void toDraftDetailResult_base64UrlBodyDecoding_roundTripsCorrectly() {
        String originalText = "Hello World! Special chars: é à ü";
        String encodedBody = Base64.getUrlEncoder()
                .encodeToString(originalText.getBytes(StandardCharsets.UTF_8));

        MessagePart bodyPart = new MessagePart();
        bodyPart.setMimeType("text/plain");
        MessagePartBody body = new MessagePartBody();
        body.setData(encodedBody);
        bodyPart.setBody(body);

        Draft draft = buildFullDraft(
                "draft-009", "msg-009", null, null,
                List.of(header("To", "test@example.com")),
                List.of(bodyPart)
        );

        DraftDetailResult result = mapper.toDraftDetailResult(draft);

        assertThat(result.body()).isEqualTo(originalText);
    }

    @Test
    void toDraftDetailResult_nullMessage_returnsMinimalResult() {
        Draft draft = new Draft();
        draft.setId("draft-010");
        // message is null

        DraftDetailResult result = mapper.toDraftDetailResult(draft);

        assertThat(result.draftId()).isEqualTo("draft-010");
        assertThat(result.messageId()).isNull();
        assertThat(result.to()).isNotNull().isEmpty();
        assertThat(result.attachments()).isNotNull().isEmpty();
    }

    // =========================================================================
    // T007 — toDraftListItem(DraftDetailResult) tests
    // =========================================================================

    @Test
    void toDraftListItem_projection_picksCorrectSubsetOfFields() {
        List<AttachmentMetadata> attachments = List.of(
                new AttachmentMetadata("file.pdf", "application/pdf", 1024L)
        );
        DraftDetailResult detail = new DraftDetailResult(
                "draft-id-1", "msg-1", "thread-1",
                List.of("to@example.com"), List.of("cc@example.com"), List.of(),
                "Test Subject", "A snippet", "body text", "text",
                "in-reply-to-id", attachments
        );

        DraftListItem item = mapper.toDraftListItem(detail);

        assertThat(item.id()).isEqualTo("draft-id-1");
        assertThat(item.to()).containsExactly("to@example.com");
        assertThat(item.cc()).containsExactly("cc@example.com");
        assertThat(item.bcc()).isEmpty();
        assertThat(item.subject()).isEqualTo("Test Subject");
        assertThat(item.snippet()).isEqualTo("A snippet");
        assertThat(item.threadId()).isEqualTo("thread-1");
        assertThat(item.attachmentCount()).isEqualTo(1);
    }

    @Test
    void toDraftListItem_attachmentCountMatchesAttachmentsSize() {
        List<AttachmentMetadata> attachments = List.of(
                new AttachmentMetadata("a.pdf", "application/pdf", 100L),
                new AttachmentMetadata("b.pdf", "application/pdf", 200L),
                new AttachmentMetadata("c.pdf", "application/pdf", 300L)
        );
        DraftDetailResult detail = new DraftDetailResult(
                "d1", "m1", null, List.of(), List.of(), List.of(),
                null, null, null, "text", null, attachments
        );

        DraftListItem item = mapper.toDraftListItem(detail);

        assertThat(item.attachmentCount()).isEqualTo(3);
    }

    // =========================================================================
    // T007 — toDraftDetailResponse(DraftDetailResult) tests
    // =========================================================================

    @Test
    void toDraftDetailResponse_oneToOneFieldMapping_allFieldsTransferred() {
        List<AttachmentMetadata> attachments = List.of(
                new AttachmentMetadata("doc.pdf", "application/pdf", 5000L)
        );
        DraftDetailResult detail = new DraftDetailResult(
                "draft-resp-1", "msg-resp-1", "thread-resp-1",
                List.of("recipient@example.com"), List.of(), List.of(),
                "Subject line", "snippet", "<p>HTML body</p>", "html",
                "in-reply-to-ref", attachments
        );

        DraftDetailResponse response = mapper.toDraftDetailResponse(detail);

        assertThat(response.id()).isEqualTo("draft-resp-1");
        assertThat(response.to()).containsExactly("recipient@example.com");
        assertThat(response.cc()).isEmpty();
        assertThat(response.bcc()).isEmpty();
        assertThat(response.subject()).isEqualTo("Subject line");
        assertThat(response.body()).isEqualTo("<p>HTML body</p>");
        assertThat(response.bodyType()).isEqualTo("html");
        assertThat(response.threadId()).isEqualTo("thread-resp-1");
        assertThat(response.inReplyToMessageId()).isEqualTo("in-reply-to-ref");
        assertThat(response.attachments()).hasSize(1);
    }

    @Test
    void toDraftDetailResponse_nullableFieldsPassThroughAsNull() {
        DraftDetailResult detail = new DraftDetailResult(
                "draft-null-fields", "msg-n", null,
                List.of(), List.of(), List.of(),
                null, null, null, "text", null, List.of()
        );

        DraftDetailResponse response = mapper.toDraftDetailResponse(detail);

        assertThat(response.subject()).isNull();
        assertThat(response.body()).isNull();
        assertThat(response.threadId()).isNull();
        assertThat(response.inReplyToMessageId()).isNull();
    }

    // =========================================================================
    // T007 — toDraftListResponse(DraftListResult) tests
    // =========================================================================

    @Test
    void toDraftListResponse_eachDraftProjectedViaToDraftListItem() {
        List<DraftDetailResult> drafts = List.of(
                new DraftDetailResult("d1", "m1", null, List.of("a@b.com"), List.of(), List.of(),
                        "S1", "snip1", null, "text", null, List.of()),
                new DraftDetailResult("d2", "m2", "t2", List.of("c@d.com"), List.of(), List.of(),
                        "S2", "snip2", null, "text", null, List.of())
        );
        DraftListResult listResult = new DraftListResult(drafts, "token-abc", 42);

        DraftListResponse response = mapper.toDraftListResponse(listResult);

        assertThat(response.results()).hasSize(2);
        assertThat(response.results().get(0).id()).isEqualTo("d1");
        assertThat(response.results().get(1).id()).isEqualTo("d2");
        assertThat(response.nextPageToken()).isEqualTo("token-abc");
        assertThat(response.totalCount()).isEqualTo(42);
    }

    @Test
    void toDraftListResponse_emptyDraftList_returnsEmptyResults() {
        DraftListResult listResult = new DraftListResult(List.of(), null, null);

        DraftListResponse response = mapper.toDraftListResponse(listResult);

        assertThat(response.results()).isNotNull().isEmpty();
        assertThat(response.nextPageToken()).isNull();
        assertThat(response.totalCount()).isNull();
    }

    @Test
    void toDraftListResponse_nextPageTokenAndTotalCountPassThrough() {
        DraftListResult listResult = new DraftListResult(List.of(), "page-token-xyz", 99);

        DraftListResponse response = mapper.toDraftListResponse(listResult);

        assertThat(response.nextPageToken()).isEqualTo("page-token-xyz");
        assertThat(response.totalCount()).isEqualTo(99);
    }
}
