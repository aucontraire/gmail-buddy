package com.aucontraire.gmailbuddy.repository;

import com.aucontraire.gmailbuddy.client.GmailClient;
import com.aucontraire.gmailbuddy.client.GmailBatchClient;
import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import com.aucontraire.gmailbuddy.exception.AuthenticationException;
import com.aucontraire.gmailbuddy.exception.AuthorizationException;
import com.aucontraire.gmailbuddy.exception.GmailApiException;
import com.aucontraire.gmailbuddy.exception.InvalidRecipientException;
import com.aucontraire.gmailbuddy.exception.MessageSendException;
import com.aucontraire.gmailbuddy.exception.MessageTooLargeException;
import com.aucontraire.gmailbuddy.exception.OriginalMessageNotFoundException;
import com.aucontraire.gmailbuddy.exception.RateLimitException;
import com.aucontraire.gmailbuddy.exception.ResourceNotFoundException;
import com.aucontraire.gmailbuddy.exception.ServiceUnavailableException;
import com.aucontraire.gmailbuddy.service.OriginalMessageLookup;
import com.aucontraire.gmailbuddy.exception.ValidationException;
import com.aucontraire.gmailbuddy.mapper.GmailMessageMapper;
import com.aucontraire.gmailbuddy.service.DraftCreationResult;
import com.aucontraire.gmailbuddy.service.SentMessageResult;
import com.aucontraire.gmailbuddy.service.TokenProvider;
import com.aucontraire.gmailbuddy.service.BulkOperationResult;
import com.aucontraire.gmailbuddy.service.MessageListResult;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;
import java.util.*;

@Component
public class GmailRepositoryImpl implements GmailRepository {

    private final GmailClient gmailClient;
    private final GmailBatchClient gmailBatchClient;
    private final TokenProvider tokenProvider;
    private final GmailBuddyProperties properties;
    private final GmailMessageMapper gmailMessageMapper;
    private final Logger logger = LoggerFactory.getLogger(GmailRepositoryImpl.class);

    @Autowired
    public GmailRepositoryImpl(GmailClient gmailClient, GmailBatchClient gmailBatchClient,
                              TokenProvider tokenProvider, GmailBuddyProperties properties,
                              GmailMessageMapper gmailMessageMapper) {
        this.gmailClient = gmailClient;
        this.gmailBatchClient = gmailBatchClient;
        this.tokenProvider = tokenProvider;
        this.properties = properties;
        this.gmailMessageMapper = gmailMessageMapper;
    }

    private Gmail getGmailService() throws IOException, GeneralSecurityException {
        try {
            String accessToken = tokenProvider.getAccessToken();
            return gmailClient.createGmailService(accessToken);
        } catch (AuthenticationException e) {
            logger.error("Failed to retrieve access token for Gmail service", e);
            throw new IllegalStateException("Failed to authenticate with Gmail API: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Message> getMessages(String userId) throws IOException {
        try {
            var gmail = getGmailService();
            return gmail.users().messages().list(userId).setMaxResults(50L).execute().getMessages();
        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    @Override
    public List<Message> getLatestMessages(String userId, long maxResults) throws IOException {
        try {
            var gmail = getGmailService();
            return gmail.users().messages()
                    .list(userId)
                    .setMaxResults(maxResults)
                    .execute()
                    .getMessages();
        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    @Override
    public List<Message> getMessagesByFilterCriteria(String userId, String query) throws IOException {
        try {
            var gmail = getGmailService();
            return gmail.users().messages()
                    .list(userId)
                    .setQ(query)
                    .execute()
                    .getMessages();
        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    @Override
    public MessageListResult getMessagesWithPagination(String userId, String pageToken, int limit) throws IOException {
        try {
            var gmail = getGmailService();
            Gmail.Users.Messages.List request = gmail.users().messages().list(userId)
                    .setMaxResults((long) limit);

            if (pageToken != null && !pageToken.isEmpty()) {
                request.setPageToken(pageToken);
            }

            ListMessagesResponse response = request.execute();
            return new MessageListResult(
                    response.getMessages(),
                    response.getNextPageToken(),
                    response.getResultSizeEstimate() != null ? response.getResultSizeEstimate().intValue() : null
            );
        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    @Override
    public MessageListResult getLatestMessagesWithPagination(String userId, String pageToken, int maxResults) throws IOException {
        try {
            var gmail = getGmailService();
            Gmail.Users.Messages.List request = gmail.users().messages().list(userId)
                    .setMaxResults((long) maxResults);

            if (pageToken != null && !pageToken.isEmpty()) {
                request.setPageToken(pageToken);
            }

            ListMessagesResponse response = request.execute();
            return new MessageListResult(
                    response.getMessages(),
                    response.getNextPageToken(),
                    response.getResultSizeEstimate() != null ? response.getResultSizeEstimate().intValue() : null
            );
        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    @Override
    public MessageListResult getMessagesByFilterCriteriaWithPagination(String userId, String query, String pageToken, int limit) throws IOException {
        try {
            var gmail = getGmailService();
            Gmail.Users.Messages.List request = gmail.users().messages().list(userId)
                    .setQ(query)
                    .setMaxResults((long) limit);

            if (pageToken != null && !pageToken.isEmpty()) {
                request.setPageToken(pageToken);
            }

            ListMessagesResponse response = request.execute();
            return new MessageListResult(
                    response.getMessages(),
                    response.getNextPageToken(),
                    response.getResultSizeEstimate() != null ? response.getResultSizeEstimate().intValue() : null
            );
        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    @Override
    public BulkOperationResult deleteMessage(String userId, String messageId) throws IOException {
        try {
            var gmail = getGmailService();
            // Use batch client for consistency, even for single message
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, List.of(messageId));

            if (result.hasFailures()) {
                String errorMessage = result.getFailedOperations().get(messageId);
                logger.error("Failed to delete message {}: {}", messageId, errorMessage);
            } else {
                logger.debug("Successfully deleted message: {}", messageId);
            }

            return result;
        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    @Override
    public BulkOperationResult deleteMessagesByFilterCriteria(String userId, String query) throws IOException {
        try {
            var gmail = getGmailService();

            // 1. Find all messages matching the query
            var messages = gmail.users().messages()
                    .list(userId)
                    .setQ(query)
                    .setMaxResults(properties.gmailApi().batchDeleteMaxResults())
                    .execute()
                    .getMessages();

            if (messages == null || messages.isEmpty()) {
                logger.info("Found 0 matching messages");
                // Return empty result with no operations
                BulkOperationResult emptyResult = new BulkOperationResult("DELETE");
                emptyResult.markCompleted();
                return emptyResult;
            }
            logger.info("Found {} matching messages", messages.size());

            // 2. Extract message IDs for batch deletion
            List<String> messageIds = messages.stream()
                    .map(com.google.api.services.gmail.model.Message::getId)
                    .toList();

            // 3. Use batch delete - this eliminates the two-phase trash+delete approach
            // and reduces API calls from 2N to approximately N/100 batch requests
            logger.info("Executing batch delete operation for {} messages", messageIds.size());
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            // 4. Log the results
            logger.info("Batch delete completed: {} successful, {} failed out of {} total",
                       result.getSuccessCount(), result.getFailureCount(), result.getTotalOperations());

            if (result.hasFailures()) {
                logger.warn("Some deletions failed. Failed message IDs: {}",
                           String.join(", ", result.getFailedOperations().keySet()));
                // For bulk operations, we don't throw an exception for partial failures
                // The caller can check the result if needed
            }

            return result;
        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    @Override
    public Map<String, String> getLabels(String userId) throws IOException {
        try {
            Gmail gmail = getGmailService();
            ListLabelsResponse response = gmail.users().labels().list(userId).execute();

            Map<String, String> labels = new HashMap<>();
            for (Label label : response.getLabels()) {
                labels.put(label.getName().toUpperCase(), label.getId());
            }
            return labels;
        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    private static List<String> getLabelIdList(Map<String, String> labelsMap, List<String> labelNameList) {
        List<String> labelIdList = new ArrayList<>();
        for (String labelName : labelNameList) {
            if (labelsMap.containsKey(labelName.toUpperCase())) {
                labelIdList.add(labelsMap.get(labelName.toUpperCase()));
            }
        }
        return labelIdList;
    }

    @Override
    public BulkOperationResult modifyMessagesLabels(String userId, List<String> labelsToAdd, List<String> labelsToRemove, String query) throws IOException {
        try {
            var gmail = getGmailService();

            Map<String, String> labelsMap = getLabels(userId);
            List<String> labelIdsToAdd = getLabelIdList(labelsMap, labelsToAdd);
            List<String> labelIdsToRemove = getLabelIdList(labelsMap, labelsToRemove);

            ModifyMessageRequest mods = new ModifyMessageRequest().setAddLabelIds(labelIdsToAdd).setRemoveLabelIds(labelIdsToRemove);

            List<Message> messages = gmail.users().messages()
                    .list(userId)
                    .setQ(query)
                    .execute()
                    .getMessages();

            if (messages == null || messages.isEmpty()) {
                logger.info("Found 0 matching messages for label modification");
                BulkOperationResult emptyResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_MODIFY);
                emptyResult.markCompleted();
                return emptyResult;
            }

            logger.info("Found {} matching messages for label modification", messages.size());

            // Extract message IDs for batch operation
            List<String> messageIds = messages.stream()
                    .map(Message::getId)
                    .toList();

            // Use batch modify labels operation
            BulkOperationResult result = gmailBatchClient.batchModifyLabels(gmail, userId, messageIds, mods);

            logger.info("Batch label modification completed: {} successful, {} failed out of {} total",
                       result.getSuccessCount(), result.getFailureCount(), result.getTotalOperations());

            if (result.hasFailures()) {
                logger.warn("Some label modifications failed. Failed message IDs: {}",
                           String.join(", ", result.getFailedOperations().keySet()));
            }

            return result;

        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    @Override
    public String getMessageBody(String userId, String messageId) throws IOException {
        try {
            Gmail gmail = getGmailService();
            Message message = gmail.users().messages().get(userId, messageId).execute();
            logger.info("Message retrieved: messageId={}", message.getId());
            return getMessageBodyFromParts(message.getPayload().getParts()); // Call helper function

        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    private String getMessageBodyFromParts(List<MessagePart> parts) {
        if (parts == null) {
            return ""; // No parts found
        }

        // Prioritize HTML content if there is any
        for (MessagePart part : parts) {
            if (part.getBody() != null && part.getBody().getData() != null) {
                String mimeType = part.getMimeType();
                if (properties.gmailApi().messageProcessing().mimeTypes().html().equals(mimeType)) {
                    String data = new String(Base64.getUrlDecoder().decode(part.getBody().getData()));
                    logger.info("Message is in text/html");
                    return data;
                }
            }
        }

        // Fallback to plain text content if HTML not found
        for (MessagePart part : parts) {
            if (part.getBody() != null && part.getBody().getData() != null) {
                String mimeType = part.getMimeType();
                if (properties.gmailApi().messageProcessing().mimeTypes().plain().equals(mimeType)) {
                    String data = new String(Base64.getUrlDecoder().decode(part.getBody().getData()));
                    logger.info("Message is in text/plain");
                    return data;
                }
            }
        }

        // Recursively check nested parts
        for (MessagePart part : parts) {
            String body = getMessageBodyFromParts(part.getParts());
            if (!body.isEmpty()) {
                return body;
            }
        }

        return ""; // No message body found
    }

    @Override
    public BulkOperationResult markMessageAsRead(String userId, String messageId) throws IOException {
        try {
            var gmail = getGmailService();
            // Remove the UNREAD label from the message
            String unreadLabel = properties.gmailApi().messageProcessing().labels().unread();
            var mods = new com.google.api.services.gmail.model.ModifyMessageRequest().setRemoveLabelIds(List.of(unreadLabel));

            BulkOperationResult result = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_MODIFY);
            try {
                gmail.users().messages().modify(userId, messageId, mods).execute();
                result.addSuccess(messageId);
                logger.debug("Successfully marked message {} as read", messageId);
            } catch (IOException e) {
                result.addFailure(messageId, e.getMessage());
                logger.error("Failed to mark message {} as read: {}", messageId, e.getMessage());
            }
            result.markCompleted();
            return result;
        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    // -------------------------------------------------------------------------
    // Send / Draft — T038 (createDraft), T045 (sendMessage), T052 (sendDraft)
    // all fully implemented below
    // -------------------------------------------------------------------------

    /**
     * Sends an email message immediately via {@code users.messages.send}. The
     * MimeMessage is serialized to its raw RFC 5322 byte form, base64url-encoded
     * (RFC 4648 §5), and submitted to the Gmail API. The resulting {@link Message}
     * is mapped to a {@link SentMessageResult} via {@link GmailMessageMapper}.
     *
     * <p>Delegates to {@link #sendMessage(String, MimeMessage, String)} with a
     * {@code null} threadId, preserving backward compatibility.</p>
     *
     * @param userId      the Gmail user identifier; typically {@code "me"}
     * @param mimeMessage the fully-constructed RFC 5322 message to send
     * @return a {@link SentMessageResult} containing the Gmail-assigned message
     *         and thread identifiers
     * @throws IOException if serialization, network I/O, or a non-JSON Gmail error occurs
     */
    @Override
    public SentMessageResult sendMessage(String userId, MimeMessage mimeMessage) throws IOException {
        return sendMessage(userId, mimeMessage, null);
    }

    /**
     * Sends an email message immediately via {@code users.messages.send}, optionally
     * placing it into the specified Gmail thread. The MimeMessage is serialized to its
     * raw RFC 5322 byte form, base64url-encoded (RFC 4648 §5), and submitted to the
     * Gmail API.
     *
     * <p>When {@code threadId} is non-null, it is applied to the Gmail API
     * {@code Message} object via {@code message.setThreadId(threadId)} before the
     * send call. This is the Gmail API envelope field that controls thread placement
     * in Gmail's UI (research.md Decision 2 — distinct from the RFC 5322
     * {@code In-Reply-To} / {@code References} MIME headers, which are set in the
     * MimeMessage by the builder layer).</p>
     *
     * <p>Any {@link GoogleJsonResponseException} from the Gmail API is mapped to an
     * appropriate project exception via {@link #mapGmailSendError(GoogleJsonResponseException)}.
     * Other {@link IOException}s propagate per the interface declaration.</p>
     *
     * @param userId      the Gmail user identifier; typically {@code "me"}
     * @param mimeMessage the fully-constructed RFC 5322 message to send
     * @param threadId    the resolved Gmail thread ID to set on the outgoing message;
     *                    {@code null} means no thread is specified (new thread)
     * @return a {@link SentMessageResult} containing the Gmail-assigned message
     *         and thread identifiers
     * @throws IOException if serialization, network I/O, or a non-JSON Gmail error occurs
     */
    @Override
    public SentMessageResult sendMessage(String userId, MimeMessage mimeMessage, String threadId) throws IOException {
        try {
            Gmail gmail = getGmailService();

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            mimeMessage.writeTo(buffer);
            String encodedRaw = Base64.getUrlEncoder().encodeToString(buffer.toByteArray());

            Message message = new Message().setRaw(encodedRaw);
            if (threadId != null) {
                message.setThreadId(threadId);
            }

            Message sentMessage = gmail.users().messages().send(userId, message).execute();
            logger.info("Message sent successfully for userId={}, messageId={}, threadId={}",
                    userId, sentMessage.getId(), sentMessage.getThreadId());

            return gmailMessageMapper.toSentMessageResult(sentMessage);

        } catch (GoogleJsonResponseException e) {
            logger.error("Gmail API error sending message for userId={}: status={}, message={}",
                    userId, e.getStatusCode(), e.getMessage());
            throw mapGmailSendError(e);
        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        } catch (jakarta.mail.MessagingException e) {
            throw new IOException("Failed to serialize MimeMessage for send", e);
        }
    }

    /**
     * Stages a draft for later send/edit. The MimeMessage is serialized to its raw
     * RFC 5322 byte form, base64url-encoded (RFC 4648 §5), and submitted to the
     * Gmail API via {@code users.drafts.create}.
     *
     * <p>Delegates to {@link #createDraft(String, MimeMessage, String)} with a
     * {@code null} threadId, preserving backward compatibility.</p>
     *
     * @param userId      the Gmail user identifier; typically {@code "me"}
     * @param mimeMessage the fully-constructed RFC 5322 message to stage as a draft
     * @return a {@link DraftCreationResult} containing the Gmail-assigned draft,
     *         message, and thread identifiers
     * @throws IOException if serialization, network I/O, or a non-JSON Gmail error occurs
     */
    @Override
    public DraftCreationResult createDraft(String userId, MimeMessage mimeMessage) throws IOException {
        return createDraft(userId, mimeMessage, null);
    }

    /**
     * Stages a draft for later send/edit, optionally associating it with the specified
     * Gmail thread. The MimeMessage is serialized to its raw RFC 5322 byte form,
     * base64url-encoded (RFC 4648 §5), and submitted to the Gmail API via
     * {@code users.drafts.create}.
     *
     * <p>When {@code threadId} is non-null, it is applied to the Gmail API
     * {@code Message} object via {@code message.setThreadId(threadId)} before the
     * draft-creation call. This is the Gmail API envelope field that controls thread
     * placement (research.md Decision 2).</p>
     *
     * <p>Any {@link GoogleJsonResponseException} from the Gmail API is mapped to an
     * appropriate project exception via {@link #mapGmailSendError(GoogleJsonResponseException)}.
     * Other {@link IOException}s propagate per the interface declaration.</p>
     *
     * @param userId      the Gmail user identifier; typically {@code "me"}
     * @param mimeMessage the fully-constructed RFC 5322 message to stage as a draft
     * @param threadId    the resolved Gmail thread ID to associate with the draft;
     *                    {@code null} means no thread association
     * @return a {@link DraftCreationResult} containing the Gmail-assigned draft,
     *         message, and thread identifiers
     * @throws IOException if serialization, network I/O, or a non-JSON Gmail error occurs
     */
    @Override
    public DraftCreationResult createDraft(String userId, MimeMessage mimeMessage, String threadId) throws IOException {
        try {
            Gmail gmail = getGmailService();

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            mimeMessage.writeTo(buffer);
            String encodedRaw = Base64.getUrlEncoder().encodeToString(buffer.toByteArray());

            Message rawMessage = new Message().setRaw(encodedRaw);
            if (threadId != null) {
                rawMessage.setThreadId(threadId);
            }
            Draft draft = new Draft().setMessage(rawMessage);

            Draft createdDraft = gmail.users().drafts().create(userId, draft).execute();
            logger.info("Draft created successfully for userId={}, draftId={}", userId, createdDraft.getId());

            return gmailMessageMapper.toDraftCreationResult(createdDraft);

        } catch (GoogleJsonResponseException e) {
            logger.error("Gmail API error creating draft for userId={}: status={}, message={}",
                    userId, e.getStatusCode(), e.getMessage());
            throw mapGmailSendError(e);
        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        } catch (jakarta.mail.MessagingException e) {
            throw new IOException("Failed to serialize MimeMessage for draft creation", e);
        }
    }

    /**
     * Sends a previously-created draft by identifier via {@code users.drafts.send}.
     * A {@link Draft} with only the {@code id} field set is submitted; Gmail resolves
     * the full message content from the draft store. The resulting {@link Message}
     * is mapped to a {@link SentMessageResult} via {@link GmailMessageMapper}.
     *
     * <p>This operation is naturally idempotent at the resource level: a successful
     * send removes the draft. A subsequent retry returns a 404 from Gmail, which
     * {@link #mapGmailSendError(GoogleJsonResponseException)} maps to
     * {@link ResourceNotFoundException} (HTTP 404).</p>
     *
     * <p>Any {@link GoogleJsonResponseException} is mapped via
     * {@link #mapGmailSendError(GoogleJsonResponseException)}. Other
     * {@link IOException}s propagate per the interface declaration.</p>
     *
     * @param userId  the Gmail user identifier; typically {@code "me"}
     * @param draftId the Gmail-assigned draft identifier
     * @return a {@link SentMessageResult} containing the Gmail-assigned message
     *         and thread identifiers
     * @throws IOException if network I/O or a non-JSON Gmail error occurs
     */
    @Override
    public SentMessageResult sendDraft(String userId, String draftId) throws IOException {
        try {
            Gmail gmail = getGmailService();

            Draft draft = new Draft().setId(draftId);

            Message sentMessage = gmail.users().drafts().send(userId, draft).execute();
            logger.info("Draft sent successfully for userId={}, draftId={}, messageId={}",
                    userId, draftId, sentMessage.getId());

            return gmailMessageMapper.toSentMessageResult(sentMessage);

        } catch (GoogleJsonResponseException e) {
            logger.error("Gmail API error sending draft for userId={}, draftId={}: status={}, message={}",
                    userId, draftId, e.getStatusCode(), e.getMessage());
            throw mapGmailSendError(e);
        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    // -------------------------------------------------------------------------
    // Private error-mapping helper (research.md Decision 10)
    // -------------------------------------------------------------------------

    /**
     * Maps a {@link GoogleJsonResponseException} from a Gmail send or draft-send
     * call to the appropriate project exception.
     *
     * <p>Error reasons are extracted from
     * {@code e.getDetails().getErrors().get(0).getReason()} when available.
     * The mapping follows research.md Decision 10:</p>
     *
     * <ul>
     *   <li>{@code invalidArgument} → {@link InvalidRecipientException} (HTTP 422)</li>
     *   <li>{@code insufficientPermissions} → {@link AuthorizationException} (HTTP 403)</li>
     *   <li>{@code dailySendLimitExceeded} → {@link RateLimitException}
     *       with {@code retryAfterSeconds=86400} (HTTP 429).
     *       <strong>CRITICAL: this path MUST NOT route through any retry-with-backoff
     *       logic.</strong> The limit resets at the next calendar day; exponential
     *       backoff is useless and wastes quota.</li>
     *   <li>{@code forbidden} (unverified send-as identity) → {@link AuthorizationException}
     *       (HTTP 403)</li>
     *   <li>{@code messageTooLarge} → {@link MessageTooLargeException} (HTTP 413)</li>
     *   <li>HTTP 404 (draft does not exist — drafts.send only) →
     *       {@link ResourceNotFoundException} (HTTP 404)</li>
     *   <li>Any other 4xx / 5xx → {@link MessageSendException} (HTTP 502)</li>
     * </ul>
     *
     * @param e the exception thrown by the Gmail API client
     * @return a project exception whose type matches the Gmail error reason
     */
    private RuntimeException mapGmailSendError(GoogleJsonResponseException e) {
        int statusCode = e.getStatusCode();

        // 404 always maps to ResourceNotFoundException (draft not found / already sent)
        if (statusCode == 404) {
            return new ResourceNotFoundException(
                    "Draft not found or already sent/discarded", e);
        }

        // Extract the error reason from the first error detail when present
        String reason = null;
        if (e.getDetails() != null
                && e.getDetails().getErrors() != null
                && !e.getDetails().getErrors().isEmpty()) {
            reason = e.getDetails().getErrors().get(0).getReason();
        }

        if (reason == null) {
            return new MessageSendException("Gmail send failed: HTTP " + statusCode, e);
        }

        return switch (reason) {
            case "invalidArgument" ->
                    new InvalidRecipientException(
                            "Gmail rejected one or more recipient addresses or message fields", e);

            case "insufficientPermissions" ->
                    new AuthorizationException(
                            "Insufficient Gmail permissions to send mail", e);

            // CRITICAL: dailySendLimitExceeded MUST NOT go through any retry-with-backoff
            // path. The daily send limit resets at the next calendar day; retrying within
            // the same day wastes quota and does not resolve the limit. retryAfterSeconds
            // is set to 86400 (24 hours) to inform the caller of the reset window.
            case "dailySendLimitExceeded" ->
                    new RateLimitException(
                            "Daily Gmail send limit reached; retry after the next-day reset",
                            e,
                            86400L);

            case "forbidden" ->
                    new AuthorizationException(
                            "Gmail rejected send: forbidden (unverified send-as identity or account restricted)", e);

            case "messageTooLarge" ->
                    new MessageTooLargeException(
                            "Message exceeds Gmail's maximum allowed size", e);

            default ->
                    new MessageSendException(
                            "Gmail send failed with reason: " + reason, e);
        };
    }

    /**
     * Fetches the RFC 5322 {@code Message-ID} header and {@code threadId} from the
     * specified message using the Gmail metadata-only format (quota: ~5 units).
     *
     * <p>Only the {@code Message-ID} header is requested via
     * {@code setMetadataHeaders(List.of("Message-ID"))}, which minimises response
     * payload size. The {@code threadId} is read from the top-level
     * {@code Message.getThreadId()} field on the same response — no second API
     * call is needed.</p>
     *
     * <p>Error mapping (per research.md Decision 3 and FR-008 / FR-008a / FR-008c):</p>
     * <ul>
     *   <li>Gmail 404 → {@link OriginalMessageNotFoundException} (HTTP 422)</li>
     *   <li>Gmail 403 → {@link AuthorizationException} (HTTP 403, FR-008c)</li>
     *   <li>Gmail 429 → {@link RateLimitException} with {@code Retry-After} seconds
     *       extracted from the response (HTTP 429, FR-008a)</li>
     *   <li>Gmail 5xx → {@link GmailApiException} (HTTP 502, FR-008a)</li>
     *   <li>{@link SocketTimeoutException} / transport {@link IOException} →
     *       {@link ServiceUnavailableException} (HTTP 503, FR-008a)</li>
     * </ul>
     *
     * @param userId    the Gmail user identifier; typically {@code "me"}
     * @param messageId the Gmail short message ID of the message being replied to
     * @return an {@link OriginalMessageLookup} containing the RFC 5322 Message-ID
     *         and the Gmail thread ID
     * @throws IOException if a transport-level I/O error occurs
     */
    @Override
    public OriginalMessageLookup getMessageHeaders(String userId, String messageId) throws IOException {
        try {
            Gmail gmail = getGmailService();

            Message response = gmail.users().messages()
                    .get(userId, messageId)
                    .setFormat("metadata")
                    .setMetadataHeaders(List.of("Message-ID"))
                    .execute();

            // Null-check payload before calling getHeaders() (research.md Open follow-ups:
            // "null-safety on getPayload()"). Draft messages and some internally-generated
            // messages may return a null payload in metadata format — treat as not found.
            MessagePart payload = response.getPayload();
            if (payload == null) {
                throw new OriginalMessageNotFoundException(
                        "Original message has no payload (messageId=" + messageId
                                + "); cannot extract Message-ID for threading");
            }

            // Case-insensitive header name match per research.md Decision 11 — Gmail
            // typically capitalises as "Message-ID" but RFC 5322 header names are
            // case-insensitive and may change across API versions.
            String rfcMessageId = null;
            List<MessagePartHeader> headers = payload.getHeaders();
            if (headers != null) {
                for (MessagePartHeader header : headers) {
                    if ("message-id".equalsIgnoreCase(header.getName())) {
                        rfcMessageId = header.getValue();
                        break;
                    }
                }
            }

            if (rfcMessageId == null) {
                // The message exists but has no RFC 5322 Message-ID (uncommon; possible
                // for internally-generated Gmail messages). Fail-closed per spec Q1 —
                // sending a reply without In-Reply-To would break the threading contract.
                throw new OriginalMessageNotFoundException(
                        "Original message has no Message-ID header (messageId=" + messageId
                                + "); cannot construct In-Reply-To / References for threading");
            }

            logger.debug("Fetched message headers for threading: messageId={}", messageId);
            return new OriginalMessageLookup(messageId, response.getThreadId(), rfcMessageId);

        } catch (GoogleJsonResponseException e) {
            logger.error("Gmail API error fetching message headers for userId={}, messageId={}: status={}, message={}",
                    userId, messageId, e.getStatusCode(), e.getMessage());
            throw mapGmailLookupError(e, messageId);
        } catch (SocketTimeoutException e) {
            // SocketTimeoutException is a subtype of IOException — intercept it explicitly
            // before the generic IOException catch below so it maps to ServiceUnavailableException
            // rather than propagating as a raw IOException (FR-008a).
            throw new ServiceUnavailableException(
                    "Timeout fetching original message headers (messageId=" + messageId
                            + "); Gmail API did not respond in time",
                    e);
        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    // -------------------------------------------------------------------------
    // Private error-mapping helper for getMessageHeaders (T033)
    // -------------------------------------------------------------------------

    /**
     * Maps a {@link GoogleJsonResponseException} from the {@code users.messages.get}
     * metadata call to the appropriate project exception.
     *
     * <p>This is a sibling to {@link #mapGmailSendError(GoogleJsonResponseException)}
     * but with a different 404 semantics: here, 404 means "the original message being
     * replied to does not exist", which maps to {@link OriginalMessageNotFoundException}
     * (HTTP 422) rather than {@link ResourceNotFoundException} (HTTP 404).</p>
     *
     * <ul>
     *   <li>404 → {@link OriginalMessageNotFoundException} (HTTP 422, FR-008)</li>
     *   <li>403 → {@link AuthorizationException} (HTTP 403, FR-008c)</li>
     *   <li>429 → {@link RateLimitException} with {@code Retry-After} seconds
     *       extracted from the response headers (HTTP 429, FR-008a)</li>
     *   <li>5xx → {@link GmailApiException} (HTTP 502, FR-008a)</li>
     *   <li>any other status → {@link GmailApiException} (HTTP 502)</li>
     * </ul>
     *
     * @param e         the exception thrown by the Gmail API client during lookup
     * @param messageId the Gmail short message ID that was being looked up (for error messages)
     * @return a project exception whose type matches the Gmail error status
     */
    private RuntimeException mapGmailLookupError(GoogleJsonResponseException e, String messageId) {
        int statusCode = e.getStatusCode();

        if (statusCode == 404) {
            return new OriginalMessageNotFoundException(
                    "Original message not found in authenticated user's Gmail account "
                            + "(messageId=" + messageId + "); cannot thread reply",
                    e);
        }

        if (statusCode == 403) {
            return new AuthorizationException(
                    "Insufficient Gmail permissions to read original message "
                            + "(messageId=" + messageId + ")",
                    e);
        }

        if (statusCode == 429) {
            // Extract Retry-After seconds from the response headers when present.
            // Gmail's 429 response may include a Retry-After header; fall back to 60s
            // (a conservative default) if absent.
            long retryAfterSeconds = 60L;
            if (e.getHeaders() != null) {
                String retryAfterHeader = e.getHeaders().getFirstHeaderStringValue("Retry-After");
                if (retryAfterHeader != null) {
                    try {
                        retryAfterSeconds = Long.parseLong(retryAfterHeader.trim());
                    } catch (NumberFormatException ignored) {
                        // Malformed Retry-After header — keep the 60s default
                    }
                }
            }
            return new RateLimitException(
                    "Gmail API rate limit exceeded during original-message lookup "
                            + "(messageId=" + messageId + ")",
                    e,
                    retryAfterSeconds);
        }

        if (statusCode >= 500) {
            return new GmailApiException(
                    "Gmail API server error during original-message lookup "
                            + "(messageId=" + messageId + "): HTTP " + statusCode,
                    e);
        }

        // Any remaining 4xx (e.g., 400 invalid request) or unexpected status
        return new GmailApiException(
                "Gmail API error during original-message lookup "
                        + "(messageId=" + messageId + "): HTTP " + statusCode,
                e);
    }
}
