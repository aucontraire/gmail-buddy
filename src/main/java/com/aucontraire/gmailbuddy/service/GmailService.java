package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.dto.DeleteResult;
import com.aucontraire.gmailbuddy.dto.FilterCriteriaWithLabelsDTO;
import com.aucontraire.gmailbuddy.exception.GmailApiException;
import com.aucontraire.gmailbuddy.exception.ResourceNotFoundException;
import com.aucontraire.gmailbuddy.mapper.FilterCriteriaMapper;
import com.aucontraire.gmailbuddy.repository.GmailRepository;
import com.aucontraire.gmailbuddy.dto.FilterCriteriaDTO;
import com.google.api.services.gmail.model.FilterCriteria;
import com.google.api.services.gmail.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class GmailService {

    private final GmailRepository gmailRepository;
    private final GmailQueryBuilder gmailQueryBuilder;
    private final FilterCriteriaMapper filterCriteriaMapper;
    private final Logger logger = LoggerFactory.getLogger(GmailService.class);

    @Autowired
    public GmailService(GmailRepository gmailRepository, GmailQueryBuilder gmailQueryBuilder, FilterCriteriaMapper filterCriteriaMapper) {
        this.gmailRepository = gmailRepository;
        this.gmailQueryBuilder = gmailQueryBuilder;
        this.filterCriteriaMapper = filterCriteriaMapper;
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
}
