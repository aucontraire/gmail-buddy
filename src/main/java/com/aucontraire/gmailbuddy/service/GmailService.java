package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.exception.GmailServiceException;
import com.aucontraire.gmailbuddy.exception.MessageNotFoundException;
import com.aucontraire.gmailbuddy.repository.GmailRepository;
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
    private final Logger logger = LoggerFactory.getLogger(GmailService.class);

    @Autowired
    public GmailService(GmailRepository gmailRepository, GmailQueryBuilder gmailQueryBuilder) {
        this.gmailRepository = gmailRepository;
        this.gmailQueryBuilder = gmailQueryBuilder;
    }

    public String buildQuery(String senderEmail, FilterCriteria filterCriteria) {
        String from = gmailQueryBuilder.from(senderEmail);
        String to = gmailQueryBuilder.to(filterCriteria.getTo());
        String subject = gmailQueryBuilder.subject(filterCriteria.getSubject());
        String hasAttachment = filterCriteria.getHasAttachment() != null && filterCriteria.getHasAttachment() ? gmailQueryBuilder.hasAttachment(true) : ""; // Include has:attachment only if true
        String additionalQuery = gmailQueryBuilder.query(filterCriteria.getQuery());
        String negatedQuery = gmailQueryBuilder.negatedQuery(filterCriteria.getNegatedQuery());

        return gmailQueryBuilder.build(from, to, subject, hasAttachment, additionalQuery, negatedQuery);
    }

    public String buildQuery(String senderEmail, List<String> labelsToRemove) {
        // Build query for labels to remove
        String labelsQuery = String.join(" AND ", labelsToRemove.stream()
                .map(label -> "label:" + label)
                .toList());

        String from = gmailQueryBuilder.from(senderEmail);
        String query = gmailQueryBuilder.query(labelsQuery);

        return gmailQueryBuilder.build(from, query);
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

    public List<Message> listMessagesFromSender(String userId, String senderEmail, FilterCriteria filterCriteria) throws GmailServiceException {
        String query = buildQuery(senderEmail, filterCriteria);
        try {
            logger.info("Generated Gmail query: {}", query);
            return gmailRepository.getMessagesFromSender(userId, senderEmail, query);
        } catch (IOException e) {
            logger.error("Failed to list messages from sender: {} for user: {}. Query: {}", senderEmail, userId, query, e);
            throw new GmailServiceException(
                    String.format("Failed to list messages from sender: %s for user: %s. Query: %s", senderEmail, userId, query),
                    e
            );
        }
    }

    public void deleteMessagesFromSender(String userId, String senderEmail, FilterCriteria filterCriteria) throws GmailServiceException {
        String query = buildQuery(senderEmail, filterCriteria);
        try {
            gmailRepository.deleteMessagesFromSender(userId, senderEmail, query);
            logger.info("Deleted messages from sender: {} for user: {}. Query: {}", senderEmail, userId, query);
        } catch (IOException e) {
            logger.error("Failed to delete messages from sender: {} for user: {}. Query: {}", senderEmail, userId, query, e);
            throw new GmailServiceException(
                    String.format("Failed to delete messages from sender: %s for user: %s. Query: %s", senderEmail, userId, query),
                    e
            );
        }
    }

    public void modifyMessagesLabels(String userId, String senderEmail, List<String> labelsToAdd, List<String> labelsToRemove) throws GmailServiceException {
        String query = buildQuery(senderEmail, labelsToRemove);
        try {
            gmailRepository.modifyMessagesLabels(userId, senderEmail, labelsToAdd, labelsToRemove, query);
            logger.info("Modified labels for messages from sender: {} for user: {}. LabelsToAdd: {}, LabelsToRemove: {}. Query: {}", senderEmail, userId, labelsToAdd, labelsToRemove, query);
        } catch (IOException e) {
            logger.error("Failed to modify labels for messages from sender: {} for user: {}. Query: {}", senderEmail, userId, query, e);
            throw new GmailServiceException(
                    String.format("Failed to modify labels for messages from sender: %s for user: %s. LabelsToAdd: %s, LabelsToRemove: %s. Query: %s", senderEmail, userId, labelsToAdd, labelsToRemove, query),
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
}
