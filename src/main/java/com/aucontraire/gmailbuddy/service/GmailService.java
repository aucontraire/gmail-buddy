package com.aucontraire.gmailbuddy.service;

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
    private final Logger logger = LoggerFactory.getLogger(GmailService.class);

    @Autowired
    public GmailService(GmailRepository gmailRepository) {
        this.gmailRepository = gmailRepository;
    }

    public List<Message> listMessages(String userId) throws IOException {
        return gmailRepository.getMessages(userId);
    }

    public List<Message> listLatestFiftyMessages(String userId) throws IOException { // New method
        int maxResults = 50;
        return gmailRepository.getLatestMessages(userId, maxResults);
    }

    public List<Message> listMessagesFromSender(String userId, String senderEmail, FilterCriteria filterCriteria) throws IOException {
        String query = new GmailQueryBuilder()
                .from(senderEmail)
                .to(filterCriteria.getTo())
                .subject(filterCriteria.getSubject())
                .hasAttachment(filterCriteria.getHasAttachment())
                .query(filterCriteria.getQuery())
                .negatedQuery(filterCriteria.getNegatedQuery())
                .build();
        logger.info(String.format("Query: %s", query));
        return gmailRepository.getMessagesFromSender(userId, senderEmail, query);
    }

    public void deleteMessagesFromSender(String userId, String senderEmail) throws IOException {
        gmailRepository.deleteMessagesFromSender(userId, senderEmail);
    }

    public String getMessageBody(String userId, String messageId) throws IOException {
        return gmailRepository.getMessageBody(userId, messageId);
    }
}
