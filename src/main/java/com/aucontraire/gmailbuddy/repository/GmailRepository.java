package com.aucontraire.gmailbuddy.repository;

import com.google.api.services.gmail.model.Message;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface GmailRepository {
    List<Message> getMessages(String userId) throws IOException;
    List<Message> getLatestMessages(String userId, long maxResults) throws IOException;
    List<Message> getMessagesByFilterCriteria(String userId, String query) throws IOException;
    void deleteMessage(String userId, String messageId) throws IOException;
    void deleteMessagesByFilterCriteria(String userId, String query) throws IOException;
    void modifyMessagesLabels(String userId, List<String> labelsToAdd, List<String> labelsToRemove, String query) throws IOException;
    String getMessageBody(String userId, String messageId) throws IOException;
    Map<String, String> getLabels(String userId) throws IOException;
    void markMessageAsRead(String userId, String messageId) throws IOException;
}
