package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import com.aucontraire.gmailbuddy.dto.Attachment;
import com.aucontraire.gmailbuddy.dto.DeleteResult;
import com.aucontraire.gmailbuddy.dto.FilterCriteriaDTO;
import com.aucontraire.gmailbuddy.dto.FilterCriteriaWithLabelsDTO;
import com.aucontraire.gmailbuddy.dto.SendMessageDTO;
import com.aucontraire.gmailbuddy.exception.GmailApiException;
import com.aucontraire.gmailbuddy.exception.MessageTooLargeException;
import com.aucontraire.gmailbuddy.exception.ResourceNotFoundException;
import com.aucontraire.gmailbuddy.mapper.FilterCriteriaMapper;
import com.aucontraire.gmailbuddy.mapper.GmailMessageMapper;
import com.aucontraire.gmailbuddy.repository.GmailRepository;
import com.google.api.services.gmail.model.FilterCriteria;
import com.google.api.services.gmail.model.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class GmailService {

    private final GmailRepository gmailRepository;
    private final GmailQueryBuilder gmailQueryBuilder;
    private final FilterCriteriaMapper filterCriteriaMapper;
    private final MimeMessageBuilder mimeMessageBuilder;
    private final GmailMessageMapper gmailMessageMapper;
    private final GmailBuddyProperties properties;
    private final Logger logger = LoggerFactory.getLogger(GmailService.class);

    @Autowired
    public GmailService(GmailRepository gmailRepository, GmailQueryBuilder gmailQueryBuilder,
                        FilterCriteriaMapper filterCriteriaMapper, MimeMessageBuilder mimeMessageBuilder,
                        GmailMessageMapper gmailMessageMapper,
                        GmailBuddyProperties properties) {
        this.gmailRepository = gmailRepository;
        this.gmailQueryBuilder = gmailQueryBuilder;
        this.filterCriteriaMapper = filterCriteriaMapper;
        this.mimeMessageBuilder = mimeMessageBuilder;
        this.gmailMessageMapper = gmailMessageMapper;
        this.properties = properties;
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

            // T043 — Stage 1: pre-construction payload size estimate (fast reject, FR-014, FR-017).
            // Only applied when attachments are present; the existing @MaxBodySize Bean Validation
            // constraint already enforces the 10 MB body cap for non-attachment requests (FR-015).
            // When attachments ARE present, the 25 MB total-payload cap takes precedence.
            long estimateBytes = estimatePayloadBytes(dto);
            if (!dto.attachments().isEmpty()) {
                enforceSizeStage1(estimateBytes);
            }

            MimeMessage mimeMessage = mimeMessageBuilder.build(dto, lookup);

            // T044 — Stage 2: post-construction safety net (strict 100% cap, FR-014, FR-017).
            // Serializes the assembled MIME to measure actual bytes — catches MIME boundary/
            // encoding overhead that Stage 1's estimate (body + decoded attachment bytes) missed.
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            mimeMessage.writeTo(baos);
            long actualBytes = baos.size();
            if (!dto.attachments().isEmpty()) {
                enforceSizeStage2(actualBytes);
            }

            // Resolve the threadId and apply it to the Gmail API Message object.
            // resolveThreadId() implements FR-005 (infer from lookup) and FR-006 (lookup wins).
            // The threadId is a Gmail API envelope field — NOT a MIME header — so it is set
            // here in the service layer, not inside MimeMessageBuilder (research.md Decision 2).
            String resolvedThreadId = mimeMessageBuilder.resolveThreadId(dto, lookup);

            // FR-019a / FR-020: log only attachment count, estimated bytes, actual bytes, and
            // MIME types. Never log filenames, base64Data, or message body content.
            // This log SUPPLEMENTS the threading-related log from Checkpoint B (T035) — do not
            // remove the threading log statement; this is the US2 attachment diagnostic.
            logger.info("Creating draft: attachmentCount={}, estimatedPayloadBytes={}, actualMimeBytes={}, mimeTypes={}",
                    dto.attachments().size(),
                    estimateBytes,
                    actualBytes,
                    dto.attachments().stream().map(Attachment::mimeType).toList());

            logger.info("Creating draft: threaded={}, threadId={}",
                    lookup != null,
                    resolvedThreadId);

            return gmailRepository.createDraft(userId, mimeMessage, resolvedThreadId);
        } catch (MessageTooLargeException e) {
            // Re-throw size rejections (Stage 1 or Stage 2) without wrapping — the
            // GlobalExceptionHandler maps MessageTooLargeException → HTTP 413 directly.
            throw e;
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

            // T043 — Stage 1: pre-construction payload size estimate (fast reject, FR-014, FR-017).
            // Only applied when attachments are present; the existing @MaxBodySize Bean Validation
            // constraint already enforces the 10 MB body cap for non-attachment requests (FR-015).
            // When attachments ARE present, the 25 MB total-payload cap takes precedence.
            long estimateBytes = estimatePayloadBytes(dto);
            if (!dto.attachments().isEmpty()) {
                enforceSizeStage1(estimateBytes);
            }

            MimeMessage mimeMessage = mimeMessageBuilder.build(dto, lookup);

            // T044 — Stage 2: post-construction safety net (strict 100% cap, FR-014, FR-017).
            // Serializes the assembled MIME to measure actual bytes — catches MIME boundary/
            // encoding overhead that Stage 1's estimate (body + decoded attachment bytes) missed.
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            mimeMessage.writeTo(baos);
            long actualBytes = baos.size();
            if (!dto.attachments().isEmpty()) {
                enforceSizeStage2(actualBytes);
            }

            // Resolve the threadId and apply it to the Gmail API Message object.
            // resolveThreadId() implements FR-005 (infer from lookup) and FR-006 (lookup wins).
            // The threadId is a Gmail API envelope field — NOT a MIME header — so it is set
            // here in the service layer, not inside MimeMessageBuilder (research.md Decision 2).
            String resolvedThreadId = mimeMessageBuilder.resolveThreadId(dto, lookup);

            // FR-019a / FR-020: log only attachment count, estimated bytes, actual bytes, and
            // MIME types. Never log filenames, base64Data, or message body content.
            // This log SUPPLEMENTS the threading-related log from Checkpoint B (T035) — do not
            // remove the threading log statement; this is the US2 attachment diagnostic.
            logger.info("Sending message: attachmentCount={}, estimatedPayloadBytes={}, actualMimeBytes={}, mimeTypes={}",
                    dto.attachments().size(),
                    estimateBytes,
                    actualBytes,
                    dto.attachments().stream().map(Attachment::mimeType).toList());

            logger.info("Sending message: threaded={}, threadId={}",
                    lookup != null,
                    resolvedThreadId);

            return gmailRepository.sendMessage(userId, mimeMessage, resolvedThreadId);
        } catch (MessageTooLargeException e) {
            // Re-throw size rejections (Stage 1 or Stage 2) without wrapping — the
            // GlobalExceptionHandler maps MessageTooLargeException → HTTP 413 directly.
            throw e;
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

    // ---------------------------------------------------------------------------
    // Draft CRUD — listDrafts, getDraft, deleteDraft, updateDraft
    // ---------------------------------------------------------------------------

    /**
     * Returns a paginated list of drafts for the specified user.
     *
     * @param userId    the Gmail user identifier; typically {@code "me"}
     * @param pageToken opaque pagination token, or null for the first page
     * @param limit     maximum number of items to return (1–50)
     * @return a {@link DraftListResult} with enriched draft summaries
     * @throws GmailApiException if the Gmail API returns an error
     */
    public DraftListResult listDrafts(String userId, String pageToken, int limit) throws GmailApiException {
        try {
            DraftListResult result = gmailRepository.listDrafts(userId, pageToken, limit);
            logger.info("Draft operation: op=list, count={}", result.drafts().size());
            return result;
        } catch (IOException e) {
            logger.error("Failed to list drafts for user: {}", userId, e);
            throw new GmailApiException(
                    String.format("Failed to list drafts for user: %s", userId), e
            );
        }
    }

    /**
     * Returns the full content of the specified draft.
     *
     * @param userId  the Gmail user identifier; typically {@code "me"}
     * @param draftId the Gmail draft identifier
     * @return a {@link DraftDetailResult} with all parsed fields
     * @throws ResourceNotFoundException if the draft does not exist
     * @throws GmailApiException if the Gmail API returns an error
     */
    public DraftDetailResult getDraft(String userId, String draftId) throws GmailApiException {
        try {
            DraftDetailResult result = gmailRepository.getDraft(userId, draftId);
            logger.info("Draft operation: op=get, draftId={}, attachmentCount={}",
                    draftId, result.attachments().size());
            return result;
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (IOException e) {
            logger.error("Failed to get draft draftId={} for user: {}", draftId, userId, e);
            throw new GmailApiException(
                    String.format("Failed to get draft %s for user: %s", draftId, userId), e
            );
        }
    }

    /**
     * Permanently deletes the specified draft.
     *
     * @param userId  the Gmail user identifier; typically {@code "me"}
     * @param draftId the Gmail draft identifier
     * @throws ResourceNotFoundException if the draft does not exist
     * @throws GmailApiException if the Gmail API returns an error
     */
    public void deleteDraft(String userId, String draftId) throws GmailApiException {
        try {
            gmailRepository.deleteDraft(userId, draftId);
            logger.info("Draft operation: op=delete, draftId={}", draftId);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (IOException e) {
            logger.error("Failed to delete draft draftId={} for user: {}", draftId, userId, e);
            throw new GmailApiException(
                    String.format("Failed to delete draft %s for user: %s", draftId, userId), e
            );
        }
    }

    /**
     * Replaces the content of the specified draft. Performs a threading lookup when
     * {@code dto.inReplyToMessageId()} is present, then builds a {@link MimeMessage}
     * and calls the repository's {@code updateDraft}.
     *
     * @param userId  the Gmail user identifier; typically {@code "me"}
     * @param draftId the Gmail draft identifier
     * @param dto     the validated update request
     * @return a {@link DraftDetailResult} reflecting the updated draft state
     * @throws ResourceNotFoundException if the draft does not exist
     * @throws GmailApiException if the Gmail API or MIME construction fails
     */
    public DraftDetailResult updateDraft(String userId, String draftId, SendMessageDTO dto) throws GmailApiException {
        try {
            boolean hasThreading = dto.inReplyToMessageId() != null;
            OriginalMessageLookup lookup = null;
            if (hasThreading) {
                lookup = gmailRepository.getMessageHeaders(userId, dto.inReplyToMessageId());
            }

            MimeMessage mimeMessage = mimeMessageBuilder.build(dto, lookup);

            DraftCreationResult updateResult = gmailRepository.updateDraft(userId, draftId, mimeMessage);

            // Fetch the updated draft to return the full DraftDetailResult
            DraftDetailResult result = gmailRepository.getDraft(userId, updateResult.draftId());

            logger.info("Draft operation: op=update, draftId={}, attachmentCount={}, hasThreading={}",
                    draftId, result.attachments().size(), hasThreading);
            return result;

        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (MessageTooLargeException e) {
            throw e;
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            logger.error("Failed to build MimeMessage for draft update draftId={}: {}", draftId, e.getMessage());
            throw new GmailApiException("Failed to construct email message for draft update", e);
        } catch (IOException e) {
            logger.error("Failed to update draft draftId={} for user: {}", draftId, userId, e);
            throw new GmailApiException(
                    String.format("Failed to update draft %s for user: %s", draftId, userId), e
            );
        }
    }

    // ---------------------------------------------------------------------------
    // Feature 004 — US1: Thread list + detail service methods (T024)
    // ---------------------------------------------------------------------------

    /**
     * Returns a paginated list of thread summaries for the specified user.
     * Delegates to the repository, which calls {@code users.threads.list} once
     * (flat 10-unit quota cost per Clarifications Q1 — no per-item enrichment).
     *
     * @param userId         the Gmail user identifier; typically "me"
     * @param filterCriteria filter criteria from query parameters; null for no filter
     * @param pageToken      opaque pagination token, or null for the first page
     * @param limit          maximum number of items to return (1–100)
     * @return a {@link ThreadListResult} with thread summaries and pagination state
     * @throws GmailApiException if the Gmail API returns an error
     */
    public ThreadListResult listThreads(String userId, FilterCriteriaDTO filterCriteria,
                                        String pageToken, int limit) throws GmailApiException {
        try {
            ThreadListResult result = gmailRepository.listThreads(userId, filterCriteria, pageToken, limit);
            logger.info("Thread operation: op=listThreads, count={}", result.threads().size());
            return result;
        } catch (IOException e) {
            logger.error("Failed to list threads for user: {}", userId, e);
            throw new GmailApiException(
                    String.format("Failed to list threads for user: %s", userId), e
            );
        }
    }

    /**
     * Returns the full content of the specified thread including all nested messages.
     * Delegates to the repository, which calls {@code users.threads.get(format=FULL)}.
     *
     * @param userId   the Gmail user identifier; typically "me"
     * @param threadId the Gmail thread identifier
     * @return a {@link ThreadDetailResult} with all messages and union label set
     * @throws ResourceNotFoundException if the thread does not exist
     * @throws GmailApiException if the Gmail API returns an error
     */
    public ThreadDetailResult getThread(String userId, String threadId) throws GmailApiException {
        try {
            ThreadDetailResult result = gmailRepository.getThread(userId, threadId);
            logger.info("Thread operation: op=getThread, threadId={}, messageCount={}",
                    threadId, result.messages().size());
            return result;
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (IOException e) {
            logger.error("Failed to get thread threadId={} for user: {}", threadId, userId, e);
            throw new GmailApiException(
                    String.format("Failed to get thread %s for user: %s", threadId, userId), e
            );
        }
    }

    // ---------------------------------------------------------------------------
    // Feature 004 — US2: Message detail service method (T034)
    // ---------------------------------------------------------------------------

    /**
     * Returns the full structured detail of the specified message.
     *
     * <p>Normalizes {@code format} to lowercase before passing to the repository
     * (per spec Edge Cases: {@code "Full"} / {@code "FULL"} → {@code "full"}).
     * The repository uses this normalized string to determine whether to fetch
     * with {@code format=FULL} (10 quota units) or {@code format=METADATA} (5 quota units,
     * 9-header whitelist per research.md Decision 1).</p>
     *
     * @param userId    the Gmail user identifier; typically "me"
     * @param messageId the Gmail message identifier
     * @param format    the format param from the controller, already lowercased;
     *                  {@code "full"} (default) or {@code "metadata"}
     * @return a {@link MessageDetailResult} with all fields; body is null when format=metadata
     * @throws ResourceNotFoundException if the message does not exist (Gmail 404)
     * @throws GmailApiException if the Gmail API returns an error
     */
    public MessageDetailResult getMessageDetail(String userId, String messageId, String format)
            throws GmailApiException {
        // Normalize to lowercase (Edge Cases: "Full"/"FULL" → "full")
        String normalizedFormat = format != null ? format.toLowerCase() : "full";
        try {
            MessageDetailResult result = gmailRepository.getMessageDetail(userId, messageId, normalizedFormat);
            logger.info("Message detail op: op=getMessageDetail, messageId={}, format={}, attachmentCount={}",
                    messageId, normalizedFormat, result.attachments().size());
            return result;
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (IOException e) {
            logger.error("Failed to get message detail for messageId={} for user: {}", messageId, userId, e);
            throw new GmailApiException(
                    String.format("Failed to getMessageDetail for messageId: %s for user: %s", messageId, userId), e
            );
        }
    }

    // ---------------------------------------------------------------------------
    // Feature 004 — US3: Label list + detail service methods (T051)
    // ---------------------------------------------------------------------------

    /**
     * Returns all visible labels (system + user-created) for the specified user.
     * Delegates to the repository, which calls {@code users.labels.list} once
     * (flat 1-unit quota cost — no per-item enrichment).
     *
     * @param userId the Gmail user identifier; typically "me"
     * @return a {@link LabelListResult} with all labels; never null
     * @throws GmailApiException if the Gmail API returns an error
     */
    public LabelListResult listLabels(String userId) throws GmailApiException {
        try {
            LabelListResult result = gmailRepository.listLabels(userId);
            logger.info("Label operation: op=listLabels, count={}", result.totalCount());
            return result;
        } catch (IOException e) {
            logger.error("Failed to list labels for user: {}", userId, e);
            throw new GmailApiException(
                    String.format("Failed to list labels for user: %s", userId), e
            );
        }
    }

    /**
     * Returns the full detail of the specified label including counts and color.
     * Delegates to the repository, which calls {@code users.labels.get}.
     *
     * @param userId  the Gmail user identifier; typically "me"
     * @param labelId the Gmail label identifier
     * @return a {@link LabelDetailResult} with all fields
     * @throws ResourceNotFoundException if the label does not exist (Gmail 404)
     * @throws GmailApiException if the Gmail API returns an error
     */
    public LabelDetailResult getLabel(String userId, String labelId) throws GmailApiException {
        try {
            LabelDetailResult result = gmailRepository.getLabel(userId, labelId);
            logger.info("Label operation: op=getLabel, labelId={}", labelId);
            return result;
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (IOException e) {
            logger.error("Failed to get label labelId={} for user: {}", labelId, userId, e);
            throw new GmailApiException(
                    String.format("Failed to get label %s for user: %s", labelId, userId), e
            );
        }
    }

    // ---------------------------------------------------------------------------
    // Feature 004 — US4: Attachment service methods (T066)
    // ---------------------------------------------------------------------------

    /**
     * Returns attachment metadata for all attachments on the specified message.
     * Delegates to the repository, which calls {@code users.messages.get format=FULL}
     * (5 quota units) and walks the MIME part tree.
     *
     * <p>When the message exists but has no attachments, returns an
     * {@link AttachmentListResult} with an empty list — never throws a
     * {@link ResourceNotFoundException} in that case (FR-024).</p>
     *
     * <p><strong>Logging</strong>: only {@code op}, {@code messageId}, and
     * {@code attachmentCount} are logged — never filename or mimeType (FR-032).</p>
     *
     * @param userId    the Gmail user identifier; typically "me"
     * @param messageId the Gmail message identifier
     * @return an {@link AttachmentListResult}; {@code attachments} is empty when none present
     * @throws ResourceNotFoundException if the message does not exist (Gmail 404)
     * @throws GmailApiException if the Gmail API returns an error
     */
    public AttachmentListResult listAttachments(String userId, String messageId) throws GmailApiException {
        try {
            AttachmentListResult result = gmailRepository.listAttachments(userId, messageId);
            logger.info("Attachment operation: op=listAttachments, messageId={}, attachmentCount={}",
                    messageId, result.attachments().size());
            return result;
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (IOException e) {
            logger.error("Failed to list attachments for messageId={} for user: {}", messageId, userId, e);
            throw new GmailApiException(
                    String.format("Failed to list attachments for messageId: %s for user: %s", messageId, userId), e
            );
        }
    }

    /**
     * Returns a {@link StreamingResponseBody} wrapping the raw binary content of the
     * specified attachment. Delegates to the repository, which calls
     * {@code users.messages.attachments.get} (5 quota units) synchronously and captures
     * the decoded bytes in the lambda closure (research.md Decision 6, Option A).
     *
     * <p><strong>Logging</strong>: only {@code op}, {@code messageId}, and
     * {@code attachmentId} are logged — never filename, mimeType, or binary content
     * (FR-032).</p>
     *
     * @param userId       the Gmail user identifier; typically "me"
     * @param messageId    the Gmail message identifier
     * @param attachmentId the Gmail attachment identifier
     * @return a {@link StreamingResponseBody} whose closure holds the decoded bytes
     * @throws ResourceNotFoundException if the message or attachment does not exist
     * @throws GmailApiException if the Gmail API returns an error
     */
    public StreamingResponseBody getAttachment(String userId, String messageId,
                                               String attachmentId) throws GmailApiException {
        try {
            StreamingResponseBody stream = gmailRepository.getAttachment(userId, messageId, attachmentId);
            logger.info("Attachment operation: op=getAttachment, messageId={}, attachmentId={}",
                    messageId, attachmentId);
            return stream;
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (IOException e) {
            logger.error("Failed to get attachment for messageId={}, attachmentId={} for user: {}",
                    messageId, attachmentId, userId, e);
            throw new GmailApiException(
                    String.format("Failed to get attachment for messageId: %s, attachmentId: %s for user: %s",
                            messageId, attachmentId, userId), e
            );
        }
    }

    // ---------------------------------------------------------------------------
    // T043 / T044 — Payload size helpers
    // ---------------------------------------------------------------------------

    /**
     * Estimates the total assembled MIME payload size before construction (Stage 1).
     *
     * <p>Computes: {@code body.getBytes(UTF_8).length + sum(base64Data.length() * 3 / 4)}
     * for all attachments. The {@code * 3 / 4} factor converts from base64 character count
     * to approximate decoded byte count (research.md Decision 9).</p>
     *
     * @param dto the validated send request DTO
     * @return estimated payload size in bytes
     */
    private long estimatePayloadBytes(SendMessageDTO dto) {
        long estimateBytes = dto.body().getBytes(StandardCharsets.UTF_8).length;
        for (Attachment att : dto.attachments()) {
            // base64Data.length() * 3 / 4 approximates decoded binary size.
            estimateBytes += (long) att.base64Data().length() * 3 / 4;
        }
        return estimateBytes;
    }

    /**
     * Stage 1 size enforcement: fast reject at 90% of the total-payload cap (T043).
     *
     * <p>The 90% threshold (rather than 100%) leaves headroom for MIME multipart
     * overhead (boundaries, headers, base64 line folding at 76 chars + CRLF) that the
     * pre-construction estimate does not account for. Stage 2 ({@link #enforceSizeStage2})
     * enforces the strict 100% cap on the actual serialized bytes (research.md Decision 9,
     * /reconcile U1).</p>
     *
     * <p>Choice — "only-with-attachments" vs "always": Stage 1 is applied ONLY when
     * {@code dto.attachments()} is non-empty. For non-attachment requests the existing
     * {@code @MaxBodySize} Bean Validation annotation (10 MB body cap, FR-015) is the
     * sole pre-check — it runs earlier, at the DTO validation layer, and produces a
     * structured 400 response. Duplicating that check here would add confusion without
     * value. Stage 2 runs only when attachments are present for the same reason.</p>
     *
     * @param estimateBytes the estimated payload size from {@link #estimatePayloadBytes}
     * @throws MessageTooLargeException (HTTP 413) if the estimate exceeds the 90% threshold
     */
    private void enforceSizeStage1(long estimateBytes) {
        long maxTotal = properties.send().maxTotalPayloadSize().toBytes();
        long threshold = (long) (0.9 * maxTotal);
        if (estimateBytes > threshold) {
            throw new MessageTooLargeException(
                    "Total payload estimate (" + estimateBytes + " bytes) exceeds 90% of "
                    + maxTotal + " byte cap (Stage 1 fast reject, FR-017)");
        }
    }

    /**
     * Stage 2 size enforcement: strict 100% cap on the actual serialized MIME bytes (T044).
     *
     * <p>Called after {@link MimeMessageBuilder#build} returns and the {@link MimeMessage}
     * has been serialized to a {@link ByteArrayOutputStream}. This safety net catches
     * any cases where Stage 1's estimate was too optimistic — e.g., MIME boundaries
     * inflating beyond the 10% margin (research.md Decision 9).</p>
     *
     * @param actualBytes the actual byte count of the serialized MIME message
     * @throws MessageTooLargeException (HTTP 413) if {@code actualBytes} exceeds the cap
     */
    private void enforceSizeStage2(long actualBytes) {
        long maxTotal = properties.send().maxTotalPayloadSize().toBytes();
        if (actualBytes > maxTotal) {
            throw new MessageTooLargeException(
                    "Assembled MIME payload (" + actualBytes + " bytes) exceeds "
                    + maxTotal + " byte cap (Stage 2 safety net, FR-017)");
        }
    }
}
