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
import java.util.ArrayList;
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
        String hasAttachment = gmailQueryBuilder.hasAttachment(filterCriteria.getHasAttachment());
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
            throw new GmailServiceException("Failed to list messages", e);
        }
    }

    public List<Message> listLatestFiftyMessages(String userId) throws GmailServiceException {
        int maxResults = 50;
        try {
            return gmailRepository.getLatestMessages(userId, maxResults);
        } catch (IOException e) {
            logger.error("Failed to list latest fifty messages for user: {}", userId, e);
            throw new GmailServiceException("Failed to list latest fifty messages", e);
        }
    }

    public List<Message> listMessagesFromSender(String userId, String senderEmail, FilterCriteria filterCriteria) throws GmailServiceException {
        String query = buildQuery(senderEmail, filterCriteria);
        try {
            return gmailRepository.getMessagesFromSender(userId, senderEmail, query);
        } catch (IOException e) {
            logger.error("Failed to list messages from sender: {} for user: {}", senderEmail, userId, e);
            throw new GmailServiceException("Failed to list messages from sender", e);
        }
    }

    public void deleteMessagesFromSender(String userId, String senderEmail, FilterCriteria filterCriteria) throws GmailServiceException {
        String query = buildQuery(senderEmail, filterCriteria);
        try {
            gmailRepository.deleteMessagesFromSender(userId, senderEmail, query);
        } catch (IOException e) {
            logger.error("Failed to delete messages from sender: {} for user: {}", senderEmail, userId, e);
            throw new GmailServiceException("Failed to delete messages from sender", e);
        }
    }

    public void modifyMessagesLabels(String userId, String senderEmail, List<String> labelsToAdd, List<String> labelsToRemove) throws GmailServiceException {
        String query = buildQuery(senderEmail, labelsToRemove);
        try {
            gmailRepository.modifyMessagesLabels(userId, senderEmail, labelsToAdd, labelsToRemove, query);
        } catch (IOException e) {
            logger.error("Failed to modify labels for messages from sender: {} for user: {}", senderEmail, userId, e);
            throw new GmailServiceException("Failed to modify labels for messages", e);
        }
    }

    public String getMessageBody(String userId, String messageId) throws GmailServiceException, MessageNotFoundException {
        try {
            return gmailRepository.getMessageBody(userId, messageId);
        } catch (IOException e) {
            logger.error("Failed to get message body for message: {} for user: {}", messageId, userId, e);
            throw new MessageNotFoundException("Failed to get message body", e);
        }
    }
}
