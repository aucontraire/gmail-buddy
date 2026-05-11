package com.aucontraire.gmailbuddy.fixture;

import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.LabelColor;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.google.api.services.gmail.model.Thread;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Static factory methods that produce Gmail SDK stub objects for use in
 * mapper, repository, service, and controller tests across feature 004 (Read
 * API completeness — threads, message detail, labels, attachments).
 *
 * <p>Centralising the SDK-stub construction here avoids per-test duplication
 * (Constitution Anti-Slop §C2) and keeps the test files focused on the
 * behaviour under test rather than fixture setup.</p>
 *
 * <p>All methods are static. This class carries no Spring context
 * ({@code @Component} is intentionally absent) so it can be used from any test
 * layer — plain JUnit, {@code @WebMvcTest}, {@code @SpringBootTest} — without
 * a running application context. Mirrors the design of
 * {@link AttachmentFixtures} and {@link SendMessageRequestFixtures}.</p>
 */
public final class ReadApiFixtures {

    private ReadApiFixtures() {
        throw new AssertionError(
                "ReadApiFixtures is a static factory class and must not be instantiated");
    }

    // -------------------------------------------------------------------------
    // Thread fixtures (US1)
    // -------------------------------------------------------------------------

    /**
     * Returns a Gmail SDK {@link Thread} stub as produced by
     * {@code users.threads.list} — only the fields available without a
     * {@code FULL} format fetch.
     */
    public static Thread buildThreadStub(String threadId, String snippet, String historyId) {
        Thread thread = new Thread();
        thread.setId(threadId);
        thread.setSnippet(snippet);
        if (historyId != null) {
            thread.setHistoryId(new java.math.BigInteger(historyId));
        }
        return thread;
    }

    /**
     * Returns a fully-populated Gmail SDK {@link Thread} as produced by
     * {@code users.threads.get(format=FULL)}.
     */
    public static Thread buildThreadFull(String threadId, List<Message> messages) {
        Thread thread = new Thread();
        thread.setId(threadId);
        thread.setMessages(messages);
        return thread;
    }

    // -------------------------------------------------------------------------
    // Message fixtures (US1, US2)
    // -------------------------------------------------------------------------

    /**
     * Returns a minimal Gmail SDK {@link Message} with the given identifiers
     * and label IDs. The payload has no headers, no body, no attachments.
     */
    public static Message buildMessage(String id, String threadId, List<String> labelIds) {
        Message message = new Message();
        message.setId(id);
        message.setThreadId(threadId);
        message.setLabelIds(labelIds);
        message.setPayload(new MessagePart());
        return message;
    }

    /**
     * Returns a Gmail SDK {@link Message} whose payload is a {@code multipart}
     * container holding the given parts. Useful for attachment-walk tests.
     */
    public static Message buildMessageWithAttachments(String id, List<MessagePart> parts) {
        Message message = new Message();
        message.setId(id);
        MessagePart payload = new MessagePart();
        payload.setMimeType("multipart/mixed");
        payload.setParts(parts);
        message.setPayload(payload);
        return message;
    }

    /**
     * Returns a Gmail SDK {@link Message} whose payload contains the given
     * headers (as {@link MessagePartHeader}s) and an optional body part. Used
     * to drive the 9-header whitelist mapper tests.
     */
    public static Message buildMessageWithHeaders(String id, String threadId,
                                                   List<MessagePartHeader> headers,
                                                   String snippet,
                                                   String bodyText) {
        Message message = new Message();
        message.setId(id);
        message.setThreadId(threadId);
        message.setSnippet(snippet);
        MessagePart payload = new MessagePart();
        payload.setMimeType("text/plain");
        payload.setHeaders(headers);
        if (bodyText != null) {
            MessagePartBody body = new MessagePartBody();
            body.setData(Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(bodyText.getBytes(StandardCharsets.UTF_8)));
            body.setSize(bodyText.getBytes(StandardCharsets.UTF_8).length);
            payload.setBody(body);
        }
        message.setPayload(payload);
        return message;
    }

    /**
     * Returns a {@link MessagePartHeader} with the given name and value.
     */
    public static MessagePartHeader header(String name, String value) {
        MessagePartHeader header = new MessagePartHeader();
        header.setName(name);
        header.setValue(value);
        return header;
    }

    /**
     * Returns a list of headers covering all 9 whitelisted RFC 5322 names with
     * sample values — useful for asserting the full whitelist is preserved.
     */
    public static List<MessagePartHeader> buildAllNineWhitelistedHeaders() {
        List<MessagePartHeader> headers = new ArrayList<>();
        headers.add(header("From", "sender@example.com"));
        headers.add(header("To", "recipient@example.com"));
        headers.add(header("Cc", "cc@example.com"));
        headers.add(header("Bcc", "bcc@example.com"));
        headers.add(header("Subject", "Sample Subject"));
        headers.add(header("Date", "Sun, 10 May 2026 12:00:00 -0700"));
        headers.add(header("In-Reply-To", "<orig@mail.example.com>"));
        headers.add(header("Message-ID", "<msg42@mail.example.com>"));
        headers.add(header("References", "<thread1@mail.example.com>"));
        return headers;
    }

    // -------------------------------------------------------------------------
    // Label fixtures (US3)
    // -------------------------------------------------------------------------

    /**
     * Returns a Gmail SDK {@link Label} with the given id, name, and type
     * ({@code "system"} or {@code "user"}). Counts and color are unset.
     */
    public static Label buildLabel(String id, String name, String type) {
        Label label = new Label();
        label.setId(id);
        label.setName(name);
        label.setType(type);
        return label;
    }

    /**
     * Returns a Gmail SDK {@link Label} with id/name/type plus a populated
     * {@link LabelColor} (for testing the color-extraction code path).
     */
    public static Label buildLabelWithColor(String id, String name,
                                             String textColor, String backgroundColor) {
        Label label = buildLabel(id, name, "user");
        LabelColor color = new LabelColor();
        color.setTextColor(textColor);
        color.setBackgroundColor(backgroundColor);
        label.setColor(color);
        return label;
    }

    // -------------------------------------------------------------------------
    // Attachment-part fixtures (US4)
    // -------------------------------------------------------------------------

    /**
     * Returns a Gmail SDK {@link MessagePart} representing a single attachment
     * leaf — body has a non-null {@code attachmentId} (which is how the mapper
     * recognises a part as an attachment per Decision 12).
     */
    public static MessagePart buildAttachmentPart(String attachmentId, String filename,
                                                   String mimeType, long sizeBytes) {
        MessagePart part = new MessagePart();
        part.setFilename(filename);
        part.setMimeType(mimeType);
        MessagePartBody body = new MessagePartBody();
        body.setAttachmentId(attachmentId);
        body.setSize((int) sizeBytes);
        part.setBody(body);
        return part;
    }
}
