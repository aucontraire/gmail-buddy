package com.aucontraire.gmailbuddy.mapper;

import com.aucontraire.gmailbuddy.dto.response.AttachmentMetadata;
import com.aucontraire.gmailbuddy.dto.response.DraftDetailResponse;
import com.aucontraire.gmailbuddy.dto.response.DraftListItem;
import com.aucontraire.gmailbuddy.dto.response.DraftListResponse;
import com.aucontraire.gmailbuddy.dto.response.LabelColor;
import com.aucontraire.gmailbuddy.dto.response.LabelDetailResponse;
import com.aucontraire.gmailbuddy.dto.response.LabelListResponse;
import com.aucontraire.gmailbuddy.dto.response.LabelSummary;
import com.aucontraire.gmailbuddy.dto.response.MessageAttachmentMetadata;
import com.aucontraire.gmailbuddy.dto.response.MessageDetailResponse;
import com.aucontraire.gmailbuddy.dto.response.ThreadDetailResponse;
import com.aucontraire.gmailbuddy.dto.response.ThreadListResponse;
import com.aucontraire.gmailbuddy.dto.response.ThreadSummary;
import com.aucontraire.gmailbuddy.service.AttachmentListResult;
import com.aucontraire.gmailbuddy.service.DraftCreationResult;
import com.aucontraire.gmailbuddy.service.DraftDetailResult;
import com.aucontraire.gmailbuddy.service.DraftListResult;
import com.aucontraire.gmailbuddy.service.LabelDetailResult;
import com.aucontraire.gmailbuddy.service.LabelListResult;
import com.aucontraire.gmailbuddy.service.MessageDetailResult;
import com.aucontraire.gmailbuddy.service.SentMessageResult;
import com.aucontraire.gmailbuddy.service.ThreadDetailResult;
import com.aucontraire.gmailbuddy.service.ThreadListResult;
import com.google.api.services.gmail.model.Draft;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.google.api.services.gmail.model.Thread;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
     * Canonical RFC 5322 header names that this application surfaces in
     * {@link MessageDetailResult#headers} per Clarifications Q2. All other
     * headers (e.g., {@code Received}, {@code Authentication-Results},
     * {@code X-Mailer}) are silently dropped during mapping.
     *
     * <p>The {@code List} form is exposed for use as the {@code metadataHeaders}
     * parameter on {@code users.messages.get(...).setMetadataHeaders(...)} —
     * limiting Gmail's METADATA-format response to only the headers we'll
     * surface (research.md Decision 1).</p>
     */
    public static final List<String> WHITELISTED_HEADERS_LIST = List.of(
            "From", "To", "Cc", "Bcc", "Subject", "Date",
            "In-Reply-To", "Message-ID", "References"
    );

    /** Set form of {@link #WHITELISTED_HEADERS_LIST} for membership checks. */
    private static final Set<String> WHITELISTED_HEADERS = Set.copyOf(WHITELISTED_HEADERS_LIST);

    /**
     * Lowercased header name → canonical RFC 5322 case. Enables case-insensitive
     * input matching with canonical-case output per research.md Decision 13.
     */
    private static final Map<String, String> CANONICAL_HEADER_CASE = Map.of(
            "from", "From",
            "to", "To",
            "cc", "Cc",
            "bcc", "Bcc",
            "subject", "Subject",
            "date", "Date",
            "in-reply-to", "In-Reply-To",
            "message-id", "Message-ID",
            "references", "References"
    );

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

    /**
     * Converts a Gmail API {@link Message} (as returned by
     * {@code users.messages.get}) into a {@link MessageDetailResult} domain
     * record. Used by US1 (nested in {@code ThreadDetailResult}) AND US2
     * (top-level on {@code GET /messages/{id}}).
     *
     * <p>Header extraction applies the 9-name whitelist with case-insensitive
     * input matching and canonical-case output (Decision 13). When
     * {@code "metadata".equalsIgnoreCase(format)}, body extraction is skipped
     * — {@code body} and {@code bodyType} are returned as null. Attachment
     * metadata is always extracted regardless of format.</p>
     *
     * @param message the Gmail API message; must not be null
     * @param format  {@code "full"} (or any value other than {@code "metadata"})
     *                to extract body, or {@code "metadata"} to skip body extraction
     * @return a {@link MessageDetailResult} with all fields populated
     *         (lists never null; body null when format=metadata)
     */
    public MessageDetailResult toMessageDetailResult(Message message, String format) {
        boolean metadataOnly = "metadata".equalsIgnoreCase(format);

        String id = message.getId();
        String threadId = message.getThreadId();
        String snippet = message.getSnippet();
        List<String> labelIds = message.getLabelIds() != null
                ? List.copyOf(message.getLabelIds())
                : List.of();

        Map<String, String> headers = new LinkedHashMap<>();
        BodyExtractionResult bodyResult = new BodyExtractionResult();
        List<MessageAttachmentMetadata> attachments = new ArrayList<>();

        MessagePart payload = message.getPayload();
        if (payload != null) {
            extractWhitelistedHeaders(payload, headers);
            extractBodyAndMessageAttachments(payload, bodyResult, attachments, metadataOnly);
        }

        String body = metadataOnly ? null : bodyResult.body;
        String bodyType = metadataOnly
                ? null
                : (bodyResult.bodyType != null ? bodyResult.bodyType : (body != null ? "text" : null));

        return new MessageDetailResult(
                id,
                threadId,
                Map.copyOf(headers),
                snippet,
                body,
                bodyType,
                labelIds,
                attachments.isEmpty() ? List.of() : List.copyOf(attachments)
        );
    }

    /**
     * Projects a {@link MessageDetailResult} to a {@link MessageDetailResponse}
     * for API serialization. One-to-one field mapping; null fields pass through.
     * Used by US1 (per nested message in {@code ThreadDetailResponse}) AND US2
     * (top-level response on {@code GET /messages/{id}}).
     *
     * @param result the message domain record; must not be null
     * @return a {@link MessageDetailResponse} ready for serialization
     */
    public MessageDetailResponse toMessageDetailResponse(MessageDetailResult result) {
        return new MessageDetailResponse(
                result.id(),
                result.threadId(),
                result.headers(),
                result.snippet(),
                result.body(),
                result.bodyType(),
                result.labelIds(),
                result.attachments()
        );
    }

    /**
     * Converts a single attachment {@link MessagePart} (a leaf with non-null
     * {@code body.attachmentId}) to a {@link MessageAttachmentMetadata} record.
     * Helper used by {@link #toMessageDetailResult} and (in US4) by
     * {@code toAttachmentListResult}. Filename falls back to {@code "unnamed"}
     * if the part has no filename (Constitution III — never null).
     *
     * @param part the attachment leaf part; must not be null
     * @return a {@link MessageAttachmentMetadata} with all fields populated
     */
    public MessageAttachmentMetadata toMessageAttachmentMetadata(MessagePart part) {
        String attachmentId = part.getBody() != null ? part.getBody().getAttachmentId() : null;
        String filename = (part.getFilename() != null && !part.getFilename().isBlank())
                ? part.getFilename()
                : "unnamed";
        String mimeType = part.getMimeType() != null ? part.getMimeType() : "application/octet-stream";
        long sizeBytes = (part.getBody() != null && part.getBody().getSize() != null)
                ? part.getBody().getSize()
                : 0L;
        return new MessageAttachmentMetadata(attachmentId, filename, mimeType, sizeBytes);
    }

    // -------------------------------------------------------------------------
    // Feature 004 — US4: Attachment mapper methods (T061)
    // -------------------------------------------------------------------------

    /**
     * Walks a Gmail SDK {@link Message}'s MIME part tree and returns an
     * {@link AttachmentListResult} containing one {@link MessageAttachmentMetadata}
     * for each part whose {@code body.attachmentId} is non-null (Decision 12 in
     * research.md).
     *
     * <p>Multipart containers ({@code multipart/*}) are recursed; inline body parts
     * ({@code text/html}, {@code text/plain}) and parts without an {@code attachmentId}
     * are skipped. The returned list is in MIME tree traversal order (depth-first).</p>
     *
     * <p>If the message has no payload, or no attachment parts are found, the returned
     * result contains {@code List.of()} (never null, per Constitution III).</p>
     *
     * @param message the fully-fetched Gmail API message (format=FULL); must not be null
     * @return an {@link AttachmentListResult} with zero or more attachment items
     */
    public AttachmentListResult toAttachmentListResult(Message message) {
        List<MessageAttachmentMetadata> collected = new ArrayList<>();
        MessagePart payload = message.getPayload();
        if (payload != null) {
            collectAttachmentParts(payload, collected);
        }
        return new AttachmentListResult(
                collected.isEmpty() ? List.of() : List.copyOf(collected)
        );
    }

    /**
     * Recursively walks a {@link MessagePart} tree, collecting parts that are
     * identified as attachments by having a non-null {@code body.attachmentId}
     * (research.md Decision 12).
     */
    private void collectAttachmentParts(MessagePart part, List<MessageAttachmentMetadata> target) {
        if (part == null) return;

        String attachmentId = part.getBody() != null ? part.getBody().getAttachmentId() : null;
        if (attachmentId != null && !attachmentId.isBlank()) {
            target.add(toMessageAttachmentMetadata(part));
            return; // Attachment leaf — no sub-parts to recurse into
        }

        // Recurse into sub-parts for multipart containers
        List<MessagePart> subParts = part.getParts();
        if (subParts != null) {
            for (MessagePart subPart : subParts) {
                collectAttachmentParts(subPart, target);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Feature 004 — Thread mapper methods (T021)
    // -------------------------------------------------------------------------

    /**
     * Converts a Gmail SDK {@link Thread} stub (from {@code users.threads.list})
     * to a {@link ThreadSummary} DTO. Direct SDK-stub→DTO conversion — no
     * intermediate domain record needed since the stub fields are already
     * domain-clean (Decision 9).
     *
     * @param stub the Gmail API thread stub; must not be null
     * @return a {@link ThreadSummary} with id, snippet, and historyId
     */
    public ThreadSummary toThreadSummary(Thread stub) {
        String historyId = stub.getHistoryId() != null ? stub.getHistoryId().toString() : null;
        return new ThreadSummary(stub.getId(), stub.getSnippet(), historyId);
    }

    /**
     * Converts a full Gmail SDK {@link Thread} (from {@code users.threads.get},
     * format=FULL) to a {@link ThreadDetailResult} domain record.
     *
     * <p>Calls {@link #toMessageDetailResult(Message, String)} with format "full"
     * for each nested message. Computes {@code labelIds} as the union across all
     * messages using a {@link LinkedHashSet} for insertion-order-stable deduplication.
     * Messages are in the chronological ascending order returned by Gmail's API
     * (Decision 4 — no additional sorting needed).</p>
     *
     * @param thread the fully-fetched Gmail API thread; must not be null
     * @return a {@link ThreadDetailResult} with all fields populated (lists never null)
     */
    public ThreadDetailResult toThreadDetailResult(Thread thread) {
        List<Message> rawMessages = thread.getMessages();
        if (rawMessages == null) {
            return new ThreadDetailResult(thread.getId(), List.of(), List.of());
        }

        List<MessageDetailResult> messages = new ArrayList<>();
        LinkedHashSet<String> labelUnion = new LinkedHashSet<>();

        for (Message msg : rawMessages) {
            MessageDetailResult detail = toMessageDetailResult(msg, "full");
            messages.add(detail);
            labelUnion.addAll(detail.labelIds());
        }

        return new ThreadDetailResult(
                thread.getId(),
                labelUnion.isEmpty() ? List.of() : List.copyOf(labelUnion),
                List.copyOf(messages)
        );
    }

    /**
     * Projects a {@link ThreadListResult} to a {@link ThreadListResponse} for
     * API serialization. Used by the controller on {@code GET /threads}.
     *
     * @param result the per-request domain value object; must not be null
     * @return a {@link ThreadListResponse} ready for serialization
     */
    public ThreadListResponse toThreadListResponse(ThreadListResult result) {
        return new ThreadListResponse(
                result.threads(),
                result.nextPageToken(),
                result.totalCount()
        );
    }

    /**
     * Projects a {@link ThreadDetailResult} to a {@link ThreadDetailResponse} for
     * API serialization. Converts each {@link MessageDetailResult} to a
     * {@link MessageDetailResponse} via {@link #toMessageDetailResponse}. Used by
     * the controller on {@code GET /threads/{id}}.
     *
     * @param result the thread domain record; must not be null
     * @return a {@link ThreadDetailResponse} ready for serialization
     */
    public ThreadDetailResponse toThreadDetailResponse(ThreadDetailResult result) {
        List<MessageDetailResponse> messages = result.messages().stream()
                .map(this::toMessageDetailResponse)
                .toList();
        return new ThreadDetailResponse(
                result.threadId(),
                result.labelIds(),
                messages
        );
    }

    // -------------------------------------------------------------------------
    // Feature 004 — Label mapper methods (T048)
    // -------------------------------------------------------------------------

    /**
     * Converts a Gmail SDK {@link Label} (from {@code users.labels.list} or
     * {@code users.labels.get}) to a {@link LabelSummary} DTO. Direct SDK→DTO
     * conversion — the summary fields are domain-clean (no further enrichment).
     *
     * <p>Null-safe: if {@code label.getName()} or {@code label.getType()} is null,
     * the corresponding field in the returned record is null. All lists default to
     * {@code null} fields in {@code LabelSummary} when not provided by Gmail.</p>
     *
     * @param label the Gmail API label; must not be null
     * @return a {@link LabelSummary} with id, name, type, and visibility fields
     */
    public LabelSummary toLabelSummary(Label label) {
        return new LabelSummary(
                label.getId(),
                label.getName(),
                label.getType() != null ? label.getType().toLowerCase() : null,
                label.getMessageListVisibility(),
                label.getLabelListVisibility()
        );
    }

    /**
     * Converts a Gmail SDK {@link Label} (from {@code users.labels.get}) to a
     * {@link LabelDetailResult} domain record. Extracts color text and background
     * as flat String fields per data-model §19.
     *
     * <p>Color: if {@code label.getColor()} is non-null, its text and background
     * fields are stored as flat strings. If color is null (system labels without
     * color, or user labels with no color chosen), both color fields are null.</p>
     *
     * <p>Counts: {@code messagesTotal}, {@code messagesUnread}, {@code threadsTotal},
     * {@code threadsUnread} are taken from the {@code MessagesUnread},
     * {@code MessagesTotal}, {@code ThreadsUnread}, {@code ThreadsTotal} fields
     * on the Gmail API {@code Label} object; null if Gmail did not include them.</p>
     *
     * @param label the Gmail API label; must not be null
     * @return a {@link LabelDetailResult} with all fields populated (nulls where absent)
     */
    public LabelDetailResult toLabelDetailResult(Label label) {
        String colorTextColor = null;
        String colorBackgroundColor = null;
        if (label.getColor() != null) {
            colorTextColor = label.getColor().getTextColor();
            colorBackgroundColor = label.getColor().getBackgroundColor();
        }

        return new LabelDetailResult(
                label.getId(),
                label.getName(),
                label.getType() != null ? label.getType().toLowerCase() : null,
                label.getMessageListVisibility(),
                label.getLabelListVisibility(),
                colorTextColor,
                colorBackgroundColor,
                label.getMessagesTotal(),
                label.getMessagesUnread(),
                label.getThreadsTotal(),
                label.getThreadsUnread()
        );
    }

    /**
     * Projects a {@link LabelListResult} to a {@link LabelListResponse} for
     * API serialization. Used by the controller on {@code GET /labels}.
     *
     * @param result the per-request domain value object; must not be null
     * @return a {@link LabelListResponse} ready for serialization
     */
    public LabelListResponse toLabelListResponse(LabelListResult result) {
        return new LabelListResponse(result.labels(), result.totalCount());
    }

    /**
     * Projects a {@link LabelDetailResult} to a {@link LabelDetailResponse} for
     * API serialization. Constructs a {@link LabelColor} record when both color
     * fields are non-null; otherwise {@code color} is null (system labels or
     * user labels without color configured).
     *
     * @param result the label domain record; must not be null
     * @return a {@link LabelDetailResponse} ready for serialization
     */
    public LabelDetailResponse toLabelDetailResponse(LabelDetailResult result) {
        LabelColor color = null;
        if (result.colorTextColor() != null || result.colorBackgroundColor() != null) {
            color = new LabelColor(result.colorTextColor(), result.colorBackgroundColor());
        }
        return new LabelDetailResponse(
                result.id(),
                result.name(),
                result.type(),
                result.messageListVisibility(),
                result.labelListVisibility(),
                color,
                result.messagesTotal(),
                result.messagesUnread(),
                result.threadsTotal(),
                result.threadsUnread()
        );
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Recursively walks a {@link MessagePart} tree and adds any whitelisted
     * RFC 5322 header found at any nesting level into {@code target}, using
     * the canonical RFC 5322 casing as the map key. Last value wins for
     * duplicates (rare in well-formed mail; expected for repeated headers
     * like {@code Received} which are not whitelisted anyway).
     */
    private void extractWhitelistedHeaders(MessagePart part, Map<String, String> target) {
        if (part == null) return;
        List<MessagePartHeader> headers = part.getHeaders();
        if (headers != null) {
            for (MessagePartHeader header : headers) {
                String name = header.getName();
                String value = header.getValue();
                if (name == null || value == null) continue;
                String canonical = CANONICAL_HEADER_CASE.get(name.toLowerCase());
                if (canonical != null) {
                    target.put(canonical, value);
                }
            }
        }
        // Headers can also live on nested parts (uncommon for the whitelisted
        // 9, but harmless to recurse).
        List<MessagePart> subParts = part.getParts();
        if (subParts != null) {
            for (MessagePart subPart : subParts) {
                extractWhitelistedHeaders(subPart, target);
            }
        }
    }

    /**
     * Recursively walks a {@link MessagePart} tree to extract the body text
     * (when {@code metadataOnly} is false) and attachment metadata (always).
     * Mirrors {@link #extractBodyAndAttachments} but produces
     * {@link MessageAttachmentMetadata} (with {@code attachmentId}) instead of
     * {@link AttachmentMetadata}.
     */
    private void extractBodyAndMessageAttachments(MessagePart part,
                                                  BodyExtractionResult bodyResult,
                                                  List<MessageAttachmentMetadata> attachments,
                                                  boolean metadataOnly) {
        if (part == null) return;

        String mimeType = part.getMimeType() != null ? part.getMimeType().toLowerCase() : "";
        String attachmentId = part.getBody() != null ? part.getBody().getAttachmentId() : null;

        // Attachment leaf: identified by non-null attachmentId per data-model §22 + Decision 12
        if (attachmentId != null) {
            attachments.add(toMessageAttachmentMetadata(part));
            return;
        }

        // Body parts (skip when metadataOnly)
        if (!metadataOnly) {
            if (mimeType.equals("text/html")) {
                String decoded = decodeBodyData(part);
                if (decoded != null) {
                    bodyResult.body = decoded;
                    bodyResult.bodyType = "html";
                }
            } else if (mimeType.equals("text/plain")) {
                if (!"html".equals(bodyResult.bodyType)) {
                    String decoded = decodeBodyData(part);
                    if (decoded != null) {
                        bodyResult.body = decoded;
                        bodyResult.bodyType = "text";
                    }
                }
            }
        }

        // Recurse into sub-parts
        List<MessagePart> parts = part.getParts();
        if (parts != null) {
            for (MessagePart subPart : parts) {
                extractBodyAndMessageAttachments(subPart, bodyResult, attachments, metadataOnly);
            }
        }
    }

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
