package com.aucontraire.gmailbuddy.repository;

import com.aucontraire.gmailbuddy.service.BulkOperationResult;
import com.aucontraire.gmailbuddy.service.MessageListResult;
import com.google.api.services.gmail.model.Message;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface GmailRepository {
    List<Message> getMessages(String userId) throws IOException;
    List<Message> getLatestMessages(String userId, long maxResults) throws IOException;
    List<Message> getMessagesByFilterCriteria(String userId, String query) throws IOException;

    // Paginated versions of list methods
    MessageListResult getMessagesWithPagination(String userId, String pageToken, int limit) throws IOException;
    MessageListResult getLatestMessagesWithPagination(String userId, String pageToken, int maxResults) throws IOException;
    MessageListResult getMessagesByFilterCriteriaWithPagination(String userId, String query, String pageToken, int limit) throws IOException;

    BulkOperationResult deleteMessage(String userId, String messageId) throws IOException;
    BulkOperationResult deleteMessagesByFilterCriteria(String userId, String query) throws IOException;
    BulkOperationResult modifyMessagesLabels(String userId, List<String> labelsToAdd, List<String> labelsToRemove, String query) throws IOException;
    String getMessageBody(String userId, String messageId) throws IOException;
    Map<String, String> getLabels(String userId) throws IOException;
    BulkOperationResult markMessageAsRead(String userId, String messageId) throws IOException;
}
