package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.dto.DeleteResult;
import com.aucontraire.gmailbuddy.dto.FilterCriteriaWithLabelsDTO;
import com.aucontraire.gmailbuddy.dto.SendMessageDTO;
import com.aucontraire.gmailbuddy.exception.GmailApiException;
import com.aucontraire.gmailbuddy.exception.ResourceNotFoundException;
import com.aucontraire.gmailbuddy.mapper.FilterCriteriaMapper;
import com.aucontraire.gmailbuddy.repository.GmailRepository;
import com.aucontraire.gmailbuddy.dto.FilterCriteriaDTO;
import com.google.api.services.gmail.model.FilterCriteria;
import com.google.api.services.gmail.model.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;

@Service
public class GmailService {

    private final GmailRepository gmailRepository;
    private final GmailQueryBuilder gmailQueryBuilder;
    private final FilterCriteriaMapper filterCriteriaMapper;
    private final MimeMessageBuilder mimeMessageBuilder;
    private final Logger logger = LoggerFactory.getLogger(GmailService.class);

    @Autowired
    public GmailService(GmailRepository gmailRepository, GmailQueryBuilder gmailQueryBuilder,
                        FilterCriteriaMapper filterCriteriaMapper, MimeMessageBuilder mimeMessageBuilder) {
        this.gmailRepository = gmailRepository;
        this.gmailQueryBuilder = gmailQueryBuilder;
        this.filterCriteriaMapper = filterCriteriaMapper;
        this.mimeMessageBuilder = mimeMessageBuilder;
    }

    public String buildQuery(FilterCriteria filterCriteria) {
        String from = gmailQueryBuilder.from(filterCriteria.getFrom());
        String to = gmailQueryBuilder.to(filterCriteria.getTo());
        String subject = gmailQueryBuilder.subject(filterCriteria.getSubject());

        // Always call gmailQueryBuilder.hasAttachment(...)
        String hasAttachment = filterCriteria.getHasAttachment() != null
                ? gmailQueryBuilder.hasAttachment(filterCriteria.getHasAttachment())
                : "";

        String additionalQuery = gmailQueryBuilder.query(filterCriteria.getQuery());
        String negatedQuery = gmailQueryBuilder.negatedQuery(filterCriteria.getNegatedQuery());

        return gmailQueryBuilder.build(from, to, subject, hasAttachment, additionalQuery, negatedQuery);
    }

    // File: src/main/java/com/aucontraire/gmailbuddy/service/GmailService.java
// Language: java
    public String buildQuery(FilterCriteriaWithLabelsDTO dto) {
        // Build search parts using non-null checks.
        String from = gmailQueryBuilder.from(dto.getFrom());
        String to = dto.getTo() != null ? gmailQueryBuilder.to(dto.getTo()) : "";
        String subject = dto.getSubject() != null ? gmailQueryBuilder.subject(dto.getSubject()) : "";
        String hasAttachment = dto.getHasAttachment() != null ? gmailQueryBuilder.hasAttachment(dto.getHasAttachment()) : "";
        String additionalQuery = dto.getQuery() != null ? gmailQueryBuilder.query(dto.getQuery()) : "";
        String negatedQuery = dto.getNegatedQuery() != null ? gmailQueryBuilder.negatedQuery(dto.getNegatedQuery()) : "";

        // Build the label-based criteria.
        String labelsQuery = "";
        if (dto.getLabelsToRemove() != null && !dto.getLabelsToRemove().isEmpty()) {
            labelsQuery = dto.getLabelsToRemove().stream()
                    .map(label -> gmailQueryBuilder.query("label:" + label))
                    .reduce((a, b) -> a + " AND " + b)
                    .orElse("");
        }

        // Check if there is any search criteria besides the "from" value.
        boolean hasSearchCriteria = !(to.isEmpty() && subject.isEmpty() && hasAttachment.isEmpty() && additionalQuery.isEmpty() && negatedQuery.isEmpty());

        if (!hasSearchCriteria) {
            // Only "from" is provided along with label modifications.
            return gmailQueryBuilder.build(from, labelsQuery);
        }

        // If search criteria exist, build the full query.
        String baseQuery = gmailQueryBuilder.build(from, to, subject, hasAttachment, additionalQuery, negatedQuery);
        baseQuery = baseQuery != null ? baseQuery : "";

        if (!baseQuery.isEmpty() && !labelsQuery.isEmpty()) {
            return baseQuery + " AND " + labelsQuery;
        } else if (!labelsQuery.isEmpty()) {
            return labelsQuery;
        } else {
            return baseQuery;
        }
    }

    public List<Message> listMessages(String userId) throws GmailApiException {
        try {
            return gmailRepository.getMessages(userId);
        } catch (IOException e) {
            logger.error("Failed to list messages for user: {}", userId, e);
            throw new GmailApiException("Failed to list messages for user: " + userId, e);
        }
    }

    public List<Message> listLatestMessages(String userId, int maxResults) throws GmailApiException {
        try {
            return gmailRepository.getLatestMessages(userId, maxResults);
        } catch (IOException e) {
            logger.error("Failed to list latest {} messages for user: {}", maxResults, userId, e);
            throw new GmailApiException(String.format("Failed to list latest %d messages for user: %s", maxResults, userId), e);
        }
    }

    public List<Message> listMessagesByFilterCriteria(String userId, FilterCriteriaDTO filterCriteriaDTO) throws GmailApiException {
        FilterCriteria criteria = filterCriteriaMapper.toFilterCriteria(filterCriteriaDTO);
        String query = buildQuery(criteria);

        try {
            logger.info("Fetching messages with query: {}", query);
            return gmailRepository.getMessagesByFilterCriteria(userId, query);
        } catch (IOException e) {
            logger.error("Failed to list messages for user: {}. Query: {}", userId, query, e);
            throw new GmailApiException("Failed to list messages", e);
        }
    }

    /**
     * Lists messages with pagination support.
     *
     * @param userId the user ID (typically "me")
     * @param pageToken the page token from a previous request (null for first page)
     * @param limit the maximum number of messages to return per page
     * @return MessageListResult containing messages, next page token, and result size estimate
     * @throws GmailApiException if the Gmail API call fails
     */
    public MessageListResult listMessagesWithPagination(String userId, String pageToken, int limit) throws GmailApiException {
        try {
            logger.debug("Listing messages with pagination - userId: {}, pageToken: {}, limit: {}", userId, pageToken, limit);
            return gmailRepository.getMessagesWithPagination(userId, pageToken, limit);
        } catch (IOException e) {
            logger.error("Failed to list messages with pagination for user: {}", userId, e);
            throw new GmailApiException("Failed to list messages for user: " + userId, e);
        }
    }

    /**
     * Lists latest messages with pagination support.
     *
     * @param userId the user ID (typically "me")
     * @param pageToken the page token from a previous request (null for first page)
     * @param maxResults the maximum number of messages to return per page
     * @return MessageListResult containing messages, next page token, and result size estimate
     * @throws GmailApiException if the Gmail API call fails
     */
    public MessageListResult listLatestMessagesWithPagination(String userId, String pageToken, int maxResults) throws GmailApiException {
        try {
            logger.debug("Listing latest messages with pagination - userId: {}, pageToken: {}, maxResults: {}", userId, pageToken, maxResults);
            return gmailRepository.getLatestMessagesWithPagination(userId, pageToken, maxResults);
        } catch (IOException e) {
            logger.error("Failed to list latest {} messages with pagination for user: {}", maxResults, userId, e);
            throw new GmailApiException(String.format("Failed to list latest %d messages for user: %s", maxResults, userId), e);
        }
    }

    /**
     * Lists messages by filter criteria with pagination support.
     *
     * @param userId the user ID (typically "me")
     * @param filterCriteriaDTO the filter criteria for message search
     * @param pageToken the page token from a previous request (null for first page)
     * @param limit the maximum number of messages to return per page
     * @return MessageListResult containing messages, next page token, and result size estimate
     * @throws GmailApiException if the Gmail API call fails
     */
    public MessageListResult listMessagesByFilterCriteriaWithPagination(String userId, FilterCriteriaDTO filterCriteriaDTO, String pageToken, int limit) throws GmailApiException {
        FilterCriteria criteria = filterCriteriaMapper.toFilterCriteria(filterCriteriaDTO);
        String query = buildQuery(criteria);

        try {
            logger.info("Fetching messages with pagination - query: {}, pageToken: {}, limit: {}", query, pageToken, limit);
            return gmailRepository.getMessagesByFilterCriteriaWithPagination(userId, query, pageToken, limit);
        } catch (IOException e) {
            logger.error("Failed to list messages with pagination for user: {}. Query: {}", userId, query, e);
            throw new GmailApiException("Failed to list messages", e);
        }
    }

    public DeleteResult deleteMessage(String userId, String messageId) throws GmailApiException {
        try {
            BulkOperationResult bulkResult = gmailRepository.deleteMessage(userId, messageId);

            // Convert BulkOperationResult to DeleteResult for single message operations
            if (bulkResult.hasSuccesses() && bulkResult.getSuccessfulOperations().contains(messageId)) {
                logger.info("Successfully deleted message: {}", messageId);
                return DeleteResult.success(messageId);
            } else if (bulkResult.hasFailures()) {
                String errorMessage = bulkResult.getFailedOperations().get(messageId);
                logger.error("Failed to delete message {}: {}", messageId, errorMessage);
                return DeleteResult.failure(messageId, errorMessage);
            } else {
                // Edge case: no success and no failure recorded
                logger.warn("Delete operation completed but no result recorded for message: {}", messageId);
                return DeleteResult.failure(messageId, "Unknown error - no result recorded");
            }
        } catch (IOException e) {
            logger.error("Failed to delete message for messageId: {} for user: {}", messageId, userId, e);
            throw new GmailApiException(
                    String.format("Failed to delete message for messageId: %s for user: %s", messageId, userId), e
            );
        }
    }

    public BulkOperationResult deleteMessagesByFilterCriteria(String userId, FilterCriteriaDTO filterCriteriaDTO) throws GmailApiException {
        try {
            FilterCriteria criteria = filterCriteriaMapper.toFilterCriteria(filterCriteriaDTO);
            String query = buildQuery(criteria);

            // Use the constructed query to delete messages
            BulkOperationResult result = gmailRepository.deleteMessagesByFilterCriteria(userId, query);

            logger.info("Bulk delete operation completed for user {}: {} successful, {} failed out of {} total",
                       userId, result.getSuccessCount(), result.getFailureCount(), result.getTotalOperations());

            return result;
        } catch (IOException e) {
            logger.error("Failed to delete messages for user: {}. Query: {}", userId, null, e);
            throw new GmailApiException(
                    String.format("Failed to delete messages for user: %s. Query: %s", userId, null), e
            );
        }
    }

    public BulkOperationResult modifyMessagesLabelsByFilterCriteria(String userId, FilterCriteriaWithLabelsDTO dto) throws GmailApiException {
        try {
            // Use the existing mapper to map filter criteria portion if needed
            // Otherwise build a FilterCriteria manually here
            FilterCriteriaMapper criteriaMapper = new FilterCriteriaMapper();
            var filterCriteria = criteriaMapper.toFilterCriteria(dto);
            String query = buildQuery(dto);
            return gmailRepository.modifyMessagesLabels(userId, dto.getLabelsToAdd(), dto.getLabelsToRemove(), query);
        } catch (IOException e) {
            throw new GmailApiException(
                    String.format("Failed to modify labels for messages for user: %s", userId), e
            );
        }
    }

    public String getMessageBody(String userId, String messageId) throws GmailApiException, ResourceNotFoundException {
        try {
            return gmailRepository.getMessageBody(userId, messageId);
        } catch (IOException e) {
            logger.error("Failed to get message body for messageId: {} for user: {}", messageId, userId, e);
            throw new GmailApiException(
                    String.format("Failed to get message body for messageId: %s for user: %s", messageId, userId),
                    e
            );
        }
    }

    public BulkOperationResult markMessageAsRead(String userId, String messageId) throws GmailApiException {
        try {
            return gmailRepository.markMessageAsRead(userId, messageId);
        } catch (IOException e) {
            logger.error("Failed to mark message as read for messageId: {} for user: {}", messageId, userId, e);
            throw new GmailApiException(
                    String.format("Failed to mark message as read for messageId: %s for user: %s", messageId, userId),
                    e
            );
        }
    }

    /**
     * Stages a draft email in the user's Gmail Drafts folder. The DTO is first
     * converted to a {@link MimeMessage} via {@link MimeMessageBuilder}, then
     * submitted to the Gmail API via the repository layer.
     *
     * <p>This is the default outreach path for AI-personalized content — the draft
     * is immediately visible in any Gmail client for the user to review, edit,
     * send, or discard before delivery.</p>
     *
     * <h2>Threading orchestration (FR-003 through FR-008c)</h2>
     * <p>When {@code dto.inReplyToMessageId()} is non-null, this method performs a
     * {@code users.messages.get} metadata lookup via the repository to obtain the
     * original message's RFC 5322 {@code Message-ID} and {@code threadId}. The lookup
     * result is passed to {@link MimeMessageBuilder#build(SendMessageDTO, OriginalMessageLookup)},
     * which sets the {@code In-Reply-To} and {@code References} MIME headers. The
     * resolved {@code threadId} (from the lookup, or from {@code dto.threadId()} as
     * a fallback) is applied to the Gmail API {@code Message} object BEFORE the API
     * call.</p>
     *
     * <p>Lookup failures ({@link com.aucontraire.gmailbuddy.exception.OriginalMessageNotFoundException},
     * {@link com.aucontraire.gmailbuddy.exception.AuthorizationException},
     * {@link com.aucontraire.gmailbuddy.exception.RateLimitException},
     * {@link com.aucontraire.gmailbuddy.exception.GmailApiException},
     * {@link com.aucontraire.gmailbuddy.exception.ServiceUnavailableException}) propagate
     * naturally — they are all {@link RuntimeException} subclasses covered by the existing
     * {@code GlobalExceptionHandler}. The draft is NOT created if the lookup fails
     * (fail-closed per spec § Clarifications Q1).</p>
     *
     * @param userId the Gmail user identifier; typically {@code "me"}
     * @param dto    the validated send request containing recipients, subject, and body
     * @return a {@link DraftCreationResult} with the Gmail-assigned draft, message,
     *         and thread identifiers
     * @throws GmailApiException if MimeMessage construction fails or the Gmail API
     *                           returns an error
     */
    public DraftCreationResult createDraft(String userId, SendMessageDTO dto) throws GmailApiException {
        try {
            // Threading orchestration (T035): perform lookup when inReplyToMessageId is present.
            // When absent, skip the lookup and pass null — no In-Reply-To / References headers.
            OriginalMessageLookup lookup = null;
            if (dto.inReplyToMessageId() != null) {
                logger.info("Threading lookup: inReplyToMessageId={}, userId={}", dto.inReplyToMessageId(), userId);
                lookup = gmailRepository.getMessageHeaders(userId, dto.inReplyToMessageId());
            }

            MimeMessage mimeMessage = mimeMessageBuilder.build(dto, lookup);

            // Resolve the threadId and apply it to the Gmail API Message object.
            // resolveThreadId() implements FR-005 (infer from lookup) and FR-006 (lookup wins).
            // The threadId is a Gmail API envelope field — NOT a MIME header — so it is set
            // here in the service layer, not inside MimeMessageBuilder (research.md Decision 2).
            String resolvedThreadId = mimeMessageBuilder.resolveThreadId(dto, lookup);

            logger.info("Creating draft: attachmentCount={}, threaded={}, threadId={}",
                    dto.attachments().size(),
                    lookup != null,
                    resolvedThreadId);

            return gmailRepository.createDraft(userId, mimeMessage, resolvedThreadId);
        } catch (MessagingException | UnsupportedEncodingException e) {
            logger.error("Failed to build MimeMessage for draft creation for user: {}", userId, e);
            throw new GmailApiException("Failed to construct email message for draft", e);
        } catch (IOException e) {
            logger.error("Failed to create draft for user: {}", userId, e);
            throw new GmailApiException(
                    String.format("Failed to create draft for user: %s", userId), e
            );
        }
    }

    /**
     * Sends a previously-created draft by its identifier. Thin pass-through to the
     * repository layer — no MimeMessage construction is needed because the message
     * content already lives in Gmail's draft store.
     *
     * <p>Naturally idempotent at the resource level: if the draft was already sent
     * (or discarded), Gmail returns 404 and the repository maps it to
     * {@link com.aucontraire.gmailbuddy.exception.ResourceNotFoundException}, which
     * the controller returns as HTTP 404.</p>
     *
     * @param userId  the Gmail user identifier; typically {@code "me"}
     * @param draftId the Gmail-assigned draft identifier returned by {@link #createDraft}
     * @return a {@link SentMessageResult} with the Gmail-assigned message and thread identifiers
     * @throws GmailApiException if the Gmail API returns an error
     */
    public SentMessageResult sendDraft(String userId, String draftId) throws GmailApiException {
        try {
            return gmailRepository.sendDraft(userId, draftId);
        } catch (IOException e) {
            logger.error("Failed to send draft for draftId: {} for user: {}", draftId, userId, e);
            throw new GmailApiException(
                    String.format("Failed to send draft for draftId: %s for user: %s", draftId, userId), e
            );
        }
    }

    /**
     * Sends an email message immediately. The DTO is first converted to a
     * {@link MimeMessage} via {@link MimeMessageBuilder}, then submitted to the
     * Gmail API via the repository layer.
     *
     * <p>Reserved for deterministic, pre-trusted templates. Callers MUST NOT
     * auto-retry POSTs after a network timeout — duplicate sends may result.</p>
     *
     * <h2>Threading orchestration (FR-003 through FR-008c)</h2>
     * <p>When {@code dto.inReplyToMessageId()} is non-null, this method performs a
     * {@code users.messages.get} metadata lookup (~5 quota units) to obtain the
     * original message's RFC 5322 {@code Message-ID} and {@code threadId}. The lookup
     * result is passed to {@link MimeMessageBuilder#build(SendMessageDTO, OriginalMessageLookup)},
     * which sets the {@code In-Reply-To} and {@code References} MIME headers. The
     * resolved {@code threadId} is applied to the Gmail API {@code Message} object
     * BEFORE the send call (~100 quota units), for a total of ~105 quota units
     * (spec § Clarifications Q2, FR-008b).</p>
     *
     * <p>Lookup failures propagate naturally — they are {@link RuntimeException} subclasses
     * covered by the existing {@code GlobalExceptionHandler}. No message is sent if the
     * lookup fails (fail-closed per spec § Clarifications Q1).</p>
     *
     * @param userId the Gmail user identifier; typically {@code "me"}
     * @param dto    the validated send request containing recipients, subject, and body
     * @return a {@link SentMessageResult} with the Gmail-assigned message and thread identifiers
     * @throws GmailApiException if MimeMessage construction fails or the Gmail API
     *                           returns an error
     */
    public SentMessageResult sendMessage(String userId, SendMessageDTO dto) throws GmailApiException {
        try {
            // Threading orchestration (T035): perform lookup when inReplyToMessageId is present.
            // When absent, skip the lookup and pass null — no In-Reply-To / References headers.
            OriginalMessageLookup lookup = null;
            if (dto.inReplyToMessageId() != null) {
                logger.info("Threading lookup: inReplyToMessageId={}, userId={}", dto.inReplyToMessageId(), userId);
                lookup = gmailRepository.getMessageHeaders(userId, dto.inReplyToMessageId());
            }

            MimeMessage mimeMessage = mimeMessageBuilder.build(dto, lookup);

            // Resolve the threadId and apply it to the Gmail API Message object.
            // resolveThreadId() implements FR-005 (infer from lookup) and FR-006 (lookup wins).
            // The threadId is a Gmail API envelope field — NOT a MIME header — so it is set
            // here in the service layer, not inside MimeMessageBuilder (research.md Decision 2).
            String resolvedThreadId = mimeMessageBuilder.resolveThreadId(dto, lookup);

            logger.info("Sending message: attachmentCount={}, threaded={}, threadId={}",
                    dto.attachments().size(),
                    lookup != null,
                    resolvedThreadId);

            return gmailRepository.sendMessage(userId, mimeMessage, resolvedThreadId);
        } catch (MessagingException | UnsupportedEncodingException e) {
            logger.error("Failed to build MimeMessage for send for user: {}", userId, e);
            throw new GmailApiException("Failed to construct email message for send", e);
        } catch (IOException e) {
            logger.error("Failed to send message for user: {}", userId, e);
            throw new GmailApiException(
                    String.format("Failed to send message for user: %s", userId), e
            );
        }
    }
}
