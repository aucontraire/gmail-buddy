package com.aucontraire.gmailbuddy.repository;

import com.aucontraire.gmailbuddy.service.BulkOperationResult;
import com.aucontraire.gmailbuddy.service.DraftCreationResult;
import com.aucontraire.gmailbuddy.service.MessageListResult;
import com.aucontraire.gmailbuddy.service.OriginalMessageLookup;
import com.aucontraire.gmailbuddy.service.SentMessageResult;
import com.google.api.services.gmail.model.Message;
import jakarta.mail.internet.MimeMessage;

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
}
