package com.aucontraire.gmailbuddy.repository;

import com.aucontraire.gmailbuddy.client.GmailClient;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.List;

@Component
public class GmailRepositoryImpl implements GmailRepository {

    private final GmailClient gmailClient;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final Logger logger = LoggerFactory.getLogger(GmailRepositoryImpl.class);

    @Autowired
    public GmailRepositoryImpl(GmailClient gmailClient, OAuth2AuthorizedClientService authorizedClientService) {
        this.gmailClient = gmailClient;
        this.authorizedClientService = authorizedClientService;
    }

    private Gmail getGmailService() throws IOException, GeneralSecurityException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient("google", authentication.getName());
        String accessToken = client.getAccessToken().getTokenValue();
        return gmailClient.createGmailService(accessToken);
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
    public List<Message> getMessagesFromSender(String userId, String senderEmail, String query) throws IOException {
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
    public void deleteMessagesFromSender(String userId, String senderEmail) throws IOException {
        try {
            var gmail = getGmailService();

            // 1. Find all messages from the given sender
            var messages = gmail.users().messages()
                    .list(userId)
                    .setQ("from:" + senderEmail)
                    .setMaxResults(500L) // Optional: limit for batch operations
                    .execute()
                    .getMessages();

            if (messages == null || messages.isEmpty()) {
                logger.info("Found 0 matching messages");
                return; // No messages to delete
            }
            logger.info(String.format("Found %d matching messages", messages.size()));

            // 2. For each message, first move it to Trash, then permanently delete it
            logger.info("Moving messages to trash");
            for (var message : messages) {
                // Move the message to Trash
                gmail.users().messages().trash(userId, message.getId()).execute();
            }

            logger.info("Deleting messages");
            for (var message : messages) {
                // Now permanently delete it
                gmail.users().messages().delete(userId, message.getId()).execute();
            }

        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    // GmailRepositoryImpl.java
    @Override
    public String getMessageBody(String userId, String messageId) throws IOException {
        try {
            Gmail gmail = getGmailService();
            Message message = gmail.users().messages().get(userId, messageId).execute();
            return getMessageBodyFromParts(message.getPayload().getParts()); // Call helper function

        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    private String getMessageBodyFromParts(List<MessagePart> parts) {
        if (parts == null) {
            return ""; // No parts found
        }

        for (MessagePart part : parts) {
            // Check if this part has a body
            if (part.getBody() != null && part.getBody().getData() != null) {
                // You can handle different MIME types here, for example:
                if (part.getMimeType().equals("text/plain")) {
                    logger.info("Message is in text/plain");
                    return new String(Base64.getUrlDecoder().decode(part.getBody().getData()));
                } else if (part.getMimeType().equals("text/html")) {
                    // Decode and return HTML content
                    logger.info("Message is in text/html");
                    return new String(Base64.getUrlDecoder().decode(part.getBody().getData()));
                } else {
                    // Handle other MIME types or log them
                    logger.info("Message is in other MIME type");
                    System.out.println("Unsupported MIME type: " + part.getMimeType());
                }
            }

            // Recursively process nested parts
            String body = getMessageBodyFromParts(part.getParts());
            if (!body.isEmpty()) {
                return body;
            }
        }

        return ""; // No body found in this part or its subparts
    }
}
