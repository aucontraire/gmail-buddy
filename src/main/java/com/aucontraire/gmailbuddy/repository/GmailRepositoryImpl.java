package com.aucontraire.gmailbuddy.repository;

import com.aucontraire.gmailbuddy.client.GmailClient;
import com.aucontraire.gmailbuddy.client.GmailBatchClient;
import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import com.aucontraire.gmailbuddy.dto.FilterCriteriaDTO;
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
import com.aucontraire.gmailbuddy.service.DraftDetailResult;
import com.aucontraire.gmailbuddy.service.DraftListResult;
import com.aucontraire.gmailbuddy.service.GmailQueryBuilder;
import com.aucontraire.gmailbuddy.service.LabelDetailResult;
import com.aucontraire.gmailbuddy.service.LabelListResult;
import com.aucontraire.gmailbuddy.service.MessageDetailResult;
import com.aucontraire.gmailbuddy.service.SentMessageResult;
import com.aucontraire.gmailbuddy.service.ThreadDetailResult;
import com.aucontraire.gmailbuddy.service.ThreadListResult;
import com.aucontraire.gmailbuddy.service.AttachmentListResult;
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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.Base64;

@Component
public class GmailRepositoryImpl implements GmailRepository {

    private final GmailClient gmailClient;
    private final GmailBatchClient gmailBatchClient;
    private final TokenProvider tokenProvider;
    private final GmailBuddyProperties properties;
    private final GmailMessageMapper gmailMessageMapper;
    private final GmailQueryBuilder gmailQueryBuilder;
    private final Logger logger = LoggerFactory.getLogger(GmailRepositoryImpl.class);

    @Autowired
    public GmailRepositoryImpl(GmailClient gmailClient, GmailBatchClient gmailBatchClient,
                              TokenProvider tokenProvider, GmailBuddyProperties properties,
                              GmailMessageMapper gmailMessageMapper,
                              GmailQueryBuilder gmailQueryBuilder) {
        this.gmailClient = gmailClient;
        this.gmailBatchClient = gmailBatchClient;
        this.tokenProvider = tokenProvider;
        this.properties = properties;
        this.gmailMessageMapper = gmailMessageMapper;
        this.gmailQueryBuilder = gmailQueryBuilder;
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
    // Feature 004 — US1: Thread list + detail (T023)
    // -------------------------------------------------------------------------

    /**
     * Returns a paginated list of thread summaries matching the given filter criteria.
     * Calls {@code users.threads.list} once; no per-item enrichment (flat 10-unit quota cost
     * per Clarifications Q1). Each stub thread is mapped to a {@link com.aucontraire.gmailbuddy.dto.response.ThreadSummary}
     * via {@link GmailMessageMapper#toThreadSummary}.
     *
     * @param userId         the Gmail user identifier; typically "me"
     * @param filterCriteria filter criteria from query parameters; null for no filter
     * @param pageToken      opaque token from a prior response, or null for the first page
     * @param limit          maximum number of items to return (1–100)
     * @return a {@link ThreadListResult} with thread summaries and pagination state
     * @throws IOException on Gmail API communication failure
     */
    @Override
    public ThreadListResult listThreads(String userId, FilterCriteriaDTO filterCriteria,
                                        String pageToken, int limit) throws IOException {
        try {
            Gmail gmail = getGmailService();

            // Build Gmail query string from filter criteria
            String query = buildQueryFromFilter(filterCriteria);

            Gmail.Users.Threads.List listRequest = gmail.users().threads()
                    .list(userId)
                    .setMaxResults((long) limit);

            if (query != null && !query.isBlank()) {
                listRequest.setQ(query);
            }
            if (pageToken != null && !pageToken.isBlank()) {
                listRequest.setPageToken(pageToken);
            }

            com.google.api.services.gmail.model.ListThreadsResponse response = listRequest.execute();

            java.util.List<com.google.api.services.gmail.model.Thread> stubs = response.getThreads();
            String nextPageToken = response.getNextPageToken();
            Integer totalCount = response.getResultSizeEstimate() != null
                    ? response.getResultSizeEstimate().intValue() : null;

            java.util.List<com.aucontraire.gmailbuddy.dto.response.ThreadSummary> summaries;
            if (stubs == null || stubs.isEmpty()) {
                summaries = List.of();
            } else {
                summaries = stubs.stream()
                        .map(gmailMessageMapper::toThreadSummary)
                        .toList();
            }

            logger.info("Listed threads: op=listThreads, count={}", summaries.size());
            return new ThreadListResult(summaries, nextPageToken, totalCount);

        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    /**
     * Returns the full content of the specified thread including all nested messages.
     * Calls {@code users.threads.get} with format=FULL. Maps via
     * {@link GmailMessageMapper#toThreadDetailResult}. On Gmail 404, throws
     * {@link ResourceNotFoundException}.
     *
     * @param userId   the Gmail user identifier; typically "me"
     * @param threadId the Gmail thread identifier
     * @return a {@link ThreadDetailResult} with all messages and union label set
     * @throws ResourceNotFoundException if the thread does not exist (Gmail 404)
     * @throws IOException on Gmail API communication failure
     */
    @Override
    public ThreadDetailResult getThread(String userId, String threadId) throws IOException {
        try {
            Gmail gmail = getGmailService();

            com.google.api.services.gmail.model.Thread thread = gmail.users().threads()
                    .get(userId, threadId)
                    .setFormat("FULL")
                    .execute();

            logger.info("Got thread: op=getThread, threadId={}, messageCount={}",
                    threadId, thread.getMessages() != null ? thread.getMessages().size() : 0);
            return gmailMessageMapper.toThreadDetailResult(thread);

        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 404) {
                throw new ResourceNotFoundException(
                        "Thread not found: " + threadId, e);
            }
            logger.error("Gmail API error getting thread threadId={}: status={}, message={}",
                    threadId, e.getStatusCode(), e.getMessage());
            throw e;
        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    /**
     * Returns the full structured detail of the specified message.
     *
     * <p>When {@code format} is {@code "full"}, calls {@code users.messages.get} with
     * {@code format=FULL} (10 quota units). When {@code format} is {@code "metadata"},
     * calls with {@code format=METADATA} and {@link GmailMessageMapper#WHITELISTED_HEADERS_LIST}
     * to limit Gmail's response to the 9 whitelisted headers (5 quota units, per
     * research.md Decision 1). Maps the result via {@link GmailMessageMapper#toMessageDetailResult}.
     * On Gmail 404 throws {@link ResourceNotFoundException}.</p>
     *
     * @param userId    the Gmail user identifier; typically "me"
     * @param messageId the Gmail message identifier
     * @param format    {@code "full"} for body + headers (default); {@code "metadata"} for headers only
     * @return a {@link MessageDetailResult} with all fields; body is null when format=metadata
     * @throws ResourceNotFoundException if the message does not exist (Gmail 404)
     * @throws IOException on Gmail API communication failure
     */
    @Override
    public MessageDetailResult getMessageDetail(String userId, String messageId, String format) throws IOException {
        try {
            Gmail gmail = getGmailService();

            boolean metadataOnly = "metadata".equalsIgnoreCase(format);

            Gmail.Users.Messages.Get getRequest = gmail.users().messages()
                    .get(userId, messageId);

            if (metadataOnly) {
                getRequest.setFormat("METADATA")
                        .setMetadataHeaders(GmailMessageMapper.WHITELISTED_HEADERS_LIST);
            } else {
                getRequest.setFormat("FULL");
            }

            Message message = getRequest.execute();

            MessageDetailResult result = gmailMessageMapper.toMessageDetailResult(message, format);
            logger.info("Got message detail: op=getMessageDetail, messageId={}, format={}, attachmentCount={}",
                    messageId, format, result.attachments().size());
            return result;

        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 404) {
                throw new ResourceNotFoundException(
                        "Message not found: " + messageId, e);
            }
            logger.error("Gmail API error getting message detail messageId={}: status={}, message={}",
                    messageId, e.getStatusCode(), e.getMessage());
            throw e;
        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    // -------------------------------------------------------------------------
    // Feature 004 — US3: Label list + detail (T050)
    // -------------------------------------------------------------------------

    /**
     * Returns all visible labels for the authenticated user.
     * Calls {@code users.labels.list} once; no per-item enrichment.
     * Returns a {@link LabelListResult} with all labels and their totalCount.
     *
     * @param userId the Gmail user identifier; typically "me"
     * @return a LabelListResult with all labels; labels list is never null
     * @throws IOException on Gmail API communication failure
     */
    @Override
    public LabelListResult listLabels(String userId) throws IOException {
        try {
            Gmail gmail = getGmailService();
            ListLabelsResponse response = gmail.users().labels().list(userId).execute();

            List<Label> rawLabels = response.getLabels();
            if (rawLabels == null || rawLabels.isEmpty()) {
                return new LabelListResult(List.of(), 0);
            }

            List<com.aucontraire.gmailbuddy.dto.response.LabelSummary> summaries = rawLabels.stream()
                    .map(gmailMessageMapper::toLabelSummary)
                    .toList();

            logger.info("Listed labels: op=listLabels, count={}", summaries.size());
            return new LabelListResult(summaries, summaries.size());

        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    /**
     * Returns the full detail of the specified label including counts and color.
     * Calls {@code users.labels.get} with the given labelId.
     * On 404, throws {@link ResourceNotFoundException}.
     *
     * @param userId  the Gmail user identifier; typically "me"
     * @param labelId the Gmail label identifier
     * @return a LabelDetailResult with all fields populated
     * @throws ResourceNotFoundException if the label does not exist (Gmail 404)
     * @throws IOException on Gmail API communication failure
     */
    @Override
    public LabelDetailResult getLabel(String userId, String labelId) throws IOException {
        try {
            Gmail gmail = getGmailService();
            Label label = gmail.users().labels().get(userId, labelId).execute();

            LabelDetailResult result = gmailMessageMapper.toLabelDetailResult(label);
            logger.info("Got label: op=getLabel, labelId={}", labelId);
            return result;

        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 404) {
                throw new ResourceNotFoundException("Label not found: " + labelId, e);
            }
            logger.error("Gmail API error getting label labelId={}: status={}, message={}",
                    labelId, e.getStatusCode(), e.getMessage());
            throw e;
        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    /**
     * Builds a Gmail query string from the given filter criteria DTO.
     * Mirrors the query-building logic in {@code GmailService.buildQuery(FilterCriteriaDTO)}.
     */
    private String buildQueryFromFilter(FilterCriteriaDTO filterCriteria) {
        if (filterCriteria == null) {
            return null;
        }
        String from = gmailQueryBuilder.from(filterCriteria.getFrom());
        String to = filterCriteria.getTo() != null ? gmailQueryBuilder.to(filterCriteria.getTo()) : "";
        String subject = filterCriteria.getSubject() != null ? gmailQueryBuilder.subject(filterCriteria.getSubject()) : "";
        String hasAttachment = filterCriteria.getHasAttachment() != null
                ? gmailQueryBuilder.hasAttachment(filterCriteria.getHasAttachment()) : "";
        String additionalQuery = filterCriteria.getQuery() != null ? gmailQueryBuilder.query(filterCriteria.getQuery()) : "";
        String negatedQuery = filterCriteria.getNegatedQuery() != null ? gmailQueryBuilder.negatedQuery(filterCriteria.getNegatedQuery()) : "";
        return gmailQueryBuilder.build(from, to, subject, hasAttachment, additionalQuery, negatedQuery);
    }

    // -------------------------------------------------------------------------
    // Draft CRUD — listDrafts, getDraft, deleteDraft, updateDraft
    // -------------------------------------------------------------------------

    /**
     * Returns a paginated list of drafts. For each draft ID in the
     * {@code users.drafts.list} response, fetches the full draft via
     * {@code users.drafts.get(format=FULL)} and maps it to a
     * {@link DraftDetailResult} via {@link GmailMessageMapper#toDraftDetailResult}.
     *
     * @param userId    the Gmail user identifier; typically {@code "me"}
     * @param pageToken opaque pagination token, or null for the first page
     * @param limit     maximum number of items to return (1–50)
     * @return a {@link DraftListResult} with enriched drafts and pagination state
     * @throws IOException on Gmail API communication failure
     */
    @Override
    public DraftListResult listDrafts(String userId, String pageToken, int limit) throws IOException {
        try {
            Gmail gmail = getGmailService();

            com.google.api.services.gmail.Gmail.Users.Drafts.List listRequest =
                    gmail.users().drafts().list(userId)
                            .setMaxResults((long) limit);
            if (pageToken != null) {
                listRequest.setPageToken(pageToken);
            }

            com.google.api.services.gmail.model.ListDraftsResponse listResponse = listRequest.execute();

            java.util.List<com.google.api.services.gmail.model.Draft> draftStubs =
                    listResponse.getDrafts();
            String nextPageToken = listResponse.getNextPageToken();
            Integer totalCount = listResponse.getResultSizeEstimate() != null
                    ? listResponse.getResultSizeEstimate().intValue() : null;

            java.util.List<DraftDetailResult> drafts = new java.util.ArrayList<>();
            if (draftStubs != null) {
                for (com.google.api.services.gmail.model.Draft stub : draftStubs) {
                    com.google.api.services.gmail.model.Draft fullDraft =
                            gmail.users().drafts().get(userId, stub.getId())
                                    .setFormat("full")
                                    .execute();
                    drafts.add(gmailMessageMapper.toDraftDetailResult(fullDraft));
                }
            }

            logger.info("Listed drafts: op=list, count={}", drafts.size());
            return new DraftListResult(
                    drafts.isEmpty() ? java.util.List.of() : java.util.List.copyOf(drafts),
                    nextPageToken,
                    totalCount
            );

        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    /**
     * Fetches the full content of a single draft via
     * {@code users.drafts.get(format=FULL)} and maps it to a
     * {@link DraftDetailResult}.
     *
     * @param userId  the Gmail user identifier; typically {@code "me"}
     * @param draftId the Gmail draft identifier
     * @return a {@link DraftDetailResult} with all fields populated
     * @throws ResourceNotFoundException if the draft does not exist (Gmail 404)
     * @throws IOException on Gmail API communication failure
     */
    @Override
    public DraftDetailResult getDraft(String userId, String draftId) throws IOException {
        try {
            Gmail gmail = getGmailService();

            com.google.api.services.gmail.model.Draft draft =
                    gmail.users().drafts().get(userId, draftId)
                            .setFormat("full")
                            .execute();

            logger.info("Got draft: op=get, draftId={}", draftId);
            return gmailMessageMapper.toDraftDetailResult(draft);

        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 404) {
                throw new ResourceNotFoundException(
                        "Draft not found: " + draftId, e);
            }
            logger.error("Gmail API error getting draft draftId={}: status={}, message={}",
                    draftId, e.getStatusCode(), e.getMessage());
            throw e;
        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    /**
     * Permanently deletes the specified draft via {@code users.drafts.delete}.
     *
     * @param userId  the Gmail user identifier; typically {@code "me"}
     * @param draftId the Gmail draft identifier
     * @throws ResourceNotFoundException if the draft does not exist (Gmail 404)
     * @throws IOException on Gmail API communication failure
     */
    @Override
    public void deleteDraft(String userId, String draftId) throws IOException {
        try {
            Gmail gmail = getGmailService();

            gmail.users().drafts().delete(userId, draftId).execute();
            logger.info("Deleted draft: op=delete, draftId={}", draftId);

        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 404) {
                throw new ResourceNotFoundException(
                        "Draft not found: " + draftId, e);
            }
            logger.error("Gmail API error deleting draft draftId={}: status={}, message={}",
                    draftId, e.getStatusCode(), e.getMessage());
            throw e;
        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    /**
     * Replaces the content of the specified draft with the provided MimeMessage
     * via {@code users.drafts.update}.
     *
     * @param userId      the Gmail user identifier; typically {@code "me"}
     * @param draftId     the Gmail draft identifier
     * @param mimeMessage the fully-constructed replacement MIME message
     * @return a {@link DraftCreationResult} with the updated draft's identifiers
     * @throws ResourceNotFoundException if the draft does not exist (Gmail 404)
     * @throws IOException on Gmail API communication failure
     */
    @Override
    public DraftCreationResult updateDraft(String userId, String draftId, MimeMessage mimeMessage) throws IOException {
        try {
            Gmail gmail = getGmailService();

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            mimeMessage.writeTo(buffer);
            String encodedRaw = Base64.getUrlEncoder().encodeToString(buffer.toByteArray());

            com.google.api.services.gmail.model.Message rawMessage =
                    new com.google.api.services.gmail.model.Message().setRaw(encodedRaw);
            com.google.api.services.gmail.model.Draft draftWrapper =
                    new com.google.api.services.gmail.model.Draft()
                            .setId(draftId)
                            .setMessage(rawMessage);

            com.google.api.services.gmail.model.Draft updatedDraft =
                    gmail.users().drafts().update(userId, draftId, draftWrapper).execute();

            logger.info("Updated draft: op=update, draftId={}", draftId);
            return gmailMessageMapper.toDraftCreationResult(updatedDraft);

        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 404) {
                throw new ResourceNotFoundException(
                        "Draft not found: " + draftId, e);
            }
            logger.error("Gmail API error updating draft draftId={}: status={}, message={}",
                    draftId, e.getStatusCode(), e.getMessage());
            throw mapGmailSendError(e);
        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        } catch (jakarta.mail.MessagingException e) {
            throw new IOException("Failed to serialize MimeMessage for draft update", e);
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

    // -------------------------------------------------------------------------
    // Feature 004 — US4: List attachments + download attachment (T064, T065)
    // -------------------------------------------------------------------------

    /**
     * Returns an {@link AttachmentListResult} for the specified message by fetching the
     * full message ({@code users.messages.get format=FULL}) and walking its MIME part tree
     * to collect all attachment parts (identified by non-null {@code body.attachmentId}
     * per research.md Decision 12).
     *
     * <p>When the message exists but has no attachments, the result contains
     * {@code List.of()} — not a 404 (FR-024).</p>
     *
     * @param userId    the Gmail user identifier; typically "me"
     * @param messageId the Gmail message identifier
     * @return an {@link AttachmentListResult} with zero or more metadata items
     * @throws ResourceNotFoundException if the message does not exist (Gmail 404)
     * @throws IOException on Gmail API communication failure
     */
    @Override
    public AttachmentListResult listAttachments(String userId, String messageId) throws IOException {
        try {
            Gmail gmail = getGmailService();

            Message message = gmail.users().messages()
                    .get(userId, messageId)
                    .setFormat("full")
                    .execute();

            AttachmentListResult result = gmailMessageMapper.toAttachmentListResult(message);
            logger.info("Attachment operation: op=listAttachments, messageId={}, attachmentCount={}",
                    messageId, result.attachments().size());
            return result;

        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 404) {
                throw new ResourceNotFoundException(
                        "Message not found: " + messageId, e);
            }
            logger.error("Gmail API error listing attachments for messageId={}: status={}, message={}",
                    messageId, e.getStatusCode(), e.getMessage());
            throw e;
        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    /**
     * Returns a {@link StreamingResponseBody} that writes the decoded binary content of
     * the specified attachment when Spring invokes its {@code writeTo(OutputStream)} method.
     *
     * <p>Per research.md Decision 6, Option A: the Gmail API call
     * ({@code users.messages.attachments.get}) is executed <em>synchronously in this
     * method body</em> before the lambda is returned. The decoded {@code byte[]} is captured
     * in the lambda closure. This ensures any Gmail 404 (message or attachment not found)
     * surfaces before the HTTP response status is committed — allowing Spring MVC to return
     * a proper 404 {@code ProblemDetail} rather than a mid-stream 500.</p>
     *
     * <p>The base64url decoding is applied via {@link java.util.Base64#getUrlDecoder()}
     * per research.md Decision 6 and FR-027. {@code Content-Length} should be set to
     * {@code decoded.length} (not Gmail's {@code MessagePartBody.getSize()}) per the
     * research.md open follow-up on base64 padding differences.</p>
     *
     * @param userId       the Gmail user identifier; typically "me"
     * @param messageId    the Gmail message identifier
     * @param attachmentId the Gmail attachment identifier
     * @return a {@link StreamingResponseBody} whose closure holds the decoded bytes
     * @throws ResourceNotFoundException if the message or attachment does not exist
     * @throws IOException on Gmail API communication failure
     */
    @Override
    public StreamingResponseBody getAttachment(String userId, String messageId,
                                               String attachmentId) throws IOException {
        try {
            Gmail gmail = getGmailService();

            MessagePartBody body = gmail.users().messages().attachments()
                    .get(userId, messageId, attachmentId)
                    .execute();

            String data = body.getData();
            if (data == null) {
                // Gmail returned a body with no data (should not occur for valid attachments).
                throw new ResourceNotFoundException(
                        "Attachment data is empty for messageId=" + messageId
                                + ", attachmentId=" + attachmentId);
            }

            // Decode from base64url to raw bytes (Option A — synchronous, before lambda fires)
            byte[] decoded = java.util.Base64.getUrlDecoder().decode(data);

            logger.info("Attachment operation: op=getAttachment, messageId={}, attachmentId={}",
                    messageId, attachmentId);

            // Return a lambda that simply writes the pre-decoded bytes to the output stream.
            // The lambda closes over the decoded byte array; no additional I/O occurs at write time.
            return (OutputStream outputStream) -> outputStream.write(decoded);

        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 404) {
                throw new ResourceNotFoundException(
                        "Attachment not found: messageId=" + messageId
                                + ", attachmentId=" + attachmentId, e);
            }
            logger.error("Gmail API error getting attachment for messageId={}, attachmentId={}: status={}, message={}",
                    messageId, attachmentId, e.getStatusCode(), e.getMessage());
            throw e;
        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }
}
