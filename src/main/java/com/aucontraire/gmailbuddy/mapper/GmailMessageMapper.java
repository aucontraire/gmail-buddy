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
import com.google.api.services.gmail.model.MessagePartHeader;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Boundary mapper that converts Gmail SDK types into project-internal domain
 * DTOs at the repository layer, keeping Gmail SDK types out of higher-level
 * layer signatures.
 *
 * <p>Per Constitution Principle II and research.md Decision 13, repository
 * interfaces MUST NOT expose Gmail API types. This mapper is the seam that
 * converts {@link com.google.api.services.gmail.model.Message} and
 * {@link com.google.api.services.gmail.model.Draft} to the corresponding
 * project records before the data crosses the repository boundary.</p>
 *
 * <p>The service layer and controller layer are therefore free of Gmail SDK
 * imports for the new send/draft code path.</p>
 *
 * @see SentMessageResult
 * @see DraftCreationResult
 */
@Component
public class GmailMessageMapper {

    /**
     * Converts a Gmail API {@link Message} (as returned by
     * {@code users.messages.send} or {@code users.drafts.send}) into a
     * {@link SentMessageResult} domain record.
     *
     * @param message the Gmail API message response; must not be {@code null}
     * @return a {@link SentMessageResult} carrying the assigned message and
     *         thread identifiers
     * @throws NullPointerException if {@code message} is {@code null}
     */
    public SentMessageResult toSentMessageResult(Message message) {
        return new SentMessageResult(message.getId(), message.getThreadId());
    }

    /**
     * Converts a Gmail API {@link Draft} (as returned by
     * {@code users.drafts.create}) into a {@link DraftCreationResult} domain
     * record.
     *
     * <p>The {@code messageId} and {@code threadId} are extracted from the
     * nested {@code draft.getMessage()} object that Gmail populates in the
     * create response. If {@code draft.getMessage()} is {@code null} (should
     * not occur for a successful create response), both identifiers are set to
     * {@code null}.</p>
     *
     * @param draft the Gmail API draft response; must not be {@code null}
     * @return a {@link DraftCreationResult} carrying the assigned draft, message,
     *         and thread identifiers
     * @throws NullPointerException if {@code draft} is {@code null}
     */
    public DraftCreationResult toDraftCreationResult(Draft draft) {
        Message nestedMessage = draft.getMessage();
        String messageId = nestedMessage != null ? nestedMessage.getId() : null;
        String threadId  = nestedMessage != null ? nestedMessage.getThreadId() : null;
        return new DraftCreationResult(draft.getId(), messageId, threadId);
    }

    /**
     * Converts a Gmail API {@link Draft} (already fetched with format=FULL) into a
     * {@link DraftDetailResult} domain record.
     *
     * <p>Extracts recipients from MIME headers, body from the message payload,
     * and attachment metadata from the message part tree. All list fields are
     * populated with {@code List.of()} when absent — never null.</p>
     *
     * @param draft the Gmail API draft; must not be null; message.payload should be present
     * @return a {@link DraftDetailResult} with all fields populated (lists never null)
     */
    public DraftDetailResult toDraftDetailResult(Draft draft) {
        String draftId = draft.getId();

        Message message = draft.getMessage();
        if (message == null) {
            return new DraftDetailResult(
                    draftId, null, null,
                    List.of(), List.of(), List.of(),
                    null, null, null, "text", null, List.of()
            );
        }

        String messageId = message.getId();
        String threadId = message.getThreadId();
        String snippet = message.getSnippet();

        // Walk MIME headers
        List<String> to = List.of();
        List<String> cc = List.of();
        List<String> bcc = List.of();
        String subject = null;
        String inReplyToMessageId = null;

        MessagePart payload = message.getPayload();
        if (payload != null) {
            List<MessagePartHeader> headers = payload.getHeaders();
            if (headers != null) {
                List<String> toList = new ArrayList<>();
                List<String> ccList = new ArrayList<>();
                List<String> bccList = new ArrayList<>();

                for (MessagePartHeader header : headers) {
                    String name = header.getName();
                    String value = header.getValue();
                    if (name == null || value == null) continue;

                    switch (name.toLowerCase()) {
                        case "to" -> parseAddressList(value, toList);
                        case "cc" -> parseAddressList(value, ccList);
                        case "bcc" -> parseAddressList(value, bccList);
                        case "subject" -> subject = value;
                        case "in-reply-to" -> inReplyToMessageId = stripAngleBrackets(value);
                        default -> { /* ignore other headers */ }
                    }
                }

                to = toList.isEmpty() ? List.of() : List.copyOf(toList);
                cc = ccList.isEmpty() ? List.of() : List.copyOf(ccList);
                bcc = bccList.isEmpty() ? List.of() : List.copyOf(bccList);
            }
        }

        // Walk MIME payload tree for body and attachments
        BodyExtractionResult bodyResult = new BodyExtractionResult();
        List<AttachmentMetadata> attachments = new ArrayList<>();

        if (payload != null) {
            extractBodyAndAttachments(payload, bodyResult, attachments);
        }

        String body = bodyResult.body;
        String bodyType = bodyResult.bodyType != null ? bodyResult.bodyType : "text";

        return new DraftDetailResult(
                draftId, messageId, threadId,
                to, cc, bcc,
                subject, snippet, body, bodyType,
                inReplyToMessageId,
                attachments.isEmpty() ? List.of() : List.copyOf(attachments)
        );
    }

    /**
     * Projects a {@link DraftDetailResult} to a {@link DraftListItem} (summary).
     * Used when building list responses from the enriched draft data.
     *
     * @param result the enriched draft domain record
     * @return a {@link DraftListItem} with summary fields
     */
    public DraftListItem toDraftListItem(DraftDetailResult result) {
        return new DraftListItem(
                result.draftId(),
                result.to(),
                result.cc(),
                result.bcc(),
                result.subject(),
                result.snippet(),
                result.threadId(),
                result.attachments().size()
        );
    }

    /**
     * Projects a {@link DraftDetailResult} to a {@link DraftDetailResponse} (full response DTO).
     * Used by GmailController on GET /drafts/{id} and PUT /drafts/{id} responses.
     * One-to-one field mapping; null fields pass through as null.
     *
     * @param result the enriched draft domain record
     * @return a {@link DraftDetailResponse} ready for serialization
     */
    public DraftDetailResponse toDraftDetailResponse(DraftDetailResult result) {
        return new DraftDetailResponse(
                result.draftId(),
                result.to(),
                result.cc(),
                result.bcc(),
                result.subject(),
                result.body(),
                result.bodyType(),
                result.threadId(),
                result.inReplyToMessageId(),
                result.attachments()
        );
    }

    /**
     * Projects a {@link DraftListResult} to a {@link DraftListResponse} (paginated response DTO).
     * Maps each {@link DraftDetailResult} in {@code result.drafts()} via
     * {@link #toDraftListItem}; passes through {@code nextPageToken} and {@code totalCount}.
     * Used by GmailController on GET /drafts response.
     *
     * @param result the per-request domain value object
     * @return a {@link DraftListResponse} ready for serialization
     */
    public DraftListResponse toDraftListResponse(DraftListResult result) {
        List<DraftListItem> items = result.drafts().stream()
                .map(this::toDraftListItem)
                .toList();
        return new DraftListResponse(items, result.nextPageToken(), result.totalCount());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Parses a comma-separated address list header value (e.g., To, Cc, Bcc)
     * and adds each trimmed address to the target list.
     */
    private void parseAddressList(String headerValue, List<String> target) {
        if (headerValue == null || headerValue.isBlank()) return;
        String[] parts = headerValue.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                target.add(trimmed);
            }
        }
    }

    /**
     * Strips leading and trailing angle brackets from an RFC 5322 message ID value.
     * E.g., {@code <CABc123@mail.gmail.com>} → {@code CABc123@mail.gmail.com}.
     */
    private String stripAngleBrackets(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * Mutable holder for the body extraction result during recursive MIME tree walk.
     * HTML body wins over text/plain (set when bodyType is not yet "html").
     */
    private static class BodyExtractionResult {
        String body;
        String bodyType;
    }

    /**
     * Recursively walks a {@link MessagePart} tree to extract the body text and
     * attachment metadata.
     *
     * <p>Strategy:
     * <ul>
     *   <li>If the part has {@code filename} set (non-empty), it is an attachment
     *       — collect its metadata and skip body extraction for this part.</li>
     *   <li>If the part has {@code text/html} MIME type, decode its body data and
     *       set as body (bodyType = "html"); HTML wins over plain text.</li>
     *   <li>If the part has {@code text/plain} MIME type and no body has been found
     *       yet, decode and set as body (bodyType = "text").</li>
     *   <li>If the part has sub-parts (multipart/*), recurse into each.</li>
     * </ul>
     * </p>
     */
    private void extractBodyAndAttachments(MessagePart part,
                                           BodyExtractionResult bodyResult,
                                           List<AttachmentMetadata> attachments) {
        if (part == null) return;

        String mimeType = part.getMimeType() != null ? part.getMimeType().toLowerCase() : "";
        String filename = part.getFilename();

        // Attachment: has a non-empty filename
        if (filename != null && !filename.isBlank()) {
            long sizeBytes = 0L;
            if (part.getBody() != null && part.getBody().getSize() != null) {
                sizeBytes = part.getBody().getSize();
            }
            attachments.add(new AttachmentMetadata(filename, part.getMimeType() != null ? part.getMimeType() : "", sizeBytes));
            return;
        }

        // Body parts
        if (mimeType.equals("text/html")) {
            String decoded = decodeBodyData(part);
            if (decoded != null) {
                // HTML always wins
                bodyResult.body = decoded;
                bodyResult.bodyType = "html";
            }
        } else if (mimeType.equals("text/plain")) {
            // Only use plain text if we don't already have an HTML body
            if (!"html".equals(bodyResult.bodyType)) {
                String decoded = decodeBodyData(part);
                if (decoded != null) {
                    bodyResult.body = decoded;
                    bodyResult.bodyType = "text";
                }
            }
        }

        // Recurse into sub-parts (multipart/*)
        List<MessagePart> parts = part.getParts();
        if (parts != null) {
            for (MessagePart subPart : parts) {
                extractBodyAndAttachments(subPart, bodyResult, attachments);
            }
        }
    }

    /**
     * Decodes the base64url-encoded body data of a {@link MessagePart} to a UTF-8 string.
     * Returns null if the body data is absent or empty.
     */
    private String decodeBodyData(MessagePart part) {
        if (part.getBody() == null) return null;
        String data = part.getBody().getData();
        if (data == null || data.isBlank()) return null;
        byte[] decoded = Base64.getUrlDecoder().decode(data);
        return new String(decoded, StandardCharsets.UTF_8);
    }
}
