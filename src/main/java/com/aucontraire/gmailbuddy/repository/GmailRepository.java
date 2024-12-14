package com.aucontraire.gmailbuddy.repository;

import com.google.api.services.gmail.model.Message;

import java.io.IOException;
import java.util.List;

public interface GmailRepository {
    List<Message> getMessages(String userId) throws IOException;
    List<Message> getLatestMessages(String userId, long maxResults) throws IOException;
    List<Message> getMessagesFromSender(String userId, String senderEmail) throws IOException;
    void deleteMessagesFromSender(String userId, String senderEmail) throws IOException;
}
