package com.aucontraire.gmailbuddy.repository;

import com.aucontraire.gmailbuddy.dto.FilterCriteriaDTO;
import com.aucontraire.gmailbuddy.service.AttachmentListResult;
import com.aucontraire.gmailbuddy.service.BulkOperationResult;
import com.aucontraire.gmailbuddy.service.DraftCreationResult;
import com.aucontraire.gmailbuddy.service.DraftDetailResult;
import com.aucontraire.gmailbuddy.service.DraftListResult;
import com.aucontraire.gmailbuddy.service.LabelDetailResult;
import com.aucontraire.gmailbuddy.service.LabelListResult;
import com.aucontraire.gmailbuddy.service.MessageDetailResult;
import com.aucontraire.gmailbuddy.service.MessageListResult;
import com.aucontraire.gmailbuddy.service.OriginalMessageLookup;
import com.aucontraire.gmailbuddy.service.SentMessageResult;
import com.aucontraire.gmailbuddy.service.ThreadDetailResult;
import com.aucontraire.gmailbuddy.service.ThreadListResult;
import com.google.api.services.gmail.model.Message;
import jakarta.mail.internet.MimeMessage;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface GmailRepository {
    List<Message> getMessages(String userId) throws IOException;
    List<Message> getLatestMessages(String userId, long maxResults) throws IOException;
    List<Message> getMessagesByFilterCriteria(String userId, String query) throws IOException;

    // Paginated versions of list methods
    MessageListResult getMessagesWithPagination(String userId, String pageToken, int limit) throws IOException;
    MessageListResult getLatestMessagesWithPagination(String userId, String pageToken, int maxResults) throws IOException;
    MessageListResult getMessagesByFilterCriteriaWithPagination(String userId, String query, String pageToken, int limit) throws IOException;

    BulkOperationResult deleteMessage(String userId, String messageId) throws IOException;
    BulkOperationResult deleteMessagesByFilterCriteria(String userId, String query) throws IOException;
    BulkOperationResult modifyMessagesLabels(String userId, List<String> labelsToAdd, List<String> labelsToRemove, String query) throws IOException;
    String getMessageBody(String userId, String messageId) throws IOException;
    Map<String, String> getLabels(String userId) throws IOException;
    BulkOperationResult markMessageAsRead(String userId, String messageId) throws IOException;

    /**
     * Sends a fully-constructed MimeMessage immediately via the Gmail
     * {@code users.messages.send} API call.
     *
     * @param userId      the Gmail user identifier; use {@code "me"} for the
     *                    authenticated user
     * @param mimeMessage the fully-populated RFC 5322 message to send
     * @return assigned message and thread identifiers returned by Gmail
     * @throws IOException          on network or Gmail API communication failure
     * @throws com.aucontraire.gmailbuddy.exception.MessageSendException
     *                              on Gmail send failure (mapped from
     *                              {@code GoogleJsonResponseException} reasons)
     */
    SentMessageResult sendMessage(String userId, MimeMessage mimeMessage) throws IOException;

    /**
     * Sends a fully-constructed MimeMessage immediately via the Gmail
     * {@code users.messages.send} API call, optionally placing the message into
     * the specified Gmail thread (FR-005, FR-006, FR-007).
     *
     * <p>When {@code threadId} is non-null, it is set on the Gmail API
     * {@code Message} object via {@code message.setThreadId(threadId)} before the
     * send call, directing Gmail to place the message in the specified thread.</p>
     *
     * @param userId      the Gmail user identifier; use {@code "me"} for the
     *                    authenticated user
     * @param mimeMessage the fully-populated RFC 5322 message to send
     * @param threadId    the resolved Gmail thread ID to set on the outgoing message;
     *                    {@code null} means no thread is specified (new thread)
     * @return assigned message and thread identifiers returned by Gmail
     * @throws IOException          on network or Gmail API communication failure
     * @throws com.aucontraire.gmailbuddy.exception.MessageSendException
     *                              on Gmail send failure
     */
    SentMessageResult sendMessage(String userId, MimeMessage mimeMessage, String threadId) throws IOException;

    /**
     * Stages a draft for later send or edit. The draft is immediately visible in
     * the user's Gmail Drafts folder via {@code users.drafts.create}.
     *
     * @param userId      the Gmail user identifier; use {@code "me"} for the
     *                    authenticated user
     * @param mimeMessage the fully-populated RFC 5322 message to save as a draft
     * @return assigned draft, message, and thread identifiers returned by Gmail
     * @throws IOException          on network or Gmail API communication failure
     * @throws com.aucontraire.gmailbuddy.exception.MessageSendException
     *                              on Gmail draft-creation failure
     */
    DraftCreationResult createDraft(String userId, MimeMessage mimeMessage) throws IOException;

    /**
     * Stages a draft for later send or edit, optionally placing the draft into
     * the specified Gmail thread (FR-005, FR-006, FR-007).
     *
     * <p>When {@code threadId} is non-null, it is set on the Gmail API
     * {@code Message} object before the draft-creation call, directing Gmail to
     * associate the draft with the specified thread.</p>
     *
     * @param userId      the Gmail user identifier; use {@code "me"} for the
     *                    authenticated user
     * @param mimeMessage the fully-populated RFC 5322 message to save as a draft
     * @param threadId    the resolved Gmail thread ID to set on the draft message;
     *                    {@code null} means no thread is specified
     * @return assigned draft, message, and thread identifiers returned by Gmail
     * @throws IOException          on network or Gmail API communication failure
     * @throws com.aucontraire.gmailbuddy.exception.MessageSendException
     *                              on Gmail draft-creation failure
     */
    DraftCreationResult createDraft(String userId, MimeMessage mimeMessage, String threadId) throws IOException;

    /**
     * Sends a previously-created draft by identifier via the Gmail
     * {@code users.drafts.send} API call.
     *
     * <p>Gmail removes the draft from the Drafts folder on a successful send,
     * making this operation naturally idempotent at the resource level — a
     * retry after a successful send returns {@code 404} from Gmail.</p>
     *
     * @param userId  the Gmail user identifier; use {@code "me"} for the
     *                authenticated user
     * @param draftId the Gmail draft identifier returned by {@link #createDraft}
     * @return assigned message and thread identifiers of the sent message
     * @throws IOException          on network or Gmail API communication failure
     * @throws com.aucontraire.gmailbuddy.exception.ResourceNotFoundException
     *                              if the draft does not exist (or was already
     *                              sent or discarded)
     * @throws com.aucontraire.gmailbuddy.exception.MessageSendException
     *                              on other Gmail send failures
     */
    SentMessageResult sendDraft(String userId, String draftId) throws IOException;

    /**
     * Returns a paginated list of drafts for the specified user.
     * Internally calls users.drafts.list followed by users.drafts.get per item
     * to populate recipient, subject, and snippet fields.
     *
     * @param userId    the Gmail user identifier; typically "me"
     * @param pageToken opaque token from a prior response, or null for the first page
     * @param limit     maximum number of items to return (1–50)
     * @return a DraftListResult containing enriched draft summaries and pagination state
     * @throws IOException on Gmail API communication failure
     */
    DraftListResult listDrafts(String userId, String pageToken, int limit) throws IOException;

    /**
     * Returns the full content of the specified draft.
     *
     * @param userId  the Gmail user identifier; typically "me"
     * @param draftId the Gmail draft identifier
     * @return a DraftDetailResult with all parsed fields
     * @throws com.aucontraire.gmailbuddy.exception.ResourceNotFoundException
     *         if the draft does not exist (Gmail 404)
     * @throws IOException on Gmail API communication failure
     */
    DraftDetailResult getDraft(String userId, String draftId) throws IOException;

    /**
     * Permanently deletes the specified draft.
     * users.drafts.delete is a hard delete; no trash or soft-delete option.
     *
     * @param userId  the Gmail user identifier; typically "me"
     * @param draftId the Gmail draft identifier
     * @throws com.aucontraire.gmailbuddy.exception.ResourceNotFoundException
     *         if the draft does not exist (Gmail 404)
     * @throws IOException on Gmail API communication failure
     */
    void deleteDraft(String userId, String draftId) throws IOException;

    /**
     * Replaces the content of the specified draft with the provided MimeMessage.
     * Uses users.drafts.update — full replacement, no partial update.
     *
     * @param userId      the Gmail user identifier; typically "me"
     * @param draftId     the Gmail draft identifier
     * @param mimeMessage the fully-constructed replacement MIME message
     * @return the updated draft's identifiers (draftId, messageId, threadId)
     * @throws com.aucontraire.gmailbuddy.exception.ResourceNotFoundException
     *         if the draft does not exist (Gmail 404)
     * @throws IOException on Gmail API communication failure
     */
    DraftCreationResult updateDraft(String userId, String draftId, MimeMessage mimeMessage) throws IOException;

    /**
     * Returns a paginated list of thread summaries matching the given filter criteria.
     * Calls {@code users.threads.list} once; no per-item enrichment (flat 10-unit quota cost).
     *
     * @param userId         the Gmail user identifier; typically "me"
     * @param filterCriteria filter criteria built from query parameters; null for no filter
     * @param pageToken      opaque token from a prior response, or null for the first page
     * @param limit          maximum number of items to return (1–100)
     * @return a ThreadListResult containing thread summaries and pagination state
     * @throws IOException on Gmail API communication failure
     */
    ThreadListResult listThreads(String userId, FilterCriteriaDTO filterCriteria,
                                 String pageToken, int limit) throws IOException;

    /**
     * Returns the full content of the specified thread including all nested messages.
     * Calls {@code users.threads.get} with format=FULL; all messages returned in chronological order.
     *
     * @param userId   the Gmail user identifier; typically "me"
     * @param threadId the Gmail thread identifier
     * @return a ThreadDetailResult with all messages and union label set
     * @throws com.aucontraire.gmailbuddy.exception.ResourceNotFoundException if the thread does not exist (Gmail 404)
     * @throws IOException on Gmail API communication failure
     */
    ThreadDetailResult getThread(String userId, String threadId) throws IOException;

    /**
     * Returns the full structured detail of the specified message.
     * When {@code format} is {@code "full"}, calls {@code users.messages.get} with
     * {@code format=FULL} (10 quota units). When {@code format} is {@code "metadata"},
     * calls with {@code format=METADATA} and the 9 whitelisted header names (5 quota units).
     *
     * @param userId    the Gmail user identifier; typically "me"
     * @param messageId the Gmail message identifier
     * @param format    {@code "full"} for body + headers; {@code "metadata"} for headers only
     * @return a MessageDetailResult with all fields; body is null when format=metadata
     * @throws com.aucontraire.gmailbuddy.exception.ResourceNotFoundException if the message does not exist (Gmail 404)
     * @throws IOException on Gmail API communication failure
     */
    MessageDetailResult getMessageDetail(String userId, String messageId, String format) throws IOException;

    /**
     * Returns all visible labels for the authenticated user.
     * Calls {@code users.labels.list}; returns a single page (Gmail does not paginate labels).
     *
     * @param userId the Gmail user identifier; typically "me"
     * @return a LabelListResult containing all labels and a totalCount
     * @throws IOException on Gmail API communication failure
     */
    LabelListResult listLabels(String userId) throws IOException;

    /**
     * Returns the full detail of the specified label including counts and color.
     * Calls {@code users.labels.get}.
     *
     * @param userId  the Gmail user identifier; typically "me"
     * @param labelId the Gmail label identifier
     * @return a LabelDetailResult with all fields
     * @throws com.aucontraire.gmailbuddy.exception.ResourceNotFoundException if the label does not exist (Gmail 404)
     * @throws IOException on Gmail API communication failure
     */
    LabelDetailResult getLabel(String userId, String labelId) throws IOException;

    /**
     * Fetches the RFC 5322 {@code Message-ID} header and {@code threadId} from the
     * specified message using the Gmail metadata-only format
     * ({@code format=METADATA}, {@code metadataHeaders=["Message-ID"]}).
     *
     * <p>The {@code Message-ID} header is extracted from the response's payload
     * header list using case-insensitive name comparison (RFC 5322 header names are
     * case-insensitive). The {@code threadId} is read from the top-level
     * {@code Message.getThreadId()} field on the same response object.</p>
     *
     * <p>The returned {@link OriginalMessageLookup} is consumed by
     * {@code MimeMessageBuilder.build(SendMessageDTO, OriginalMessageLookup)} to
     * set {@code In-Reply-To} and {@code References} headers on the outgoing MIME,
     * and by the service layer to resolve the effective thread ID per FR-005 and
     * FR-006.</p>
     *
     * @param userId    the Gmail user identifier; typically {@code "me"} for the
     *                  authenticated user
     * @param messageId the Gmail short message ID of the original message being
     *                  replied to (the caller's {@code inReplyToMessageId})
     * @return an {@link OriginalMessageLookup} containing the Gmail short message
     *         ID, the {@code threadId}, and the RFC 5322 {@code Message-ID} header
     *         value (angle-bracket-delimited, e.g.
     *         {@code <CABc123xyz@mail.gmail.com>})
     * @throws com.aucontraire.gmailbuddy.exception.OriginalMessageNotFoundException
     *         if the message does not exist or is not accessible (Gmail returns
     *         HTTP 404), or if the message exists but its payload carries no
     *         {@code Message-ID} header — both cases make threading impossible and
     *         must be surfaced to the caller as HTTP 422. This is an unchecked
     *         exception ({@code RuntimeException}) and is therefore not listed in
     *         the {@code throws} clause.
     * @throws IOException if the Gmail API call fails for a transient reason
     *         (HTTP 5xx, network timeout, or rate-limit on the lookup); callers
     *         should propagate this exception and let the existing
     *         {@code GmailService} → {@code GmailController} exception chain map
     *         it to HTTP 502 or 503 as appropriate
     */
    OriginalMessageLookup getMessageHeaders(String userId, String messageId) throws IOException;

    /**
     * Returns the list of attachment metadata for the specified message.
     * Calls {@code users.messages.get} with {@code format=FULL} to enumerate MIME parts.
     * Returns an {@link AttachmentListResult} with an empty list when the message has no
     * attachments — does NOT throw {@link com.aucontraire.gmailbuddy.exception.ResourceNotFoundException}
     * for the zero-attachments case (FR-024 — HTTP 200 with empty results, not 404).
     *
     * @param userId    the Gmail user identifier; typically "me"
     * @param messageId the Gmail message identifier
     * @return an {@link AttachmentListResult}; {@code attachments} is {@code List.of()} when none present
     * @throws com.aucontraire.gmailbuddy.exception.ResourceNotFoundException if the message does not exist (Gmail 404)
     * @throws IOException on Gmail API communication failure
     */
    AttachmentListResult listAttachments(String userId, String messageId) throws IOException;

    /**
     * Returns the raw binary content of the specified attachment as a
     * {@link StreamingResponseBody} lambda.
     *
     * <p>Per research.md Decision 6, Option A is used: the underlying Gmail API call
     * ({@code users.messages.attachments.get}) is made synchronously in this method
     * before the lambda is returned. The decoded byte array is captured by the lambda
     * closure and written to the output stream when Spring invokes {@code writeTo()}.
     * This ensures that any 404 (message or attachment not found) surfaces before the
     * HTTP response is committed — allowing the controller to return a proper 404
     * {@link org.springframework.http.ProblemDetail}.</p>
     *
     * @param userId       the Gmail user identifier; typically "me"
     * @param messageId    the Gmail message identifier
     * @param attachmentId the Gmail attachment identifier
     * @return a {@link StreamingResponseBody} that writes the decoded attachment bytes;
     *         the returned lambda holds the decoded {@code byte[]} in its closure
     * @throws com.aucontraire.gmailbuddy.exception.ResourceNotFoundException if the message
     *         or attachment does not exist (Gmail 404 on the attachments.get call)
     * @throws IOException on Gmail API communication failure
     */
    StreamingResponseBody getAttachment(String userId, String messageId, String attachmentId) throws IOException;
}
