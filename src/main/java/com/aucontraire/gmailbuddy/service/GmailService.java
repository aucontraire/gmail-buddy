package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.exception.GmailServiceException;
import com.aucontraire.gmailbuddy.exception.MessageNotFoundException;
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

    public String buildQuery(String senderEmail, FilterCriteria filterCriteria) {
        String from = gmailQueryBuilder.from(senderEmail);
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

    public String buildQuery(String senderEmail, List<String> labelsToRemove) {
        // Use gmailQueryBuilder to build the labelsQuery
        String labelsQuery = (labelsToRemove == null || labelsToRemove.isEmpty())
                ? ""
                : labelsToRemove.stream()
                .map(label -> gmailQueryBuilder.query("label:" + label)) // Use query() for each label
                .reduce((a, b) -> a + " AND " + b)  // Combine labels with AND
                .orElse("");

        String from = gmailQueryBuilder.from(senderEmail);  // Generate "from" filter part

        // Return a full query combining both "from" and labels
        return gmailQueryBuilder.build(from, labelsQuery);
    }

    public List<Message> listMessages(String userId) throws GmailServiceException {
        try {
            return gmailRepository.getMessages(userId);
        } catch (IOException e) {
            logger.error("Failed to list messages for user: {}", userId, e);
            throw new GmailServiceException("Failed to list messages for user: " + userId, e);
        }
    }

    public List<Message> listLatestMessages(String userId, int maxResults) throws GmailServiceException {
        try {
            return gmailRepository.getLatestMessages(userId, maxResults);
        } catch (IOException e) {
            logger.error("Failed to list latest {} messages for user: {}", maxResults, userId, e);
            throw new GmailServiceException(String.format("Failed to list latest %d messages for user: %s", maxResults, userId), e);
        }
    }

    public List<Message> listMessagesFromSender(String userId, String senderEmail, FilterCriteriaDTO filterCriteriaDTO) throws GmailServiceException {
        FilterCriteria criteria = filterCriteriaMapper.toFilterCriteria(filterCriteriaDTO);
        String query = buildQuery(senderEmail, criteria);

        try {
            logger.info("Fetching messages with query: {}", query);
            return gmailRepository.getMessagesFromSender(userId, senderEmail, query);
        } catch (IOException e) {
            logger.error("Failed to list messages from sender: {} for user: {}. Query: {}", senderEmail, userId, query, e);
            throw new GmailServiceException("Failed to list messages", e);
        }
    }

    public void deleteMessagesFromSender(String userId, String senderEmail, FilterCriteriaDTO filterCriteriaDTO) throws GmailServiceException {
        try {
            FilterCriteria criteria = filterCriteriaMapper.toFilterCriteria(filterCriteriaDTO);
            String query = buildQuery(senderEmail, criteria);

            // Use the constructed query to delete messages
            gmailRepository.deleteMessagesFromSender(userId, senderEmail, query);
        } catch (IOException e) {
            logger.error("Failed to delete messages from sender: {} for user: {}. Query: {}", senderEmail, userId, null, e);
            throw new GmailServiceException(
                    String.format("Failed to delete messages for sender: %s for user: %s. Query: %s", senderEmail, userId, null), e
            );
        }
    }

    public void modifyMessagesLabels(String userId, String senderEmail, List<String> labelsToAdd, List<String> labelsToRemove) throws GmailServiceException {
        // The query must be built correctly
        String query = buildQuery(senderEmail, labelsToRemove);

        try {
            // Pass the correctly built query to gmailRepository
            gmailRepository.modifyMessagesLabels(userId, senderEmail, labelsToAdd, labelsToRemove, query);
        } catch (IOException e) {
            logger.error("Failed to modify labels for messages from sender: {} for user: {}. Query: {}", senderEmail, userId, query, e);
            throw new GmailServiceException(
                    String.format("Failed to modify labels for messages from sender: %s for user: %s. LabelsToAdd: %s, LabelsToRemove: %s. Query: %s",
                            senderEmail, userId, labelsToAdd, labelsToRemove, query
                    ),
                    e
            );
        }
    }

    public String getMessageBody(String userId, String messageId) throws GmailServiceException, MessageNotFoundException {
        try {
            return gmailRepository.getMessageBody(userId, messageId);
        } catch (IOException e) {
            logger.error("Failed to get message body for messageId: {} for user: {}", messageId, userId, e);
            throw new GmailServiceException(
                    String.format("Failed to get message body for messageId: %s for user: %s", messageId, userId),
                    e
            );
        }
    }

    public void markMessageAsRead(String userId, String messageId) throws GmailServiceException {
        try {
            gmailRepository.markMessageAsRead(userId, messageId);
        } catch (IOException e) {
            logger.error("Failed to mark message as read for messageId: {} for user: {}", messageId, userId, e);
            throw new GmailServiceException(
                    String.format("Failed to mark message as read for messageId: %s for user: %s", messageId, userId),
                    e
            );
        }
    }
}
