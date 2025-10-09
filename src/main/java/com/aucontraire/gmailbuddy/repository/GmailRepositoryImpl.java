package com.aucontraire.gmailbuddy.repository;

import com.aucontraire.gmailbuddy.client.GmailClient;
import com.aucontraire.gmailbuddy.client.GmailBatchClient;
import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import com.aucontraire.gmailbuddy.exception.AuthenticationException;
import com.aucontraire.gmailbuddy.service.TokenProvider;
import com.aucontraire.gmailbuddy.service.BulkOperationResult;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;

@Component
public class GmailRepositoryImpl implements GmailRepository {

    private final GmailClient gmailClient;
    private final GmailBatchClient gmailBatchClient;
    private final TokenProvider tokenProvider;
    private final GmailBuddyProperties properties;
    private final Logger logger = LoggerFactory.getLogger(GmailRepositoryImpl.class);

    @Autowired
    public GmailRepositoryImpl(GmailClient gmailClient, GmailBatchClient gmailBatchClient,
                              TokenProvider tokenProvider, GmailBuddyProperties properties) {
        this.gmailClient = gmailClient;
        this.gmailBatchClient = gmailBatchClient;
        this.tokenProvider = tokenProvider;
        this.properties = properties;
    }

    private Gmail getGmailService() throws IOException, GeneralSecurityException {
        try {
            String accessToken = tokenProvider.getAccessToken();
            return gmailClient.createGmailService(accessToken);
        } catch (AuthenticationException e) {
            logger.error("Failed to retrieve access token for Gmail service", e);
            throw new IllegalStateException("Failed to authenticate with Gmail API: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Message> getMessages(String userId) throws IOException {
        try {
            var gmail = getGmailService();
            return gmail.users().messages().list(userId).execute().getMessages();
        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    @Override
    public List<Message> getLatestMessages(String userId, long maxResults) throws IOException {
        try {
            var gmail = getGmailService();
            return gmail.users().messages()
                    .list(userId)
                    .setMaxResults(maxResults)
                    .execute()
                    .getMessages();
        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    @Override
    public List<Message> getMessagesByFilterCriteria(String userId, String query) throws IOException {
        try {
            var gmail = getGmailService();
            return gmail.users().messages()
                    .list(userId)
                    .setQ(query)
                    .execute()
                    .getMessages();
        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    @Override
    public void deleteMessage(String userId, String messageId) throws IOException {
        try {
            var gmail = getGmailService();
            // Use batch client for consistency, even for single message
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, List.of(messageId));

            if (result.hasFailures()) {
                String errorMessage = result.getFailedOperations().get(messageId);
                throw new IOException("Failed to delete message " + messageId + ": " + errorMessage);
            }

            logger.debug("Successfully deleted message: {}", messageId);
        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    @Override
    public void deleteMessagesByFilterCriteria(String userId, String query) throws IOException {
        try {
            var gmail = getGmailService();

            // 1. Find all messages matching the query
            var messages = gmail.users().messages()
                    .list(userId)
                    .setQ(query)
                    .setMaxResults(properties.gmailApi().batchDeleteMaxResults())
                    .execute()
                    .getMessages();

            if (messages == null || messages.isEmpty()) {
                logger.info("Found 0 matching messages");
                return; // No messages to delete
            }
            logger.info("Found {} matching messages", messages.size());

            // 2. Extract message IDs for batch deletion
            List<String> messageIds = messages.stream()
                    .map(com.google.api.services.gmail.model.Message::getId)
                    .toList();

            // 3. Use batch delete - this eliminates the two-phase trash+delete approach
            // and reduces API calls from 2N to approximately N/100 batch requests
            logger.info("Executing batch delete operation for {} messages", messageIds.size());
            BulkOperationResult result = gmailBatchClient.batchDeleteMessages(gmail, userId, messageIds);

            // 4. Log the results
            logger.info("Batch delete completed: {} successful, {} failed out of {} total",
                       result.getSuccessCount(), result.getFailureCount(), result.getTotalOperations());

            if (result.hasFailures()) {
                logger.warn("Some deletions failed. Failed message IDs: {}",
                           String.join(", ", result.getFailedOperations().keySet()));
                // For bulk operations, we don't throw an exception for partial failures
                // The caller can check the result if needed
            }

        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    @Override
    public Map<String, String> getLabels(String userId) throws IOException {
        try {
            Gmail gmail = getGmailService();
            ListLabelsResponse response = gmail.users().labels().list(userId).execute();

            Map<String, String> labels = new HashMap<>();
            for (Label label : response.getLabels()) {
                labels.put(label.getName().toUpperCase(), label.getId());
            }
            return labels;
        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    private static List<String> getLabelIdList(Map<String, String> labelsMap, List<String> labelNameList) {
        List<String> labelIdList = new ArrayList<>();
        for (String labelName : labelNameList) {
            if (labelsMap.containsKey(labelName.toUpperCase())) {
                labelIdList.add(labelsMap.get(labelName.toUpperCase()));
            }
        }
        return labelIdList;
    }

    @Override
    public void modifyMessagesLabels(String userId, List<String> labelsToAdd, List<String> labelsToRemove, String query) throws IOException {
        try {
            var gmail = getGmailService();

            Map<String, String> labelsMap = getLabels(userId);
            List<String> labelIdsToAdd = getLabelIdList(labelsMap, labelsToAdd);
            List<String> labelIdsToRemove = getLabelIdList(labelsMap, labelsToRemove);

            ModifyMessageRequest mods = new ModifyMessageRequest().setAddLabelIds(labelIdsToAdd).setRemoveLabelIds(labelIdsToRemove);

            List<Message> messages = gmail.users().messages()
                    .list(userId)
                    .setQ(query)
                    .execute()
                    .getMessages();

            if (messages == null || messages.isEmpty()) {
                logger.info("Found 0 matching messages for label modification");
                return;
            }

            logger.info("Found {} matching messages for label modification", messages.size());

            // Extract message IDs for batch operation
            List<String> messageIds = messages.stream()
                    .map(Message::getId)
                    .toList();

            // Use batch modify labels operation
            BulkOperationResult result = gmailBatchClient.batchModifyLabels(gmail, userId, messageIds, mods);

            logger.info("Batch label modification completed: {} successful, {} failed out of {} total",
                       result.getSuccessCount(), result.getFailureCount(), result.getTotalOperations());

            if (result.hasFailures()) {
                logger.warn("Some label modifications failed. Failed message IDs: {}",
                           String.join(", ", result.getFailedOperations().keySet()));
            }

        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    @Override
    public String getMessageBody(String userId, String messageId) throws IOException {
        try {
            Gmail gmail = getGmailService();
            Message message = gmail.users().messages().get(userId, messageId).execute();
            logger.info("Message retrieved: {}", message.toPrettyString());
            return getMessageBodyFromParts(message.getPayload().getParts()); // Call helper function

        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    private String getMessageBodyFromParts(List<MessagePart> parts) {
        if (parts == null) {
            return ""; // No parts found
        }

        // Prioritize HTML content if there is any
        for (MessagePart part : parts) {
            if (part.getBody() != null && part.getBody().getData() != null) {
                String mimeType = part.getMimeType();
                if (properties.gmailApi().messageProcessing().mimeTypes().html().equals(mimeType)) {
                    String data = new String(Base64.getUrlDecoder().decode(part.getBody().getData()));
                    logger.info("Message is in text/html");
                    return data;
                }
            }
        }

        // Fallback to plain text content if HTML not found
        for (MessagePart part : parts) {
            if (part.getBody() != null && part.getBody().getData() != null) {
                String mimeType = part.getMimeType();
                if (properties.gmailApi().messageProcessing().mimeTypes().plain().equals(mimeType)) {
                    String data = new String(Base64.getUrlDecoder().decode(part.getBody().getData()));
                    logger.info("Message is in text/plain");
                    return data;
                }
            }
        }

        // Recursively check nested parts
        for (MessagePart part : parts) {
            String body = getMessageBodyFromParts(part.getParts());
            if (!body.isEmpty()) {
                return body;
            }
        }

        return ""; // No message body found
    }

    @Override
    public void markMessageAsRead(String userId, String messageId) throws IOException {
        try {
            var gmail = getGmailService();
            // Remove the UNREAD label from the message
            String unreadLabel = properties.gmailApi().messageProcessing().labels().unread();
            var mods = new com.google.api.services.gmail.model.ModifyMessageRequest().setRemoveLabelIds(List.of(unreadLabel));
            gmail.users().messages().modify(userId, messageId, mods).execute();
        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }
}
