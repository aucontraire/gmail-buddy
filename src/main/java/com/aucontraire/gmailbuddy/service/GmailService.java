package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.repository.GmailRepository;
import com.google.api.services.gmail.model.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class GmailService {

    private final GmailRepository gmailRepository;

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

    public List<Message> listMessagesFromSender(String userId, String senderEmail) throws IOException {
        return gmailRepository.getMessagesFromSender(userId, senderEmail);
    }

    public void deleteMessagesFromSender(String userId, String senderEmail) throws IOException {
        gmailRepository.deleteMessagesFromSender(userId, senderEmail);
    }

    public String getMessageBody(String userId, String messageId) throws IOException {
        return gmailRepository.getMessageBody(userId, messageId);
    }
}
