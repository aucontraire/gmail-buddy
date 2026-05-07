package com.aucontraire.gmailbuddy.repository;

import com.aucontraire.gmailbuddy.service.BulkOperationResult;
import com.aucontraire.gmailbuddy.service.DraftCreationResult;
import com.aucontraire.gmailbuddy.service.MessageListResult;
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
}
